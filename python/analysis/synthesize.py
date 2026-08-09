"""
Synthetic phonk-style audio generator used by the Python tests.

It produces a deterministic kick/bass/hat/snare loop at a known BPM with a
programmed drop (sudden energy + bass jump) so the analysis pipeline can be
asserted against exact ground truth.
"""

from __future__ import annotations

import numpy as np

RATE = 44100


def _env(duration: float, attack: float, release: float, sr: int) -> np.ndarray:
    n = int(duration * sr)
    t = np.arange(n) / sr
    a = min(attack, duration)
    r = min(release, duration)
    env = np.minimum(t / max(a, 1e-6), (duration - t) / max(r, 1e-6))
    env = np.clip(env, 0.0, 1.0) ** 0.5
    return env.astype(np.float32)


def kick(sr: int = RATE) -> np.ndarray:
    dur = 0.22
    t = np.arange(int(dur * sr)) / sr
    freq = 160.0 * np.exp(-t * 28.0) + 45.0
    phase = 2 * np.pi * np.cumsum(freq) / sr
    return (np.sin(phase) * _env(dur, 0.001, 0.18, sr)).astype(np.float32)


def bass_note(sr: int = RATE, freq: float = 55.0, dur: float = 0.5) -> np.ndarray:
    t = np.arange(int(dur * sr)) / sr
    tone = np.sin(2 * np.pi * freq * t) + 0.5 * np.sin(2 * np.pi * 2 * freq * t)
    return (tone * _env(dur, 0.005, 0.1, sr)).astype(np.float32)


def snare(sr: int = RATE) -> np.ndarray:
    dur = 0.12
    n = int(dur * sr)
    noise = np.random.default_rng(42).standard_normal(n)
    noise = noise * _env(dur, 0.001, 0.1, sr)
    # band-pass-ish by differencing twice
    noise = np.diff(np.diff(noise, prepend=0), prepend=0)
    return noise.astype(np.float32)


def hat(sr: int = RATE) -> np.ndarray:
    dur = 0.05
    n = int(dur * sr)
    noise = np.random.default_rng(7).standard_normal(n)
    return (noise * _env(dur, 0.0005, 0.04, sr)).astype(np.float32)


def synthesize(bpm: float = 140.0, seconds: float = 16.0,
               sr: int = RATE, drop_at_sec: float = 8.0) -> np.ndarray:
    """A 16-second 140-BPM phonk loop with a hard drop at second 8."""
    beat = 60.0 / bpm
    n = int(seconds * sr)
    out = np.zeros(n, dtype=np.float32)

    kick_s = kick(sr)
    snare_s = snare(sr)
    hat_s = hat(sr)
    bass_freq = 55.0
    in_drop = False

    t = 0.0
    beat_i = 0
    while t < seconds:
        start = int(t * sr)
        if start >= n:
            break
        drop_bass = 1.0 if t >= drop_at_sec else 0.45
        bass_f = bass_freq * (1.0 if t < drop_at_sec else 1.0)
        emphasis = 1.0 if t >= drop_at_sec else 0.55

        # kick on every beat, snare on 2 & 4, hats on 8ths
        _add(out, kick_s * emphasis, start)
        if beat_i % 4 in (1, 3):
            _add(out, snare_s, start)
        if beat_i % 2 == 0:
            _add(out, hat_s, start)
            _add(out, hat_s, start + int(0.5 * beat * sr))
        # sub-bass octave doubles the low end on the drop
        _add(out, bass_note(sr, bass_f, dur=beat * 0.9) * drop_bass, start)
        if t >= drop_at_sec:
            _add(out, bass_note(sr, bass_f / 2.0, dur=beat * 0.9) * 0.6, start)
        # a touch more snare right at the drop start gives a clear onset spike
        if t >= drop_at_sec and t < drop_at_sec + beat:
            _add(out, snare_s, start)

        t += beat
        beat_i += 1

    return out / max(1e-6, float(np.max(np.abs(out))))


def _add(buf: np.ndarray, sample: np.ndarray, at: int) -> None:
    if at < 0 or at >= buf.size:
        return
    end = min(at + sample.size, buf.size)
    n = end - at
    buf[at:end] += sample[:n]


if __name__ == "__main__":
    import sys

    from scipy.io import wavfile

    bpm = float(sys.argv[1]) if len(sys.argv) > 1 else 140.0
    dst = sys.argv[2] if len(sys.argv) > 2 else "synthetic.wav"
    audio = synthesize(bpm=bpm)
    wavfile.write(dst, RATE, (audio * 32767).astype(np.int16))
    print(f"wrote {dst} at {bpm:.0f} BPM")
