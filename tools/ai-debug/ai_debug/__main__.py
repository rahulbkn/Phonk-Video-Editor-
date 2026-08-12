"""CLI entry points for the ai-debug system.

Commands:
  python -m ai_debug poll --repo owner/repo [--interval 300]
      Run the GitHub polling worker forever.
  python -m ai_debug handle --repo owner/repo --run-id 1234 [--branch main]
      Handle one failure directly from a JSON summary file (webhook repl).
  python -m ai_debug health --json
      Print model health summary.
  python -m ai_debug webhook
      Run the Flask webhook receiver (requires flask).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .config import load_config
from .health import HealthRegistry
from .orchestrator import DebugOrchestrator


def _cmd_poll(args: argparse.Namespace) -> int:
    from .worker import run_poller_forever
    run_poller_forever(args.repo, interval_seconds=args.interval, workflow=args.workflow,
                       max_workers=args.max_workers)
    return 0


def _cmd_handle(args: argparse.Namespace) -> int:
    cfg = load_config(args.config)
    health = HealthRegistry(cfg, Path(args.data_dir) / "health.json")
    orch = DebugOrchestrator(cfg, health)
    summary = {"status": "failed", "failures": ["(manual invocation)"], "crashes": []}
    if args.summary_file:
        with open(args.summary_file, encoding="utf-8") as fh:
            summary.update(json.load(fh))
    result = orch.handle_failure(args.repo, summary, args.run_id, ci_branch=args.branch)
    print(json.dumps(result, indent=2))
    return 0


def _cmd_health(args: argparse.Namespace) -> int:
    cfg = load_config(args.config)
    health = HealthRegistry(cfg, Path(args.data_dir) / "health.json")
    print(json.dumps(health.summary(), indent=2))
    return 0


def _cmd_stop(args: argparse.Namespace) -> int:
    from . import proc
    report = proc.stop_worker(Path(args.data_dir), grace=args.grace)
    print(json.dumps({k: v for k, v in report.items() if k != "root"}, indent=2))
    return 0 if report.get("status") in ("ok", "not_running", "already_dead") else 1


def _cmd_webhook(args: argparse.Namespace) -> int:
    from .webhook_server import run_server
    run_server(port=args.port, host=args.host)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="ai-debug", description="Autonomous AI Android debugging system")
    sub = parser.add_subparsers(dest="command", required=True)

    p_poll = sub.add_parser("poll", help="poll GitHub for failed runs")
    p_poll.add_argument("--repo", required=True, help="owner/repo")
    p_poll.add_argument("--interval", type=int, default=300)
    p_poll.add_argument("--workflow", default=None)
    p_poll.add_argument("--max-workers", type=int, default=2)
    p_poll.set_defaults(func=_cmd_poll)

    p_handle = sub.add_parser("handle", help="handle one failure")
    p_handle.add_argument("--repo", required=True)
    p_handle.add_argument("--run-id", required=True)
    p_handle.add_argument("--branch", default="main")
    p_handle.add_argument("--summary-file", default=None)
    p_handle.add_argument("--config", default=None)
    p_handle.add_argument("--data-dir", default="./.ai-debug-data")
    p_handle.set_defaults(func=_cmd_handle)

    p_health = sub.add_parser("health", help="print model health")
    p_health.add_argument("--config", default=None)
    p_health.add_argument("--data-dir", default="./.ai-debug-data")
    p_health.set_defaults(func=_cmd_health)

    p_stop = sub.add_parser("stop", help="stop the polling worker and its process tree")
    p_stop.add_argument("--data-dir", default="./.ai-debug-data")
    p_stop.add_argument("--grace", type=float, default=10.0)
    p_stop.set_defaults(func=_cmd_stop)

    p_wh = sub.add_parser("webhook", help="run Flask webhook receiver")
    p_wh.add_argument("--host", default="0.0.0.0")
    p_wh.add_argument("--port", type=int, default=8080)
    p_wh.set_defaults(func=_cmd_webhook)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())
