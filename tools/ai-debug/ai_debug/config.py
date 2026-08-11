"""Configuration loading for the ai-debug system.

Centralized model pool lives in config/free_models.json so models can be
added/removed/reordered without touching code. Env vars provide runtime
secrets (never logged).
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

DEFAULT_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "free_models.json"


class AIDebugConfigError(RuntimeError):
    pass


def _read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise AIDebugConfigError(f"config file not found: {path}")
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except (json.JSONDecodeError, OSError) as exc:
        raise AIDebugConfigError(f"could not read config {path}: {exc}") from exc


class Config:
    def __init__(self, data: dict[str, Any]):
        self.provider: str = data.get("provider", "opencode")
        self.free_only: bool = bool(data.get("free_only", True))
        self.max_attempts: int = int(data.get("max_attempts", 3))
        self.default_task: str = data.get("default_task", "GENERAL_DEBUGGING")
        self.request_timeout_seconds: int = int(data.get("request_timeout_seconds", 600))
        self.health_check_timeout_seconds: int = int(data.get("health_check_timeout_seconds", 120))
        self.health_cooldown_seconds: int = int(data.get("health_cooldown_seconds", 900))
        self.models: dict[str, dict[str, Any]] = data.get("model", {})
        self.task_categories: set[str] = {
            "ANDROID_UI", "KOTLIN", "JAVA", "GRADLE_BUILD", "C_CPP", "FFMPEG",
            "MEDIA_CODEC", "FIREBASE_TEST", "CRASH_ANALYSIS", "LOGCAT_ANALYSIS",
            "LARGE_REPOSITORY_ANALYSIS", "MULTI_FILE_REFACTOR", "SIMPLE_BUG_FIX",
            "TEST_FAILURE", "CI_FAILURE", "GENERAL_DEBUGGING", "REASONING",
        }

    @property
    def model_names(self) -> list[str]:
        return list(self.models.keys())

    def verify(self) -> None:
        if self.free_only and not self.provider:
            raise AIDebugConfigError("free_only mode requires a provider")
        if not self.models:
            raise AIDebugConfigError("model pool is empty — nothing to route to")
        for name, meta in self.models.items():
            if not name.startswith(f"{self.provider}/"):
                raise AIDebugConfigError(
                    f"model '{name}' does not belong to provider '{self.provider}'"
                )
            tasks = meta.get("tasks", [])
            if not isinstance(tasks, list) or not tasks:
                raise AIDebugConfigError(f"model '{name}' must declare a non-empty tasks list")
            for t in tasks:
                if t not in self.task_categories:
                    raise AIDebugConfigError(f"model '{name}' uses unknown task category '{t}'")


def load_config(path: Path | str | None = None) -> Config:
    cfg_path = Path(path) if path else DEFAULT_CONFIG_PATH
    data = _read_json(cfg_path)
    cfg = Config(data)
    cfg.verify()
    return cfg


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def env_bool(name: str, default: bool = False) -> bool:
    val = os.environ.get(name)
    if val is None:
        return default
    return val.strip().lower() in {"1", "true", "yes", "on"}
