"""OpenCode CLI driver.

Invokes the real `opencode run` CLI (v1.17+) with a prompt file, the target
working directory, a specific free model, JSON event output, a timeout, and
auto-approve of file permissions so it can run unattended.

Contract verified against: opencode run --help (opencode 1.17.9)
  opencode run [message..]
      --model provider/model
      --dir <directory>
      --format json
      --file <prompt-file>
      --dangerously-skip-permissions
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Any

from .health import HealthRegistry
from .net import RateLimitError, retry
from .router import ModelUnavailableError


class ModelError(RuntimeError):
    """A model call failed (timeout, hang, rate limit, network, crash)."""


class ModelTimeout(ModelError):
    pass


class ModelHang(ModelError):
    pass


class ModelRateLimited(ModelError):
    pass


class OpenCodeUnavailable(ModelError):
    pass


RESULT_OK = "ok"
RESULT_EMPTY = "empty"
RESULT_TIMEOUT = "timeout"
RESULT_ERROR = "error"
RESULT_RATE_LIMIT = "rate_limit"


def _opencode_bin() -> str:
    bin_path = shutil.which("opencode")
    if not bin_path:
        raise OpenCodeUnavailable("opencode CLI not found on PATH")
    return bin_path


class OpenCodeDriver:
    """Runs one non-interactive opencode session for a model."""

    def __init__(self, timeout_seconds: int = 600):
        self.timeout_seconds = timeout_seconds

    def run(
        self,
        *,
        model: str,
        cwd: Path | str,
        prompt_file: Path | str,
        max_output_bytes: int = 2_000_000,
    ) -> tuple[str, str]:
        """Run opencode. Returns (result_code, text_output).

        result_code is one of RESULT_*. text_output is the assistant's final
        text (for RESULT_OK) or error context otherwise.
        """
        bin_path = _opencode_bin()
        cmd = [
            bin_path, "run",
            "--model", model,
            "--dir", str(cwd),
            "--format", "json",
            "--file", str(prompt_file),
            "--dangerously-skip-permissions",
            "Follow the instructions in the attached prompt file. "
            "Report DONE when finished.",
        ]
        started = time.time()
        try:
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                cwd=str(cwd),
                env=dict(os.environ),
            )
        except OSError as exc:
            raise OpenCodeUnavailable(f"failed to start opencode: {exc}") from exc

        stdout_lines: list[str] = []
        stderr_chunks: list[str] = []
        timeout_secs = self.timeout_seconds

        def _drain() -> None:
            # non-blocking drain of stderr while we wait for stdout to finish
            import selectors
            sel = selectors.DefaultSelector()
            sel.register(proc.stdout, selectors.EVENT_READ, "out")
            sel.register(proc.stderr, selectors.EVENT_READ, "err")
            deadline = time.time() + timeout_secs
            while time.time() < deadline:
                events = sel.select(timeout=1.0)
                for key, _ in events:
                    line = key.fileobj.readline()
                    if not line:
                        sel.unregister(key.fileobj)
                        continue
                    if key.data == "out":
                        stdout_lines.append(line)
                    else:
                        stderr_chunks.append(line)
                if proc.poll() is not None:
                    # final drain
                    for line in proc.stdout:
                        stdout_lines.append(line)
                    for line in proc.stderr:
                        stderr_chunks.append(line)
                    break
            else:
                proc.kill()
                proc.wait()
                raise ModelHang(
                    f"opencode run for {model} hung past {timeout_secs}s "
                    f"(output so far: {len(stdout_lines)} lines)"
                )

        try:
            _drain()
            exit_code = proc.wait(timeout=5)
        except ModelHang:
            raise
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()
            raise ModelTimeout(f"opencode run for {model} exceeded {timeout_secs}s")

        if exit_code != 0:
            stderr_text = "".join(stderr_chunks)[-2000:]
            low = stderr_text.lower()
            if "429" in low or "rate limit" in low or "rate_limit" in low:
                raise ModelRateLimited(f"{model} hit a rate limit: {stderr_text.strip()}")
            raise ModelError(f"opencode exited {exit_code} for {model}: {stderr_text.strip()}")

        error_msg = self._has_error(stdout_lines)
        if error_msg:
            low = error_msg.lower()
            if "429" in low or "rate limit" in low or "rate_limit" in low:
                raise ModelRateLimited(f"{model} hit a rate limit: {error_msg}")
            raise ModelError(f"opencode error for {model}: {error_msg}")

        text = self._extract_text(stdout_lines)
        if not text.strip():
            return RESULT_EMPTY, "opencode produced no assistant text"
        return RESULT_OK, text

    def _extract_text(self, jsonl_lines: list[str]) -> str:
        """Parse newline-delimited JSON events from --format json.

        We look for assistant message events and assemble the final text.
        Event shapes differ across opencode versions, so this is defensive:
        any JSON object that carries role/text fields is collected in order.
        """
        texts: list[str] = []
        for raw in jsonl_lines:
            raw = raw.strip()
            if not raw:
                continue
            try:
                event = json.loads(raw)
            except json.JSONDecodeError:
                # Not JSON — could be a stray log line; ignore.
                continue
            text = self._event_text(event)
            if text:
                texts.append(text)
        return "\n".join(texts).strip()

    @staticmethod
    def _event_text(event: Any) -> str:
        if isinstance(event, dict):
            t = event.get("type")
            if t == "text":
                part = event.get("part")
                if isinstance(part, dict):
                    return str(part.get("text", ""))
                return str(event.get("text", ""))
            if t in ("message.partial", "message.updated", "message"):
                data = event.get("data") if isinstance(event.get("data"), dict) else event
                if isinstance(data, dict):
                    return str(data.get("text", ""))
                return str(event.get("text", ""))
            # tool_result carries file edits that did not produce a text event
            if t == "tool_result":
                part = event.get("part")
                if isinstance(part, dict) and part.get("tool") == "write":
                    return ""
        return ""

    @staticmethod
    def _has_error(jsonl_lines: list[str]) -> str:
        """Return the error message if an `error` event appeared in the stream."""
        for raw in jsonl_lines:
            raw = raw.strip()
            if not raw:
                continue
            try:
                event = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if isinstance(event, dict) and event.get("type") == "error":
                err = event.get("error")
                if isinstance(err, dict):
                    data = err.get("data") or {}
                    return str(data.get("message", err.get("name", "opencode error")))
                return str(err)
        return ""


def run_model_with_fallback(
    *,
    driver: OpenCodeDriver,
    health: HealthRegistry,
    model_names: list[str],
    cwd: Path | str,
    prompt_file: Path | str,
    retries: int = 2,
    record_timeout_health: bool = True,
) -> tuple[str, str, str]:
    """Try models in order; on failure record health and switch.

    Returns (model_used, result_code, text). Raises ModelUnavailableError if
    every candidate fails.
    """
    last_exc: ModelError | None = None
    for model in model_names:
        started = health.record_start(model)
        try:
            result = retry(
                lambda m=model: driver.run(model=m, cwd=cwd, prompt_file=prompt_file),
                attempts=retries,
                base_delay=2.0,
                max_delay=30.0,
                retry_on=(ModelTimeout, ModelHang, ModelRateLimited, OSError),
            )
            if result[0] == RESULT_OK:
                health.record_success(model, started)
                return model, RESULT_OK, result[1]
            health.record_failure(model, "empty_response", started, result[1])
            last_exc = ModelError(f"{model} produced empty response")
            continue
        except ModelRateLimited as exc:
            if record_timeout_health:
                health.record_failure(model, "rate_limit", started, str(exc))
            last_exc = exc
            continue
        except ModelTimeout as exc:
            if record_timeout_health:
                health.record_failure(model, "timeout", started, str(exc))
            last_exc = exc
            continue
        except ModelHang as exc:
            if record_timeout_health:
                health.record_failure(model, "timeout", started, str(exc))
            last_exc = exc
            continue
        except ModelError as exc:
            if record_timeout_health:
                health.record_failure(model, "error", started, str(exc))
            last_exc = exc
            continue
        except Exception as exc:  # noqa: BLE001
            if record_timeout_health:
                health.record_failure(model, "error", started, str(exc))
            last_exc = ModelError(str(exc))
            continue
    raise ModelUnavailableError(
        f"all candidate models failed ({len(model_names)} tried); last error: {last_exc}"
    )
