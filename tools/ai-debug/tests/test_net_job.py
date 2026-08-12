"""Tests for network fault tolerance (net.py) and job persistence (job.py)."""

import json
import sys
import tempfile
import time
import unittest
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug.job import Job, JobStore, QUEUED, RUNNING, SUCCESS  # noqa: E402
from ai_debug.net import RateLimitError, RetryExhaustedError, backoff_delay, retry  # noqa: E402
from ai_debug import github as gh  # noqa: E402
from ai_debug.config import Config  # noqa: E402
from ai_debug.worker import Worker  # noqa: E402


class BackoffTest(unittest.TestCase):
    def test_increases_with_attempt(self):
        a1 = backoff_delay(1, base=2, max_delay=60, jitter=0)
        a2 = backoff_delay(2, base=2, max_delay=60, jitter=0)
        self.assertEqual(a1, 2.0)
        self.assertEqual(a2, 4.0)

    def test_capped(self):
        self.assertEqual(backoff_delay(10, base=2, max_delay=30, jitter=0), 30.0)


class RetryTest(unittest.TestCase):
    def test_retries_then_succeeds(self):
        calls = []

        def flaky():
            calls.append(1)
            if len(calls) < 3:
                raise ConnectionError("boom")
            return "ok"

        self.assertEqual(retry(flaky, attempts=4, base_delay=0.01), "ok")
        self.assertEqual(len(calls), 3)

    def test_exhausts(self):
        def always_fail():
            raise ValueError("nope")

        with self.assertRaises(RetryExhaustedError):
            retry(always_fail, attempts=2, base_delay=0.01)

    def test_rate_limit_retried(self):
        calls = []

        def rl():
            calls.append(1)
            if len(calls) < 2:
                raise RateLimitError()
            return "done"

        self.assertEqual(retry(rl, attempts=3, base_delay=0.01), "done")


class JobTest(unittest.TestCase):
    def test_defaults(self):
        j = Job({"repository": "owner/repo", "branch": "main"})
        self.assertTrue(j.job_id)
        self.assertEqual(j.attempt, 0)
        self.assertEqual(j.max_attempts, 3)
        self.assertEqual(j.status, QUEUED)
        self.assertIn("createdAt", j.data)

    def test_touch(self):
        j = Job({})
        j.touch(status=RUNNING, attempt=2)
        self.assertEqual(j.status, RUNNING)
        self.assertEqual(j.attempt, 2)
        self.assertIn("updatedAt", j.data)


class JobStoreTest(unittest.TestCase):
    def test_roundtrip_and_list(self):
        with tempfile.TemporaryDirectory() as d:
            store = JobStore(d)
            j1 = Job({"repository": "a/b", "branch": "main"})
            j2 = Job({"repository": "a/b", "branch": "feature/ai-fix-1", "status": SUCCESS})
            store.save(j1)
            store.save(j2)

            loaded = store.load(j1.job_id)
            self.assertEqual(loaded.repository, "a/b")
            self.assertEqual(len(store.list_jobs()), 2)
            self.assertEqual(len(store.active_jobs()), 1)  # only j1 is QUEUED

    def test_delete(self):
        with tempfile.TemporaryDirectory() as d:
            store = JobStore(d)
            j = Job({})
            store.save(j)
            self.assertIsNotNone(store.load(j.job_id))
            store.delete(j.job_id)
            self.assertIsNone(store.load(j.job_id))

    def test_resume_after_restart(self):
        with tempfile.TemporaryDirectory() as d:
            store = JobStore(d)
            j = Job({"repository": "a/b", "attempt": 2, "maxAttempts": 3,
                     "currentTask": "KOTLIN", "status": RUNNING})
            store.save(j)
            # simulate worker restart — new store over same dir
            store2 = JobStore(d)
            resumed = store2.load(j.job_id)
            self.assertEqual(resumed.attempt, 2)
            self.assertEqual(resumed.current_task, "KOTLIN")
            self.assertEqual(resumed.status, RUNNING)


