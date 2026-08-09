package dev.phonk.editor.editor

import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.model.BeatMarker
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.DropMarker
import dev.phonk.editor.model.DropType
import dev.phonk.editor.model.EffectKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the beat-synchronized cut planner. Uses a synthetic beat
 * grid (steady 140 BPM, a drop on beat 8) so expectations are exact.
 */
class CutPlannerTest {

    private fun analysis(bpm: Double = 140.0, beats: Int = 16, dropAtBeats: List<Int> = listOf(8)): AnalysisResult {
        val beatMs = 60000.0 / bpm
        val beatsList = (0 until beats).map { i ->
            BeatMarker(
                timestampMs = i * beatMs,
                confidence = 1f,
                beatIndex = i,
                downbeat = i % 4 == 0,
            )
        }
        val drops = dropAtBeats.map { i ->
            DropMarker(
                timestampMs = i * beatMs,
                confidence = 0.9f,
                strength = 0.9f,
                type = DropType.HARD_DROP,
            )
        }
        return AnalysisResult(
            bpm = bpm,
            sampleRate = 11025,
            durationMs = (beats * beatMs).toLong() + 1000,
            beats = beatsList,
            drops = drops,
            sections = emptyList(),
            beatConfidence = 1f,
            dropConfidence = 0.9f,
            energyCurve = FloatArray(0),
            fluxCurve = FloatArray(0),
        )
    }

    @Test
    fun patternB_twoBeatsPerCut() {
        val a = analysis()
        val plan = CutPlanner.planPattern(a, CutPattern.B)
        val beatMs = 60000.0 / 140.0 // 428.57ms
        // 16 beats at 2 beats/cut -> 8 segments
        assertEquals(8, plan.clips.size)
        // deterministic: running the planner twice yields identical output
        // (normalize the random per-clip id)
        fun norm(c: List<ClipSegment>) = c.map { it.copy(id = "x") }
        val again = CutPlanner.planPattern(a, CutPattern.B)
        assertEquals(norm(plan.clips), norm(again.clips))
        assertEquals(plan.totalDurationMs, again.totalDurationMs)
    }

    @Test
    fun everyCutIsNonNegativeAndOrdered() {
        val a = analysis()
        for (pattern in CutPattern.entries) {
            val plan = CutPlanner.planPattern(a, pattern)
            plan.clips.forEachIndexed { i, clip ->
                assertTrue("clip $i in $pattern source start", clip.sourceStartMs >= 0)
                assertTrue("clip $i source span", clip.sourceEndMs > clip.sourceStartMs)
                assertTrue("clip $i dest span", clip.destEndMs > clip.destStartMs)
                if (i > 0) {
                    assertTrue("clip $i continuity", clip.destStartMs >= plan.clips[i - 1].destEndMs)
                }
            }
        }
    }

    @Test
    fun strongDropGetsFlashEffect() {
        val a = analysis(dropAtBeats = listOf(8))
        val plan = CutPlanner.planPattern(a, CutPattern.F_DROP_EMPHASIS)
        val dropMs = 8 * (60000.0 / 140.0)
        val nearDrop = plan.clips.firstOrNull {
            it.dropTransition && Math.abs(it.destEndMs - dropMs) < 600
        }
        assertTrue("a clip should land on the drop", nearDrop != null)
        assertTrue(
            "drop clip uses flash for strength 0.9",
            nearDrop!!.dropTransition && nearDrop.effect == EffectKind.FLASH,
        )
        // the clip must record where the drop actually sits in source time so
        // the renderer can anchor the flash on the drop, not the clip end
        assertTrue("dropSourceMs must be recorded", nearDrop.dropSourceMs != null)
        assertEquals(
            "dropSourceMs should equal the drop timestamp",
            Math.round(dropMs),
            nearDrop.dropSourceMs!!,
        )
    }

    @Test
    fun weakDropAlsoGetsFlashEffect() {
        // Every drop must produce a visible effect. ZOOM/SHAKE are no-ops in
        // the bundled renderer, so a weak drop must not silently disappear.
        val a = analysis().copy(
            drops = listOf(
                DropMarker(
                    timestampMs = 8 * (60000.0 / 140.0),
                    confidence = 0.6f,
                    strength = 0.4f,
                    type = DropType.BEAT_DROP,
                ),
            )
        )
        val plan = CutPlanner.planPattern(a, CutPattern.F_DROP_EMPHASIS)
        val nearDrop = plan.clips.firstOrNull { it.dropTransition }
        assertTrue("a clip should land on the weak drop", nearDrop != null)
        assertEquals(
            "weak drops must still get a visible flash",
            EffectKind.FLASH,
            nearDrop!!.effect,
        )
    }

    @Test
    fun planIsClampedToVideoDuration() {
        // The decoded audio can outlive the video track; the plan must never
        // extend past the real video length or exports grow beyond the source.
        val a = analysis() // durationMs = 16 beats + 1000ms
        val realVideoMs = (14 * (60000.0 / 140.0)).toLong() // ~8571ms, shorter than audio
        val plan = CutPlanner.planPattern(a, CutPattern.B, maxSourceMs = realVideoMs)
        assertTrue("plan must not exceed the video length", plan.totalDurationMs <= realVideoMs)
        assertTrue(
            "last clip end must stay within the video",
            plan.clips.none { it.sourceEndMs > realVideoMs },
        )
    }

    @Test
    fun emptyBeatsProducesEmptyPlan() {
        val empty = analysis().copy(beats = emptyList())
        assertEquals(0L, CutPlanner.planPattern(empty, CutPattern.A).totalDurationMs)
    }
}