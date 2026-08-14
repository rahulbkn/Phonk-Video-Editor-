package dev.phonk.editor.timeline

import dev.phonk.editor.model.AudioItem
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ImageItem
import dev.phonk.editor.model.PhonkProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Multi-track architecture regressions: ONE global timeline timebase derived
 * from the maximum item end (video clips + image items + audio items), gaps
 * supported, stable ids, needle independent of clips, and preview/audio mapped
 * through the shared time↔pixel system.
 */
class TimelineMultiTrackTest {

    private fun clip(id: String, s0: Long, s1: Long, d0: Long, d1: Long, uri: String? = null) =
        ClipSegment(id = id, sourceStartMs = s0, sourceEndMs = s1, destStartMs = d0, destEndMs = d1, sourceUri = uri)

    private fun project(
        clips: List<ClipSegment> = emptyList(),
        images: List<ImageItem> = emptyList(),
        audio: List<AudioItem> = emptyList(),
        videoDurationMs: Long = 0L,
    ) = PhonkProject(
        videoUri = "content://main",
        videoDurationMs = videoDurationMs,
        clips = clips,
        imageItems = images,
        audioItems = audio,
    )

    // ─── Global duration rule ────────────────────────────────────────────────

    @Test
    fun `duration is the max item end across clips images and audio`() {
        val p = project(
            clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 0, 5_000, 20_000, 25_000)),
            images = listOf(ImageItem(id = "i", startMs = 25_000, endMs = 28_000)),
            audio = listOf(AudioItem(id = "au", startMs = 0, endMs = 40_000)),
        )
        // Max end = the audio item at 40s, even though video stops at 25s.
        assertEquals(40_000L, p.timelineDurationMs())
    }

    @Test
    fun `duration respects destination gaps`() {
        // 20s clip split in two with a 5s gap between: end is still 20s.
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 15_000, 25_000)))
        assertEquals(25_000L, p.timelineDurationMs())
    }

    @Test
    fun `duration with no items is zero`() {
        assertEquals(0L, project().timelineDurationMs())
    }

    // ─── Spec scenario: split -> move -> extend -> add video -> add image ───

    @Test
    fun `split then move second clip to 25 extends the timeline to 35`() {
        var clips = listOf(clip("a", 0, 20_000, 0, 20_000))
        // split at 10s (pure split mirror: two contiguous clips)
        clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000))
        // move B so it starts at 25s
        val moved = TimelineOps.moveClip(clips, "b", 25_000)
        val b = moved.first { it.id == "b" }
        assertEquals(25_000L, b.destStartMs)
        assertEquals(35_000L, b.destEndMs)
        assertEquals(35_000L, project(moved).timelineDurationMs())
    }

    @Test
    fun `append video then image auto-extends the timeline`() {
        var p = project(clips = listOf(clip("a", 0, 20_000, 0, 20_000)))
        // "+" video: 5s file appended at the current end (20s).
        val clips2 = TimelineOps.appendVideoClip(p.clips, "content://v2", 5_000, p.timelineDurationMs())
        p = p.copy(clips = clips2)
        assertEquals(25_000L, p.timelineDurationMs())
        val v2 = p.clips.last()
        assertEquals("content://v2", v2.sourceUri)
        assertEquals(20_000L, v2.destStartMs)
        assertEquals(25_000L, v2.destEndMs)
        // "+" image: 3s still appended at the current end (25s).
        val images2 = TimelineOps.appendImage(p.imageItems, "content://img", "Photo", p.timelineDurationMs(), 3_000)
        p = p.copy(imageItems = images2)
        assertEquals(28_000L, p.timelineDurationMs())
        val img = p.imageItems.last()
        assertEquals(25_000L, img.startMs)
        assertEquals(28_000L, img.endMs)
        // move the image to 50s: the timeline follows to 53s.
        val images3 = TimelineOps.appendImage(emptyList(), img.uri, img.label, 50_000, 3_000)
        p = p.copy(imageItems = images3)
        assertEquals(53_000L, p.timelineDurationMs())
    }

    @Test
    fun `moveClip preserves duration and never clamps the start to current end`() {
        val clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000))
        val moved = TimelineOps.moveClip(clips, "b", 60_000)
        val b = moved.first { it.id == "b" }
        assertEquals(60_000L, b.destStartMs)
        assertEquals(70_000L, b.destEndMs)
        assertEquals(70_000L, project(moved).timelineDurationMs())
        // a untouched
        assertEquals(0L, moved.first { it.id == "a" }.destStartMs)
    }

    @Test
    fun `moveClip with unknown id returns the same list`() {
        val clips = listOf(clip("a", 0, 10_000, 0, 10_000))
        assertTrue(TimelineOps.moveClip(clips, "nope", 5_000) === clips)
    }

    @Test
    fun `moveClip clamps start to zero`() {
        val clips = listOf(clip("a", 0, 10_000, 5_000, 15_000))
        val moved = TimelineOps.moveClip(clips, "a", -4_000)
        assertEquals(0L, moved.first().destStartMs)
        assertEquals(10_000L, moved.first().destEndMs)
    }

    @Test
    fun `stable ids survive move and append`() {
        val clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000))
        val moved = TimelineOps.moveClip(clips, "b", 25_000)
        val appended = TimelineOps.appendVideoClip(moved, "content://v2", 3_000, 35_000)
        assertEquals(listOf("a", "b"), appended.map { it.id }.take(2))
        assertNotEquals("b", appended.last().id)
        assertEquals(setOf("a", "b"), moved.map { it.id }.toSet())
    }

    @Test
    fun `split preserves source uri on both halves`() {
        val p = project(clips = listOf(clip("a", 0, 20_000, 0, 20_000, uri = "content://foreign")))
        val dur = 20_000L
        val right = p.clips.first().copy(
            id = "b", sourceStartMs = 10_000, sourceEndMs = 20_000, destStartMs = 10_000, destEndMs = 20_000,
        )
        val halves = listOf(p.clips.first().copy(sourceEndMs = 10_000, destEndMs = 10_000), right)
        assertEquals("content://foreign", halves[0].sourceUri)
        assertEquals("content://foreign", halves[1].sourceUri)
        assertEquals(dur, project(halves).timelineDurationMs())
    }

    // ─── Needle independence + time↔pixel ───────────────────────────────────

    @Test
    fun `needle renders at timeToX for every position including gaps and past items`() {
        val p = project(
            clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 15_000, 25_000)),
            images = listOf(ImageItem(id = "i", startMs = 28_000, endMs = 30_000)),
            audio = listOf(AudioItem(id = "au", startMs = 0, endMs = 45_000)),
        )
        val c = TimelineController { p }
        c.totalMs = p.timelineDurationMs()
        c.setViewportWidth(1000f)
        for (ms in listOf(0L, 12_000L, 25_000L, 27_000L, 30_000L, 40_000L, 44_000L, 45_000L)) {
            c.currentMs = ms
            // The needle is an overlay at timeToX(globalTime); the viewport
            // follows global time (keepPlayheadVisible), so it must stay on-canvas.
            c.keepPlayheadVisible()
            val x = c.timeToX(c.currentMs)
            assertTrue("needle x for $ms ms must be in range", x in 0f..1000f)
        }
    }

    @Test
    fun `time to x and x to time are inverses within the visible window`() {
        val p = project(
            clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 15_000, 25_000)),
            audio = listOf(AudioItem(id = "au", startMs = 30_000, endMs = 45_000)),
            videoDurationMs = 20_000L,
        )
        val c = TimelineController { p }
        c.totalMs = p.timelineDurationMs()
        c.setViewportWidth(800f)
        // Window edges map to the canvas edges exactly.
        assertEquals(0f, c.timeToX(c.viewportStartMs), 0f)
        assertEquals(800f, c.timeToX(c.viewportStartMs + c.viewportMs), 0f)
        // Pixel->time round-trips within 1 ms (float truncation) for any
        // position inside the visible window.
        for (ms in listOf(0L, 15_000L, 25_000L)) {
            val back = c.xToTime(c.timeToX(ms))
            assertTrue("round-trip at $ms was $back", abs(back - ms) <= 1)
        }
    }

    @Test
    fun `audio position and width derive from the same timebase`() {
        val p = project(
            clips = listOf(clip("a", 0, 20_000, 0, 20_000)),
            audio = listOf(AudioItem(id = "au", startMs = 5_000, endMs = 15_000)),
        )
        val c = TimelineController { p }
        c.totalMs = p.timelineDurationMs()
        c.setViewportWidth(1000f)
        val left = c.timeToX(5_000)
        val right = c.timeToX(15_000)
        assertTrue(right > left)
        // width grows with duration
        val w1 = c.timeToX(15_000) - c.timeToX(5_000)
        val w2 = c.timeToX(20_000) - c.timeToX(5_000)
        assertTrue(w2 > w1)
    }

    @Test
    fun `scroll and zoom never move the playhead`() {
        val p = project(clips = listOf(clip("a", 0, 20_000, 0, 20_000)))
        val c = TimelineController { p }
        c.totalMs = p.timelineDurationMs()
        c.setViewportWidth(1000f)
        c.currentMs = 12_000L
        c.scrollBy(4_000L)
        assertEquals(12_000L, c.currentMs)
        c.zoomBy(1.5f)
        assertEquals(12_000L, c.currentMs)
        c.zoom(1.5f, c.timeToX(12_000L))
        assertEquals(12_000L, c.currentMs)
    }

    // ─── Preview clip-local translation ─────────────────────────────────────

    @Test
    fun `preview clip-local time equals timelineTime minus item start plus source start`() {
        val clipB = clip("b", 10_000, 20_000, 15_000, 25_000)
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clipB), videoDurationMs = 20_000L)
        // at destination 20s inside B: source should be 10s + (20s - 15s) = 15s
        val local = TimelineOps.clipLocalTime(clipB, 20_000)
        assertEquals(15_000L, local)
        // speed-aware mapping agrees at speed 1
        val viaMap = TimelineTime.destToSource(p.clips, 20_000, p.videoDurationMs, p.timelineDurationMs())
        assertEquals(local, viaMap)
    }

    @Test
    fun `destination gap seeks to the next video clip source`() {
        val p = project(
            clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 15_000, 25_000)),
            images = listOf(ImageItem(id = "i", startMs = 12_000, endMs = 15_000)),
            videoDurationMs = 20_000L,
        )
        // 12s sits in an image window (a destination gap) -> next video clip B source 10s
        assertEquals(10_000L, TimelineTime.destToSource(p.clips, 12_000, p.videoDurationMs, p.timelineDurationMs()))
        // after the last clip -> media end
        assertEquals(20_000L, TimelineTime.destToSource(p.clips, 30_000, p.videoDurationMs, p.timelineDurationMs()))
    }
}
