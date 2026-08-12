"""Network fault tolerance: timeouts, exponential backoff with jitter.

Used for AI model calls (subprocess), GitHub API, and Firebase interaction.
"""

from __future__ import annotations

import random
import time
from collections.abc import Callable
from typing import TypeVar

T = TypeVar("T")


class RetryExhaustedError(RuntimeError):
    pass


class RateLimitError(RuntimeError):
    pass


def backoff_delay(attempt: int, base: float = 2.0, max_delay: float = 60.0, jitter: float = 0.3) -> float:
    """Exponential backoff with full jitter: base * 2^attempt, randomized."""
    delay = min(max_delay, base * (2 ** max(0, attempt - 1)))
    return delay * random.uniform(1.0 - jitter, 1.0 + jitter)


def retry(
    fn: Callable[[], T],
    *,
    attempts: int = 4,
    base_delay: float = 2.0,
    max_delay: float = 60.0,
    jitter: float = 0.3,
    retry_on: tuple[type[BaseException], ...] = (Exception,),
) -> T:
    """Call fn with exponential backoff + jitter on failure.

    Stops retrying on RateLimitError after respecting a backoff. Raises
    RetryExhaustedError when all attempts are consumed.
    """
    last_exc: BaseException | None = None
    for attempt in range(1, attempts + 1):
        try:
            return fn()
        except RateLimitError:
            # Rate limits need a longer backoff before we even try again.
            delay = backoff_delay(attempt, base=base_delay * 4, max_delay=max_delay * 2, jitter=jitter)
            time.sleep(delay)
            last_exc = RateLimitError()
        except retry_on as exc:  # type: ignore[assignment]
            last_exc = exc
            if attempt < attempts:
                delay = backoff_delay(attempt, base=base_delay, max_delay=max_delay, jitter=jitter)
                time.sleep(delay)
    raise RetryExhaustedError(f"retry exhausted after {attempts} attempts: {last_exc}")
