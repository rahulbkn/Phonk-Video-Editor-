"""Debug orchestrator: the main autonomous debugging loop.

Flow per job:
  1. Determine fix branch (resume existing feature/ai-fix-* or create one).
  2. Load persisted state (.ai-debug/state.json on the branch).
  3. Loop attempts (max MAX_ATTEMPTS):
       a. classify task
       b. pick free model via router
       c. invoke OpenCode (with per-model retry + fallback)
       d. run safety checks on the diff
       e. run local Gradle build/test
       f. on pass: commit + push branch (Firebase becomes authoritative)
       g. on fail: discard, record attempt, next model/attempt
  4. On exhausted attempts: open GitHub issue.

Never pushes to main/master. Job state is persisted after every step and
resumed on worker restart.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import github as gh
from . import safety
from .config import Config, env
from .driver import OpenCodeDriver, run_model_with_fallback
from .health import HealthRegistry
from .job import Job, JobStore
from .net import retry
from .report import build_prompt, format_previous_attempts
from .router import ModelRouter

JOB_TAG = "[JOB]"
MODEL_TAG = "[MODEL]"
ROUTER_TAG = "[ROUTER]"
OPENCODE_TAG = "[OPENCODE]"
BUILD_TAG = "[BUILD]"
GITHUB_TAG = "[GITHUB]"


class OrchestratorError(RuntimeError):
    pass


def _log(tag: str, msg: str) -> None:
    print(f"{tag} {msg}", flush=True)


def _sh(cmd: list[str], cwd: Path | str, check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True)
    if check and result.returncode != 0:
        raise OrchestratorError(
            f"command failed ({' '.join(cmd)}): {result.stderr.strip()[-2000:]}"
        )
    return result


def _clone_repo(workdir: Path, repo: str, branch: str) -> Path:
    token = env("GITHUB_TOKEN", env("GH_TOKEN"))
    if not token:
        raise OrchestratorError("GITHUB_TOKEN not set — cannot clone repo")
    repo_dir = workdir / "repo"
    url = f"https://x-access-token:{token}@github.com/{repo}.git"
    _sh(["git", "clone", "--depth", "50", url, str(repo_dir)], cwd=workdir)
    _sh(["git", "config", "user.name", "opencode-debug-bot"], cwd=repo_dir)
    _sh(["git", "config", "user.email", "opencode-debug-bot@users.noreply.github.com"], cwd=repo_dir)
    if gh.is_fix_branch(branch):
        _sh(["git", "fetch", "origin", branch], cwd=repo_dir)
        _sh(["git", "checkout", branch], cwd=repo_dir)
    _write_local_properties(repo_dir)
    return repo_dir


def _write_local_properties(repo_dir: Path) -> None:
    """Point Gradle at the Android SDK + CMake when no local.properties is
    committed.

    Reads env vars (set by the deploy script / worker):
      - ANDROID_HOME or AI_DEBUG_ANDROID_SDK  -> sdk.dir
      - AI_DEBUG_CMAKE_DIR                    -> cmake.dir (Termux: /usr)
    """
    if (repo_dir / "local.properties").exists():
        return
    sdk = env("AI_DEBUG_ANDROID_SDK", env("ANDROID_HOME", ""))
    cmake_dir = env("AI_DEBUG_CMAKE_DIR", "")
    lines = []
    if sdk:
        lines.append(f"sdk.dir={sdk}")
    if cmake_dir:
        lines.append(f"cmake.dir={cmake_dir}")
    if lines:
        (repo_dir / "local.properties").write_text(
            "\n".join(lines) + "\n", encoding="utf-8")


def _load_branch_state(repo_dir: Path) -> dict[str, Any]:
    path = repo_dir / ".ai-debug" / "state.json"
    if path.exists():
        try:
            with open(path, encoding="utf-8") as fh:
                return json.load(fh)
        except (json.JSONDecodeError, OSError):
            pass
    return {"attempts": 0, "history": []}


def _save_branch_state(repo_dir: Path, state: dict[str, Any]) -> None:
    d = repo_dir / ".ai-debug"
    d.mkdir(parents=True, exist_ok=True)
    tmp = d / "state.json.tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(state, fh, indent=2)
    os.replace(tmp, d / "state.json")


def _write_attempt_note(repo_dir: Path, attempt: int, text: str) -> None:
    d = repo_dir / ".ai-debug"
    d.mkdir(parents=True, exist_ok=True)
    (d / f"attempt-{attempt}.md").write_text(text, encoding="utf-8")


def _run_local_build(repo_dir: Path, log_path: Path | None = None) -> bool:
    gradlew = repo_dir / "gradlew"
    if not gradlew.exists():
        _log(BUILD_TAG, "no gradlew found — cannot run local build check")
        return False
    _sh(["chmod", "+x", str(gradlew)], cwd=repo_dir, check=False)
    cmd = [str(gradlew), "assembleDebug", "testDebugUnitTest", "--console=plain", "--no-daemon"]
    result = subprocess.run(cmd, cwd=str(repo_dir), capture_output=True, text=True)
    if log_path:
        try:
            log_path.write_text(result.stdout[-8000:] + "\n" + result.stderr[-4000:], encoding="utf-8")
        except OSError:
            pass
    ok = result.returncode == 0
    _log(BUILD_TAG, f"local build {'PASS' if ok else 'FAIL'}")
    if not ok:
        _log(BUILD_TAG, result.stdout[-2000:])
        _log(BUILD_TAG, result.stderr[-1500:])
    return ok


def _read_attempt_note(repo_dir: Path, attempt: int) -> str:
    note = repo_dir / ".ai-debug" / f"attempt-{attempt}.md"
    if note.exists():
        try:
            return note.read_text(encoding="utf-8").strip().splitlines()[0][:200]
        except OSError:
            pass
    return "(OpenCode did not write an attempt note)"


def _extract_stacktrace(summary: dict[str, Any]) -> str:
    crashes = summary.get("crashes", [])
    if crashes:
        return crashes[0].get("context", "")
    return summary.get("stacktrace", "")


def _issue_body(repo: str, branch: str, state: dict[str, Any], summary: dict[str, Any],
                models_used: list[str], infra_issues: list[str], final_error: str) -> str:
    lines = [
        f"AI AUTONOMOUS DEBUGGING FAILED",
        "",
        f"Job: {summary.get('job_id', '?')}",
        f"Repository: {repo}",
        f"Branch: `{branch}`",
        "",
        "Original Failure:",
        f"- Status: {summary.get('status', '?')}",
        f"- Device: {summary.get('device', '?')} / Android {summary.get('androidVersion', '?')}",
        f"- Failures: {summary.get('failures', [])}",
        "",
        "## Attempt history",
    ]
    for h in state.get("history", []):
        lines.append(
            f"- Attempt {h.get('attempt')}: model={h.get('model', '?')} "
            f"root_cause={h.get('root_cause', '?')} "
            f"local_build={'PASS' if h.get('local_build_pass') else 'FAIL'} "
            f"firebase={h.get('firebase_status', '?')}"
        )
    lines.append("")
    if models_used:
        lines.append("## Models used")
        lines += [f"- {m}" for m in models_used]
        lines.append("")
    if infra_issues:
        lines.append("## Infrastructure issues")
        lines += [f"- {i}" for i in infra_issues]
        lines.append("")
    lines.append("## Final error")
    lines.append("```")
    lines.append(final_error or "(none)")
    lines.append("```")
    lines.append("")
    lines.append(
        "Recommended human investigation. The branch above has been left intact "
        "with the AI's notes in `.ai-debug/`."
    )
    return "\n".join(lines)


class DebugOrchestrator:
    def __init__(self, cfg: Config, health: HealthRegistry, driver: OpenCodeDriver | None = None):
        self.cfg = cfg
        self.health = health
        self.router = ModelRouter(cfg, health)
        self.driver = driver or OpenCodeDriver(timeout_seconds=cfg.request_timeout_seconds)

    # ---- public entry ----
    def handle_failure(self, repo: str, summary: dict[str, Any], run_id: str,
                       ci_branch: str = "main") -> dict[str, Any]:
        gh.require_gh()
        if not gh.is_fix_branch(ci_branch) and ci_branch != "main":
            raise OrchestratorError(f"unexpected CI branch '{ci_branch}' — refusing to act")

        is_retry = gh.is_fix_branch(ci_branch)
        fix_branch = ci_branch if is_retry else gh.fix_branch_name(run_id)
        gh.assert_safe_branch(fix_branch)

        _log(JOB_TAG, f"handling failure for {repo} (branch {fix_branch}, retry={is_retry})")

        workdir = Path(tempfile.mkdtemp(prefix="ai-debug-"))
        try:
            repo_dir = _clone_repo(workdir, repo, fix_branch if is_retry else "main")
            state = _load_branch_state(repo_dir)

            if is_retry and state.get("history"):
                state["history"][-1]["firebase_status"] = summary.get("status", "unknown")
                _save_branch_state(repo_dir, state)

            if state["attempts"] >= self.cfg.max_attempts:
                self._open_issue(repo, fix_branch, state, summary, [], [], "max attempts already reached")
                return {"status": "stopped_max_attempts_already_reached", "branch": fix_branch}

            return self._run_attempts(repo, fix_branch, repo_dir, state, summary)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)

    # ---- core loop ----
    def _run_attempts(self, repo: str, fix_branch: str, repo_dir: Path,
                      state: dict[str, Any], summary: dict[str, Any]) -> dict[str, Any]:
        pushed = False
        models_used: list[str] = []
        infra_issues: list[str] = []
        last_error = ""

        task = summary.get("task", "")
        _log(ROUTER_TAG, f"task hint from failure: {task or '(none)'}")

        while state["attempts"] < self.cfg.max_attempts and not pushed:
            attempt = state["attempts"] + 1
            _log(JOB_TAG, f"=== Attempt {attempt}/{self.cfg.max_attempts} on {fix_branch} ===")

            if not self.router.verify_pool_available():
                _log(MODEL_TAG, "all free models unavailable — stopping safely")
                self._open_issue(repo, fix_branch, state, summary, models_used,
                                 ["all free models unavailable"], "model pool exhausted")
                return {"status": "stopped_all_models_unavailable", "branch": fix_branch}

            model = self.router.pick(task)
            models_used.append(model)
            _log(ROUTER_TAG, f"task classified={task or self.cfg.default_task} → model {model}")

            prompt = build_prompt(
                repo=repo, branch=fix_branch, attempt=attempt,
                max_attempts=self.cfg.max_attempts, summary=summary,
                previous_attempts=format_previous_attempts(state.get("history", [])),
            )
            prompt_file = repo_dir / ".ai-debug" / f"prompt-{attempt}.md"
            prompt_file.parent.mkdir(parents=True, exist_ok=True)
            prompt_file.write_text(prompt, encoding="utf-8")

            try:
                used_model, result_code, _text = run_model_with_fallback(
                    driver=self.driver, health=self.health,
                    model_names=[model] + self._alternates(task, model),
                    cwd=repo_dir, prompt_file=prompt_file,
                )
            except Exception as exc:  # noqa: BLE001  (all candidates failed)
                _log(MODEL_TAG, f"all models failed: {exc}")
                infra_issues.append(str(exc))
                last_error = str(exc)
                state["attempts"] = attempt
                state["history"].append({
                    "attempt": attempt, "timestamp": datetime.now(timezone.utc).isoformat(),
                    "model": model, "root_cause": f"model failure: {exc}",
                    "local_build_pass": False,
                })
                _save_branch_state(repo_dir, state)
                _log(JOB_TAG, f"model failure on attempt {attempt} (counts toward max)")
                continue

            if result_code != "ok":
                _log(OPENCODE_TAG, f"opencode returned {result_code}")
                last_error = f"opencode result {result_code}"
                state["attempts"] = attempt
                state["history"].append({
                    "attempt": attempt, "timestamp": datetime.now(timezone.utc).isoformat(),
                    "model": used_model, "root_cause": f"opencode {result_code}",
                    "local_build_pass": False,
                })
                _save_branch_state(repo_dir, state)
                continue

            is_safe, violations = safety.validate_diff(repo_dir)
            if not is_safe:
                _log(OPENCODE_TAG, f"SAFETY VIOLATION: {violations}")
                safety.discard_changes(repo_dir)
                state["attempts"] = attempt
                state["history"].append({
                    "attempt": attempt, "timestamp": datetime.now(timezone.utc).isoformat(),
                    "model": used_model, "root_cause": "safety violation",
                    "safety_violations": violations, "local_build_pass": False,
                })
                _save_branch_state(repo_dir, state)
                last_error = "safety violation"
                continue

            build_ok = _run_local_build(repo_dir)
            root_cause = _read_attempt_note(repo_dir, attempt)

            state["attempts"] = attempt
            state["history"].append({
                "attempt": attempt, "timestamp": datetime.now(timezone.utc).isoformat(),
                "model": used_model, "root_cause": root_cause,
                "local_build_pass": build_ok,
            })
            _save_branch_state(repo_dir, state)

            if build_ok:
                commit_msg = f"fix: AI attempt {attempt} - {root_cause[:120]}"
                try:
                    gh.create_branch_and_commit(repo_dir, fix_branch, commit_msg)
                    gh.push_branch(repo_dir, fix_branch)
                except Exception as exc:  # noqa: BLE001
                    _log(GITHUB_TAG, f"push failed: {exc}")
                    infra_issues.append(f"push failed: {exc}")
                    last_error = str(exc)
                    continue
                pushed = True
                _log(GITHUB_TAG, f"pushed {fix_branch} — waiting for CI/Firebase verification")
            else:
                _log(BUILD_TAG, f"local build failed on attempt {attempt}, retrying if attempts remain")
                safety.discard_changes(repo_dir)

        if pushed:
            return {"status": "pushed_for_ci_verification", "branch": fix_branch,
                    "attempts": state["attempts"], "models_used": models_used}

        self._open_issue(repo, fix_branch, state, summary, models_used, infra_issues, last_error)
        return {"status": "stopped_max_attempts", "branch": fix_branch,
                "attempts": state["attempts"], "models_used": models_used}

    def _alternates(self, task: str, chosen: str) -> list[str]:
        """Models to fall back to within the same attempt."""
        return [m for m in self.router.available_models(task) if m != chosen]

    def _open_issue(self, repo: str, branch: str, state: dict[str, Any],
                    summary: dict[str, Any], models_used: list[str],
                    infra_issues: list[str], final_error: str) -> None:
        title = f"AI debug loop exhausted ({self.cfg.max_attempts} attempts) on {branch}"
        body = _issue_body(repo, branch, state, summary, models_used, infra_issues, final_error)
        try:
            url = gh.create_issue(repo, title, body)
            _log(GITHUB_TAG, f"opened issue: {url}")
        except Exception as exc:  # noqa: BLE001
            _log(GITHUB_TAG, f"could not open issue: {exc}")


def run_orchestrator_job(cfg: Config, job: Job, summary: dict[str, Any], run_id: str) -> dict[str, Any]:
    """Entry point used by the worker."""
    health = HealthRegistry(cfg, Path(env("AI_DEBUG_DATA_DIR", "./.ai-debug-data")) / "health.json")
    orch = DebugOrchestrator(cfg, health)
    return orch.handle_failure(job.repository, summary, run_id, ci_branch=job.branch)
