"""Background worker: receives failures and runs the AI debug orchestrator.

Two ingestion modes (both feed the same queue):

  1. Webhook mode — GitHub Actions POSTs /webhook/firebase-result with an
     HMAC-SHA256 signature (usable when the worker has a public URL, e.g. via
     a tunnel to a Render/Railway instance or a cloudflared tunnel).
  2. Polling mode — the worker periodically asks the GitHub REST API (stdlib
     only, no `gh` CLI needed) for failed runs on main / feature/ai-fix-*
     branches. This works when the worker lives behind a LAN (like the
     Android build box) and GitHub cannot reach it directly.

Jobs are persisted via JobStore so a worker restart resumes active jobs.
"""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any

from . import github as gh
from .config import Config, env, env_bool, load_config
from .health import HealthRegistry
from .job import Job, JobStore, QUEUED
from .orchestrator import run_orchestrator_job
from .router import classify_task


class Worker:
    def __init__(self, cfg: Config, store: JobStore, max_workers: int = 2):
        self.cfg = cfg
        self.store = store
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        self.webhook_secret = env("WEBHOOK_SECRET")
        self.allow_unsigned = env_bool("ALLOW_UNSIGNED_WEBHOOK", False)

    # ---------- webhook ----------
    def verify_signature(self, raw_body: bytes, signature_header: str) -> bool:
        if not self.webhook_secret:
            return self.allow_unsigned
        if not signature_header or not signature_header.startswith("sha256="):
            return False
        expected = hmac.new(self.webhook_secret.encode(), raw_body, hashlib.sha256).hexdigest()
        provided = signature_header.split("=", 1)[1]
        return hmac.compare_digest(expected, provided)

    def enqueue_from_webhook(self, payload: dict[str, Any], run_id: str) -> str:
        job = Job({
            "repository": payload.get("repo") or env("GITHUB_REPO", ""),
            "branch": payload.get("branch", "main"),
            "run_id": run_id,
            "attempt": 0,
            "maxAttempts": self.cfg.max_attempts,
            "currentTask": classify_task(json.dumps(payload.get("summary", {}))),
            "currentModel": "",
            "status": QUEUED,
            "previousFailures": [],
            "summary": payload.get("summary", {}),
        })
        self.store.save(job)
        self._dispatch(job)
        return job.job_id

    # ---------- polling ----------
    def poll_for_failures(self, repo: str, workflow_name: str | None = None) -> int:
        """Check for recently failed workflow runs and enqueue jobs for them.

        Polls failed runs on `main` AND on `feature/ai-fix-*` branches
        (retry detection — the verify workflow failing on a pushed fix branch
        must feed back into the debug loop).
        """
        enqueued = 0
        candidates: list[tuple[str, dict[str, Any]]] = []

        main_run = gh.latest_failed_run_for_branch(repo, "main", workflow=workflow_name)
        if main_run:
            candidates.append(("main", main_run))

        for run in gh.recent_failed_runs(repo, workflow=workflow_name,
                                         branch_prefix="feature/ai-fix-"):
            candidates.append((run["headBranch"], run))

        seen: set[str] = set()
        for branch, run in candidates:
            run_id = str(run.get("databaseId", ""))
            if not run_id or run_id in seen:
                continue
            seen.add(run_id)
            if self._find_by_run_id(run_id):
                continue  # already handled
            job = Job({
                "repository": repo,
                "branch": branch,
                "run_id": run_id,
                "attempt": 0,
                "maxAttempts": self.cfg.max_attempts,
                "currentTask": self.cfg.default_task,
                "currentModel": "",
                "status": QUEUED,
                "previousFailures": [],
                "summary": {"status": "failed", "run_id": run_id,
                            "run_title": run.get("displayTitle", "")},
            })
            self.store.save(job)
            self._dispatch(job)
            enqueued += 1
        return enqueued

    def _find_by_run_id(self, run_id: str) -> Job | None:
        for job in self.store.list_jobs():
            if job.data.get("run_id") == run_id:
                return job
        return None

    # ---------- dispatch ----------
    def _dispatch(self, job: Job) -> None:
        self.executor.submit(self._process, job)

    def _process(self, job: Job) -> None:
        job.touch(status="RUNNING")
        self.store.save(job)
        try:
            summary = job.data.get("summary", {})
            if job.data.get("branch", "main") != "main":
                # fix-branch retry: fetch the real failure summary from the
                # failed verify workflow run so the AI sees actual test output
                tmp = Path(env("AI_DEBUG_DATA_DIR", "./.ai-debug-data")) / "tmp" / job.job_id
                tmp.mkdir(parents=True, exist_ok=True)
                fetched = gh.fetch_run_summary(
                    job.repository, str(job.data.get("run_id", job.job_id)), tmp)
                if fetched.get("status") == "failed":
                    summary = {**summary, **fetched}
            result = run_orchestrator_job(self.cfg, job, summary, str(job.data.get("run_id", job.job_id)))
            status = "SUCCESS" if result.get("status") == "pushed_for_ci_verification" else result.get("status", "FAILED")
            job.touch(status=status, result=result)
            self.store.save(job)
        except Exception as exc:  # noqa: BLE001
            job.touch(status="FAILED", error=str(exc))
            self.store.save(job)
            print(f"[JOB] {job.job_id} failed: {exc}", flush=True)


def default_worker_paths() -> tuple[Config, JobStore]:
    data_dir = Path(env("AI_DEBUG_DATA_DIR", "./.ai-debug-data"))
    cfg = load_config()
    store = JobStore(data_dir / "jobs")
    return cfg, store


# -------- standalone runner (no Flask required) --------
def run_poller_forever(
    repo: str,
    interval_seconds: int = 300,
    workflow: str | None = None,
    max_workers: int = 2,
) -> None:
    gh.require_gh()
    cfg, store = default_worker_paths()
    worker = Worker(cfg, store, max_workers=max_workers)
    print(f"[JOB] poller started for {repo} every {interval_seconds}s", flush=True)
    while True:
        try:
            n = worker.poll_for_failures(repo, workflow=workflow)
            if n:
                print(f"[JOB] enqueued {n} failed run(s)", flush=True)
        except Exception as exc:  # noqa: BLE001
            print(f"[JOB] poll error: {exc}", flush=True)
        time.sleep(interval_seconds)
