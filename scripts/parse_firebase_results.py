#!/usr/bin/env python3
"""
Parses raw `gcloud firebase test android run --format=json` output (and any
downloaded GCS artifacts) into a predictable ci-results/ directory that
OpenCode (or any other agent/CI consumer) can read without touching gcloud
or GCS directly.

Produces:
  ci-results/summary.json  -> machine-readable
  ci-results/summary.md    -> human-readable, also appended to the GitHub
                               Actions job summary

Crash-pattern matching against logcat is deliberately simple/regex based —
tune CRASH_PATTERNS below as you learn what shows up in this project
(FFmpeg, MediaCodec, JNI, etc.)
"""

import json
import os
import re
import glob

RESULTS_DIR = "ci-results"
RAW_OUTPUT_PATH = os.path.join(RESULTS_DIR, "raw-testlab-output.json")
GCS_DIR = os.path.join(RESULTS_DIR, "raw-gcs")

CRASH_PATTERNS = [
    r"FATAL EXCEPTION",
    r"AndroidRuntime",
    r"Caused by:.*Exception",
    r"RuntimeException",
    r"IllegalStateException",
    r"NullPointerException",
    r"SecurityException",
    r"ActivityNotFoundException",
    r"Resources\$NotFoundException",
    r"UnsatisfiedLinkError",
    r"NoSuchMethodError",
    r"SIGSEGV",
    r"SIGABRT",
    r"libc\s*:",
    r"libart\s*:",
    r"libandroid_runtime\s*:",
]
CRASH_RE = re.compile("|".join(CRASH_PATTERNS))


def load_raw_output():
    if not os.path.exists(RAW_OUTPUT_PATH) or os.path.getsize(RAW_OUTPUT_PATH) == 0:
        return []
    try:
        with open(RAW_OUTPUT_PATH) as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return []


def find_logcat_files():
    return sorted(glob.glob(os.path.join(GCS_DIR, "**", "logcat*"), recursive=True))


def find_files(*patterns):
    found = []
    for p in patterns:
        found.extend(sorted(glob.glob(os.path.join(GCS_DIR, "**", p), recursive=True)))
    return found


def extract_crashes(logcat_files):
    crashes = []
    for path in logcat_files:
        try:
            with open(path, errors="ignore") as f:
                lines = f.readlines()
        except OSError:
            continue
        for i, line in enumerate(lines):
            if CRASH_RE.search(line):
                # grab a small context window around the match
                start = max(0, i - 2)
                end = min(len(lines), i + 8)
                crashes.append({
                    "file": path,
                    "line": i + 1,
                    "match": line.strip(),
                    "context": "".join(lines[start:end]).strip(),
                })
    return crashes


def main():
    os.makedirs(RESULTS_DIR, exist_ok=True)
    os.makedirs(os.path.join(RESULTS_DIR, "logcat"), exist_ok=True)
    os.makedirs(os.path.join(RESULTS_DIR, "screenshots"), exist_ok=True)
    os.makedirs(os.path.join(RESULTS_DIR, "videos"), exist_ok=True)
    os.makedirs(os.path.join(RESULTS_DIR, "test-results"), exist_ok=True)

    raw = load_raw_output()
    entry = raw[0] if raw else {}

    outcome = str(entry.get("outcome", "unknown")).lower()
    status = "passed" if outcome == "passed" else ("failed" if raw else "blocked")

    device = entry.get("axis_value", entry.get("device", "unknown"))
    android_version = entry.get("api_level", entry.get("android_version", "unknown"))
    execution_id = entry.get("test_execution_id", entry.get("history_id", ""))
    project_id = os.environ.get("GOOGLE_CLOUD_PROJECT", "")

    logcat_files = find_logcat_files()
    screenshots = find_files("*.png", "*.jpg")
    videos = find_files("*.mp4", "*.webm")
    test_result_files = find_files("*test_result*", "*instrumentation*.xml", "*.pb")

    crashes = extract_crashes(logcat_files)

    failures = []
    if status == "failed" and not crashes:
        failures.append("Firebase Test Lab reported FAILED but no known crash "
                         "signature matched in logcat — inspect logcat manually.")
    for c in crashes:
        failures.append(f"{c['file']}:{c['line']} -> {c['match']}")

    if status == "blocked":
        failures.append("No Firebase Test Lab output was produced — likely an "
                         "infrastructure/credential/quota issue, not an app bug.")

    artifacts = logcat_files + screenshots + videos + test_result_files

    summary = {
        "status": status,
        "executionId": execution_id,
        "projectId": project_id,
        "device": device,
        "androidVersion": android_version,
        "failures": failures,
        "crashes": crashes,
        "artifacts": artifacts,
    }

    with open(os.path.join(RESULTS_DIR, "summary.json"), "w") as f:
        json.dump(summary, f, indent=2)

    md = [f"## Firebase Test Lab", "", f"**Status:** {status.upper()}", ""]
    md.append(f"- Device: {device}")
    md.append(f"- Android: {android_version}")
    md.append(f"- Execution ID: {execution_id or '(none)'}")
    md.append("")
    if failures:
        md.append("### Failures")
        for f_ in failures:
            md.append(f"- {f_}")
        md.append("")
    if crashes:
        md.append("### Crash context (first match)")
        md.append("```")
        md.append(crashes[0]["context"])
        md.append("```")
        md.append("")
    md.append("### Artifacts")
    if artifacts:
        for a in artifacts:
            md.append(f"- `{a}`")
    else:
        md.append("- (none collected — check bucket permissions / results-dir path)")

    with open(os.path.join(RESULTS_DIR, "summary.md"), "w") as f:
        f.write("\n".join(md) + "\n")

    print("\n".join(md))


if __name__ == "__main__":
    main()