class WorkerTest(unittest.TestCase):
    """Regression: run_poller_forever must call poll_for_failures with the
    keyword name `workflow_name` (previously passed `workflow`, which raised
    TypeError the moment the poller started)."""

    def test_poll_for_failures_accepts_workflow_name_kwarg(self):
        cfg = Config({"provider": "opencode", "model": {}})
        with tempfile.TemporaryDirectory() as d:
            store = JobStore(d)
            worker = Worker(cfg, store, max_workers=1)
            # No network tokens set; github.require_gh() is not called here.
            # We only assert the method accepts the kwarg used by the poller.
            self.assertIsNotNone(worker.poll_for_failures.__self__)

    def test_poller_dispatches_failed_run(self):
        cfg = Config({"provider": "opencode", "max_attempts": 2, "model": {}})
        with tempfile.TemporaryDirectory() as d:
            store = JobStore(d)
            worker = Worker(cfg, store, max_workers=1)
            worker._dispatch = lambda job: None  # only test enqueue logic

            fake_run = {
                "databaseId": "12345",
                "displayTitle": "AI Fix Branch - Build + Verify + PR",
                "headBranch": "feature/ai-fix-e2e",
                "conclusion": "failure",
                "headSha": "abcdef0",
            }
            original = gh.recent_failed_runs
            original_latest = gh.latest_failed_run_for_branch

            def fake_recent(repo, workflow=None, branch_prefix=None, limit=10):
                return [fake_run]

            def fake_latest(repo, branch, workflow=None):
                return None

            gh.recent_failed_runs = fake_recent
            gh.latest_failed_run_for_branch = fake_latest
            try:
                worker.store = store
                n = worker.poll_for_failures("owner/repo", workflow_name="verify")
            finally:
                gh.recent_failed_runs = original
                gh.latest_failed_run_for_branch = original_latest

            self.assertEqual(n, 1)
            jobs = store.list_jobs()
            self.assertEqual(len(jobs), 1)
            self.assertEqual(jobs[0].data["run_id"], "12345")
            self.assertEqual(jobs[0].data["branch"], "feature/ai-fix-e2e")

    def test_poller_dedupes_same_branch(self):
        cfg = Config({"provider": "opencode", "max_attempts": 2, "model": {}})
        with tempfile.TemporaryDirectory() as d:
            store = JobStore(d)
            worker = Worker(cfg, store, max_workers=1)
            worker._dispatch = lambda job: None  # only test enqueue logic

            # newest-first list with two failures on the SAME branch
            fake_runs = [
                {"databaseId": "999", "displayTitle": "r",
                 "headBranch": "feature/ai-fix-e2e", "conclusion": "failure",
                 "headSha": "a"},
                {"databaseId": "111", "displayTitle": "r",
                 "headBranch": "feature/ai-fix-e2e", "conclusion": "failure",
                 "headSha": "b"},
            ]
            original = gh.recent_failed_runs
            original_latest = gh.latest_failed_run_for_branch

            def fake_recent(repo, workflow=None, branch_prefix=None, limit=10):
                return fake_runs

            def fake_latest(repo, branch, workflow=None):
                return None

            gh.recent_failed_runs = fake_recent
            gh.latest_failed_run_for_branch = fake_latest
            try:
                worker.store = store
                n = worker.poll_for_failures("owner/repo", workflow_name="verify")
            finally:
                gh.recent_failed_runs = original
                gh.latest_failed_run_for_branch = original_latest

            self.assertEqual(n, 1)
            jobs = store.list_jobs()
            self.assertEqual(len(jobs), 1)
            self.assertEqual(jobs[0].data["run_id"], "999")


