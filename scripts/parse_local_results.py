#!/usr/bin/env python3
"""
Parses Gradle JUnit XML test results (testDebugUnitTest) into the same
ci-results/summary.json schema produced by parse_firebase_results.py.

This is the deterministic, free failure source used when Firebase Test Lab
credentials are NOT configured on the repo. The workflow runs
`./gradlew testDebugUnitTest`; on any failing test this parser reports
status=failed so the AI debug loop is triggered.

Produces:
  ci-results/summary.json  -> machine-readable (same schema as Firebase path)
  ci-results/summary.md    -> human-readable, appended to the job summary
"""

import glob
import json
import os
import xml.etree.ElementTree as ET

RESULTS_DIR = "ci-results"
TEST_RESULTS_GLOB = "app/build/test-results/testDebugUnitTest/*.xml"

NS = "{http://www.w3.org/2001/XMLSchema-instance}"
FALSE_BOOLS = {"false", "0", "no"}


def _parse_int(value: str | None) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def load_suites() -> list[dict]:
    """Parse all JUnit XML files into normalized suite/test dicts."""
    suites = []
    for path in sorted(glob.glob(TEST_RESULTS_GLOB)):
        try:
            tree = ET.parse(path)
        except (ET.ParseError, OSError):
            continue
        root = tree.getroot()
        suite_name = root.get("name", os.path.basename(path))
        for case in root.iter("testcase"):
            failures = list(case.iter("failure"))
            errors = list(case.iter("error"))
            skipped = list(case.iter("skipped"))
            failed = bool(failures or errors)
            detail = ""
            if failures:
                detail = failures[0].get("message", "") or failures[0].text or ""
            elif errors:
                detail = errors[0].get("message", "") or errors[0].text or ""
            suites.append({
                "suite": suite_name,
                "class": case.get("classname", ""),
                "name": case.get("name", ""),
                "time": case.get("time", ""),
                "failed": failed,
                "skipped": bool(skipped),
                "detail": detail.strip(),
            })
    return suites


def main():
    os.makedirs(RESULTS_DIR, exist_ok=True)
    suites = load_suites()

    failures = [s for s in suites if s["failed"]]

    if not suites:
        summary = {
            "status": "blocked",
            "executionId": "local-unit-tests",
            "projectId": "",
            "device": "local-jvm",
            "androidVersion": "unit-test",
            "failures": [
                "No JUnit test result XMLs found under "
                f"{TEST_RESULTS_GLOB} — the unit-test task likely did not run "
                "(infrastructure/configuration issue, not an app bug)."
            ],
            "crashes": [],
            "artifacts": [],
        }
        status = "blocked"
    else:
        status = "failed" if failures else "passed"
        failure_lines = [
            f"{s['class']}.{s['name']} -> {s['detail'] or 'failed (no message)'}"
            for s in failures
        ] or ["(no unit test failures)"]
        first_detail = failures[0]["detail"] if failures else ""
        crashes = ([{
            "file": f"{failures[0]['class']}.kt",
            "line": 0,
            "match": failures[0]["name"],
            "context": first_detail[:2000] or "(no assertion detail captured)",
        }] if failures else [])
        summary = {
            "status": status,
            "executionId": "local-unit-tests",
            "projectId": "",
            "device": "local-jvm",
            "androidVersion": "unit-test",
            "failures": failure_lines,
            "crashes": crashes,
            "artifacts": glob.glob(TEST_RESULTS_GLOB),
            "testCount": len(suites),
            "failureCount": len(failures),
        }

    with open(os.path.join(RESULTS_DIR, "summary.json"), "w") as f:
        json.dump(summary, f, indent=2)

    md = ["## Local unit tests (no Firebase configured)", "",
          f"**Status:** {status.upper()}", "",
          f"- Tests: {summary.get('testCount', '?')}",
          f"- Failures: {summary.get('failureCount', '?')}", ""]
    if failures:
        md.append("### Failures")
        for line in summary["failures"]:
            md.append(f"- {line}")
        md.append("")
    if summary["crashes"]:
        md.append("### First failure context")
        md.append("```")
        md.append(summary["crashes"][0]["context"])
        md.append("```")
        md.append("")

    with open(os.path.join(RESULTS_DIR, "summary.md"), "w") as f:
        f.write("\n".join(md) + "\n")

    print("\n".join(md))
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
