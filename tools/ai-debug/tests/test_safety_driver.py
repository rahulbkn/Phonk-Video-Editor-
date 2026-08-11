"""Tests for safety checks and the OpenCode JSON event parser."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug.driver import OpenCodeDriver  # noqa: E402
from ai_debug import safety  # noqa: E402


def _git(repo: Path, *args: str):
    return subprocess.run(["git", *args], cwd=str(repo), capture_output=True, text=True, check=True)


def _init_repo(tmp: Path) -> Path:
    repo = tmp / "repo"
    repo.mkdir()
    _git(repo, "init", "-q", "-b", "main")
    _git(repo, "config", "user.email", "t@t")
    _git(repo, "config", "user.name", "t")
    return repo


class SafetyTest(unittest.TestCase):
    def test_forbidden_secret_file(self):
        with tempfile.TemporaryDirectory() as d:
            repo = _init_repo(Path(d))
            (repo / "local.properties").write_text("sdk.dir=/x")
            is_safe, violations = safety.validate_diff(repo)
            self.assertFalse(is_safe)
            self.assertTrue(any("local.properties" in v for v in violations))

    def test_forbidden_workflow_edit(self):
        with tempfile.TemporaryDirectory() as d:
            repo = _init_repo(Path(d))
            wf = repo / ".github" / "workflows"
            wf.mkdir(parents=True)
            (wf / "ci.yml").write_text("name: x\n")
            is_safe, violations = safety.validate_diff(repo)
            self.assertFalse(is_safe)

    def test_ignore_added(self):
        with tempfile.TemporaryDirectory() as d:
            repo = _init_repo(Path(d))
            (repo / "A.kt").write_text("// @Ignore\nclass A\n")
            is_safe, violations = safety.validate_diff(repo)
            self.assertFalse(is_safe)
            self.assertTrue(any("@Ignore" in v for v in violations))

    def test_deleted_test_detected(self):
        with tempfile.TemporaryDirectory() as d:
            repo = _init_repo(Path(d))
            (repo / "A.kt").write_text("@Test fun a() {}\n")
            _git(repo, "add", ".")
            _git(repo, "commit", "-m", "init")
            (repo / "A.kt").write_text("fun b() {}\n")
            is_safe, violations = safety.validate_diff(repo)
            self.assertFalse(is_safe)
            self.assertTrue(any("deleted" in v.lower() for v in violations))

    def test_clean_diff_passes(self):
        with tempfile.TemporaryDirectory() as d:
            repo = _init_repo(Path(d))
            (repo / "B.kt").write_text("fun foo() = 1\n")
            is_safe, violations = safety.validate_diff(repo)
            self.assertTrue(is_safe, violations)

    def test_discard_changes(self):
        with tempfile.TemporaryDirectory() as d:
            repo = _init_repo(Path(d))
            (repo / "keep.txt").write_text("orig\n")
            _git(repo, "add", ".")
            _git(repo, "commit", "-m", "init")
            (repo / "keep.txt").write_text("changed\n")
            (repo / "new.txt").write_text("new\n")
            safety.discard_changes(repo)
            self.assertEqual((repo / "keep.txt").read_text(), "orig\n")
            self.assertFalse((repo / "new.txt").exists())


class DriverParserTest(unittest.TestCase):
    def _events(self, *lines: str) -> list[str]:
        return list(lines)

    def test_text_events_extracted(self):
        lines = self._events(
            json.dumps({"type": "step_start", "part": {"type": "step-start"}}),
            json.dumps({"type": "text", "part": {"type": "text", "text": "Root cause is X."}}),
            json.dumps({"type": "step_finish", "part": {"reason": "stop"}}),
        )
        text = OpenCodeDriver._extract_text(OpenCodeDriver, lines)
        self.assertIn("Root cause is X.", text)

    def test_empty_when_no_text(self):
        lines = self._events(json.dumps({"type": "step_start", "part": {}}))
        text = OpenCodeDriver._extract_text(OpenCodeDriver, lines)
        self.assertEqual(text, "")

    def test_error_event_detected(self):
        lines = self._events(
            json.dumps({"type": "error", "error": {"name": "APIError",
                        "data": {"message": "Rate limit exceeded", "statusCode": 429}}}),
        )
        msg = OpenCodeDriver._has_error(lines)
        self.assertIn("Rate limit", msg)

    def test_ignores_garbage_lines(self):
        lines = ["not json at all", "  ", json.dumps({"type": "text", "part": {"text": "ok"}})]
        text = OpenCodeDriver._extract_text(OpenCodeDriver, lines)
        self.assertEqual(text, "ok")


if __name__ == "__main__":
    unittest.main()
