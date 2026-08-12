"""Process-tree utilities for safe worker shutdown.

Why this exists: the AI worker spawns `opencode`, which in turn spawns
shells and Gradle. A naive `pkill -f ai_debug` misses all of those children
because their command lines carry the hyphenated temp-dir marker
(`ai-debug-<rand>/repo`, `ai-debug-cppqeoet/repo`) rather than `ai_debug` —
so opencode/gradle are left running and get reparented to init.

Design (so we never kill unrelated processes):

  * The worker runs in its own session/process group (``os.setsid()``),
    so every child it spawns inherits the worker's PGID. A process-group
    kill is therefore only used when ``pgid == pid`` (the worker is its own
    group leader) — that group contains nothing but the worker's own tree.
  * Teardown enumerates the full descendant set from ``/proc`` (agnostic to
    the ai_debug vs ai-debug naming) and TERM-then-KILLs exactly that set,
    plus the worker itself.
  * A pidfile makes shutdown idempotent and guarantees a single worker.
"""

from __future__ import annotations

import os
import signal
import time
from pathlib import Path
from typing import Any

WORKER_PIDFILE = "worker.pid"

# Command-line markers used to positively identify the AI worker before
# killing anything (protects against a stale pidfile pointing at an
# unrelated, recycled PID).
WORKER_MARKERS = (
    "ai_debug poll",
    "ai-debug poll",
    "ai_debug webhook",
    "ai-debug webhook",
)


def all_pids() -> list[int]:
    pids: list[int] = []
    try:
        for name in os.listdir("/proc"):
            if name.isdigit():
                pids.append(int(name))
    except OSError:
        pass
    return pids


def pid_info(pid: int) -> dict[str, Any] | None:
    """Parse /proc/<pid>/stat. Returns pid, state, ppid, pgid or None."""
    try:
        with open(f"/proc/{pid}/stat", encoding="utf-8", errors="replace") as fh:
            stat = fh.read()
    except OSError:
        return None
    close = stat.rfind(")")
    if close < 0:
        return None
    tail = stat[close + 2:].split()
    if len(tail) < 3:
        return None
    try:
        return {
            "pid": pid,
            "state": tail[0],
            "ppid": int(tail[1]),
            "pgid": int(tail[2]),
        }
    except ValueError:
        return None


def cmdline(pid: int) -> str:
    try:
        with open(f"/proc/{pid}/cmdline", "rb") as fh:
            raw = fh.read()
        return raw.replace(b"\x00", b" ").decode("utf-8", "replace").strip()
    except OSError:
        return ""


def is_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    info = pid_info(pid)
    if info is None:
        return False
    # Zombie (Z) / dead-but-unreaped (X) processes still have a /proc entry
    # until the parent reaps them; treat them as not running so teardown
    # never reports a half-reaped process as a survivor.
    return info["state"] not in ("Z", "X")


def pgid(pid: int) -> int | None:
    info = pid_info(pid)
    return info["pgid"] if info else None


def is_worker_pid(pid: int) -> bool:
    cl = cmdline(pid)
    return any(m in cl for m in WORKER_MARKERS)


def descendants(pid: int) -> set[int]:
    """All transitive descendants of `pid` from the current /proc snapshot."""
    children: dict[int, list[int]] = {}
    for p in all_pids():
        info = pid_info(p)
        if info:
            children.setdefault(info["ppid"], []).append(p)
    found: set[int] = set()
    frontier = [pid]
    while frontier:
        nxt: list[int] = []
        for parent in frontier:
            for child in children.get(parent, []):
                if child not in found:
                    found.add(child)
                    nxt.append(child)
        frontier = nxt
    return found


def tree_of(pid: int) -> set[int]:
    found = descendants(pid)
    if is_alive(pid):
        found.add(pid)
    return found


def _send(sig: int, pids: set[int]) -> None:
    for p in pids:
        try:
            os.kill(p, sig)
        except (OSError, ProcessLookupError, PermissionError):
            pass


def _send_group(sig: int, gid: int) -> None:
    try:
        os.killpg(gid, sig)
    except (ProcessLookupError, OSError, PermissionError):
        pass


