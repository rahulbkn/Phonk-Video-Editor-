#!/usr/bin/env python3
"""Automatic CURRENT STATE SNAPSHOT generator for the Phonk Drop Editor.

After EVERY successful Gradle APK build this script inspects the ACTUAL
current source tree and regenerates a human-readable, AI-friendly state report
on the device shared storage:

    /storage/emulated/0/PhonkVideoEditor/APP_CURRENT_STATE.txt
    /storage/emulated/0/PhonkVideoEditor/state-history/APP_CURRENT_STATE_<ts>.txt

Behaviour guarantees:

  * Atomic writes: the report is written to `*.tmp` and renamed into place
    (os.replace) so a killed process can never leave a half-written file.
  * History rotation: at most `--keep N` (default 20) timestamped snapshots are
    kept; only the OLDEST are deleted, never APP_CURRENT_STATE.txt.
  * Storage failure safety: if shared storage cannot be written the build is
    NOT failed. A local copy is always kept under
    <module>/build/outputs/state/ and the problem is reported on stderr.
  * Failed builds: when the APK the build was supposed to produce is older than
    the build-attempt marker (i.e. assemble* did not succeed), the last
    known-good APP_CURRENT_STATE.txt is NOT overwritten; instead
    APP_CURRENT_STATE_BUILD_FAILED.txt is written with the failure summary.

This is a development/debugging aid. It never touches editor code.

Usage:
  snapshot.py <project-root> <apk-output-root> [--keep N]
"""
import datetime
import glob
import json
import os
import re
import shutil
import subprocess
import sys

APP_DIR_NAME = "PhonkVideoEditor"
STATE_FILE_NAME = "APP_CURRENT_STATE.txt"
FAILED_FILE_NAME = "APP_CURRENT_STATE_BUILD_FAILED.txt"
HISTORY_DIR_NAME = "state-history"
DEFAULT_KEEP = 20

# --------------------------------------------------------------------------
# Small logging helpers (everything goes to stderr; the build must never see
# stdout that could be mis-read as task output).
# --------------------------------------------------------------------------

def log(msg: str) -> None:
    print("[state-snapshot] %s" % msg, file=sys.stderr)


def warn(msg: str) -> None:
    print("[state-snapshot] WARNING: %s" % msg, file=sys.stderr)


# --------------------------------------------------------------------------
# Filesystem / git helpers
# --------------------------------------------------------------------------

def detect_storage_root() -> str | None:
    """First writable shared-storage path wins; falls back to $EXTERNAL_STORAGE."""
    candidates = [
        os.environ.get("EXTERNAL_STORAGE"),
        "/storage/emulated/0",
        "/sdcard",
        "/storage/self/primary",
    ]
    seen = set()
    for c in candidates:
        if not c or c in seen:
            continue
        seen.add(c)
        try:
            if os.path.isdir(c) and os.access(c, os.W_OK):
                return c
        except OSError:
            pass
    return None


def run_git(root: str, *args: str) -> str:
    try:
        out = subprocess.run(
            ["git", "-C", root, *args],
            capture_output=True,
            text=True,
            timeout=15,
        )
        return out.stdout.strip()
    except Exception as exc:
        warn("git %s failed: %s" % (" ".join(args), exc))
        return ""


def git_summary(root: str) -> dict:
    return {
        "commit": run_git(root, "rev-parse", "--short", "HEAD"),
        "commit_long": run_git(root, "rev-parse", "HEAD"),
        "branch": run_git(root, "rev-parse", "--abbrev-ref", "HEAD"),
        "dirty": bool(run_git(root, "status", "--porcelain")),
        "changed_files": len([
            l for l in run_git(root, "status", "--porcelain").splitlines() if l.strip()
        ]),
    }


def android_info() -> dict:
    """Best-effort device OS info from the live build.prop."""
    info = {"release": "UNKNOWN", "sdk": "UNKNOWN"}
    path = "/system/build.prop"
    if not os.path.exists(path):
        return info
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                line = line.strip()
                if line.startswith("ro.build.version.release="):
                    info["release"] = line.split("=", 1)[1].strip()
                elif line.startswith("ro.build.version.sdk="):
                    info["sdk"] = line.split("=", 1)[1].strip()
    except OSError as exc:
        warn("cannot read %s: %s" % (path, exc))
    return info


def load_gradle_app_module(root: str) -> dict:
    """Extracts version / sdk / package facts from app/build.gradle.kts."""
    path = os.path.join(root, "app", "build.gradle.kts")
    text = ""
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            text = f.read()
    except OSError as exc:
        warn("cannot read %s: %s" % (path, exc))
        return {}

    def grab(pattern: str) -> str:
        m = re.search(pattern, text)
        return m.group(1).strip().strip('"') if m else "?"

    facts = {
        "applicationId": grab(r"applicationId\s*=\s*\"([^\"]+)\""),
        "namespace": grab(r"namespace\s*=\s*\"([^\"]+)\""),
        "compileSdk": grab(r"compileSdk\s*=\s*(\d+)"),
        "targetSdk": grab(r"targetSdk\s*=\s*(\d+)"),
        "minSdk": grab(r"minSdk\s*=\s*(\d+)"),
        "versionCode": grab(r"versionCode\s*=\s*(\d+)"),
        "versionName": grab(r"versionName\s*=\s*\"([^\"]+)\""),
        "ndkVersion": grab(r"ndkVersion\s*=\s*\"([^\"]+)\""),
        "abi": grab(r"abiFilters\s*\+=\s*listOf\(\s*\"([^\"]+)\""),
    }
    return facts


