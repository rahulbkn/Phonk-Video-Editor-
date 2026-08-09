package dev.phonk.editor.editor

import dev.phonk.editor.model.DropType
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Undo/redo and effect-scheduling tests for the edit engine. */
class EditEngineTest {

    @Test
    fun undoRestoresProject() {
        val engine = EditEngine()
        val base = PhonkProject()
        val added = engine.apply(base) { EditEngine.Actions.addDrop(it, 5000.0, DropType.HARD_DROP) }
        assertEquals(1, added.drops.size)

        val restored = engine.undo(added)
        assertEquals(0, restored.drops.size)
        assertTrue(engine.canRedo)
    }

    @Test
    fun redoReappliesAction() {
        val engine = EditEngine()
        val base = PhonkProject()
        val added = engine.apply(base) { EditEngine.Actions.addDrop(it, 5000.0, DropType.BASS_DROP) }
        val restored = engine.undo(added)
        val reapplied = engine.redo(restored)
        assertEquals(1, reapplied.drops.size)
        assertEquals(DropType.BASS_DROP, reapplied.drops[0].type)
    }

    @Test
    fun moveDropIsIdempotentWithinTolerance() {
        val base = PhonkProject(drops = listOf(
            dev.phonk.editor.model.DropMarker(1000.0, 0.9f, 0.8f, DropType.HARD_DROP)
        ))
        val moved = EditEngine.Actions.moveDrop(base, 1000.0, 2000.0)
        assertEquals(2000.0, moved.drops[0].timestampMs, 0.01)
    }

    @Test
    fun effectSchedulerEmitsDropPulse() {
        val clips = listOf(
            dev.phonk.editor.model.ClipSegment(
                sourceStartMs = 0L, sourceEndMs = 1000L,
                destStartMs = 0L, destEndMs = 1000L,
                effect = EffectKind.FLASH, effectStrength = 0.9f, dropTransition = true,
            )
        )
        val beats = listOf(
            dev.phonk.editor.model.BeatMarker(0.0, 1f, 0, downbeat = true)
        )
        val events = EffectScheduler.schedule(clips, beats, dropPulseEnabled = true)
        assertTrue("drop transition must schedule an effect", events.isNotEmpty())
        assertEquals(EffectKind.FLASH, events[0].kind)
        // flash at end of the clip, 80ms window
        assertEquals(80L, events[0].durationMs)
        assertTrue(events[0].atDestMs <= 1000L)
    }

    @Test
    fun flashAnchorsOnDropNotClipEnd() {
        // Clip spans source 4000..6000; drop is at source 4500 (100ms into the
        // clip). In dest the drop maps to destStart + (dropSource - sourceStart).
        val clips = listOf(
            dev.phonk.editor.model.ClipSegment(
                sourceStartMs = 4000L, sourceEndMs = 6000L,
                destStartMs = 2000L, destEndMs = 4000L,
                effect = EffectKind.FLASH, effectStrength = 0.9f, dropTransition = true,
                dropSourceMs = 4500L,
            )
        )
        val events = EffectScheduler.schedule(clips, emptyList(), dropPulseEnabled = true)
        assertEquals(1, events.size)
        // dest drop position = 2000 + (4500-4000) = 2500; 80ms window centered on it
        assertEquals(2460L, events[0].atDestMs)
        assertEquals(80L, events[0].durationMs)
    }

    @Test
    fun flashFallsBackToClipEndWhenNoDropSource() {
        val clips = listOf(
            dev.phonk.editor.model.ClipSegment(
                sourceStartMs = 0L, sourceEndMs = 1000L,
                destStartMs = 0L, destEndMs = 1000L,
                effect = EffectKind.FLASH, effectStrength = 0.9f, dropTransition = true,
            )
        )
        val events = EffectScheduler.schedule(clips, emptyList(), dropPulseEnabled = true)
        assertEquals(1, events.size)
        // 80ms window ending at clip end: atDestMs = 1000 - 40 = 960
        assertEquals(960L, events[0].atDestMs)
    }

    @Test
    fun downbeatPulseOnlyWhenEnabled() {
        val beats = listOf(dev.phonk.editor.model.BeatMarker(0.0, 1f, 0, downbeat = true))
        val clips = emptyList<dev.phonk.editor.model.ClipSegment>()
        val off = EffectScheduler.schedule(clips, beats, dropPulseEnabled = false, downbeatPulse = false)
        assertTrue(off.isEmpty())
        val on = EffectScheduler.schedule(clips, beats, dropPulseEnabled = false, downbeatPulse = true)
        assertFalse(on.isEmpty())
        assertEquals(EffectKind.BRIGHTNESS, on[0].kind)
    }
}