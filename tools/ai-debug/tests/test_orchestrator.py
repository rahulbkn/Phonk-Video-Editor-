"""Integration-style tests for the orchestrator with mocked model/build/gh.

Covers:
  - full pass: model ok -> safe -> build ok -> pushed for CI
  - safety violation path
  - local build failure path
  - model failure fallback
  - max attempts -> issue opened
"""

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug.config import Config  # noqa: E402
from ai_debug.driver import RESULT_OK  # noqa: E402
from ai_debug.orchestrator import DebugOrchestrator  # noqa: E402


def make_cfg(max_attempts=3) -> Config:
    return Config({
        "provider": "opencode",
        "free_only": True,
        "max_attempts": max_attempts,
        "request_timeout_seconds": 60,
        "model": {
            "opencode/deepseek-v4-flash-free": {"tasks": ["KOTLIN", "SIMPLE_BUG_FIX"], "priority": 1},
            "opencode/ling-3.0-tiny-free": {"tasks": ["SIMPLE_BUG_FIX"], "priority": 2},
        },
    })


def _git(repo: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["git", *args], cwd=str(repo), capture_output=True, text=True)


def _seed_repo(tmp: Path) -> tuple[Path, Path]:
    remote = tmp / "remote.git"
    remote.mkdir()
    _git(remote, "init", "-q", "--bare")
    _git(remote, "symbolic-ref", "HEAD", "refs/heads/main")
    seed = tmp / "seed"
    seed.mkdir()
    _git(seed, "init", "-q", "-b", "main")
    _git(seed, "config", "user.email", "t@t")
    _git(seed, "config", "user.name", "t")
    (seed / "gradlew").write_text("#!/bin/sh\nexit 0\n")
    os.chmod(seed / "gradlew", 0o755)
    (seed / "settings.gradle.kts").write_text("rootProject.name = 'test'\n")
    (seed / "A.kt").write_text("fun main() {}\n")
    _git(seed, "add", ".")
    _git(seed, "commit", "-m", "init")
    _git(seed, "remote", "add", "origin", str(remote))
    _git(seed, "push", "-q", "-u", "origin", "main")
    return seed, remote


class _FakeHealth:
    def is_available(self, name):
        return True

    def record_start(self, name):
        return 0.0

    def record_success(self, name, started):
        pass

    def record_failure(self, name, kind, started, message=""):
        pass


class FakeDriver:
    def __init__(self, behaviors):
        self.behaviors = list(behaviors)
        self.calls = []

    def run(self, *, model, cwd, prompt_file, **kwargs):
        self.calls.append(model)
        b = self.behaviors.pop(0) if self.behaviors else {"result": RESULT_OK, "text": "done"}
        if "exc" in b:
            raise b["exc"]
        return b.get("result", RESULT_OK), b.get("text", "done")


def _summary():
    return {
        "status": "failed", "device": "redfin", "androidVersion": "30",
        "failures": ["com.example.CrashTest test failed"],
        "crashes": [{"context": "FATAL EXCEPTION main"}],
        "stacktrace": "FATAL EXCEPTION\n  at com.example.MainActivity.onCreate",
    }


