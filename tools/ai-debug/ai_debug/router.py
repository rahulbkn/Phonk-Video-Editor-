"""Task classification + free-model routing.

The router:
  1. Classifies a failure into a task category (ANDROID_UI, KOTLIN, CRASH_ANALYSIS, ...).
  2. Returns an ordered candidate list of free models (task priority, then
     global order) filtered by the model health system.
  3. Falls back to the next healthy model when one is unavailable.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence

from .config import Config

# Ordered global preference: strongest broad model first. Used as tie-breaker
# between models that share the same task priority.
GLOBAL_ORDER = [
    "opencode/longcat-2.0-free",
    "opencode/nemotron-3-ultra-free",
    "opencode/nemotron-3.5-lighting-free",
    "opencode/deepseek-v4-flash-free",
    "opencode/mimo-v2.5-free",
    "opencode/big-pickle",
    "opencode/ling-3.0-tiny-free",
    "opencode/laguna-s-2.1-free",
]

KEYWORD_TASKS: list[tuple[tuple[str, ...], str]] = [
    (("execution failed for task", "gradle", "agp", "dependency", "jdk", "kotlin compiler"), "GRADLE_BUILD"),
    (("github actions", "workflow", "ci "), "CI_FAILURE"),
    (("kotlin", ".kt"), "KOTLIN"),
    ((".java", "javax", "java.lang"), "JAVA"),
    ((".cpp", ".cc", ".hpp", "jni", "ndk", "native"), "C_CPP"),
    (("ffmpeg", "avcodec", "avformat", "swscale"), "FFMPEG"),
    (("mediacodec", "codec", "encoder", "decoder", "surface"), "MEDIA_CODEC"),
    (("android ui", "compose", "layout", "view", "screen", "render", "xml"), "ANDROID_UI"),
    (("firebase test lab", "instrumentation", "espresso", "robo"), "FIREBASE_TEST"),
    (("stack trace", "stacktrace", "fatal exception", "crash"), "CRASH_ANALYSIS"),
    (("logcat", "logcat", "androidruntime"), "LOGCAT_ANALYSIS"),
    (("unit test", "test failed", "assertion", "failed test"), "TEST_FAILURE"),
    (("refactor", "multi-file", "large repository"), "MULTI_FILE_REFACTOR"),
]


def classify_task(text: str, fallback: str = "GENERAL_DEBUGGING") -> str:
    """Classify a failure summary into a task category by keyword matching."""
    low = (text or "").lower()
    if not low:
        return fallback
    for keywords, task in KEYWORD_TASKS:
        for kw in keywords:
            if kw in low:
                return task
    return fallback


def _model_sort_key(name: str, meta: dict) -> tuple[int, int]:
    priority = int(meta.get("priority", 99))
    try:
        global_idx = GLOBAL_ORDER.index(name)
    except ValueError:
        global_idx = len(GLOBAL_ORDER) + 1
    return (priority, global_idx)


def candidates_for_task(cfg: Config, task: str) -> list[str]:
    """All models that claim the task, sorted by priority then global order."""
    matching = [name for name, meta in cfg.models.items() if task in meta.get("tasks", [])]
    matching.sort(key=lambda n: _model_sort_key(n, cfg.models[n]))
    return matching


class ModelUnavailableError(RuntimeError):
    """Raised when no model in the pool is currently usable for a task."""


class ModelRouter:
    def __init__(self, cfg: Config, health):
        self.cfg = cfg
        self.health = health

    def available_models(self, task: str) -> list[str]:
        ordered = candidates_for_task(self.cfg, task)
        if not ordered:
            ordered = candidates_for_task(self.cfg, self.cfg.default_task)
        healthy = [m for m in ordered if self.health.is_available(m)]
        if not healthy:
            # fall back to any healthy model regardless of task affinity
            all_healthy = [m for m in self.cfg.model_names if self.health.is_available(m)]
            all_healthy.sort(key=lambda n: _model_sort_key(n, self.cfg.models[n]))
            healthy = all_healthy
        return healthy

    def pick(self, task: str, exclude: Iterable[str] = ()) -> str:
        """Pick the best currently-healthy free model for the task."""
        excluded = set(exclude)
        for name in self.available_models(task):
            if name in excluded:
                continue
            return name
        raise ModelUnavailableError(
            f"no free model available for task '{task}' (all models unhealthy or excluded)"
        )

    def verify_pool_available(self) -> bool:
        return any(self.health.is_available(m) for m in self.cfg.model_names)
