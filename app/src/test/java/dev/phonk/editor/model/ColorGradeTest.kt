package dev.phonk.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the shared grade model, keyframe interpolation and beat engine. */
class ColorGradeTest {

    @Test
    fun gradeParamClampsToRange() {
        assertEquals(1f, ColorGrade().with(GradeParam.BRIGHTNESS, 5f).brightness, 0.001f)
        assertEquals(0f, ColorGrade(grain = 0.2f).with(GradeParam.GRAIN, -2f).grain, 0.001f)
        assertEquals(0f, ColorGrade().with(GradeParam.BLUR, 0.001f).blur, 0.001f)
    }

    @Test
    fun neutralGradeReportsZero() {
        assertTrue(ColorGrade().isNeutral)
        assertFalse(ColorGrade(blur = 0.1f).isNeutral)
    }

    @Test
    fun keyframesDisabledReturnsBase() {
        val g = ColorGrade(brightness = 0.4f)
        val keyframes = listOf(
            GradeKeyframe(0L, ColorGrade(brightness = 0f)),
            GradeKeyframe(1000L, ColorGrade(brightness = 1f)),
        )
        // Disabled: the base grade wins regardless of keyframes.
        assertEquals(0.4f, evaluateColorGrade(g, keyframes, 500L, enabled = false).brightness, 0.001f)
        // Enabled: interpolation overrides the base.
        assertEquals(0.5f, evaluateColorGrade(g, keyframes, 500L, enabled = true).brightness, 0.001f)
    }

    @Test
    fun keyframeInterpolatesLinearly() {
        val keyframes = listOf(
            GradeKeyframe(0L, ColorGrade(brightness = 0f, temperature = 0f)),
            GradeKeyframe(1000L, ColorGrade(brightness = 1f, temperature = 0.5f)),
        )
        val at250 = evaluateColorGrade(ColorGrade(), keyframes, 250L, enabled = true)
        assertEquals(0.25f, at250.brightness, 0.001f)
        assertEquals(0.125f, at250.temperature, 0.001f)
        val at1000 = evaluateColorGrade(ColorGrade(), keyframes, 1000L, enabled = true)
        assertEquals(1f, at1000.brightness, 0.001f)
        // Outside the keyframe span the base grade is used, not the last keyframe.
        val at2000 = evaluateColorGrade(ColorGrade(), keyframes, 2000L, enabled = true)
        assertEquals(0f, at2000.brightness, 0.001f)
    }

    @Test
    fun keyframesSortedByTimeRegardlessOfInsertionOrder() {
        val keyframes = listOf(
            GradeKeyframe(1000L, ColorGrade(brightness = 1f)),
            GradeKeyframe(0L, ColorGrade(brightness = 0f)),
        )
        val at500 = evaluateColorGrade(ColorGrade(), keyframes, 500L, enabled = true)
        assertEquals(0.5f, at500.brightness, 0.001f)
    }

    @Test
    fun singleKeyframeDoesNotAnimate() {
        val keyframes = listOf(GradeKeyframe(0L, ColorGrade(brightness = 1f)))
        assertEquals(0f, evaluateColorGrade(ColorGrade(), keyframes, 400L, enabled = true).brightness, 0.001f)
    }

    @Test
    fun beforeFirstKeyframeReturnsBaseGrade() {
        val base = ColorGrade(brightness = 0.4f, contrast = 0.3f)
        val keyframes = listOf(
            GradeKeyframe(1000L, ColorGrade(brightness = 0f)),
            GradeKeyframe(2000L, ColorGrade(brightness = 1f)),
        )
        val before = evaluateColorGrade(base, keyframes, 500L, enabled = true)
        assertEquals(0.4f, before.brightness, 0.001f)
        assertEquals(0.3f, before.contrast, 0.001f)
    }

    @Test
    fun afterLastKeyframeReturnsBaseGrade() {
        val base = ColorGrade(brightness = 0.4f, contrast = 0.3f)
        val keyframes = listOf(
            GradeKeyframe(1000L, ColorGrade(brightness = 0f)),
            GradeKeyframe(2000L, ColorGrade(brightness = 1f)),
        )
        val after = evaluateColorGrade(base, keyframes, 3000L, enabled = true)
        assertEquals(0.4f, after.brightness, 0.001f)
        assertEquals(0.3f, after.contrast, 0.001f)
    }

