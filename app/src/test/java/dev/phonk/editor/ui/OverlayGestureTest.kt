package dev.phonk.editor.ui

import dev.phonk.editor.editor.EditEngine
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the stale-overlay-gesture undo bug.
 *
 * The ViewModel captures `overlayGestureStart` before an overlay drag and only
 * seals the undo entry from [EditorViewModel.endOverlayTransform]. When a
 * drag's pointer-input coroutine is cancelled mid-gesture (navigation away,
 * recomposition, the overlay editor leaving composition) the on-end path never
 * runs, so a stale pre-gesture snapshot is left behind. The NEXT gesture that
 * ends (even a plain tap) then pushed an undo entry built from
 * (staleBefore, currentProject), so Undo restored an arbitrarily old project.
 *
 * The ViewModel is not instantiable in plain JUnit (it needs an Android
 * Context), so these tests drive the extracted pure-Kotlin
 * [OverlayGestureTracker] + [EditEngine] exactly as the ViewModel does.
 */
class OverlayGestureTest {

    private val base = PhonkProject(name = "base")

    @Test
    fun completedGestureCreatesExactlyOneUndoEntry() {
        val engine = EditEngine()
        val tracker = OverlayGestureTracker()
        var p = base

        tracker.begin(p)
        p = p.copy(name = "dragged")
        tracker.markDirty()

        val before = tracker.end()
        assertNotNull("a completed gesture must seal exactly one undo entry", before)
        p = engine.apply(before!!) { p }

        assertTrue("one completed gesture => exactly one undo entry", engine.canUndo)
        assertFalse(engine.canRedo)

        val undone = engine.undo(p)
        assertEquals("base", undone.name)
        assertFalse("undoing the gesture must empty the undo stack", engine.canUndo)
    }

    @Test
    fun cancelledGestureMustNotPoisonTheNextGesture() {
        val engine = EditEngine()
        val tracker = OverlayGestureTracker()
        var p = base

        // ---- gesture 1: drag, then the pointer coroutine is CANCELLED ----
        tracker.begin(p)                 // snapshot "base"
        p = p.copy(name = "afterDrag1")  // live transform applied in memory
        tracker.markDirty()
        tracker.cancel()                 // UI cancellation path; end() never runs

        // ---- gesture 2: a plain tap on the same overlay: end WITHOUT begin ----
        // Pre-fix this sealed an undo entry rooted at the stale "base" snapshot.
        val stale = tracker.end()
        assertNull("a cancelled gesture must not leave a stale snapshot", stale)
        if (stale != null) p = engine.apply(stale) { p }

        assertFalse("no undo entry may be rooted at the stale pre-gesture snapshot", engine.canUndo)

        // A genuinely started second gesture still seals its own single entry,
        // rooted at the CURRENT project (afterDrag1), never at "base".
        tracker.begin(p)
        p = p.copy(name = "afterDrag2")
        tracker.markDirty()
        val before2 = tracker.end()
        assertNotNull(before2)
        p = engine.apply(before2!!) { p }
        assertTrue(engine.canUndo)

        val undone = engine.undo(p)
        assertEquals("afterDrag1", undone.name)
    }

    @Test
    fun gestureWithoutLiveTransformDoesNotSealAnUndoEntry() {
        val engine = EditEngine()
        val tracker = OverlayGestureTracker()
        var p = base

        tracker.begin(p)            // gesture began but never moved
        val before = tracker.end()  // no live transform fired

        assertNull("a gesture with no live transform must not seal an undo entry", before)
        if (before != null) p = engine.apply(before) { p }
        assertFalse(engine.canUndo)
    }

    @Test
    fun repeatedEndsAfterCancelStayNoOps() {
        val tracker = OverlayGestureTracker()
        tracker.begin(base)
        tracker.markDirty()
        tracker.cancel()
        assertNull(tracker.end())
        assertNull(tracker.end())
    }
}
