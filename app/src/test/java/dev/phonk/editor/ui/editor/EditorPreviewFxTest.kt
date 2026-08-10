package dev.phonk.editor.ui.editor

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.GradeKeyframe
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the preview "needs animation" gate (fix7). */
class EditorPreviewFxTest {

    private val idleProject = PhonkProject(
        videoDurationMs = 10_000L,
        clips = listOf(
            ClipSegment(
                sourceStartMs = 0L,
                sourceEndMs = 10_000L,
                destStartMs = 0L,
                destEndMs = 10_000L,
            ),
        ),
    )

    private fun glitchClip(project: PhonkProject, startMs: Long = 0L, endMs: Long = 10_000L) =
        project.copy(
            clips = listOf(
                ClipSegment(
                    sourceStartMs = 0L,
                    sourceEndMs = 10_000L,
                    destStartMs = startMs,
                    destEndMs = endMs,
                    effect = EffectKind.GLITCH,
                ),
            ),
        )

    @Test
    fun pausedNeverAnimatesEvenWithEffects() {
        val beat = idleProject.copy(beatSync = true)
        val keyframed = idleProject.copy(
            gradeKeyframesEnabled = true,
            gradeKeyframes = listOf(
                GradeKeyframe(0L, ColorGrade(brightness = 0f)),
                GradeKeyframe(1000L, ColorGrade(brightness = 1f)),
            ),
        )
        val glitch = glitchClip(idleProject)
        assertFalse(previewNeedsAnimation(beat, isPlaying = false, destMs = 500L))
        assertFalse(previewNeedsAnimation(keyframed, isPlaying = false, destMs = 500L))
        assertFalse(previewNeedsAnimation(glitch, isPlaying = false, destMs = 500L))
    }

    @Test
    fun idlePlayingProjectDoesNotAnimate() {
        assertFalse(previewNeedsAnimation(idleProject, isPlaying = true, destMs = 500L))
        assertFalse(previewNeedsAnimation(null, isPlaying = true, destMs = 0L))
    }

    @Test
    fun beatSyncAnimatesWhilePlaying() {
        val p = idleProject.copy(beatSync = true, beatSyncStrength = 0.8f)
        assertTrue(previewNeedsAnimation(p, isPlaying = true, destMs = 500L))
    }

    @Test
    fun animatedGradeKeyframesAnimateWhilePlaying() {
        val p = idleProject.copy(
            gradeKeyframesEnabled = true,
            gradeKeyframes = listOf(
                GradeKeyframe(0L, ColorGrade(brightness = 0f)),
                GradeKeyframe(1000L, ColorGrade(brightness = 1f)),
            ),
        )
        assertTrue(previewNeedsAnimation(p, isPlaying = true, destMs = 500L))
    }

    @Test
    fun disabledOrSingletonGradeKeyframesDoNotAnimate() {
        val singleton = idleProject.copy(
            gradeKeyframesEnabled = true,
            gradeKeyframes = listOf(GradeKeyframe(0L, ColorGrade(brightness = 1f))),
        )
        val disabled = idleProject.copy(
            gradeKeyframesEnabled = false,
            gradeKeyframes = listOf(
                GradeKeyframe(0L, ColorGrade(brightness = 0f)),
                GradeKeyframe(1000L, ColorGrade(brightness = 1f)),
            ),
        )
        assertFalse(previewNeedsAnimation(singleton, isPlaying = true, destMs = 500L))
        assertFalse(previewNeedsAnimation(disabled, isPlaying = true, destMs = 500L))
    }

    @Test
    fun animatedClipEffectAnimatesWhilePlaying() {
        for (effect in listOf(EffectKind.GLITCH, EffectKind.SHAKE, EffectKind.ZOOM, EffectKind.RGBSPLIT)) {
            val p = idleProject.copy(
                clips = listOf(
                    ClipSegment(
                        sourceStartMs = 0L,
                        sourceEndMs = 10_000L,
                        destStartMs = 0L,
                        destEndMs = 10_000L,
                        effect = effect,
                    ),
                ),
            )
            assertTrue("$effect should animate", previewNeedsAnimation(p, isPlaying = true, destMs = 500L))
        }
    }

    @Test
    fun staticClipEffectDoesNotAnimateWhilePlaying() {
        for (effect in listOf(EffectKind.NONE, EffectKind.FLASH, EffectKind.FADE, EffectKind.BRIGHTNESS, EffectKind.CONTRAST, EffectKind.BLUR, EffectKind.FAST)) {
            val p = idleProject.copy(
                clips = listOf(
                    ClipSegment(
                        sourceStartMs = 0L,
                        sourceEndMs = 10_000L,
                        destStartMs = 0L,
                        destEndMs = 10_000L,
                        effect = effect,
                    ),
                ),
            )
            assertFalse("$effect should not animate", previewNeedsAnimation(p, isPlaying = true, destMs = 500L))
        }
    }

    @Test
    fun animatedEffectOutsidePlayheadDoesNotAnimate() {
        val p = glitchClip(idleProject, startMs = 0L, endMs = 1000L)
        assertTrue(previewNeedsAnimation(p, isPlaying = true, destMs = 500L))
        assertFalse(previewNeedsAnimation(p, isPlaying = true, destMs = 2000L))
    }
}
