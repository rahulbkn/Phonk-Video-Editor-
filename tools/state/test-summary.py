#!/usr/bin/env python3
"""Aggregates JVM unit-test results from Gradle XML into a tiny JSON summary.

The Gradle `collectTestResults` task runs this after `testDebugUnitTest`/
`testReleaseUnitTest`. snapshot.py reads the JSON to report real pass/fail
numbers without spawning another Gradle process (single-Gradle rule).

Usage:
  test-summary.py <test-results-dir> <out-json>
"""
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

def main(argv):
    if len(argv) < 3:
        print("usage: test-summary.py <test-results-dir> <out-json>", file=sys.stderr)
        return 2
    results_dir = argv[1]
    out_json = argv[2]

    suites = []
    total = failures = errors = skipped = 0
    for xml_path in glob.glob(os.path.join(results_dir, "**", "TEST-*.xml"), recursive=True):
        try:
            tree = ET.parse(xml_path)
        except (ET.ParseError, OSError) as exc:
            print("[state-snapshot] cannot parse %s: %s" % (xml_path, exc), file=sys.stderr)
            continue
        root = tree.getroot()
        total += int(root.attrib.get("tests", 0))
        failures += int(root.attrib.get("failures", 0))
        errors += int(root.attrib.get("errors", 0))
        skipped += int(root.attrib.get("skipped", 0))
        name = root.attrib.get("name", os.path.basename(xml_path))
        suites.append({
            "name": name,
            "tests": int(root.attrib.get("tests", 0)),
            "failures": int(root.attrib.get("failures", 0)),
            "errors": int(root.attrib.get("errors", 0)),
            "skipped": int(root.attrib.get("skipped", 0)),
        })

    import datetime
    summary = {
        "timestamp": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "tests": total,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "suites": [s["name"] for s in sorted(suites, key=lambda s: s["name"])],
    }
    try:
        os.makedirs(os.path.dirname(out_json), exist_ok=True)
        with open(out_json, "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=2)
        print("[state-snapshot] test summary: %d tests, %d failed, %d errors, %d skipped"
              % (total, failures, errors, skipped), file=sys.stderr)
    except OSError as exc:
        print("[state-snapshot] cannot write %s: %s" % (out_json, exc), file=sys.stderr)
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv))
