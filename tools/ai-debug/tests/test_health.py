"""Tests for the model health registry."""

import json
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug.config import Config  # noqa: E402
from ai_debug.health import DEGRADED, HEALTHY, UNAVAILABLE, HealthRegistry, ModelHealth  # noqa: E402


class ModelHealthTest(unittest.TestCase):
    def test_starts_healthy(self):
        h = ModelHealth("opencode/x", cooldown_seconds=10)
        self.assertEqual(h.status, HEALTHY)
        self.assertTrue(h.is_available())

    def test_single_failure_degrades(self):
        h = ModelHealth("opencode/x", consecutive_threshold=3, cooldown_seconds=10)
        h.record_failure("timeout", time.time())
        self.assertEqual(h.status, DEGRADED)
        self.assertTrue(h.is_available())

    def test_threshold_marks_unavailable_and_cooldown(self):
        h = ModelHealth("opencode/x", consecutive_threshold=3, cooldown_seconds=10)
        for _ in range(3):
            h.record_failure("error", time.time())
        self.assertEqual(h.status, UNAVAILABLE)
        self.assertFalse(h.is_available())
        self.assertTrue(h.in_cooldown())

    def test_success_resets_and_re_healths(self):
        h = ModelHealth("opencode/x", consecutive_threshold=3, cooldown_seconds=10)
        for _ in range(3):
            h.record_failure("error", time.time())
        h.mark_healthy()
        self.assertEqual(h.status, HEALTHY)
        self.assertTrue(h.is_available())
        self.assertEqual(h.consecutive_failures, 0)

    def test_rate_limit_tracking(self):
        h = ModelHealth("opencode/x", consecutive_threshold=3, cooldown_seconds=10)
        h.record_rate_limit(time.time())
        self.assertEqual(h.rate_limit_count, 1)
        self.assertEqual(h.last_failure, "rate_limit")

    def test_avg_response(self):
        h = ModelHealth("opencode/x")
        started = h.record_start()
        h.record_success(started)
        self.assertEqual(h.success_count, 1)
        self.assertGreaterEqual(h.avg_response_seconds, 0)

    def test_serialization_roundtrip(self):
        h = ModelHealth("opencode/x", cooldown_seconds=10)
        for _ in range(3):
            h.record_failure("timeout", time.time())
        d = h.to_dict()
        h2 = ModelHealth.from_dict(d, cooldown_seconds=10)
        self.assertEqual(h2.status, UNAVAILABLE)
        self.assertEqual(h2.timeout_count, 3)


class HealthRegistryTest(unittest.TestCase):
    def _cfg(self):
        return Config({"provider": "opencode", "free_only": True,
                       "model": {"opencode/x": {"tasks": ["KOTLIN"], "priority": 1}}})

    def test_persistence(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "health.json"
            reg = HealthRegistry(self._cfg(), path)
            started = reg.record_start("opencode/x")
            reg.record_failure("opencode/x", "timeout", started)
            reg.record_failure("opencode/x", "timeout", time.time())
            reg.record_failure("opencode/x", "timeout", time.time())
            self.assertFalse(reg.is_available("opencode/x"))

            reg2 = HealthRegistry(self._cfg(), path)
            self.assertEqual(reg2.summary()["opencode/x"]["timeout_count"], 3)

    def test_save_after_success(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "health.json"
            reg = HealthRegistry(self._cfg(), path)
            started = reg.record_start("opencode/x")
            reg.record_success("opencode/x", started)
            data = json.loads(path.read_text())
            self.assertEqual(data["opencode/x"]["success_count"], 1)


if __name__ == "__main__":
    unittest.main()
