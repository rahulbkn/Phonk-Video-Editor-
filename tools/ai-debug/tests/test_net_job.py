"""Tests for network fault tolerance (net.py) and job persistence (job.py)."""

import json
import sys
import tempfile
import time
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug.job import Job, JobStore, QUEUED, RUNNING, SUCCESS  # noqa: E402
from ai_debug.net import RateLimitError, RetryExhaustedError, backoff_delay, retry  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
