"""Tests for config loading, task classification and model routing."""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug.config import AIDebugConfigError, Config, load_config  # noqa: E402
from ai_debug.router import ModelRouter, candidates_for_task, classify_task  # noqa: E402


def make_config(model_overrides: dict | None = None) -> Config:
    data = {
        "provider": "opencode",
        "free_only": True,
        "max_attempts": 3,
        "model": {
            "opencode/longcat-2.0-free": {"tasks": ["LARGE_REPOSITORY_ANALYSIS", "REASONING"], "priority": 1},
            "opencode/nemotron-3-ultra-free": {"tasks": ["REASONING", "CRASH_ANALYSIS"], "priority": 2},
            "opencode/deepseek-v4-flash-free": {"tasks": ["KOTLIN", "GRADLE_BUILD", "SIMPLE_BUG_FIX"], "priority": 3},
            "opencode/ling-3.0-tiny-free": {"tasks": ["SIMPLE_BUG_FIX"], "priority": 4},
        },
    }
    if model_overrides:
        data["model"].update(model_overrides)
    return Config(data)


class ConfigTest(unittest.TestCase):
    def test_load_default_config(self):
        cfg = load_config()
        self.assertEqual(cfg.provider, "opencode")
        self.assertTrue(cfg.free_only)
        self.assertGreaterEqual(len(cfg.models), 8)
        for name, meta in cfg.models.items():
            self.assertTrue(name.startswith("opencode/"))

    def test_rejects_non_free_provider_model(self):
        cfg = Config({"provider": "opencode", "model": {"google/gemini-x": {"tasks": ["KOTLIN"]}}})
        with self.assertRaises(AIDebugConfigError):
            cfg.verify()

    def test_rejects_unknown_task(self):
        cfg = Config({"provider": "opencode", "model": {"opencode/x": {"tasks": ["NOPE"]}}})
        with self.assertRaises(AIDebugConfigError):
            cfg.verify()

    def test_missing_config_file(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(AIDebugConfigError):
                load_config(Path(d) / "nope.json")

    def test_bad_json(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "bad.json"
            p.write_text("{ not json")
            with self.assertRaises(AIDebugConfigError):
                load_config(p)


class ClassifyTest(unittest.TestCase):
    def test_kotlin(self):
        self.assertEqual(classify_task("Kotlin code crash in EditorViewModel.kt"), "KOTLIN")

    def test_cpp_ffmpeg(self):
        self.assertEqual(classify_task("SIGSEGV in ffmpeg avcodec"), "FFMPEG")
        self.assertEqual(classify_task("jni native crash libmp3lame.so"), "C_CPP")

    def test_gradle(self):
        self.assertEqual(classify_task("Execution failed for task ':app:compileDebugKotlin'"), "GRADLE_BUILD")

    def test_crash_analysis(self):
        self.assertEqual(classify_task("FATAL EXCEPTION in AndroidRuntime"), "CRASH_ANALYSIS")

    def test_fallback(self):
        self.assertEqual(classify_task(""), "GENERAL_DEBUGGING")
        self.assertEqual(classify_task("random text with no signals"), "GENERAL_DEBUGGING")


class RouterTest(unittest.TestCase):
    def test_candidates_ordered_by_priority(self):
        cfg = make_config()
        got = candidates_for_task(cfg, "SIMPLE_BUG_FIX")
        self.assertEqual(got, [
            "opencode/deepseek-v4-flash-free",
            "opencode/ling-3.0-tiny-free",
        ])

    def test_pick_skips_unhealthy(self):
        cfg = make_config()

        class Health:
            def is_available(self, name):
                return name != "opencode/deepseek-v4-flash-free"

        router = ModelRouter(cfg, Health())
        picked = router.pick("SIMPLE_BUG_FIX")
        self.assertEqual(picked, "opencode/ling-3.0-tiny-free")

    def test_pick_falls_back_to_any_healthy(self):
        cfg = make_config()

        class Health:
            def is_available(self, name):
                return name == "opencode/longcat-2.0-free"

        router = ModelRouter(cfg, Health())
        picked = router.pick("KOTLIN")  # deepseek is unavailable, longcat doesn't claim KOTLIN
        self.assertEqual(picked, "opencode/longcat-2.0-free")

    def test_pick_raises_when_none_available(self):
        cfg = make_config()

        class Health:
            def is_available(self, name):
                return False

        router = ModelRouter(cfg, Health())
        with self.assertRaises(Exception):
            router.pick("KOTLIN")


if __name__ == "__main__":
    unittest.main()
