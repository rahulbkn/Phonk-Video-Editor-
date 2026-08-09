"""
Tests for the offline Python analysis pipeline.

Uses the synthetic phonk loop whose ground truth is known exactly: 140 BPM,
a hard drop at second 8 (plus typical silence at the very start), 16 seconds.
"""

from __future__ import annotations

import json
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import numpy as np  # noqa: E402

from analysis import analysis as ana  # noqa: E402
from analysis.synthesize import synthesize, RATE  # noqa: E402


class AnalysisPipelineTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.audio = synthesize(bpm=140.0, seconds=16.0, drop_at_sec=8.0)
        cls.result = ana.analyze_samples(cls.audio, RATE)

    def test_bpm_close_to_140(self):
        self.assertAlmostEqual(self.result.bpm, 140.0, delta=3.0)

    def test_beats_count_about_right(self):
        # 16s / 428.57ms ~ 37 beat grid steps; the phase-greedy walk can drop
        # low-energy extremes but must stay within a sane band.
        expected = 16.0 / (60.0 / 140.0)  # ~37.3
        self.assertGreater(len(self.result.beats), expected * 0.6)
        self.assertLessEqual(len(self.result.beats), expected + 4)

    def test_beats_are_regular(self):
        times = [b.time_ms for b in self.result.beats]
        if len(times) > 3:
            gaps = np.diff(times)
            beat = 60000.0 / 140.0
            # most gaps near one beat (allow a few half/double from grid lock)
            near = np.abs(gaps - beat) < beat * 0.35
            self.assertGreater(near.mean(), 0.5)

    def test_drop_detected_near_8_seconds(self):
        self.assertGreater(len(self.result.drops), 0)
        closest = min(self.result.drops, key=lambda d: abs(d.time_ms - 8000.0))
        self.assertLess(abs(closest.time_ms - 8000.0), 1000.0,
                        "strongest drop should land near the programmed 8s drop")

    def test_strongest_drop_is_hard_drop(self):
        strongest = max(self.result.drops, key=lambda d: d.confidence)
        self.assertEqual(strongest.type, "hard_drop",
                         "the programmed 8s drop must classify as hard_drop")
        self.assertGreater(strongest.confidence, 0.8)

    def test_drop_confidence_and_strength_sane(self):
        for d in self.result.drops:
            self.assertGreaterEqual(d.confidence, 0.0)
            self.assertLessEqual(d.confidence, 1.0)
            self.assertGreaterEqual(d.strength, 0.0)
            self.assertLessEqual(d.strength, 1.0)
            self.assertIn(d.type, ana.DROP_TYPES)

    def test_json_schema_matches_native(self):
        text = ana.to_json(self.result)
        o = json.loads(text)
        self.assertIn("bpm", o)
        self.assertIn("sampleRate", o)
        self.assertIn("durationMs", o)
        self.assertIn("beatConfidence", o)
        self.assertIn("dropConfidence", o)
        self.assertIn("beats", o)
        self.assertIn("drops", o)
        self.assertIn("sections", o)
        self.assertIn("energyCurve", o)
        self.assertIn("fluxCurve", o)
        # key names must match the native/C++ resultToJson exactly
        for b in o["beats"]:
            self.assertEqual(
                set(b.keys()), {"timeMs", "confidence", "beatIndex", "downbeat"}
            )
        for d in o["drops"]:
            self.assertEqual(set(d.keys()), {"timeMs", "confidence", "strength", "type"})
        self.assertIsInstance(o["energyCurve"], list)
        self.assertIsInstance(o["fluxCurve"], list)
        self.assertLessEqual(len(o["energyCurve"]), ana.CURVE_POINTS)

    def test_curve_lengths(self):
        self.assertLessEqual(self.result.energy_curve.size, ana.CURVE_POINTS)
        self.assertLessEqual(self.result.flux_curve.size, ana.CURVE_POINTS)

    def test_deterministic(self):
        again = ana.analyze_samples(self.audio, RATE)
        self.assertEqual(ana.to_json(self.result), ana.to_json(again))


if __name__ == "__main__":
    unittest.main()