def kill_tree(pid: int, grace: float = 10.0) -> dict[str, Any]:
    """TERM the whole tree rooted at `pid`, wait, then KILL stragglers.

    Only ever signals descendants of `pid` plus `pid` itself. If the worker
    is its own process-group leader (``pgid == pid``) the group is signalled
    too, which also reaches anything that got reparented mid-shutdown.

    Returns a report with the pids that were terminated, force-killed, or
    that survived (should be empty in the happy path).
    """
    report: dict[str, Any] = {"root": pid, "already_dead": False,
                              "terminated": [], "force_killed": [], "survivors": []}
    if not is_alive(pid):
        report["already_dead"] = True
        return report

    gid = pgid(pid)
    group_safe = gid is not None and gid == pid

    start_tree = tree_of(pid)

    # 1) graceful shutdown: SIGTERM the whole tree (children + worker)
    _send(signal.SIGTERM, start_tree)
    if group_safe:
        _send_group(signal.SIGTERM, gid)

    # 2) wait for shutdown
    deadline = time.time() + grace
    while time.time() < deadline:
        if not is_alive(pid):
            break
        time.sleep(0.1)
    time.sleep(0.1)

    # 3) force-kill only what still remains (including anything spawned since)
    tree = tree_of(pid)
    remaining = {p for p in tree if is_alive(p)}
    _send(signal.SIGKILL, remaining)
    if group_safe:
        _send_group(signal.SIGKILL, gid)
    time.sleep(0.2)

    survivors = {p for p in (start_tree | tree) if is_alive(p)}
    report["terminated"] = sorted((start_tree - survivors) - remaining)
    report["force_killed"] = sorted(remaining - survivors)
    report["survivors"] = sorted(survivors)
    return report


# ---------- pidfile (single-worker lock + idempotent stop) ----------

def write_pidfile(path: Path, pid: int, gid: int | None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".pid.tmp")
    tmp.write_text(f"{pid}\n{gid if gid else ''}\n", encoding="utf-8")
    os.replace(tmp, path)


def read_pidfile(path: Path) -> tuple[int, int | None] | None:
    try:
        raw = path.read_text(encoding="utf-8").strip().split()
    except OSError:
        return None
    if not raw:
        return None
    try:
        pid = int(raw[0])
    except ValueError:
        return None
    gid: int | None = None
    if len(raw) > 1 and raw[1]:
        try:
            gid = int(raw[1])
        except ValueError:
            gid = None
    return pid, gid


def clear_pidfile(path: Path) -> None:
    try:
        path.unlink()
    except OSError:
        pass


def ensure_single_worker(data_dir: Path | str) -> int | None:
    """Return the running worker's pid if one is alive, else None."""
    entry = read_pidfile(Path(data_dir) / WORKER_PIDFILE)
    if entry and is_alive(entry[0]) and is_worker_pid(entry[0]):
        return entry[0]
    return None


def stop_worker(data_dir: Path | str, grace: float = 10.0) -> dict[str, Any]:
    """Idempotently stop the polling worker and its entire process tree."""
    d = Path(data_dir)
    pidfile = d / WORKER_PIDFILE
    entry = read_pidfile(pidfile)
    if not entry:
        return {"status": "not_running", "reason": "no pidfile"}
    pid, gid = entry
    if not is_alive(pid):
        clear_pidfile(pidfile)
        return {"status": "not_running", "reason": "worker already gone"}
    if not is_worker_pid(pid):
        return {"status": "refusing",
                "reason": f"pid {pid} is not an ai-debug worker (stale pidfile?)"}
    report = kill_tree(pid, grace=grace)
    report["status"] = "ok" if not report["survivors"] else "partial"
    clear_pidfile(pidfile)
    return report


def find_matching_cmdline(substr: str) -> list[int]:
    """Pids whose command line contains `substr` (used for verification)."""
    return [p for p in all_pids() if substr in cmdline(p)]


def become_session_leader() -> int | None:
    """Put this process in its own session/process group (best effort).

    Returns the resulting pgid (== pid when it succeeded, i.e. it is safe to
    use a process-group kill for this worker's tree).
    """
    try:
        os.setsid()
    except OSError:
        pass
    return pgid(os.getpid())
