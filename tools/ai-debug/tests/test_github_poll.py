"""Tests for the workflow-run polling functions in ai_debug.github.

Regression: GitHub's REST API may ignore the `conclusion` query parameter
(`status=completed&conclusion=failure` returned ALL runs, including successful
ones), which made the worker enqueue re-fix jobs for its own successful
verification runs (infinite loop). The poll functions must therefore filter
`conclusion == "failure"` client-side, on top of any API-side filtering.
"""

import json
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from ai_debug import github  # noqa: E402


def _run(rid, conclusion, branch, sha, title="title"):
    return {
        "id": rid,
        "display_title": title,
        "name": "Build + Verify",
        "conclusion": conclusion,
        "head_branch": branch,
        "head_sha": sha,
    }


class RecentFailedRunsTest(unittest.TestCase):
    def test_filters_out_success_runs(self):
        payload = {"workflow_runs": [
            _run(1, "failure", "feature/ai-fix-1", "a" * 8),
            _run(2, "success", "feature/ai-fix-1", "b" * 8),
            _run(3, "failure", "feature/ai-fix-2", "c" * 8),
        ]}
        with mock.patch.object(github, "_request", return_value=payload):
            runs = github.recent_failed_runs("owner/repo",
                                             branch_prefix="feature/ai-fix-")
        self.assertEqual([r["databaseId"] for r in runs], [1, 3])
        for r in runs:
            self.assertEqual(r["conclusion"], "failure")

    def test_respects_branch_prefix(self):
        payload = {"workflow_runs": [
            _run(1, "failure", "feature/ai-fix-1", "a" * 8),
            _run(2, "failure", "main", "b" * 8),
            _run(3, "failure", "release/v2", "c" * 8),
        ]}
        with mock.patch.object(github, "_request", return_value=payload):
            runs = github.recent_failed_runs("owner/repo",
                                             branch_prefix="feature/ai-fix-")
        self.assertEqual([r["databaseId"] for r in runs], [1])


class LatestFailedRunForBranchTest(unittest.TestCase):
    def test_skips_when_newest_is_success(self):
        payload = {"workflow_runs": [
            _run(1, "success", "main", "a" * 8),
        ]}
        with mock.patch.object(github, "_request", return_value=payload):
            run = github.latest_failed_run_for_branch("owner/repo", "main")
        self.assertIsNone(run)

    def test_returns_failure_even_if_success_above(self):
        payload = {"workflow_runs": [
            _run(2, "success", "main", "b" * 8),
            _run(1, "failure", "main", "a" * 8),
        ]}
        with mock.patch.object(github, "_request", return_value=payload):
            run = github.latest_failed_run_for_branch("owner/repo", "main")
        self.assertIsNotNone(run)
        self.assertEqual(run["databaseId"], 1)
        self.assertEqual(run["conclusion"], "failure")

    def test_empty(self):
        with mock.patch.object(github, "_request", return_value={"workflow_runs": []}):
            run = github.latest_failed_run_for_branch("owner/repo", "main")
        self.assertIsNone(run)


if __name__ == "__main__":
    unittest.main()
