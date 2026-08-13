#!/usr/bin/env bash
# Deploy the ai-debug worker to the remote Termux build box and start it.
#
# The remote box acts as the "Railway/Render worker" — it polls GitHub for
# failed Firebase runs (since GitHub Actions cannot reach a LAN IP) and drives
# the local OpenCode + Gradle toolchain.
#
# Usage:
#   ./tools/ai-debug/deploy-remote.sh [host] [port]
#
# Required env on the remote box (persisted in ~/.ai-debug.env):
#   GITHUB_TOKEN=<PAT with repo scope>
#   GITHUB_REPO=owner/repo
# Optional:
#   WEBHOOK_SECRET=...          (only needed for webhook mode)
#   AI_DEBUG_DATA_DIR=~/.ai-debug-data
set -euo pipefail

HOST="${1:-u0_a258@192.168.49.1}"
PORT="${2:-8022}"
SSH=(ssh -p "$PORT" "$HOST")

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/tools/ai-debug"

echo "[deploy] copying ai-debug to remote..."
"${SSH[@]}" "mkdir -p ~/ai-debug"
scp -P "$PORT" -r "$SRC/ai_debug" "$SRC/config" "$SRC/prompts" "$SRC/requirements.txt" "$HOST:~/ai-debug/"

echo "[deploy] writing env file (from local .ai-debug.env if present)..."
if [ -f "$ROOT/tools/ai-debug/.ai-debug.env" ]; then
  scp -P "$PORT" "$ROOT/tools/ai-debug/.ai-debug.env" "$HOST:~/ai-debug/.env"
else
  "${SSH[@]}" "touch ~/ai-debug/.env"
  echo "  (no .ai-debug.env found — set GITHUB_TOKEN/GITHUB_REPO on the remote)"
fi

echo "[deploy] installing python deps..."
"${SSH[@]}" "cd ~/ai-debug && (python3 -c 'import flask' 2>/dev/null || pip3 install -q -r requirements.txt)"

echo "[deploy] writing launcher..."
"${SSH[@]}" "cat > ~/ai-debug/start.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
export \$(grep -v '^#' ~/ai-debug/.env | xargs) 2>/dev/null || true
export GITHUB_REPO=\${GITHUB_REPO:-}
export AI_DEBUG_DATA_DIR=\${AI_DEBUG_DATA_DIR:-\$HOME/.ai-debug-data}
cd ~/ai-debug
exec python3 -m ai_debug poll --repo \"\$GITHUB_REPO\" --interval 300
EOF
chmod +x ~/ai-debug/start.sh"

echo "[deploy] done. Start the worker with:"
echo "  ${SSH[@]} '~/ai-debug/start.sh'"
echo "Or as a service via: nohup ~/ai-debug/start.sh > ~/ai-debug/worker.log 2>&1 &"
