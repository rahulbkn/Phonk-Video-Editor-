"""Model health registry.

Tracks request count, successes, failures, timeouts, rate limits, average
response time, last failure and a cooldown. Unhealthy models are removed from
routing until a cooldown passes; a lightweight health check then re-admits
them on success.

Persisted to disk so the worker survives restarts without losing health state.
"""

from __future__ import annotations

import json
import os
import time
from pathlib import Path

from .config import Config

HEALTHY = "HEALTHY"
DEGRADED = "DEGRADED"
UNAVAILABLE = "UNAVAILABLE"

STATUSES = (HEALTHY, DEGRADED, UNAVAILABLE)


class ModelHealth:
    def __init__(
        self,
        name: str,
        consecutive_threshold: int = 3,
        cooldown_seconds: int = 900,
    ):
        self.name = name
        self.consecutive_threshold = consecutive_threshold
        self.cooldown_seconds = cooldown_seconds
        self.request_count = 0
        self.success_count = 0
        self.failure_count = 0
        self.timeout_count = 0
        self.rate_limit_count = 0
        self.total_response_seconds = 0.0
        self.consecutive_failures = 0
        self.last_failure: str | None = None
        self.last_failure_time: float | None = None
        self.cooldown_until: float = 0.0
        self.status = HEALTHY

    # ---- records ----
    def record_start(self) -> float:
        self.request_count += 1
        return time.time()

    def record_success(self, started: float) -> None:
        self.success_count += 1
        self.total_response_seconds += max(0.0, time.time() - started)
        self.consecutive_failures = 0
        self.status = HEALTHY
        self.cooldown_until = 0.0

    def record_failure(self, kind: str, started: float, message: str = "") -> None:
        self.failure_count += 1
        self.consecutive_failures += 1
        self.last_failure = kind
        self.last_failure_time = time.time()
        self.total_response_seconds += max(0.0, time.time() - started)
        if kind == "timeout":
            self.timeout_count += 1
        elif kind == "rate_limit":
            self.rate_limit_count += 1
        self._maybe_degrade()

    def record_rate_limit(self, started: float) -> None:
        self.record_failure("rate_limit", started, "HTTP 429 / rate limit")

    def _maybe_degrade(self) -> None:
        if self.consecutive_failures >= self.consecutive_threshold:
            self.status = UNAVAILABLE
            self.cooldown_until = time.time() + self.cooldown_seconds
        elif self.consecutive_failures > 0:
            self.status = DEGRADED

    # ---- queries ----
    @property
    def avg_response_seconds(self) -> float:
        if self.success_count == 0:
            return 0.0
        return self.total_response_seconds / self.success_count

    def is_available(self, now: float | None = None) -> bool:
        now = now if now is not None else time.time()
        if self.status == UNAVAILABLE:
            if now >= self.cooldown_until:
                return True  # cooldown elapsed — candidate for re-health-check
            return False
        return True

    def in_cooldown(self, now: float | None = None) -> bool:
        now = now if now is not None else time.time()
        return self.status == UNAVAILABLE and now < self.cooldown_until

    def ready_for_health_check(self, now: float | None = None) -> bool:
        now = now if now is not None else time.time()
        return self.status == UNAVAILABLE and now >= self.cooldown_until

    def mark_healthy(self) -> None:
        self.status = HEALTHY
        self.consecutive_failures = 0
        self.cooldown_until = 0.0

    # ---- persistence ----
    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "status": self.status,
            "request_count": self.request_count,
            "success_count": self.success_count,
            "failure_count": self.failure_count,
            "timeout_count": self.timeout_count,
            "rate_limit_count": self.rate_limit_count,
            "total_response_seconds": self.total_response_seconds,
            "consecutive_failures": self.consecutive_failures,
            "last_failure": self.last_failure,
            "last_failure_time": self.last_failure_time,
            "cooldown_until": self.cooldown_until,
        }

    @classmethod
    def from_dict(cls, data: dict, cooldown_seconds: int = 900) -> "ModelHealth":
        h = cls(data.get("name", "?"), cooldown_seconds=cooldown_seconds)
        for k in ("status", "request_count", "success_count", "failure_count",
                  "timeout_count", "rate_limit_count", "total_response_seconds",
                  "consecutive_failures", "last_failure", "last_failure_time",
                  "cooldown_until"):
            if k in data:
                setattr(h, k, data[k])
        return h


class HealthRegistry:
    def __init__(self, cfg: Config, state_path: Path | str):
        self.cfg = cfg
        self.state_path = Path(state_path)
        self.models: dict[str, ModelHealth] = {}
        self.load()

    def _ensure(self, name: str) -> ModelHealth:
        if name not in self.models:
            self.models[name] = ModelHealth(name, cooldown_seconds=self.cfg.health_cooldown_seconds)
        return self.models[name]

    def is_available(self, name: str) -> bool:
        return self._ensure(name).is_available()

    def record_start(self, name: str) -> float:
        return self._ensure(name).record_start()

    def record_success(self, name: str, started: float) -> None:
        self._ensure(name).record_success(started)
        self.save()

    def record_failure(self, name: str, kind: str, started: float, message: str = "") -> None:
        self._ensure(name).record_failure(kind, started, message)
        self.save()

    def ready_for_health_check(self, name: str) -> bool:
        return self._ensure(name).ready_for_health_check()

    def mark_healthy(self, name: str) -> None:
        self._ensure(name).mark_healthy()
        self.save()

    def summary(self) -> dict[str, dict]:
        return {n: h.to_dict() for n, h in self.models.items()}

    def load(self) -> None:
        if not self.state_path.exists():
            return
        try:
            with open(self.state_path, encoding="utf-8") as fh:
                data = json.load(fh)
        except (json.JSONDecodeError, OSError):
            return
        for name, d in data.items():
            h = ModelHealth.from_dict(d, cooldown_seconds=self.cfg.health_cooldown_seconds)
            if name in self.cfg.models:
                self.models[name] = h

    def save(self) -> None:
        try:
            self.state_path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.state_path.with_suffix(".json.tmp")
            with open(tmp, "w", encoding="utf-8") as fh:
                json.dump(self.summary(), fh, indent=2)
            os.replace(tmp, self.state_path)
        except OSError:
            pass
