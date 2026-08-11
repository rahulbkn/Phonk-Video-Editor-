"""Persistent debugging job state (debug-job.json).

The orchestrator saves job state after every step so a worker restart resumes
the same job instead of losing the debugging task, analysis and attempt count.
"""

from __future__ import annotations

import json
import os
import time
import uuid
from pathlib import Path
from typing import Any

QUEUED = "QUEUED"
WAITING_FOR_MODEL = "WAITING_FOR_MODEL"
RUNNING = "RUNNING"
PUSHED_FOR_CI = "PUSHED_FOR_CI"
WAITING_FOR_CI = "WAITING_FOR_CI"
FAILED = "FAILED"
STOPPED_MAX_ATTEMPTS = "STOPPED_MAX_ATTEMPTS"
INFRASTRUCTURE_FAILURE = "INFRASTRUCTURE_FAILURE"
SUCCESS = "SUCCESS"

ACTIVE_STATUSES = {QUEUED, WAITING_FOR_MODEL, RUNNING, PUSHED_FOR_CI, WAITING_FOR_CI}


class Job:
    def __init__(self, data: dict[str, Any]):
        self.data: dict[str, Any] = data
        self.data.setdefault("jobId", str(uuid.uuid4()))
        self.data.setdefault("createdAt", time.time())

    @property
    def job_id(self) -> str:
        return str(self.data["jobId"])

    @property
    def repository(self) -> str:
        return str(self.data.get("repository", ""))

    @property
    def branch(self) -> str:
        return str(self.data.get("branch", ""))

    @property
    def attempt(self) -> int:
        return int(self.data.get("attempt", 0))

    @property
    def max_attempts(self) -> int:
        return int(self.data.get("maxAttempts", 3))

    @property
    def current_task(self) -> str:
        return str(self.data.get("currentTask", ""))

    @property
    def current_model(self) -> str:
        return str(self.data.get("currentModel", ""))

    @property
    def status(self) -> str:
        return str(self.data.get("status", QUEUED))

    def touch(self, **fields: Any) -> None:
        self.data.update(fields)
        self.data["updatedAt"] = time.time()

    def to_dict(self) -> dict[str, Any]:
        return self.data


class JobStore:
    def __init__(self, data_dir: Path | str):
        self.data_dir = Path(data_dir)
        self.data_dir.mkdir(parents=True, exist_ok=True)

    def _path(self, job_id: str) -> Path:
        return self.data_dir / f"debug-job-{job_id}.json"

    def save(self, job: Job) -> None:
        path = self._path(job.job_id)
        tmp = path.with_suffix(".tmp")
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(job.to_dict(), fh, indent=2)
        os.replace(tmp, path)

    def load(self, job_id: str) -> Job | None:
        path = self._path(job_id)
        if not path.exists():
            return None
        try:
            with open(path, encoding="utf-8") as fh:
                return Job(json.load(fh))
        except (json.JSONDecodeError, OSError):
            return None

    def delete(self, job_id: str) -> None:
        try:
            self._path(job_id).unlink(missing_ok=True)
        except OSError:
            pass

    def list_jobs(self, status: str | None = None) -> list[Job]:
        jobs = []
        for path in sorted(self.data_dir.glob("debug-job-*.json")):
            try:
                with open(path, encoding="utf-8") as fh:
                    job = Job(json.load(fh))
            except (json.JSONDecodeError, OSError):
                continue
            if status is None or job.status == status:
                jobs.append(job)
        return jobs

    def active_jobs(self) -> list[Job]:
        return [j for j in self.list_jobs() if j.status in ACTIVE_STATUSES]