class OrchestratorTest(unittest.TestCase):
    def _setup_env(self):
        os.environ["GITHUB_TOKEN"] = "fake-token"
        os.environ["GH_TOKEN"] = "fake-token"

    def _gh_patch(self, gh_mock, remote):
        gh_mock.require_gh = lambda: None
        gh_mock.is_fix_branch = lambda b: b.startswith("feature/ai-fix-")
        gh_mock.fix_branch_name = lambda rid: f"feature/ai-fix-{rid}"
        gh_mock.assert_safe_branch = lambda b: None
        gh_mock.create_issue = lambda repo, title, body: "https://github.com/x/issues/1"

    def _clone_patch(self, remote):
        def fake_clone(workdir, repo, branch):
            repo_dir = workdir / "repo"
            subprocess.run(["git", "clone", "-q", str(remote), str(repo_dir)], check=True)
            subprocess.run(["git", "config", "user.email", "t@t"], cwd=str(repo_dir), check=True)
            subprocess.run(["git", "config", "user.name", "t"], cwd=str(repo_dir), check=True)
            if branch != "main":
                subprocess.run(["git", "checkout", "-b", branch], cwd=str(repo_dir), check=True)
            return repo_dir
        from unittest.mock import MagicMock
        m = MagicMock(side_effect=fake_clone)
        return m

    def test_full_pass_pushes_branch(self):
        with tempfile.TemporaryDirectory() as d:
            self._setup_env()
            tmp = Path(d)
            _, remote = _seed_repo(tmp)
            cfg = make_cfg()
            driver = FakeDriver([{"result": RESULT_OK, "text": "fixed it"}])
            orch = DebugOrchestrator(cfg, _FakeHealth(), driver=driver)

            with patch("ai_debug.orchestrator.gh") as gh_mock, \
                 patch("ai_debug.orchestrator._clone_repo", self._clone_patch(remote)) as cl, \
                 patch("ai_debug.orchestrator._run_local_build", return_value=True) as build, \
                 patch("ai_debug.orchestrator.safety") as safety_mock:
                self._gh_patch(gh_mock, remote)
                gh_mock.create_branch_and_commit = lambda r, b, m: None
                gh_mock.push_branch = lambda r, b: None
                safety_mock.validate_diff = lambda repo: (True, [])
                safety_mock.discard_changes = lambda repo: None

                result = orch.handle_failure("owner/repo", _summary(), "run-1")
                cl.assert_called_once()

            self.assertEqual(result["status"], "pushed_for_ci_verification")
            self.assertEqual(result["attempts"], 1)
            self.assertIn("feature/ai-fix-run-1", result["branch"])
            self.assertEqual(build.call_count, 1)

    def test_safety_violation_then_build_pass(self):
        with tempfile.TemporaryDirectory() as d:
            self._setup_env()
            tmp = Path(d)
            _, remote = _seed_repo(tmp)
            cfg = make_cfg()

            driver_calls = {"n": 0}

            class ViolatingDriver(FakeDriver):
                def run(self, *, model, cwd, prompt_file, **kwargs):
                    driver_calls["n"] += 1
                    if driver_calls["n"] == 1:
                        (Path(cwd) / "local.properties").write_text("sdk=/x")
                    return RESULT_OK, "done"

            driver = ViolatingDriver([])
            orch = DebugOrchestrator(cfg, _FakeHealth(), driver=driver)

            # attempt 1: real safety check flags local.properties -> discard
            # removes it; attempt 2: clean -> pass
            with patch("ai_debug.orchestrator.gh") as gh_mock, \
                 patch("ai_debug.orchestrator._clone_repo", self._clone_patch(remote)), \
                 patch("ai_debug.orchestrator._run_local_build", return_value=True):
                self._gh_patch(gh_mock, remote)
                gh_mock.create_branch_and_commit = lambda r, b, m: None
                gh_mock.push_branch = lambda r, b: None

                result = orch.handle_failure("owner/repo", _summary(), "run-2")

            self.assertEqual(result["status"], "pushed_for_ci_verification")
            self.assertEqual(result["attempts"], 2)

    def test_max_attempts_opens_issue(self):
        with tempfile.TemporaryDirectory() as d:
            self._setup_env()
            tmp = Path(d)
            _, remote = _seed_repo(tmp)
            cfg = make_cfg(max_attempts=2)
            driver = FakeDriver([
                {"result": RESULT_OK, "text": "fix"},
                {"result": RESULT_OK, "text": "fix2"},
            ])
            orch = DebugOrchestrator(cfg, _FakeHealth(), driver=driver)
            issue_count = {"n": 0}

            def create_issue(repo, title, body):
                issue_count["n"] += 1
                return "https://github.com/x/issues/1"

            with patch("ai_debug.orchestrator.gh") as gh_mock, \
                 patch("ai_debug.orchestrator._clone_repo", self._clone_patch(remote)), \
                 patch("ai_debug.orchestrator._run_local_build", return_value=False), \
                 patch("ai_debug.orchestrator.safety") as safety_mock:
                self._gh_patch(gh_mock, remote)
                gh_mock.create_issue = create_issue
                safety_mock.validate_diff = lambda repo: (True, [])
                safety_mock.discard_changes = lambda repo: None

                result = orch.handle_failure("owner/repo", _summary(), "run-3")

            self.assertEqual(result["status"], "stopped_max_attempts")
            self.assertEqual(result["attempts"], 2)
            self.assertEqual(issue_count["n"], 1)

    def test_model_failure_falls_back_within_attempt(self):
        with tempfile.TemporaryDirectory() as d:
            self._setup_env()
            tmp = Path(d)
            _, remote = _seed_repo(tmp)
            cfg = make_cfg()
            from ai_debug.driver import ModelError
            driver = FakeDriver([
                {"exc": ModelError("deepseek down")},
                {"result": RESULT_OK, "text": "fixed by ling"},
            ])
            orch = DebugOrchestrator(cfg, _FakeHealth(), driver=driver)

            with patch("ai_debug.orchestrator.gh") as gh_mock, \
                 patch("ai_debug.orchestrator._clone_repo", self._clone_patch(remote)), \
                 patch("ai_debug.orchestrator._run_local_build", return_value=True), \
                 patch("ai_debug.orchestrator.safety") as safety_mock:
                self._gh_patch(gh_mock, remote)
                gh_mock.create_branch_and_commit = lambda r, b, m: None
                gh_mock.push_branch = lambda r, b: None
                safety_mock.validate_diff = lambda repo: (True, [])
                safety_mock.discard_changes = lambda repo: None

                result = orch.handle_failure("owner/repo", _summary(), "run-4")

            self.assertEqual(result["status"], "pushed_for_ci_verification")
            self.assertEqual(driver.calls,
                             ["opencode/deepseek-v4-flash-free", "opencode/ling-3.0-tiny-free"])
            self.assertEqual(result["attempts"], 1)


if __name__ == "__main__":
    unittest.main()