    @Test
    fun betweenKeyframesInterpolatesAtMidpoint() {
        val base = ColorGrade(brightness = 0.4f)
        val keyframes = listOf(
            GradeKeyframe(1000L, ColorGrade(brightness = 0f)),
            GradeKeyframe(2000L, ColorGrade(brightness = 1f)),
        )
        val mid = evaluateColorGrade(base, keyframes, 1500L, enabled = true)
        assertEquals(0.5f, mid.brightness, 0.001f)
    }

    @Test
    fun staticBaseValuesRemainActiveOutsideKeyframeSpan() {
        val base = ColorGrade(brightness = 0.4f, contrast = -0.3f, saturation = 0.2f)
        val keyframes = listOf(
            GradeKeyframe(1000L, ColorGrade(brightness = 0f)),
            GradeKeyframe(2000L, ColorGrade(brightness = 1f)),
        )
        val before = evaluateColorGrade(base, keyframes, 999L, enabled = true)
        val after = evaluateColorGrade(base, keyframes, 2001L, enabled = true)
        for (result in listOf(before, after)) {
            assertEquals(0.4f, result.brightness, 0.001f)
            assertEquals(-0.3f, result.contrast, 0.001f)
            assertEquals(0.2f, result.saturation, 0.001f)
        }
    }

    @Test
    fun keyframesStillAnimateInsideSpan() {
        val base = ColorGrade(brightness = 0.4f, contrast = 0.3f)
        val keyframes = listOf(
            GradeKeyframe(0L, ColorGrade(brightness = 0f, contrast = 0.2f)),
            GradeKeyframe(1000L, ColorGrade(brightness = 1f, contrast = 0.6f)),
            GradeKeyframe(2000L, ColorGrade(brightness = 0.5f, contrast = 0.4f)),
        )
        val at500 = evaluateColorGrade(base, keyframes, 500L, enabled = true)
        assertEquals(0.5f, at500.brightness, 0.001f)
        assertEquals(0.4f, at500.contrast, 0.001f)
        val at1000 = evaluateColorGrade(base, keyframes, 1000L, enabled = true)
        assertEquals(1f, at1000.brightness, 0.001f)
        val at1500 = evaluateColorGrade(base, keyframes, 1500L, enabled = true)
        assertEquals(0.75f, at1500.brightness, 0.001f)
        assertEquals(0.5f, at1500.contrast, 0.001f)
    }

    @Test
    fun beatFrameDetectsAndReportsProgress() {
        val beats = listOf(
            BeatMarker(0.0, 0.9f, 0, downbeat = true),
            BeatMarker(500.0, 0.5f, 1, downbeat = false),
            BeatMarker(1000.0, 1f, 2, downbeat = true),
        )
        // Mid-interval: between beat 0 and beat 1 → progress 0.5
        val mid = BeatSyncEngine.frame(beats, emptyList(), 250L)
        assertEquals(0.5f, mid.beatProgress, 0.001f)
        assertFalse(mid.isBeat)
        assertEquals(0, mid.beatIndex)

        // Exactly on a beat → active within the engine window.
        val on = BeatSyncEngine.frame(beats, emptyList(), 1000L)
        assertTrue(on.isBeat)
        assertEquals(1f, on.beatStrength, 0.001f)
        assertEquals(2, on.beatIndex)
    }

    @Test
    fun beatFrameReportsDropWindow() {
        val drops = listOf(DropMarker(0.0, 1f, 0.8f, DropType.HARD_DROP))
        val active = BeatSyncEngine.frame(emptyList(), drops, 100L)
        assertTrue(active.isDrop)
        assertEquals(0.8f, active.dropStrength, 0.001f)
        val expired = BeatSyncEngine.frame(emptyList(), drops, 500L)
        assertFalse(expired.isDrop)
    }
}