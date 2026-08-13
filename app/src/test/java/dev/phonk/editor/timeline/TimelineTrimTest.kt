package dev.phonk.editor.timeline

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the shared trim-bound math.
 *
 * These pin the editor-spec behaviour: a clip's destination window may shrink
 * freely and may grow back into the free destination space around it (previous
 * clip's end / next clip's start) but never past the source media bounds, so
 * trims cannot fabricate footage, overlap neighbours, or snap back after the
 * model rejects a drag. In the normal fully-tiled timeline middle clips are
 * shrink-only, matching this ripple model.
 */
class TimelineTrimTest {

    private fun project(vararg clips: ClipSegment, videoMs: Long = 3000L): PhonkProject =
        PhonkProject(videoDurationMs = videoMs, clips = clips.toList())

    private fun clip(
        id: String,
        srcStart: Long,
        srcEnd: Long,
        destStart: Long,
        destEnd: Long,
        speed: Float = 1f,
    ) = ClipSegment(
        id = id,
        sourceStartMs = srcStart,
        sourceEndMs = srcEnd,
        destStartMs = destStart,
        destEndMs = destEnd,
        speed = speed,
    )

    @Test
    fun tiledMiddleClipIsShrinkOnly() {
        val a = clip("a", 0, 1000, 0, 1000)
        val b = clip("b", 1000, 2000, 1000, 2000)
        val c = clip("c", 2000, 3000, 2000, 3000)
        val bounds = TimelineTrim.bounds(b, project(a, b, c))
        assertEquals(1000L, bounds.minDestStart)
        assertEquals(2000L, bounds.maxDestEnd)
    }

    @Test
    fun firstClipCanRestoreTrimmedLeftMaterial() {
        // Clip was trimmed at the start: source/dest moved inward to 300.
        val a = clip("a", 300, 1000, 300, 1000)
        val b = clip("b", 1000, 2000, 1000, 2000)
        val bounds = TimelineTrim.bounds(a, project(a, b))
        assertEquals(0L, bounds.minDestStart)
        val (start, end) = TimelineTrim.clamp(0L, 1000L, bounds)
        assertEquals(0L, start)
        assertEquals(1000L, end)
        // Restoring maps the source window back onto the original media.
        val (srcStart, srcEnd) = TimelineTrim.toSource(a, 0L, 1000L, mediaEndMs = 3000L)
        assertEquals(0L, srcStart)
        assertEquals(1000L, srcEnd)
    }

    @Test
    fun middleClipExtendsIntoGapUpToNeighbour() {
        // A was trimmed from 1000 down to 500, leaving a gap before B.
        val a = clip("a", 0, 500, 0, 500)
        val b = clip("b", 1000, 2000, 1000, 2000)
        val bounds = TimelineTrim.bounds(b, project(a, b))
        assertEquals(500L, bounds.minDestStart)
        val (start, _) = TimelineTrim.clamp(600L, 2000L, bounds)
        assertEquals(600L, start)
    }

    @Test
    fun middleClipExtensionBoundedBySourceMaterial() {
        // B has little source material before its source span, so the
        // extension into the gap is capped by what the source actually has:
        // sourceBefore = 100ms * (600/300) ratio = 200 dest-ms, which runs
        // out before the gap's 500ms, so B stops at 800 instead of 500.
        val a = clip("a", 0, 500, 0, 500)
        val b = clip("b", 100, 400, 1000, 1600)
        val bounds = TimelineTrim.bounds(b, project(a, b))
        assertEquals(800L, bounds.minDestStart)
        val (start, _) = TimelineTrim.clamp(500L, 1600L, bounds)
        assertEquals(800L, start)
    }

    @Test
    fun lastClipRightEdgeBoundedByTimelineEnd() {
        // Timeline length = last clip end; the ruler cannot extend past it.
        val a = clip("a", 0, 1000, 0, 1000)
        val c = clip("c", 2000, 2500, 1000, 2500)
        val bounds = TimelineTrim.bounds(c, project(a, c))
        assertEquals(2500L, bounds.maxDestEnd)
        val (_, end) = TimelineTrim.clamp(1000L, 4000L, bounds)
        assertEquals(2500L, end)
    }

    @Test
    fun toSourceMapsDestWindowBackIntoSource() {
        val c = clip("c", 1000, 2000, 500, 1500)
        val (srcStart, srcEnd) = TimelineTrim.toSource(c, 500L, 1200L)
        assertEquals(1000L, srcStart)
        assertEquals(1700L, srcEnd)
    }

    @Test
    fun toSourceRespectsSpeedRatio() {
        // Speed 0.5 => dest is twice the source; trimming to half the dest
        // window must yield half the source span, keeping the speed intact.
        val c = clip("c", 0, 1000, 0, 2000, speed = 0.5f)
        val (srcStart, srcEnd) = TimelineTrim.toSource(c, 0L, 1000L)
        assertEquals(0L, srcStart)
        assertEquals(500L, srcEnd)
    }

    @Test
    fun toSourceNeverEscapesMediaBounds() {
        val c = clip("c", 0, 1000, 0, 1000)
        val (srcStart, srcEnd) = TimelineTrim.toSource(c, -500L, 1500L, mediaEndMs = 1000L)
        assertTrue(srcStart >= 0L)
        assertTrue(srcStart < srcEnd)
        assertTrue(srcEnd <= 1000L)
    }

    @Test
    fun clampKeepsMinDuration() {
        val bounds = TimelineTrim.Bounds(1000L, 2000L)
        val (start, end) = TimelineTrim.clamp(1990L, 2000L, bounds, minDurationMs = 100L)
        assertEquals(100L, end - start)
        assertTrue(start >= bounds.minDestStart)
        assertTrue(end <= bounds.maxDestEnd)
    }

    @Test
    fun clampNeverRejectsCurrentWindow() {
        val a = clip("a", 0, 1000, 0, 1000)
        val b = clip("b", 1000, 2000, 1000, 2000)
        val bounds = TimelineTrim.bounds(b, project(a, b))
        val (start, end) = TimelineTrim.clamp(b.destStartMs, b.destEndMs, bounds)
        assertEquals(1000L, start)
        assertEquals(2000L, end)
    }
}
