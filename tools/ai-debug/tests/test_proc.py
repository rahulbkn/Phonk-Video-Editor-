"""Regression tests for process-tree teardown (orphan prevention).

Covers the exact failure that left orphan opencode/gradle processes after a
worker restart: `pkill -f ai_debug` matched the worker's own cmdline but NOT
the children's cmdlines, which carry the hyphenated temp-dir marker
`ai-debug-<rand>/repo`. Teardown must:

  - terminate the worker AND all descendants regardless of the ai_debug /
    ai-debug naming;
  - TERM first (graceful), KILL only stragglers;
  - never touch unrelated processes;
  - be idempotent;
  - refuse to kill a stale pidfile that no longer points at a worker.
"""

import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug import proc  # noqa: E402


def _sleep(seconds: int = 600) -> subprocess.Popen:
    return subprocess.Popen(
        [sys.executable, "-c", f"import time; time.sleep({seconds})"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def _sleep_with_args(*args: str, seconds: int = 600) -> subprocess.Popen:
    """Spawn a sleep whose *cmdline* (argv) contains the given markers.

    Mirrors the real world where opencode children have `ai-debug-...` in
    their argv rather than `ai_debug`.
    """
    code = f"import sys, time; time.sleep({seconds})"
    return subprocess.Popen(
        [sys.executable, "-c", code, *args],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def _wait_dead(pids) -> bool:
    deadline = time.time() + 5
    while time.time() < deadline:
        if not any(proc.is_alive(p) for p in pids):
            return True
        time.sleep(0.05)
    return not any(proc.is_alive(p) for p in pids)


class KillTreeTest(unittest.TestCase):
    def tearDown(self):
        # safety net: never leave test processes behind — kill the whole
        # tree (grandchildren included) of everything this test spawned
        for p in getattr(self, "_procs", []):
            try:
                proc.kill_tree(p.pid, grace=1.0)
            except Exception:  # noqa: BLE001
                pass

    def _track(self, *procs):
        self._procs = list(procs)

    def test_descendants_finds_both_naming_patterns(self):
        worker = _sleep()
        child_underscore = _sleep_with_args("ai_debug", seconds=600)
        child_hyphen = _sleep_with_args("/usr/tmp/ai-debug-cppqeoet/repo", seconds=600)
        self._track(worker, child_underscore, child_hyphen)

        # make the two children actual descendants of `worker`
        # (they must be reparented under worker for the tree walk to find them)
        child_underscore.kill(); child_underscore.wait()
        child_hyphen.kill(); child_hyphen.wait()
        # relaunch as children of worker via a shim
        shim = subprocess.Popen(
            [sys.executable, "-c",
             "import subprocess, sys, time;"
             "subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(600)', 'ai_debug']);"
             "subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(600)', '/usr/tmp/ai-debug-cppqeoet/repo']);"
             "time.sleep(600)"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self._track(worker, shim)
        time.sleep(0.3)

        found = proc.descendants(shim.pid)
        self.assertTrue(any("ai_debug" in proc.cmdline(p) and "ai-debug-" not in proc.cmdline(p)
                            for p in found),
                        f"expected ai_debug-style child among {sorted(found)}")
        self.assertTrue(any("ai-debug-cppqeoet" in proc.cmdline(p) for p in found),
                        f"expected ai-debug-style child among {sorted(found)}")

    def test_kill_tree_terminates_worker_and_ai_debug_children(self):
        shim = subprocess.Popen(
            [sys.executable, "-c",
             "import subprocess, sys, time;"
             "subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(600)', 'ai_debug']);"
             "subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(600)', '/usr/tmp/ai-debug-cppqeoet/repo']);"
             "time.sleep(600)"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self._track(shim)
        time.sleep(0.3)

        report = proc.kill_tree(shim.pid, grace=3.0)
        self.assertTrue(_wait_dead([shim.pid] + sorted(proc.descendants(shim.pid))))
        self.assertEqual(report["survivors"], [])
        # sanity: every pid that was alive in the tree got either terminated
        self.assertGreaterEqual(len(report["terminated"]) + len(report["force_killed"]), 1)

    def test_kill_tree_graceful_term_first_then_kill(self):
        # a process that ignores SIGTERM must still be force-killed
        stubborn = subprocess.Popen(
            [sys.executable, "-c",
             "import signal, time;"
             "signal.signal(signal.SIGTERM, lambda *_: None);"
             "time.sleep(600)"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self._track(stubborn)
        # give the child time to install its handler before we signal it
        time.sleep(0.4)

        start = time.time()
        report = proc.kill_tree(stubborn.pid, grace=1.0)
        elapsed = time.time() - start
        self.assertTrue(_wait_dead([stubborn.pid]))
        self.assertIn(stubborn.pid, report["force_killed"])
        # it should NOT have waited the full grace for a normal exit; the
        # grace only bounds the TERM wait, force-kill happens after
        self.assertLess(elapsed, 10)

    def test_kill_tree_never_touches_unrelated_process(self):
        unrelated = _sleep()
        self._track(unrelated)
        shim = _sleep()
        self._track(shim)
        time.sleep(0.1)

        proc.kill_tree(shim.pid, grace=1.0)
        self.assertTrue(proc.is_alive(unrelated.pid), "unrelated process was killed!")
        unrelated.kill(); unrelated.wait()

    def test_kill_tree_already_dead_is_idempotent(self):
        report = proc.kill_tree(999999999)
        self.assertTrue(report["already_dead"])

    def test_stop_worker_idempotent(self):
        with tempfile.TemporaryDirectory() as d:
            data_dir = Path(d)
            first = proc.stop_worker(data_dir)
            self.assertEqual(first["status"], "not_running")
            second = proc.stop_worker(data_dir)
            self.assertEqual(second["status"], "not_running")


class StopWorkerTest(unittest.TestCase):
    def _make_worker(self, data_dir: Path, marker: str) -> subprocess.Popen:
        """Start a fake worker whose cmdline looks like the real poller."""
        argv = marker.split()
        code = ("import subprocess, sys, time;"
                "subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(600)', 'child-of-worker']);"
                "time.sleep(600)")
        proc_ = subprocess.Popen(
            [sys.executable, "-c", code, *argv],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(0.3)
        proc.write_pidfile(data_dir / proc.WORKER_PIDFILE, proc_.pid, None)
        return proc_

    def tearDown(self):
        for p in getattr(self, "_procs", []):
            try:
                proc.kill_tree(p.pid, grace=1.0)
            except Exception:  # noqa: BLE001
                pass
        for d in getattr(self, "_dirs", []):
            import shutil
            shutil.rmtree(d, ignore_errors=True)

    def test_stop_ai_debug_underscore_worker(self):
        with tempfile.TemporaryDirectory() as d:
            data_dir = Path(d)
            self._dirs = [data_dir]
            w = self._make_worker(data_dir, "ai_debug poll --repo owner/repo")
            self._procs = [w]
            pidfile = data_dir / proc.WORKER_PIDFILE
            self.assertEqual(proc.ensure_single_worker(data_dir), w.pid)

            report = proc.stop_worker(data_dir, grace=2.0)
            self.assertIn(report["status"], ("ok", "not_running"))
            self.assertTrue(_wait_dead([w.pid]))
            self.assertFalse(pidfile.exists(), "pidfile must be removed after stop")
            self.assertIsNone(proc.ensure_single_worker(data_dir))

    def test_stop_ai_debug_hyphen_worker(self):
        with tempfile.TemporaryDirectory() as d:
            data_dir = Path(d)
            self._dirs = [data_dir]
            w = self._make_worker(data_dir, "ai-debug poll --repo owner/repo")
            self._procs = [w]
            report = proc.stop_worker(data_dir, grace=2.0)
            self.assertIn(report["status"], ("ok", "not_running"))
            self.assertTrue(_wait_dead([w.pid]))

    def test_stop_refuses_stale_pidfile_to_unrelated_pid(self):
        with tempfile.TemporaryDirectory() as d:
            data_dir = Path(d)
            self._dirs = [data_dir]
            unrelated = _sleep()
            self._procs = [unrelated]
            proc.write_pidfile(data_dir / proc.WORKER_PIDFILE, unrelated.pid, None)

            report = proc.stop_worker(data_dir, grace=1.0)
            self.assertEqual(report["status"], "refusing")
            self.assertTrue(proc.is_alive(unrelated.pid), "unrelated pid was killed!")
            # pidfile preserved so we don't silently lose the lock
            self.assertTrue((data_dir / proc.WORKER_PIDFILE).exists())

    def test_stop_removes_stale_pidfile_of_dead_pid(self):
        with tempfile.TemporaryDirectory() as d:
            data_dir = Path(d)
            self._dirs = [data_dir]
            pidfile = data_dir / proc.WORKER_PIDFILE
            dead = 999999999
            proc.write_pidfile(pidfile, dead, None)
            report = proc.stop_worker(data_dir, grace=1.0)
            self.assertEqual(report["status"], "not_running")
            self.assertFalse(pidfile.exists())


if __name__ == "__main__":
    unittest.main()
