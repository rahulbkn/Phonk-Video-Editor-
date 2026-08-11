"""GitHub operations via the REST API (stdlib only — no `gh` CLI required).

Enforces branch safety: the AI may only work on feature/ai-fix-* or
fix/ai-fix-* branches. It is structurally impossible to push to main/master.
"""

from __future__ import annotations

import json
import os
import subprocess
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

API = "https://api.github.com"
PROTECTED_BRANCHES = {"main", "master", "production"}


class GitHubError(RuntimeError):
    pass


def _token() -> str:
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN") or ""
    if not token:
        raise GitHubError("GITHUB_TOKEN not set")
    return token


def _request(method: str, url: str, payload: dict | None = None) -> dict[str, Any]:
    token = _token()
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", "phonk-ai-debug")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = resp.read().decode()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode()[:400]
        raise GitHubError(f"GitHub API {exc.code} on {method} {url}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise GitHubError(f"GitHub API network error on {method} {url}: {exc.reason}") from exc


def require_gh() -> None:
    """No-op for REST-based backend (kept for interface compatibility)."""
    _token()


def assert_safe_branch(branch: str) -> None:
    if branch in PROTECTED_BRANCHES or branch.startswith("release/"):
        raise GitHubError(f"refusing to work on protected branch '{branch}'")


def fix_branch_name(run_id: str, prefix: str = "feature/ai-fix-") -> str:
    safe = "".join(ch for ch in run_id if ch.isalnum() or ch in "-_")[:64]
    return f"{prefix}{safe}"


def is_fix_branch(branch: str) -> bool:
    return branch.startswith("feature/ai-fix-") or branch.startswith("fix/ai-fix-")


# ---------- git (local) ----------

def create_branch_and_commit(repo_dir: Path | str, branch: str, commit_msg: str) -> None:
    assert_safe_branch(branch)
    _sh(["git", "checkout", "-b", branch], repo_dir)
    _sh(["git", "add", "-A"], repo_dir)
    result = _sh(["git", "commit", "-m", commit_msg], repo_dir, check=False)
    if result.returncode != 0 and "nothing to commit" not in result.stderr:
        raise GitHubError(f"commit failed: {result.stderr.strip()}")


def push_branch(repo_dir: Path | str, branch: str) -> None:
    assert_safe_branch(branch)
    result = _sh(["git", "push", "-u", "origin", branch], repo_dir, check=False)
    if result.returncode != 0:
        raise GitHubError(f"push to '{branch}' failed: {result.stderr.strip()}")


def _sh(cmd: list[str], cwd: Path | str, check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True)
    if check and result.returncode != 0:
        raise GitHubError(f"git command failed ({' '.join(cmd)}): {result.stderr.strip()[:2000]}")
    return result


# ---------- PRs / issues ----------

def open_or_update_pr(
    repo: str,
    head_branch: str,
    base_branch: str = "main",
    title: str = "AI fix (autonomous debug loop)",
    body: str = "",
) -> str:
    assert_safe_branch(head_branch)
    url = f"{API}/repos/{repo}/pulls?state=open&head={head_branch}&base={base_branch}"
    existing = _request("GET", url)
    if existing:
        pr = existing[0]
        _request("PATCH", f"{API}/repos/{repo}/pulls/{pr['number']}",
                 {"title": title, "body": body})
        return pr["html_url"]
    created = _request("POST", f"{API}/repos/{repo}/pulls", {
        "title": title, "body": body, "head": head_branch, "base": base_branch,
    })
    return created.get("html_url", f"{API}/repos/{repo}/pulls/{created.get('number', '?')}")


def create_issue(repo: str, title: str, body: str) -> str:
    created = _request("POST", f"{API}/repos/{repo}/issues", {"title": title, "body": body})
    return created.get("html_url", f"{API}/repos/{repo}/issues/{created.get('number', '?')}")


# ---------- workflow runs (polling) ----------

def latest_failed_run_for_branch(repo: str, branch: str, workflow: str | None = None) -> dict[str, Any] | None:
    url = f"{API}/actions/runs?branch={branch}&status=failure&per_page=1"
    if workflow:
        url += f"&name={workflow}"
    try:
        data = _request("GET", url)
    except GitHubError:
        return None
    runs = data.get("workflow_runs", [])
    if not runs:
        return None
    run = runs[0]
    return {
        "databaseId": run.get("id"),
        "displayTitle": run.get("display_title", ""),
        "workflowName": run.get("name", ""),
        "conclusion": run.get("conclusion"),
        "headSha": run.get("head_sha", ""),
    }


def recent_failed_runs(repo: str, workflow: str | None = None,
                       branch_prefix: str | None = None, limit: int = 10) -> list[dict[str, Any]]:
    """List recent failed runs, optionally filtered to a branch prefix
    (e.g. 'feature/ai-fix-') so the worker can pick up retry failures."""
    url = f"{API}/actions/runs?status=failure&per_page={limit}"
    if workflow:
        url += f"&name={workflow}"
    try:
        data = _request("GET", url)
    except GitHubError:
        return []
    runs = []
    for run in data.get("workflow_runs", []):
        branch = run.get("head_branch", "")
        if branch_prefix and not branch.startswith(branch_prefix):
            continue
        runs.append({
            "databaseId": run.get("id"),
            "displayTitle": run.get("display_title", ""),
            "workflowName": run.get("name", ""),
            "headBranch": branch,
            "conclusion": run.get("conclusion"),
            "headSha": run.get("head_sha", ""),
        })
    return runs


def workflow_run_url(repo: str, run_id: str) -> str:
    return f"https://github.com/{repo}/actions/runs/{run_id}"


def download_run_artifacts(repo: str, run_id: str, dest: Path | str) -> None:
    """Download workflow run artifacts (zip) using the REST API."""
    url = f"{API}/repos/{repo}/actions/runs/{run_id}/artifacts?per_page=100"
    data = _request("GET", url)
    dest_path = Path(dest)
    dest_path.mkdir(parents=True, exist_ok=True)
    token = _token()
    for artifact in data.get("artifacts", []):
        dl_url = artifact.get("archive_download_url")
        if not dl_url:
            continue
        req = urllib.request.Request(dl_url, method="GET")
        req.add_header("Authorization", f"Bearer {token}")
        req.add_header("User-Agent", "phonk-ai-debug")
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                out = dest_path / f"{artifact['name']}.zip"
                out.write_bytes(resp.read())
        except (urllib.error.HTTPError, urllib.error.URLError, OSError) as exc:
            raise GitHubError(f"artifact download failed: {exc}") from exc


def fetch_run_summary(repo: str, run_id: str, tmp_dir: Path | str) -> dict[str, Any]:
    """Download the ci-results artifact of a failed run and return its
    summary.json. Falls back to a minimal failed stub if unavailable."""
    stub: dict[str, Any] = {"status": "failed", "run_id": str(run_id)}
    try:
        download_run_artifacts(repo, str(run_id), tmp_dir)
    except GitHubError:
        return stub
    for zipped in sorted(Path(tmp_dir).glob("ci-results*.zip")):
        try:
            import zipfile
            with zipfile.ZipFile(zipped) as zf:
                if "ci-results/summary.json" in zf.namelist():
                    summary = json.loads(zf.read("ci-results/summary.json"))
                    stub.update(summary)
                    stub["run_id"] = str(run_id)
                    return stub
        except (OSError, zipfile.BadZipFile, json.JSONDecodeError):
            continue
    return stub