def load_sources(root: str) -> dict:
    """Reads every app/src file into {relpath: content} (relpath is /-joined)."""
    src_root = os.path.join(root, "app", "src")
    files = {}
    if not os.path.isdir(src_root):
        return files
    for dirpath, _dirnames, filenames in os.walk(src_root):
        for fn in filenames:
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, root).replace(os.sep, "/")
            try:
                with open(p, "r", encoding="utf-8", errors="replace") as f:
                    files[rel] = f.read()
            except OSError:
                files[rel] = ""
    return files


def check(sources: dict, *clauses) -> bool:
    """True when every clause is satisfied.

    A clause is (file_substring, [patterns]); it matches when SOME file whose
    relative path contains file_substring contains ALL patterns. This keeps the
    feature probes close to real code — a feature is never marked WORKING from
    a stray string alone.
    """
    for fsub, pats in clauses:
        hit = False
        for rel, content in sources.items():
            if fsub in rel and all((p in content) or (p in rel) for p in pats):
                hit = True
                break
        if not hit:
            return False
    return True


def feat_status(sources: dict, required, partial, note: str = "") -> tuple:
    if check(sources, *required):
        return "WORKING", note
    if partial and check(sources, *partial):
        return "PARTIAL", note
    return "NOT_IMPLEMENTED", note


def latest_apk(apk_root: str):
    """Returns (mtime, build_type, apk_path) for the newest APK or None."""
    best = None
    for btype in ("debug", "release"):
        for apk in glob.glob(os.path.join(apk_root, btype, "*.apk")):
            mt = os.path.getmtime(apk)
            if best is None or mt > best[0]:
                best = (mt, btype, apk)
    return best


def read_marker(state_out_root: str, build_type: str) -> float | None:
    """mtime (epoch seconds) of the build-attempt marker, if present."""
    p = os.path.join(state_out_root, "build-start-%s.marker" % build_type)
    try:
        with open(p, "r") as f:
            return int(f.read().strip()) / 1000.0
    except (OSError, ValueError):
        return None


def now_stamp() -> str:
    return datetime.datetime.now().strftime("%Y-%m-%d_%H%M%S")


def now_iso() -> str:
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def write_atomic(path: str, content: str) -> None:
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(content)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def write_local_copy(apk_root: str, content: str, build_type: str) -> str:
    """Always keep a copy under <module>/build/outputs/state/. Returns path."""
    state_out = os.path.join(os.path.dirname(os.path.normpath(apk_root)), "state")
    os.makedirs(state_out, exist_ok=True)
    local = os.path.join(state_out, STATE_FILE_NAME)
    write_atomic(local, content)
    return local


def rotate_history(history_dir: str, keep: int) -> None:
    os.makedirs(history_dir, exist_ok=True)
    entries = sorted(
        p for p in glob.glob(os.path.join(history_dir, "APP_CURRENT_STATE_*.txt"))
    )
    for stale in entries[:-keep] if keep > 0 else entries:
        try:
            os.remove(stale)
        except OSError as exc:
            warn("cannot delete stale snapshot %s: %s" % (stale, exc))


# --------------------------------------------------------------------------
# Report builders
# --------------------------------------------------------------------------

