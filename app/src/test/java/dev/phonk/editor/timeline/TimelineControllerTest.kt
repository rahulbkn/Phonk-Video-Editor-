package dev.phonk.editor.timeline

import dev.phonk.editor.model.PhonkProject
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the timeline viewport/zoom controller.
 *
 * These pin the editor-spec behavior: the "− / +" toolbar buttons zoom around
 * the playhead (so the needle never leaves the viewport), the zoom readout
 * mirrors the real viewport clamp instead of an arbitrary range, and the
 * time<->pixel conversions stay consistent on one shared time axis.
 */
class TimelineControllerTest {

    private fun controller(totalMs: Long = 120_000L, widthPx: Float = 1000f): TimelineController =
        TimelineController { PhonkProject() }.apply {
            this.totalMs = totalMs
            setViewportWidth(widthPx)
        }

    @Test
    fun zoomInPivotsAroundPlayheadAndKeepsItOnScreen() {
        val c = controller()
        c.currentMs = 30_000L
        val xBefore = c.timeToX(30_000L)
        c.zoomBy(1.5f)
        val xAfter = c.timeToX(c.currentMs)
        assertEquals(xBefore, xAfter, 1f)
        assertTrue(c.currentMs in c.visibleRange())
        assertTrue(c.viewportMs < 30_000L)
    }

    @Test
    fun zoomOutKeepsPlayheadVisible() {
        val c = controller()
        c.currentMs = 15_000L
        c.zoomBy(1f / 1.5f)
        assertTrue(c.currentMs in c.visibleRange())
        assertTrue(c.viewportMs > 30_000L)
    }

    @Test
    fun repeatedZoomInClampsToMinViewport() {
        val c = controller()
        c.currentMs = 0L
        repeat(20) { c.zoomBy(4f) }
        assertEquals(500L, c.viewportMs)
        assertTrue(c.zoomPercent >= 100)
    }

    @Test
    fun repeatedZoomOutClampsToMaxViewport() {
        val c = controller(totalMs = 60_000L)
        c.currentMs = 30_000L
        repeat(20) { c.zoomBy(0.01f) }
        assertEquals(600_000L, c.viewportMs)
        assertTrue(c.visibleRange().last <= c.totalMs)
    }

    @Test
    fun zoomPercentReflectsActualViewport() {
        val c = controller()
        assertEquals(100, c.zoomPercent)
        c.zoomBy(2f)
        assertEquals(200, c.zoomPercent)
        c.zoomBy(0.5f)
        assertEquals(100, c.zoomPercent)
    }

    @Test
    fun timeXconversionsRoundTripWithinViewport() {
        val c = controller(totalMs = 120_000L)
        c.currentMs = 0L
        // 30s base viewport zoomed out 1.5x -> 45s window starting at 0.
        c.zoomBy(1f / 1.5f)
        for (ms in longArrayOf(0L, 1_000L, 22_000L, 44_999L)) {
            val back = c.xToTime(c.timeToX(ms))
            assertTrue("round-trip drift at $ms: $back", abs(back - ms) <= 1L)
        }
    }

    @Test
    fun seekAndScrollClampToProjectBounds() {
        val c = controller(totalMs = 60_000L)
        // Seeking past the right edge clamps to the visible window end.
        c.seekTo(99_999f, 1000f)
        assertEquals(30_000L, c.currentMs)
        c.scrollBy(1_000_000L)
        assertEquals(c.visibleRange().last, c.totalMs)
        c.scrollBy(-1_000_000L)
        assertEquals(0L, c.viewportStartMs)
    }
}
