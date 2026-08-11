#!/usr/bin/env bash
# Stop the ai-debug polling worker and its entire process tree.
# Idempotent: safe to run repeatedly; refuses to touch non-worker pids.
set -euo pipefail
export $(grep -v "^#" ~/ai-debug/.env | xargs) 2>/dev/null || true
export AI_DEBUG_DATA_DIR=${AI_DEBUG_DATA_DIR:-$HOME/.ai-debug-data}
cd ~/ai-debug
exec python3 -m ai_debug stop --data-dir "$AI_DEBUG_DATA_DIR"