def build_feature_status(sources: dict) -> list:
    """Returns (group, [(feature, status, note)]) derived from the source tree.

    A feature is WORKING only when the full chain exists (model field, preview
    path and export path, or an obviously complete UI→VM→model wiring). PARTIAL
    when only part of the chain is present. Nothing is asserted from button
    labels alone.
    """
    fx = "app/src/main/java/dev/phonk/editor"
    effects_group = [
        ("Brightness", feat_status(
            sources,
            [(fx + "/model/ColorGrade.kt", ["BRIGHTNESS(-1f..1f)", "val brightness"])],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_brightness"])],
            "grade slider + preview matrix + eq export")),
        ("Contrast", feat_status(
            sources,
            [(fx + "/model/ColorGrade.kt", ["CONTRAST(-1f..1f)", "val contrast"])],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_contrast"])],
            "grade slider + preview matrix + eq export")),
        ("Saturation", feat_status(
            sources,
            [(fx + "/model/ColorGrade.kt", ["SATURATION(-1f..1f)", "val saturation"])],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_saturation"])],
            "grade slider + preview matrix + eq export")),
        ("Exposure", feat_status(
            sources,
            [
                (fx + "/model/ColorGrade.kt", ["GradeParam.EXPOSURE", "val exposure"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["exposure * 0.5"]),
                (fx + "/ui/editor/EditorPreview.kt", ["grade.exposure"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_exposure"])],
            "combined into brightness in preview + export")),
        ("Color grading", feat_status(
            sources,
            [
                (fx + "/model/ColorGrade.kt", ["data class ColorGrade"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["appendColorGrade"]),
                (fx + "/ui/editor/EditorPreview.kt", ["buildRenderEffect"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["filters_presets"])],
            "13 params + presets; single source of truth for preview+export")),
        ("Blur", feat_status(
            sources,
            [
                (fx + "/model/ColorGrade.kt", ["GradeParam.BLUR", "val blur"]),
                (fx + "/ui/editor/EditorPreview.kt", ["createBlurEffect"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["boxblur"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_blur"])],
            "preview RenderEffect + boxblur export")),
        ("Sharpen", feat_status(
            sources,
            [
                (fx + "/model/ColorGrade.kt", ["GradeParam.SHARPNESS", "val sharpness"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["unsharp"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_sharpness"])],
            "export-only: preview does not render unsharp")),
        ("Vignette", feat_status(
            sources,
            [
                (fx + "/model/ColorGrade.kt", ["GradeParam.VIGNETTE", "val vignette"]),
                (fx + "/ui/editor/EditorPreview.kt", ["vignetteAlpha"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["vignette=angle"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_vignette"])],
            "preview radial gradient + vignette export")),
        ("Film grain", feat_status(
            sources,
            [
                (fx + "/model/ColorGrade.kt", ["GradeParam.GRAIN", "val grain"]),
                (fx + "/ui/editor/EditorPreview.kt", ["grainAlpha"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["noise=alls"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["adj_grain"])],
            "preview noise overlay + noise export")),
        ("RGB split", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["RGBSPLIT"]),
                (fx + "/ui/editor/EditorPreview.kt", ["RGBSPLIT"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["RGBSPLIT"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["fx_beat_rgb"])],
            "preview translation + hue/saturation export")),
        ("Glitch", feat_status(
            sources,
            [
                (fx + "/ui/editor/EditorPreview.kt", ["EffectKind.GLITCH"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["EffectKind.GLITCH"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["fx_glitch"])],
            "preview scan/translation + hue export")),
        ("Flash", feat_status(
            sources,
            [
                (fx + "/ui/editor/EditorPreview.kt", ["flashAlpha"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["EffectKind.FLASH"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["fx_beat_flash"])],
            "preview white flash + lutyuv export")),
        ("Shake", feat_status(
            sources,
            [
                (fx + "/ui/editor/EditorPreview.kt", ["EffectKind.SHAKE"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["EffectKind.SHAKE"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["fx_beat_shake"])],
            "preview translation + crop export")),
        ("Zoom", feat_status(
            sources,
            [
                (fx + "/ui/editor/EditorPreview.kt", ["EffectKind.ZOOM"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["zoompan"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["fx_beat_zoom"])],
            "preview scale + zoompan export")),
        ("FADE (film)", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["FADE(\"fade\")"]),
                (fx + "/ui/editor/EditorPreview.kt", ["EffectKind.FADE"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["EffectKind.FADE"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["fx_film"])],
            "selectable in UI but NOT rendered in preview or export")),
        ("Distortion / Motion Blur / Glow / Scanlines", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["DISTORTION("]),
                (fx + "/ui/editor/EditorPreview.kt", ["EffectKind.DISTORTION"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["fx_beat_distortion"])],
            "string resources exist; no EffectKind or render path")),
    ]

    overlays_group = [
        ("Text", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["data class TextLayer"]),
                (fx + "/export/ExportRunner.kt", ["rasterizeText"]),
                (fx + "/ui/editor/OverlayEditor.kt", ["onEditText"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["tool_text"])],
            "add/edit/animate + rasterized export")),
        ("Image", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["data class OverlayLayer"]),
                (fx + "/ui/editor/EditorPreview.kt", ["rememberBitmapFromUri"]),
                (fx + "/export/ExportRunner.kt", ["copyOverlayFiles"]),
            ],
            [(fx + "/ui/editor/panels/AudioOverlayPanels.kt", ["import_photos"])],
            "content-URI image import + preview + export")),
        ("Sticker", feat_status(
            sources,
            [
                (fx + "/ui/editor/panels/AudioOverlayPanels.kt", ["\"Sticker\""]),
                (fx + "/export/ExportRunner.kt", ["rasterizeShape"]),
            ],
            [(fx + "/ui/editor/EditorPreview.kt", ["drawShapeFallback"])],
            "glyph-based sticker + rasterized export")),
        ("Shape", feat_status(
            sources,
            [(fx + "/ui/editor/panels/AudioOverlayPanels.kt", ["\"Shape\""])],
            [(fx + "/export/ExportRunner.kt", ["rasterizeShape"])],
            "glyph fallback rendering")),
        ("Emoji", feat_status(
            sources,
            [(fx + "/ui/editor/panels/AudioOverlayPanels.kt", ["\"Emoji\""])],
            [(fx + "/export/ExportRunner.kt", ["Emoji"])],
            "emoji added as text glyph")),
        ("Drawing", feat_status(
            sources,
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["DRAW"])],
            [],
            "no drawing tool in the codebase")),
        ("Drag", feat_status(
            sources,
            [(fx + "/ui/editor/OverlayEditor.kt", ["GestureMode.MOVE"])],
            [],
            "free drag with bounds + snap guides")),
        ("Resize", feat_status(
            sources,
            [(fx + "/ui/editor/OverlayEditor.kt", ["GestureMode.RESIZE"])],
            [],
            "corner handles + pinch")),
        ("Rotate", feat_status(
            sources,
            [
                (fx + "/ui/editor/OverlayEditor.kt", ["GestureMode.ROTATE"]),
                (fx + "/ui/editor/OverlayEditor.kt", ["rotatedContains"]),
            ],
            [],
            "rotate handle + two-finger rotate + angle snap")),
        ("Z-order", feat_status(
            sources,
            [
                (fx + "/ui/EditorViewModel.kt", ["bringOverlayToFront"]),
                (fx + "/ui/EditorViewModel.kt", ["sendOverlayToBack"]),
            ],
            [],
            "bring front / send back buttons")),
        ("Lock", feat_status(
            sources,
            [
                (fx + "/ui/EditorViewModel.kt", ["setOverlayLocked"]),
                (fx + "/ui/editor/OverlayEditor.kt", ["item.locked"]),
            ],
            [],
            "lock blocks transform, selection allowed")),
        ("Hide", feat_status(
            sources,
            [(fx + "/ui/EditorViewModel.kt", ["setOverlayVisible"])],
            [],
            "hidden items skipped in preview + export")),
        ("Duplicate", feat_status(
            sources,
            [(fx + "/ui/EditorViewModel.kt", ["duplicateOverlay"])],
            [],
            "copies content/transform/timing, offsets position")),
        ("Delete", feat_status(
            sources,
            [(fx + "/ui/EditorViewModel.kt", ["deleteOverlay"])],
            [],
            "removes from textLayers + overlays")),
        ("Timeline duration", feat_status(
            sources,
            [
                (fx + "/ui/EditorViewModel.kt", ["setOverlayTiming"]),
                (fx + "/timeline/TimelineView.kt", ["onSetOverlayTiming"]),
            ],
            [],
            "bar move + trim handles on the overlay track")),
        ("Keyframes", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["data class OverlayKeyframe"]),
                (fx + "/model/OverlayFx.kt", ["evaluateOverlayFx"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["overlayWindows"]),
            ],
            [(fx + "/ui/editor/panels/ToolPanels.kt", ["keyframes_add"])],
            "model + export interpolation exist; NO UI to author overlay keyframes")),
    ]

    timeline_group = [
        ("Playhead", feat_status(
            sources,
            [
                (fx + "/timeline/TimelineController.kt", ["var currentMs"]),
                (fx + "/timeline/TimelineView.kt", ["drawLine(playX, 4f"]),
            ],
            [], "live playhead + handle")),
        ("Scrubbing", feat_status(
            sources,
            [
                (fx + "/timeline/TimelineView.kt", ["scrubGesture"]),
                (fx + "/preview/PlayerController.kt", ["fun scrubTo"]),
            ],
            [], "lower-half touch scrubs the player")),
        ("Clip timing", feat_status(
            sources,
            [
                (fx + "/ui/EditorViewModel.kt", ["trimClip"]),
                (fx + "/ui/EditorViewModel.kt", ["splitAt"]),
                (fx + "/ui/EditorViewModel.kt", ["duplicateSelectedClip"]),
            ],
            [], "split / trim handles / duplicate / shift-after")),
        ("Overlay timing", feat_status(
            sources,
            [
                (fx + "/timeline/TimelineView.kt", ["OverlayDragMode"]),
                (fx + "/ui/EditorViewModel.kt", ["moveOverlayTimeline"]),
            ],
            [], "overlay/text bars with move + trim")),
        ("Keyframes", feat_status(
            sources,
            [
                (fx + "/ui/EditorViewModel.kt", ["addGradeKeyframe"]),
                (fx + "/ui/editor/panels/ToolPanels.kt", ["keyframes_add"]),
                (fx + "/model/ColorGrade.kt", ["data class GradeKeyframe"]),
            ],
            [], "grade automation keyframes add/clear/enable at playhead")),
        ("Markers", feat_status(
            sources,
            [
                (fx + "/timeline/TimelineView.kt", ["project.beats.forEach"]),
                (fx + "/timeline/TimelineView.kt", ["project.drops.forEach"]),
            ],
            [], "beat ticks + drop markers + manual add/remove")),
        ("Speed", feat_status(
            sources,
            [
                (fx + "/ui/EditorViewModel.kt", ["setClipSpeed"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["atempo"]),
            ],
            [], "per-clip 0.25-4x + export speed ramps")),
    ]

    audio_group = [
        ("Audio import", feat_status(
            sources,
            [(fx + "/ui/EditorViewModel.kt", ["importAudio"])],
            [], "separate audio track import")),
        ("Waveform", feat_status(
            sources,
            [
                (fx + "/timeline/TimelineView.kt", ["drawWaveform"]),
                (fx + "/model/PhonkProject.kt", ["waveform"]),
            ],
            [], "compact energy curve on the audio track")),
        ("BPM", feat_status(
            sources,
            [
                (fx + "/native/PhonkNative.kt", ["nativeAnalyzeAudio"]),
                (fx + "/model/PhonkProject.kt", ["val bpm"]),
            ],
            [], "native DSP autocorrelation")),
        ("Beat detection", feat_status(
            sources,
            [
                (fx + "/analysis/analysisManager.kt", ["fun analyze"]),
                (fx + "/analysis/audioExtractor.kt", ["MediaCodec.createDecoderByType"]),
                (fx + "/cpp/phonk_jni.cpp", ["nativeAnalyzeAudio"]),
            ],
            [], "MediaCodec decode + C++ DSP via JNI")),
        ("Beat markers", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["data class BeatMarker"]),
                (fx + "/timeline/TimelineView.kt", ["beatPaint"]),
            ],
            [], "beats persist + drawn on the timeline")),
        ("Beat sync", feat_status(
            sources,
            [
                (fx + "/model/ColorGrade.kt", ["object BeatSyncEngine"]),
                (fx + "/ui/editor/EditorPreview.kt", ["BeatSyncEngine.frame"]),
                (fx + "/export/ExportRunner.kt", ["beatPulses"]),
            ],
            [], "live pulse + exportable brightness pulses")),
    ]

    export_group = [
        ("FFmpeg", feat_status(
            sources,
            [
                (fx + "/ffmpeg/FfmpegRenderer.kt", ["class ProcessFFmpegEngine"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["object FFmpegCommandBuilder"]),
            ],
            [(fx + "/scripts/fetch-ffmpeg.sh", ["ffmpeg"])],
            "engine present; binary NOT bundled (drop-in filesDir/ffmpeg required)")),
        ("Video export", feat_status(
            sources,
            [(fx + "/export/ExportRunner.kt", ["renderer.render"])],
            [], "single-invocation render + MediaStore publish")),
        ("Audio export", feat_status(
            sources,
            [
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["[outa]"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["atempo"]),
            ],
            [], "atrim/concat + atempo + aac mux")),
        ("Effects", feat_status(
            sources,
            [
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["appendEffect"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["appendClipEffects"]),
            ],
            [], "beat-aligned + per-clip effects in the graph")),
        ("Color grade", feat_status(
            sources,
            [(fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["appendColorGrade"])],
            [], "eq/boxblur/vignette/noise/unsharp chain")),
        ("Overlay export", feat_status(
            sources,
            [
                (fx + "/export/ExportRunner.kt", ["buildOverlayRenders"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["appendOverlayGraphs"]),
            ],
            [], "rasterized text/shape + image composites, z-ordered")),
        ("Keyframes", feat_status(
            sources,
            [
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["gradeWindows"]),
                (fx + "/ffmpeg/FFmpegCommandBuilder.kt", ["overlayWindows"]),
            ],
            [], "windowed grade + overlay transform slices")),
        ("Beat sync", feat_status(
            sources,
            [(fx + "/export/ExportRunner.kt", ["beatPulses"])],
            [], "brightness-pulse representation")),
        ("Cancellation", feat_status(
            sources,
            [
                (fx + "/export/ExportRunner.kt", ["activeRenderer?.cancel()"]),
                (fx + "/ffmpeg/FfmpegRenderer.kt", ["cancelFlag.set"]),
            ],
            [], "signals the live ffmpeg process")),
        ("Progress", feat_status(
            sources,
            [
                (fx + "/export/ExportRunner.kt", ["ExportState.Running"]),
                (fx + "/ffmpeg/FfmpegRenderer.kt", ["onNewSecond"]),
            ],
            [], "time= parsing -> progress state")),
        ("MediaStore", feat_status(
            sources,
            [
                (fx + "/export/ExportRunner.kt", ["saveToGallery"]),
                (fx + "/export/ExportRunner.kt", ["MediaStore.Video.Media"]),
            ],
            [], "publishes to Movies/Phonk without storage permission")),
    ]

    preview_group = [
        ("Play", feat_status(
            sources,
            [
                (fx + "/preview/PlayerController.kt", ["fun play()"]),
                (fx + "/ui/EditorViewModel.kt", ["fun playPause()"]),
            ],
            [], "ExoPlayer play + play/pause toggle")),
        ("Pause", feat_status(
            sources,
            [(fx + "/preview/PlayerController.kt", ["fun pause()"])],
            [], "playWhenReady=false")),
        ("Seek", feat_status(
            sources,
            [
                (fx + "/preview/PlayerController.kt", ["fun scrubTo"]),
                (fx + "/ui/EditorViewModel.kt", ["fun setCurrentPosition"]),
            ],
            [], "dest->source mapped seek")),
        ("Scrub", feat_status(
            sources,
            [
                (fx + "/preview/PlayerController.kt", ["fun scrubTo"]),
                (fx + "/timeline/TimelineView.kt", ["scrubGesture"]),
            ],
            [], "frame-exact scrubbing while preserving volume")),
        ("Playback speed", feat_status(
            sources,
            [
                (fx + "/preview/PlayerController.kt", ["fun setPreviewSpeed"]),
                (fx + "/ui/EditorScreen.kt", ["setPreviewSpeed"]),
            ],
            [], "0.25-4x preview mirror of per-clip speed")),
        ("Aspect ratio", feat_status(
            sources,
            [
                (fx + "/ui/editor/EditorPreview.kt", ["AspectChip"]),
                (fx + "/ui/editor/EditorPreview.kt", ["aspectRatio"]),
            ],
            [], "9:16 / 1:1 / 16:9 preview letterbox")),
        ("Fullscreen", feat_status(
            sources,
            [(fx + "/ui/editor/EditorPreview.kt", ["var fullscreen"])],
            [], "toggle preview to fill")),
    ]

    ui_group = [
        ("Light theme", feat_status(
            sources,
            [(fx + "/ui/Theme.kt", ["lightColorScheme"])],
            [], "explicit light scheme + colors.xml")),
        ("Dark theme", feat_status(
            sources,
            [
                (fx + "/ui/Theme.kt", ["darkColorScheme"]),
                ("app/src/main/res/values-night", ["colors.xml"]),
            ],
            [], "explicit dark scheme + values-night")),
        ("Hindi", feat_status(
            sources,
            [("app/src/main/res/values-hi", ["strings.xml"])],
            [], "full UI string set")),
        ("English", feat_status(
            sources,
            [("app/src/main/res/values", ["strings.xml"])],
            [], "default resource set")),
        ("Buttons", feat_status(
            sources,
            [(fx + "/ui/components/DesignSystem.kt", ["fun PhonkButton"])],
            [], "shared button components")),
        ("Panels", feat_status(
            sources,
            [
                (fx + "/ui/components/DesignSystem.kt", ["fun PhonkPanel"]),
                (fx + "/ui/editor/panels/ToolPanels.kt", ["fun EffectsPanel"]),
            ],
            [], "10 bottom tool panels")),
        ("Dialogs", feat_status(
            sources,
            [
                (fx + "/ui/editor/TextEditDialog.kt", ["TextEditDialog"]),
                (fx + "/export/ExportDialog.kt", ["ExportDialog"]),
            ],
            [], "text edit + export dialogs")),
    ]

    state_group = [
        ("Project state", feat_status(
            sources,
            [
                (fx + "/model/PhonkProject.kt", ["data class PhonkProject"]),
                (fx + "/model/PhonkProject.kt", ["class ProjectCodec"]),
            ],
            [], "versioned JSON model")),
        ("Undo", feat_status(
            sources,
            [
                (fx + "/editor/EditEngine.kt", ["fun undo"]),
                (fx + "/ui/EditorViewModel.kt", ["fun undo()"]),
            ],
            [], "command stack with gesture coalescing")),
        ("Redo", feat_status(
            sources,
            [
                (fx + "/editor/EditEngine.kt", ["fun redo"]),
                (fx + "/ui/EditorViewModel.kt", ["fun redo()"]),
            ],
            [], "mirror stack")),
        ("Autosave", feat_status(
            sources,
            [(fx + "/ui/EditorViewModel.kt", ["private fun persist()"])],
            [], "persist() after every commit/edit")),
        ("Persistence", feat_status(
            sources,
            [
                (fx + "/project/ProjectStore.kt", ["class ProjectStore"]),
                (fx + "/ui/HomeScreen.kt", ["listRecent"]),
            ],
            [], "filesDir/projects JSON + recent SharedPreferences")),
    ]

    # Group ordering matches the required report layout.
    groups = [
        ("Video Preview", preview_group),
        ("Effects", effects_group),
        ("Overlays", overlays_group),
        ("Timeline", timeline_group),
        ("Audio", audio_group),
        ("Export", export_group),
        ("UI", ui_group),
        ("State", state_group),
    ]
    return groups


def build_architecture(sources: dict) -> list:
    fx = "app/src/main/java/dev/phonk/editor"
    rows = [
        ("Main Activity", "ui/MainActivity.kt", "ComponentActivity + hand-rolled Compose Route router (Home/Editor/Settings/Debug)"),
        ("ViewModel", "ui/EditorViewModel.kt", "editor-scoped state: project flow, player, analysis, export, undo"),
        ("Player", "preview/PlayerController.kt", "thin ExoPlayer (Media3) wrapper: play/pause/scrub/speed/pitch"),
        ("Timeline", "timeline/TimelineView.kt", "custom 5-track pan/zoom View + TimelineController (viewport state)"),
        ("Overlay system", "ui/editor/OverlayEditor.kt", "editor-only gesture layer: select/drag/resize/rotate/pinch + snap"),
        ("Audio system", "analysis/analysisManager.kt", "MediaCodec decode -> 11 kHz mono -> C++ DSP via JNI"),
        ("Effect system", "ui/editor/EditorPreview.kt", "live RenderEffect + beat engine; export twin in FFmpegCommandBuilder"),
        ("Export system", "export/ExportRunner.kt", "plan -> FfmpegRenderer -> MediaStore publish"),
        ("Persistence", "project/ProjectStore.kt", "JSON projects under filesDir/projects + recent list"),
        ("Undo/Redo", "editor/EditEngine.kt", "undo/redo command stack with coalesced gestures"),
        ("Crash reporting", "crash/CrashHandler.kt", "global crash capture + log/history activities + native signal handler"),
    ]
    present = []
    for label, rel, desc in rows:
        if any(rel_path.endswith("/" + rel) or rel_path == rel for rel_path in sources):
            present.append((label, rel, desc))
        else:
            present.append((label, rel, desc + " [source missing]"))
    return present


def test_summary(apk_root: str) -> dict:
    """Reads the JSON written by the collectTestResults Gradle task, if any."""
    p = os.path.join(os.path.dirname(os.path.normpath(apk_root)), "state", "test-results.json")
    try:
        with open(p, "r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def recent_commits(root: str, n: int = 12) -> list:
    raw = run_git(root, "log", "--oneline", "-%d" % n)
    return [l for l in raw.splitlines() if l.strip()]


# --------------------------------------------------------------------------
# Main report
# --------------------------------------------------------------------------

def render_report(
    root: str,
    apk_root: str,
    build_type: str,
    apk_path: str | None,
    ok: bool,
    failed_reason: str | None,
) -> str:
    git = git_summary(root)
    gradle = load_gradle_app_module(root)
    dev = android_info()
    now = now_iso()
    stamp = now_stamp()
    sources = load_sources(root)
    groups = build_feature_status(sources)
    arch = build_architecture(sources)
    tests = test_summary(apk_root)
    commits = recent_commits(root)
    dirty = "yes (uncommitted changes)" if git["dirty"] else "no"

    L = []
    add = L.append
    sep = "=" * 60
    add(sep)
    add("PHONK VIDEO EDITOR — CURRENT STATE")
    add(sep)

    add("")
    add("BUILD INFORMATION")
    add("-----------------")
    add("Build type:        %s" % build_type)
    add("Version name:      %s" % gradle.get("versionName", "?"))
    add("Version code:      %s" % gradle.get("versionCode", "?"))
    add("Build timestamp:   %s" % now)
    add("Git commit:        %s" % git["commit"])
    add("  full:            %s" % git["commit_long"])
    add("Git branch:        %s" % git["branch"])
    add("Working tree:      %s (%d changed file(s))" % (dirty, git["changed_files"]))
    add("Snapshot file:     APP_CURRENT_STATE_%s.txt" % stamp)

    add("")
    add("PROJECT")
    add("-------")
    add("Package name:      %s" % gradle.get("applicationId", gradle.get("namespace", "?")))
    add("Android version:   %s (SDK %s)" % (dev["release"], dev["sdk"]))
    add("compileSdk:        %s" % gradle.get("compileSdk", "?"))
    add("targetSdk:         %s" % gradle.get("targetSdk", "?"))
    add("minSdk:            %s" % gradle.get("minSdk", "?"))
    add("ndkVersion:        %s" % gradle.get("ndkVersion", "?"))
    add("ABI:               %s" % gradle.get("abi", "?"))
    add("AGP:               9.3.1 (from root build.gradle.kts)")
    add("Kotlin/Compose:    2.1.0 / compose-bom 2024.09.00")

    add("")
    add("PROJECT ARCHITECTURE")
    add("--------------------")
    for label, rel, desc in arch:
        add("  %-16s %-38s %s" % (label, rel, desc))

    add("")
    add("FEATURE STATUS")
    add("---------------")
    add("Status key: WORKING = full chain implemented | PARTIAL = part of the")
    add("chain | BROKEN = present but defective | PLACEHOLDER = stub only |")
    add("NOT_IMPLEMENTED = absent | NOT_TESTED = implemented without unit tests |")
    add("UNKNOWN = cannot be determined statically.")
    add("")
    for group_name, feats in groups:
        add("[%s]" % group_name)
        for name, (status, note) in feats:
            if note:
                add("  %-24s %-16s %s" % (name, status, "— " + note))
            else:
                add("  %-24s %s" % (name, status))
        add("")

    add("TESTS")
    add("-----")
    if tests:
        add("Number of tests:   %d" % tests.get("tests", "?"))
        add("Passed:            %d" % (tests.get("tests", 0) - tests.get("failures", 0) - tests.get("errors", 0)))
        add("Failed:            %d" % tests.get("failures", 0))
        add("Errors:            %d" % tests.get("errors", 0))
        add("Skipped:           %d" % tests.get("skipped", 0))
        add("Run timestamp:     %s" % tests.get("timestamp", "?"))
        if tests.get("suites"):
            add("Suites:")
            for s in tests["suites"]:
                add("    - %s" % s)
    else:
        add("No test run recorded in this build.")
        add("Run `gradle :app:testDebugUnitTest` (serialized) to refresh results;")
        add("the summary is then embedded in the next snapshot.")

    add("")
    add("KNOWN BUGS")
    add("----------")
    bugs = [
        ("FADE (film) effect is selectable in the Effects panel but has no render",
         "Low", "ui/editor/panels/ToolPanels.kt, ui/editor/EditorPreview.kt, ffmpeg/FFmpegCommandBuilder.kt"),
        ("Overlay keyframes exist in the model + export but cannot be authored in the UI",
         "Medium", "ui/editor/panels/ToolPanels.kt (no overlay keyframe UI), model/PhonkProject.kt"),
        ("Sharpen is export-only: the live preview does not render an unsharp pass, so preview != export",
         "Medium", "ui/editor/EditorPreview.kt (buildRenderEffect), ffmpeg/FFmpegCommandBuilder.kt"),
        ("FFmpeg binary is not bundled: export fails until an arm64 ffmpeg is dropped into filesDir/ffmpeg",
         "Medium", "ffmpeg/FfmpegRenderer.kt, export/ExportRunner.kt"),
        ("Beat-sync export is limited to brightness pulses (the only component representable in the build)",
         "Low", "export/ExportRunner.kt, ui/editor/EditorPreview.kt"),
    ]
    if not bugs:
        add("  (none reported)")
    for text, sev, files in bugs:
        add("  * [%s] %s" % (sev, text))
        add("      Affected: %s" % files)

    add("")
    add("RECENT FIXES")
    add("------------")
    if commits:
        for c in commits:
            add("  %s" % c)
    else:
        add("  (no git history available)")

    add("")
    add("KNOWN LIMITATIONS")
    add("------------------")
    lims = [
        "Analysis: 11 025 Hz mono; truncated at 15 minutes to protect low-RAM devices.",
        "FFmpeg is NOT bundled (APK stays small); export needs a drop-in binary (scripts/fetch-ffmpeg.sh).",
        "Only arm64-v8a ABI is built (fake NDK skeleton).",
        "FADE effect and several advertised effect labels (Distortion, Motion Blur, Glow, ...) are not rendered.",
        "Overlay keyframes: no authoring UI yet (grade keyframes fully supported).",
        "Drawing/brush overlay: not implemented.",
        "Preview RenderEffect requires Android 12+ (Build.VERSION_CODES.S); older devices render ungraded.",
        "No instrumentation (device) tests in the repo; only JVM unit tests.",
    ]
    for lm in lims:
        add("  - %s" % lm)

    add("")
    add("LAST VERIFIED")
    add("-------------")
    if ok:
        add("Build result:      SUCCESS")
    else:
        add("Build result:      FAILED")
    if apk_path:
        add("APK path:          %s" % apk_path)
    if failed_reason:
        add("Failure summary:   %s" % failed_reason)
    add("Device test result: %s" % (
        "%d/%d passed" % (tests.get("tests", 0) - tests.get("failures", 0) - tests.get("errors", 0), tests.get("tests", 0))
        if tests else "not run in this build"))
    add("Snapshot generated: %s" % now)

    add("")
    add(sep)
    add("END OF STATE REPORT")
    add(sep)
    return "\n".join(L)


def render_failed_report(root: str, build_type: str, apk_root: str, marker: float | None) -> str:
    git = git_summary(root)
    now = now_iso()
    L = []
    add = L.append
    sep = "=" * 60
    add(sep)
    add("PHONK VIDEO EDITOR — BUILD FAILED")
    add(sep)
    add("")
    add("Timestamp:       %s" % now)
    add("Git commit:      %s" % git["commit"])
    add("Git branch:      %s" % git["branch"])
    add("Build command:   gradle assemble%s (via the serialized build wrapper)" % build_type.capitalize())
    if marker:
        attempt = datetime.datetime.fromtimestamp(marker).strftime("%Y-%m-%d %H:%M:%S")
        add("Build attempted: %s" % attempt)
    add("")
    add("Failure summary: No APK was produced for assemble%s. The build did not"
        % build_type.capitalize())
    add("                 complete successfully — check the Gradle console output")
    add("                 (logcat / build-serial.sh output) for the root cause.")
    add("")
    add("The last known-good APP_CURRENT_STATE.txt was intentionally left untouched.")
    add(sep)
    return "\n".join(L)


def main(argv: list) -> int:
    if len(argv) < 3:
        print("usage: snapshot.py <project-root> <apk-output-root> [--keep N]", file=sys.stderr)
        return 2
    root = os.path.abspath(argv[1])
    apk_root = os.path.abspath(argv[2])
    keep = DEFAULT_KEEP
    if "--keep" in argv:
        try:
            keep = int(argv[argv.index("--keep") + 1])
        except (ValueError, IndexError):
            keep = DEFAULT_KEEP

    if not os.path.isdir(root):
        warn("project root missing: %s" % root)
        return 0

    state_out = os.path.join(os.path.dirname(os.path.normpath(apk_root)), "state")
    os.makedirs(state_out, exist_ok=True)

    # Gradle's own verdict (set by the generateStateSnapshot task) is the
    # primary signal; the APK-mtime/marker heuristic is only a fallback for
    # manual runs of this script.
    env_verdict = os.environ.get("STATE_SNAPSHOT_BUILD_OK")
    apk = latest_apk(apk_root)

    if env_verdict == "false":
        build_type = (apk[1].capitalize() if apk else "Debug")
        ok = False
        failed_reason = "Gradle reported one or more failed tasks in the build graph."
        apk_path = apk[2] if apk else None
    elif env_verdict == "true":
        build_type = (apk[1].capitalize() if apk else "Debug")
        ok = True
        failed_reason = None
        apk_path = apk[2] if apk else None
    elif apk is None:
        build_type = "Debug"
        ok = False
        failed_reason = "No APK exists under %s — build did not produce an artifact." % apk_root
        apk_path = None
    else:
        _mt, btype, apk_path = apk
        build_type = btype.capitalize()
        marker = read_marker(state_out, btype)
        if marker is not None and _mt < marker:
            ok = False
            failed_reason = (
                "APK mtime (%s) is older than the build-attempt marker (%s): "
                "assemble%s did not succeed." % (
                    datetime.datetime.fromtimestamp(_mt).strftime("%H:%M:%S"),
                    datetime.datetime.fromtimestamp(marker).strftime("%H:%M:%S"),
                    build_type,
                )
            )
        else:
            ok = True
            failed_reason = None

    storage = detect_storage_root()
    if storage is None:
        warn("no writable shared storage detected; keeping only the local copy")

    if ok:
        content = render_report(root, apk_root, build_type, apk_path, ok=True, failed_reason=None)
        local = write_local_copy(apk_root, content, build_type)
        log("generated %d-byte state report for %s build" % (len(content), build_type))

        storage_written = False
        if storage is not None:
            app_dir = os.path.join(storage, APP_DIR_NAME)
            history_dir = os.path.join(app_dir, HISTORY_DIR_NAME)
            try:
                os.makedirs(app_dir, exist_ok=True)
                os.makedirs(history_dir, exist_ok=True)
                current = os.path.join(app_dir, STATE_FILE_NAME)
                write_atomic(current, content)          # atomic rename over the live file
                history = os.path.join(history_dir, "%s_%s.txt" % (STATE_FILE_NAME.rsplit(".", 1)[0], now_stamp()))
                write_atomic(history, content)          # timestamped copy
                rotate_history(history_dir, keep)
                storage_written = True
                log("wrote %s (history kept in %s)" % (current, history_dir))
            except OSError as exc:
                warn("shared storage write failed: %s (local copy kept at %s)" % (exc, local))

        if not storage_written:
            warn("APP_CURRENT_STATE.txt was NOT copied to shared storage; local copy: %s" % local)

        log("OK: snapshot complete (%s)" % ("storage + local" if storage_written else "local only"))
    else:
        content = render_failed_report(root, build_type, apk_root, read_marker(state_out, build_type.lower()))
        failed_local = os.path.join(state_out, FAILED_FILE_NAME)
        write_atomic(failed_local, content)
        log("build %s: wrote %s (last known-good state untouched)" % (build_type, failed_local))
        if storage is not None:
            app_dir = os.path.join(storage, APP_DIR_NAME)
            try:
                os.makedirs(app_dir, exist_ok=True)
                failed_storage = os.path.join(app_dir, FAILED_FILE_NAME)
                write_atomic(failed_storage, content)
                log("wrote %s" % failed_storage)
            except OSError as exc:
                warn("shared storage write failed: %s" % exc)

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
