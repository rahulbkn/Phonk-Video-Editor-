#!/data/data/com.termux/files/usr/bin/env bash
# State snapshot wrapper (build-safe).
#
# Runs tools/state/snapshot.py after a Gradle build and guarantees the build is
# NEVER broken by snapshot tooling: any tooling problem is logged on stderr and
# the exit status is 0 unless the wrapper itself cannot start at all.
#
# Usage: snapshot.sh <project-root> <apk-output-root> [--keep N]
set -u

ROOT_ARG="${1:?usage: snapshot.sh <project-root> <apk-output-root>}"
APK_ROOT="${2:?usage: snapshot.sh <project-root> <apk-output-root>}"
shift 2

if [ ! -x "$ROOT_ARG/tools/state/snapshot.py" ] && [ ! -f "$ROOT_ARG/tools/state/snapshot.py" ]; then
  echo "[state-snapshot] WARNING: tools/state/snapshot.py missing — snapshot skipped (build not affected)" >&2
  exit 0
fi

PYTHON_BIN="${PYTHON:-python3}"
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "[state-snapshot] WARNING: python3 not found — snapshot skipped (build not affected)" >&2
  exit 0
fi

exec "$PYTHON_BIN" "$ROOT_ARG/tools/state/snapshot.py" "$ROOT_ARG" "$APK_ROOT" "$@"
