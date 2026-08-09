#!/data/data/com.termux/files/usr/bin/env bash
# Python analysis smoke test + CLI usage check.
# Usage: scripts/run-python-analysis.sh <audio.wav> [out.json]
set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"
PYTHON=${PYTHON:-python3}

echo "[phonk] running python analysis tests..."
(cd python && "$PYTHON" -m tests.test_analysis)

if [[ $# -ge 1 ]]; then
  echo "[phonk] analyzing $1"
  (cd python && "$PYTHON" -m analysis.analysis "$1" "${2:-}")
fi