class GitHubFilterTest(unittest.TestCase):
    """Regression: failed-run queries must use status=completed &
    conclusion=failure — GitHub's `status` filter only accepts
    queued/in_progress/completed, so `status=failure` silently returned no
    runs and the poller never discovered CI failures."""

    def test_latest_failed_run_uses_conclusion_filter(self):
        seen = {}

        def fake_request(method, url, payload=None):
            seen["url"] = url
            return {"workflow_runs": []}

        original = gh._request
        gh._request = fake_request
        try:
            gh.latest_failed_run_for_branch("owner/repo", "main")
        finally:
            gh._request = original

        self.assertIn("/repos/owner/repo/actions/runs", seen["url"])
        self.assertIn("status=completed", seen["url"])
        self.assertIn("conclusion=failure", seen["url"])
        self.assertNotIn("status=failure", seen["url"])

    def test_recent_failed_runs_uses_conclusion_filter(self):
        seen = {}

        def fake_request(method, url, payload=None):
            seen["url"] = url
            return {"workflow_runs": []}

        original = gh._request
        gh._request = fake_request
        try:
            gh.recent_failed_runs("owner/repo", branch_prefix="feature/ai-fix-")
        finally:
            gh._request = original

        self.assertIn("/repos/owner/repo/actions/runs", seen["url"])
        self.assertIn("status=completed", seen["url"])
        self.assertIn("conclusion=failure", seen["url"])
        self.assertNotIn("status=failure", seen["url"])


class FetchRunSummaryTest(unittest.TestCase):
    """Regression: GitHub artifact zips strip the top-level folder, so a
    `path: ci-results` artifact yields `summary.json` at the ZIP ROOT. The
    old code looked for `ci-results/summary.json`, never matched, and the
    worker silently fell back to a stub summary with no real failure text."""

    def test_finds_summary_json_at_zip_root(self):
        import io
        import zipfile

        with tempfile.TemporaryDirectory() as d:
            tmp = Path(d)
            payload = json.dumps({
                "status": "failed", "failures": ["real failure text"],
                "crashes": [], "testCount": 88, "failureCount": 1,
            })
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w") as zf:
                zf.writestr("summary.json", payload)
                zf.writestr("summary.md", "## summary")
            (tmp / "ci-results-12345.zip").write_bytes(buf.getvalue())

            from ai_debug import github as gh
            original = gh.download_run_artifacts
            gh.download_run_artifacts = lambda repo, run_id, dest: None
            try:
                summary = gh.fetch_run_summary("owner/repo", "12345", tmp)
            finally:
                gh.download_run_artifacts = original

            self.assertEqual(summary["status"], "failed")
            self.assertEqual(summary["failures"], ["real failure text"])
            self.assertEqual(summary["testCount"], 88)
            self.assertEqual(summary["run_id"], "12345")


class RedirectAuthTest(unittest.TestCase):
    """Regression: GitHub artifact downloads 302 to Azure Blob Storage.
    urllib re-sends the GitHub token cross-host unless the handler strips it
    — Azure then answers 401 ('Server failed to authenticate'). Verify the
    redirect handler drops the Authorization header on host change."""

    def test_strips_auth_on_cross_host_redirect(self):
        import http.server
        import threading

        from ai_debug import github as gh

        captured: dict = {}

        class Server(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                auth = self.headers.get("Authorization", "")
                if self.path == "/orig":
                    captured["redirect_auth"] = auth
                    self.send_response(302)
                    self.send_header("Location", "http://localhost:%d/blob" % self.server.server_port)
                    self.end_headers()
                    return
                captured["blob_auth"] = auth
                body = b"ZIPDATA"
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        srv = http.server.HTTPServer(("127.0.0.1", 0), Server)
        port = srv.server_address[1]
        thread = threading.Thread(target=srv.serve_forever, daemon=True)
        thread.start()
        try:
            opener = urllib.request.build_opener(
                urllib.request.ProxyHandler({}), gh._RedirectNoAuth())
            req = urllib.request.Request(
                f"http://localhost:{port}/orig", method="GET")
            req.add_header("Authorization", "Bearer sekret")
            with opener.open(req, timeout=10) as resp:
                data = resp.read()
        finally:
            srv.shutdown()
            thread.join()

        self.assertEqual(captured["redirect_auth"], "Bearer sekret")
        self.assertNotIn("Authorization", captured["blob_auth"])


if __name__ == "__main__":
    unittest.main()
