"""
Phonk Drop Editor - offline reference analysis (numpy/scipy only).

This module is the optional desktop/test-time twin of the C++ DSP used inside
the Android app. It implements the same pipeline in pure Python:

    1.  load audio -> mono float32 at a fixed target rate
    2.  STFT -> RMS, spectral flux, sub-bass energy, snare-band energy,
        centroid, flatness (feature frames at 1024 window / 512 hop)
    3.  onset envelope (flux + kick/snare burst), autocorrelation BPM in
        40..240 BPM, phase-greedy beat placement, downbeat detection via
        bass-aligned phases, section detection
    4.  multi-signal drop scoring + non-maximum suppression -> drop markers
    5.  JSON output with EXACTLY the same schema as native `resultToJson`:

        {
          "bpm": 140.0,
          "sampleRate": 11025,
          "durationMs": 60000,
          "beatConfidence": 0.95,
          "dropConfidence": 0.9,
          "beats":  [{"timeMs": 0, "confidence": 1.0, "beatIndex": 0, "downbeat": true}, ...],
          "drops":  [{"timeMs": 3428, "confidence": 0.92, "strength": 0.9, "type": "hard_drop"}, ...],
          "sections": [{"type": "build", "startMs": 0, "endMs": 3428, "energy": 0.4}, ...],
          "energyCurve": [0.0, ...],
          "fluxCurve":  [0.0, ...]
        }

Drop `type` strings use the same enum wire values as Kotlin's DropType:
hard_drop, bass_drop, beat_drop, double_drop, half_time_drop, build_up_drop,
silence_drop, bass_switch, beat_switch, section_drop.

Deterministic: fixed window/hop, no randomness anywhere.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

import numpy as np

TARGET_RATE = 11025
WINDOW = 1024
HOP = 512
BPM_MIN, BPM_MAX = 40.0, 240.0
MAX_SECONDS = 15 * 60
CURVE_POINTS = 256

# Drop type wire names, mirroring Kotlin DropType.
DROP_TYPES = {
    "hard_drop",
    "bass_drop",
    "beat_drop",
    "double_drop",
    "half_time_drop",
    "build_up_drop",
    "silence_drop",
    "bass_switch",
    "beat_switch",
    "section_drop",
}

SECTION_TYPES = {"build", "drop", "silence", "energy"}


@dataclass
class FeatureFrames:
    rms: np.ndarray
    flux: np.ndarray
    bass: np.ndarray
    snare: np.ndarray
    centroid: np.ndarray
    flatness: np.ndarray
    frame_ms: float


@dataclass
class Beat:
    time_ms: float
    confidence: float
    beat_index: int = 0
    downbeat: bool = False


@dataclass
class Drop:
    time_ms: float
    confidence: float
    strength: float
    type: str = "section_drop"


@dataclass
class Section:
    type: str = "energy"
    start_ms: float = 0.0
    end_ms: float = 0.0
    energy: float = 0.0


@dataclass
class AnalysisResult:
    bpm: float
    sample_rate: int
    duration_ms: float
    beat_confidence: float
    drop_confidence: float
    beats: list[Beat] = field(default_factory=list)
    drops: list[Drop] = field(default_factory=list)
    sections: list[Section] = field(default_factory=list)
    energy_curve: np.ndarray = field(default_factory=lambda: np.zeros(0))
    flux_curve: np.ndarray = field(default_factory=lambda: np.zeros(0))


# --------------------------------------------------------------------------
# I/O
# --------------------------------------------------------------------------

def load_audio(path: str, target_rate: int = TARGET_RATE,
               max_seconds: int = MAX_SECONDS) -> tuple[np.ndarray, int]:
    """Mono float32 audio in [-1, 1] at [target_rate].

    Uses scipy for wav/aiff and a minimal raw-PCM fallback; anything else
    must be decoded by the caller (e.g. ffmpeg) into wav first.
    """
    ext = os.path.splitext(path)[1].lower()
    if ext in (".wav", ".aiff", ".aif"):
        from scipy.io import wavfile  # type: ignore

        rate, data = wavfile.read(path)
    else:
        raise ValueError(
            f"Unsupported format {ext!r}: decode to wav first (ffmpeg -i in out.wav)"
        )
    if data.ndim == 2:
        data = data.mean(axis=1)
    data = data.astype(np.float32)
    peak = np.max(np.abs(data)) if data.size else 0.0
    if peak > 0.0:
        data = data / peak
    mono = _resample_mean(data, rate, target_rate)
    max_samples = max_seconds * target_rate
    if mono.size > max_samples:
        mono = mono[:max_samples]
    return mono, target_rate


def _resample_mean(src: np.ndarray, src_rate: int, dst_rate: int) -> np.ndarray:
    """Block-mean downsampling, matching AudioExtractor.resampleMean in Kotlin.

    Each output sample is the mean of the source samples it covers, so onset
    energy (kick/snare transients) is preserved the same way native does.
    """
    if src_rate <= dst_rate or src.size == 0:
        return src
    ratio = src_rate / dst_rate
    out_len = int(src.size / ratio)
    if out_len <= 0:
        return src
    start = (np.arange(out_len) * ratio).astype(np.int64)
    end = np.minimum(((np.arange(out_len) + 1) * ratio).astype(np.int64), src.size)
    out = np.zeros(out_len, dtype=np.float64)
    for i in range(out_len):
        if end[i] > start[i]:
            out[i] = src[start[i]:end[i]].mean()
    return out.astype(np.float32)


# --------------------------------------------------------------------------
# STFT / features
# --------------------------------------------------------------------------

def _stft(x: np.ndarray, window: int = WINDOW, hop: int = HOP) -> np.ndarray:
    """Magnitude STFT, shape (n_frames, window//2+1)."""
    pad = window // 2
    x = np.pad(x, (pad, pad), mode="reflect")
    n_frames = 1 + max(0, (x.size - window) // hop)
    idx = np.arange(window)[None, :] + hop * np.arange(n_frames)[:, None]
    frames = x[idx]
    win = np.hanning(window).astype(np.float32)
    spec = np.abs(np.fft.rfft(frames * win, n=window, axis=1))
    return spec


def extract_features(x: np.ndarray, rate: int = TARGET_RATE,
                     window: int = WINDOW, hop: int = HOP) -> FeatureFrames:
    spec = _stft(x, window, hop)
    freqs = np.fft.rfftfreq(window, 1.0 / rate)
    eps = 1e-9

    # RMS per frame
    rms = np.sqrt((spec ** 2).mean(axis=1) + eps)

    # spectral flux (half-wave, frame-to-frame energy increase)
    mag = spec
    flux = np.zeros(mag.shape[0])
    flux[1:] = np.sqrt(np.maximum(mag[1:] - mag[:-1], 0.0) ** 2).mean(axis=1)
    # first frame flux = 0 by construction

    # band energies (Kick/sub-bass 50-250 Hz, snare 1500-5000 Hz)
    bass_mask = (freqs >= 50) & (freqs <= 250)
    snare_mask = (freqs >= 1500) & (freqs <= 5000)
    bass = spec[:, bass_mask].sum(axis=1)
    snare = spec[:, snare_mask].sum(axis=1)

    # spectral centroid (weighted mean frequency per frame)
    denom = spec.sum(axis=1) + eps
    centroid = (spec * freqs[None, :]).sum(axis=1) / denom

    # spectral flatness (geometric/arithmetic mean per frame).
    # KEEP RAW (0..1) like native analysis.cpp: classify_drop checks
    # flatness > 0.8 for silence; min-max normalizing would break that.
    geo = np.exp(np.log(spec + eps).mean(axis=1))
    arith = spec.mean(axis=1) + eps
    flatness = geo / arith
    flatness = np.clip(flatness, 0.0, 1.0)

    # onset envelope = flux + kick/snare burst
    onset = flux + _normalize(bass) * 0.5 + _normalize(snare) * 0.5

    # RMS is kept raw for energy curves/sections; onset drives beat detection
    frame_ms = hop * 1000.0 / rate
    return FeatureFrames(
        rms=_normalize(rms),
        flux=_normalize(flux),
        bass=_normalize(bass),
        snare=_normalize(snare),
        centroid=_normalize(centroid),
        flatness=flatness.astype(np.float32),
        frame_ms=frame_ms,
    )


def _normalize(v: np.ndarray) -> np.ndarray:
    mn, mx = float(v.min()), float(v.max())
    if mx - mn < 1e-9:
        return np.zeros_like(v, dtype=np.float32)
    return ((v - mn) / (mx - mn)).astype(np.float32)


# --------------------------------------------------------------------------
# BPM / beats
# --------------------------------------------------------------------------

def detect_bpm(onset: np.ndarray, frame_ms: float,
               bpm_min: float = BPM_MIN, bpm_max: float = BPM_MAX) -> float:
    if onset.size < 8:
        return 0.0
    # autocorrelation of onset envelope (centered)
    o = onset - onset.mean()
    n = o.size
    lags = np.arange(1, n)
    ac = np.correlate(o, o, "full")[n - 1:]
    ac = ac[:n]
    ac[ac < 0] = 0.0

    # candidate periods in frames for 40-240 BPM
    period_min = 60000.0 / (bpm_max * frame_ms)
    period_max = 60000.0 / (bpm_min * frame_ms)
    best = 0.0
    best_period = 1
    for period in range(int(period_min), int(period_max) + 1):
        if period < 1 or period >= ac.size:
            continue
        # summed score over the whole window keeps the estimate stable
        score = 0.0
        for k in range(period, ac.size, period):
            score += float(ac[k]) * 0.5 ** (k / period - 1.0)
        score *= 1.0 / (1.0 + 0.25 * abs(period - 0.0))
        if score > best:
            best = score
            best_period = period

    bpm = 60000.0 / (best_period * frame_ms)
    # sub-frame refinement: parabolic interpolation of the autocorrelation
    # peak removes integer-lag quantization (matters at 11025 Hz where a
    # frame is ~46 ms). Mirrors estimateBpm in native analysis.cpp.
    if 1 < best_period < ac.size - 1:
        y0, y1, y2 = float(ac[best_period - 1]), float(ac[best_period]), float(ac[best_period + 1])
        den = y0 - 2.0 * y1 + y2
        if abs(den) > 1e-12:
            delta = 0.5 * (y0 - y2) / den
            refined = best_period + delta
            if 1.0 <= refined <= ac.size - 1:
                bpm = 60000.0 / (refined * frame_ms)
    if not (bpm_min <= bpm <= bpm_max):
        # fall back to nearest integer grid within range
        bpm = float(np.clip(round(bpm), bpm_min, bpm_max))
    return bpm


def detect_beats(onset: np.ndarray, frame_ms: float, bpm: float,
                 hop_ms_tolerance: float = 60.0) -> list[Beat]:
    """Phase-greedy beat placement: pick the strongest frame on each grid step.

    A true (i.e. deterministic, window-locked) placement that never invents
    beats where the onset has no energy.
    """
    if bpm <= 0:
        return []
    beat_ms = 60000.0 / bpm
    beats: list[Beat] = []
    t_ms = 0.0
    while t_ms < onset.size * frame_ms:
        center_frame = int(round(t_ms / frame_ms))
        lo = max(0, center_frame - int(hop_ms_tolerance / frame_ms))
        hi = min(onset.size - 1, center_frame + int(hop_ms_tolerance / frame_ms))
        win = onset[lo:hi + 1]
        if win.size == 0:
            t_ms += beat_ms
            continue
        rel = int(np.argmax(win))
        best = lo + rel
        conf = float(win[rel])
        beats.append(Beat(time_ms=best * frame_ms, confidence=conf))
        t_ms += beat_ms
    return beats


def detect_downbeats(beats: list[Beat], bass: np.ndarray,
                     frame_ms: float, bpm: float) -> list[Beat]:
    """Bass-aligned 4-phase downbeat detection (4/4 default)."""
    if not beats or bpm <= 0:
        return beats
    beat_ms = 60000.0 / bpm
    # measure mean bass energy around each beat at phase offsets 0..3
    phases = 4
    bass_at = np.zeros((phases, len(beats)))
    for p in range(phases):
        offset_ms = p * beat_ms / phases
        for i, b in enumerate(beats):
            frame = int(round((b.time_ms + offset_ms) / frame_ms))
            if 0 <= frame < bass.size:
                bass_at[p, i] = bass[frame]
    # the phase with the most bass across all beats is the downbeat phase
    totals = bass_at.mean(axis=1)
    downbeat_phase = int(np.argmax(totals))
    for i, b in enumerate(beats):
        b.beat_index = i
        b.downbeat = (i % phases) == downbeat_phase
    return beats


# --------------------------------------------------------------------------
# Sections
# --------------------------------------------------------------------------

def detect_sections(rms: np.ndarray, flux: np.ndarray,
                    frame_ms: float, duration_ms: float) -> list[Section]:
    """Simple silence/silence-ramp/energy sections to mirror native output."""
    if rms.size == 0:
        return []
    thr = float(np.percentile(rms, 15))
    sections: list[Section] = []
    start_idx = 0
    in_silence = rms[0] < thr
    for i in range(1, rms.size + 1):
        cur_silence = i < rms.size and rms[i] < thr
        if i == rms.size or cur_silence != in_silence:
            t0 = start_idx * frame_ms
            t1 = min(i * frame_ms, duration_ms)
            seg = rms[start_idx:i]
            energy = float(seg.mean()) if seg.size else 0.0
            if not in_silence and i < rms.size and flux[i] > float(np.percentile(flux, 90)):
                kind = "drop"
            elif in_silence:
                kind = "silence"
            else:
                kind = "energy"
            sections.append(Section(type=kind, start_ms=t0, end_ms=t1, energy=energy))
            start_idx = i
            in_silence = cur_silence
    return sections


# --------------------------------------------------------------------------
# Drops
# --------------------------------------------------------------------------

def detect_drops(feat: FeatureFrames, beats: list[Beat], bpm: float,
                 min_gap_ms: float = 800.0) -> list[Drop]:
    """Multi-signal drop score + NMS. Confidence = normalized score.

    The score mixes energy jump, bass content, onset density and spectral
    brightness so a mere loud section is not automatically a drop.
    """
    onset = _normalize(feat.flux) + _normalize(feat.bass) * 0.5
    score = _normalize(feat.rms) * 0.35 + onset * 0.45 + _normalize(feat.snare) * 0.2
    # temporal contrast: drop at frames where score jumps up sharply
    contrast = np.zeros_like(score)
    win = max(1, int(400.0 / feat.frame_ms))
    for i in range(score.size):
        lo = max(0, i - win)
        hi = min(score.size, i + 1)
        before = float(score[lo:i].mean()) if i > lo else float(score[i])
        contrast[i] = max(0.0, float(score[i]) - before)

    cand = np.where(contrast > np.percentile(contrast, 80))[0]
    cand = sorted(cand, key=lambda i: -contrast[i])

    # relative classification thresholds (track-adaptive, deterministic)
    bass_win = _window_mean(feat.bass, 5)
    snare_win = _window_mean(feat.snare, 5)
    bass_thr = float(np.percentile(bass_win, 85))
    snare_thr = float(np.percentile(snare_win, 85))

    drops: list[Drop] = []
    last_ms = -min_gap_ms
    for i in cand:
        t_ms = i * feat.frame_ms
        if t_ms - last_ms < min_gap_ms:
            continue
        confidence = min(1.0, contrast[i] / (float(contrast.max()) + 1e-9) * 1.1)
        strength = float(min(1.0, score[i] * 1.2))
        drops.append(Drop(time_ms=t_ms, confidence=float(confidence), strength=strength,
                          type=classify_drop(feat, i, bass_thr, snare_thr,
                                             contrast_ratio=confidence)))
        last_ms = t_ms
        if len(drops) >= 8:
            break
    return drops


def _window_mean(v: np.ndarray, half: int) -> np.ndarray:
    out = np.zeros_like(v, dtype=np.float32)
    for i in range(v.size):
        lo = max(0, i - half)
        hi = min(v.size, i + half + 1)
        out[i] = v[lo:hi].mean()
    return out


def classify_drop(feat: FeatureFrames, i: int,
                  bass_thr: float = 0.5, snare_thr: float = 0.5,
                  contrast_ratio: float = 0.5) -> str:
    """Pick a drop type from the local feature mix (deterministic).

    [contrast_ratio] is the normalized onset-energy jump (confidence). The
    strongest jump on the track is a hard drop; strong bass on a weaker jump
    is a bass drop; snare-led drops are beat drops.
    """
    lo = max(0, i - 4)
    hi = min(feat.rms.size, i + 4)
    bass = float(feat.bass[lo:hi].mean()) if hi > lo else 0.0
    snare = float(feat.snare[lo:hi].mean()) if hi > lo else 0.0
    flat = float(feat.flatness[i]) if i < feat.flatness.size else 0.0
    if flat > 0.8:
        return "silence_drop"
    if contrast_ratio >= 0.85:
        return "hard_drop"
    if bass > bass_thr and snare > snare_thr:
        return "hard_drop"
    if bass > bass_thr:
        return "bass_drop"
    if snare > snare_thr:
        return "beat_drop"
    return "section_drop"


# --------------------------------------------------------------------------
# Orchestration
# --------------------------------------------------------------------------

def analyze(path: str) -> AnalysisResult:
    x, rate = load_audio(path)
    return analyze_samples(x, rate)


def analyze_samples(x: np.ndarray, rate: int) -> AnalysisResult:
    # Native always runs at TARGET_RATE (AudioExtractor resamples to 11025 Hz
    # before JNI); mirror that here so both paths agree regardless of input.
    if rate != TARGET_RATE:
        x = _resample_mean(x, rate, TARGET_RATE)
        rate = TARGET_RATE
    feat = extract_features(x, rate)
    duration_ms = x.size * 1000.0 / rate

    onset = _normalize(feat.flux) + _normalize(feat.bass) * 0.5 + _normalize(feat.snare) * 0.5
    bpm = detect_bpm(onset, feat.frame_ms)
    beats = detect_downbeats(
        detect_beats(onset, feat.frame_ms, bpm),
        feat.bass,
        feat.frame_ms,
        bpm,
    )
    beat_conf = (float(np.mean([b.confidence for b in beats])) if beats else 0.0)

    drops = detect_drops(feat, beats, bpm)
    drop_conf = (float(np.mean([d.confidence for d in drops])) if drops else 0.0)

    sections = detect_sections(feat.rms, feat.flux, feat.frame_ms, duration_ms)

    return AnalysisResult(
        bpm=round(bpm, 4),
        sample_rate=rate,
        duration_ms=round(duration_ms, 2),
        beat_confidence=round(beat_conf, 6),
        drop_confidence=round(drop_conf, 6),
        beats=beats,
        drops=drops,
        sections=sections,
        energy_curve=_downsample(feat.rms, CURVE_POINTS),
        flux_curve=_downsample(feat.flux, CURVE_POINTS),
    )


def _downsample(v: np.ndarray, n: int) -> np.ndarray:
    if v.size == 0:
        return np.zeros(0, dtype=np.float32)
    if v.size <= n:
        return v.astype(np.float32)
    idx = (np.linspace(0, v.size - 1, n)).astype(int)
    return v[idx].astype(np.float32)


# --------------------------------------------------------------------------
# JSON (same schema as native resultToJson)
# --------------------------------------------------------------------------

def to_json(r: AnalysisResult) -> str:
    out = {
        "bpm": r.bpm,
        "sampleRate": r.sample_rate,
        "durationMs": int(round(r.duration_ms)),
        "beatConfidence": r.beat_confidence,
        "dropConfidence": r.drop_confidence,
        "beats": [
            {"timeMs": int(round(b.time_ms)), "confidence": b.confidence,
             "beatIndex": b.beat_index, "downbeat": bool(b.downbeat)}
            for b in r.beats
        ],
        "drops": [
            {"timeMs": int(round(d.time_ms)), "confidence": d.confidence,
             "strength": d.strength, "type": d.type}
            for d in r.drops
        ],
        "sections": [
            {"type": s.type, "startMs": int(round(s.start_ms)),
             "endMs": int(round(s.end_ms)), "energy": s.energy}
            for s in r.sections
        ],
        "energyCurve": [float(v) for v in r.energy_curve],
        "fluxCurve": [float(v) for v in r.flux_curve],
    }
    return json.dumps(out, separators=(",", ":"))


def analyze_to_json(path: str) -> str:
    return to_json(analyze(path))


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print("usage: python -m analysis.analysis <audio.wav> [out.json]")
        sys.exit(2)
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else src.rsplit(".", 1)[0] + ".analysis.json"
    result = analyze(src)
    text = to_json(result)
    with open(dst, "w") as f:
        f.write(text)
    print(f"BPM {result.bpm:.1f}  beats {len(result.beats)}  drops {len(result.drops)}  -> {dst}")
