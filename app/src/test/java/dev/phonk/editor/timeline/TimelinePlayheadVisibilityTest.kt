package dev.phonk.editor.timeline

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the needle/playhead DISAPPEARING after the main video
 * is split and a resulting clip is moved.
 *
 * Architecture rule (matching LibreCuts): the needle is GLOBAL TIMELINE TIME.
 * Its X is always needleX = timeToX(globalTime) — it never derives from the
 * selected/current/first/last clip — and the viewport follows the global
 * playhead so the needle can never leave the canvas, even when it parks in a
 * destination gap (which split + move creates) or after scroll/zoom pushed it
 * out of the visible window.
 */
class TimelinePlayheadVisibilityTest {

    private val mediaEnd = 100_000L
    private val timelineMs = 120_000L

    /** Main video (0..100s) split into A and B; B moved from 40s to 60s.
     *  Destination gap [40s, 60s]; the source media is still continuous. */
    private val clips = listOf(
        ClipSegment(id = "A", sourceStartMs = 0L, sourceEndMs = 40_000L, destStartMs = 0L, destEndMs = 40_000L),
        ClipSegment(id = "B", sourceStartMs = 40_000L, sourceEndMs = 100_000L, destStartMs = 60_000L, destEndMs = 120_000L),
    )

    private fun newController(): TimelineController =
        TimelineController { PhonkProject() }.apply {
            totalMs = timelineMs
            setViewportWidth(1000f)
        }

    @Test
    fun needleStaysOnScreenForEveryGlobalTimeAfterSplitAndMove() {
        val c = newController()
        // Default window is 0..30s on a 120s timeline: the gap and B are far
        // outside it, which is exactly when the needle used to vanish.
        val targets = longArrayOf(
            0L,               // before A
            10_000L,          // inside A
            30_000L,          // A end / window edge
            40_000L,          // gap start
            50_000L,          // inside the gap
            60_000L,          // B start
            90_000L,          // inside B
            120_000L,         // after B / timeline end
        )
        for (t in targets) {
            c.currentMs = t
            c.keepPlayheadVisible()
            val x = c.timeToX(c.currentMs)
            assertTrue("t=$t x=$x off-screen", x >= 0f && x <= 1000f)
            assertTrue("t=$t not inside the visible window", c.currentMs in c.visibleRange())
        }
    }

    @Test
    fun needleStaysOnScreenWhenZoomedInAroundAnotherRegion() {
        val c = newController()
        // Pinch-style zoom-in pivoting far away from the playhead (gap).
        c.zoom(8f, 900f, 1000f)
        c.currentMs = 50_000L
        assertTrue(c.currentMs !in c.visibleRange()) // precondition: would vanish
        c.keepPlayheadVisible()
        assertTrue(c.currentMs in c.visibleRange())
        val x = c.timeToX(c.currentMs)
        assertTrue("x=$x off-screen", x >= 0f && x <= 1000f)
    }

    @Test
    fun needleStaysOnScreenAfterScrollingAway() {
        val c = newController()
        c.currentMs = 0L
        c.scrollBy(90_000L) // pan far right, playhead at 0 left behind
        assertTrue(c.currentMs !in c.visibleRange()) // precondition: would vanish
        c.keepPlayheadVisible()
        assertTrue(c.currentMs in c.visibleRange())
        val x = c.timeToX(c.currentMs)
        assertTrue("x=$x off-screen", x >= 0f && x <= 1000f)
    }

    @Test
    fun followDoesNotFightAPanThatKeepsPlayheadVisible() {
        val c = newController()
        c.currentMs = 10_000L
        c.scrollBy(5_000L) // still contains the playhead
        val startBefore = c.viewportStartMs
        assertTrue(c.currentMs in c.visibleRange())
        c.keepPlayheadVisible()
        assertEquals(startBefore, c.viewportStartMs)
    }

    @Test
    fun needleXIsFiniteForEveryGlobalTime() {
        val c = newController()
        var t = 0L
        while (t <= timelineMs) {
            val x = c.timeToX(t)
            assertTrue("x=$x at t=$t not finite", x.isFinite())
            t += 250L
        }
    }
}
