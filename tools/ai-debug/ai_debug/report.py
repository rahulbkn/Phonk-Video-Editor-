"""Structured failure report + prompt building.

Builds the targeted context given to the AI — the failure report, not the
whole repository — and renders the prompt template.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

TEMPLATE_PATH = Path(__file__).resolve().parents[1] / "prompts" / "task_template.md"


def render_failure_report(summary: dict[str, Any]) -> str:
    lines = [
        "FAILURE TYPE:",
        summary.get("failure_type", "ANDROID_RUNTIME_CRASH"),
        "",
        "DEVICE:",
        f"{summary.get('device', 'unknown')} / Android {summary.get('androidVersion', 'unknown')}",
        "",
        "TEST:",
        summary.get("test", "(robo test / no specific test)"),
        "",
        "STATUS:",
        str(summary.get("status", "unknown")),
        "",
    ]
    if summary.get("executionId"):
        lines += ["EXECUTION ID:", str(summary["executionId"]), ""]
    lines += ["LOGCAT / STACKTRACE:", summary.get("stacktrace", "(none collected)"), ""]
    lines += ["LAST ACTION:", summary.get("last_action", "(unknown)"), ""]
    lines += ["EXPECTED:", summary.get("expected", "(unknown)"), ""]
    lines += ["ACTUAL:", summary.get("actual", "(unknown)"), ""]
    if summary.get("recent_changes"):
        lines += ["RECENT CHANGES:", summary["recent_changes"], ""]
    if summary.get("previous_attempts"):
        lines += ["PREVIOUS AI ATTEMPTS:", summary["previous_attempts"], ""]
    return "\n".join(lines)


def build_prompt(
    *,
    repo: str,
    branch: str,
    attempt: int,
    max_attempts: int,
    summary: dict[str, Any],
    previous_attempts: str,
    template: str | None = None,
) -> str:
    template = template or TEMPLATE_PATH.read_text(encoding="utf-8")
    failures = "\n".join(f"- {x}" for x in summary.get("failures", [])) or "(none listed)"
    crashes = summary.get("crashes", [])
    crash_context = crashes[0]["context"] if crashes else summary.get("stacktrace", "(no crash pattern matched)")

    return template.format(
        repo=repo,
        branch=branch,
        attempt=attempt,
        max_attempts=max_attempts,
        status=summary.get("status", "unknown"),
        device=summary.get("device", "unknown"),
        android_version=summary.get("androidVersion", "unknown"),
        execution_id=summary.get("executionId", ""),
        failures=failures,
        crash_context=crash_context,
        previous_attempts=previous_attempts,
    )


def format_previous_attempts(history: list[dict[str, Any]]) -> str:
    if not history:
        return "(this is the first attempt)"
    lines = []
    for h in history:
        attempt = h.get("attempt")
        root = h.get("root_cause", "?")
        build = "PASS" if h.get("local_build_pass") else "FAIL"
        model = h.get("model", "?")
        line = f"- Attempt {attempt}: root cause = {root}, local build = {build}, model = {model}"
        if "firebase_status" in h:
            line += f", Firebase = {h['firebase_status']}"
        lines.append(line)
    return "\n".join(lines)
