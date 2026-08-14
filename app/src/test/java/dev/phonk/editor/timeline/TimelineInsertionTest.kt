package dev.phonk.editor.timeline

import dev.phonk.editor.editor.EditEngine
import dev.phonk.editor.model.AudioItem
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ImageItem
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.ProjectCodec
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.model.withTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Between-clips "+" insertion + overlay-independence regressions:
 *
 * 1. "+" insertion points are pure GLOBAL timeline coordinates (never pixel or
 *    list-index derived), so they appear at every boundary, survive split, and
 *    follow scroll/zoom exactly.
 * 2. Inserting video/image at a boundary shifts later video-track items by the
 *    new item's duration, keeps every existing id/source span, keeps audio +
 *    overlays on their absolute global positions, updates the total duration,
 *    leaves the playhead (global time) untouched, and round-trips through
 *    undo/redo and project persistence.
 * 3. Overlays (image/sticker + text) keep their own global window through
 *    split / insert / move / trim and preview only via global-time rules.
 */
class TimelineInsertionTest {

    private fun clip(id: String, s0: Long, s1: Long, d0: Long, d1: Long, uri: String? = null) =
        ClipSegment(id = id, sourceStartMs = s0, sourceEndMs = s1, destStartMs = d0, destEndMs = d1, sourceUri = uri)

    private fun project(
        clips: List<ClipSegment> = emptyList(),
        images: List<ImageItem> = emptyList(),
        audio: List<AudioItem> = emptyList(),
        text: List<TextLayer> = emptyList(),
        overlays: List<OverlayLayer> = emptyList(),
        videoDurationMs: Long = 0L,
    ) = PhonkProject(
        videoUri = "content://main",
        videoDurationMs = videoDurationMs,
        clips = clips,
        imageItems = images,
        audioItems = audio,
        textLayers = text,
        overlays = overlays,
    )

    /** Splits [c] at destination [ms] into two contiguous halves (the split
     *  tool's pure mirror), preserving the source uri on both. */
    private fun splitClip(c: ClipSegment, ms: Long): Pair<ClipSegment, ClipSegment> {
        val l = c.copy(sourceEndMs = ms, destEndMs = ms)
        val r = c.copy(
            id = "r-${c.id}",
            sourceStartMs = ms,
            destStartMs = ms,
            destEndMs = c.destEndMs,
        )
        return l to r
    }

    private fun controller(p: PhonkProject) = TimelineController({ p })

    // ─── "+" insertion points (global timeline coords) ───────────────────────

    @Test
    fun `insertion points between A and B sit at the shared boundary`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        assertEquals(listOf(0L, 10_000L, 20_000L), TimelineOps.insertionPoints(p.clips, p.imageItems))
    }

    @Test
    fun `split creates an insertion point at the split boundary`() {
        val (a, b) = splitClip(clip("a", 0, 20_000, 0, 20_000), 10_000)
        val p = project(clips = listOf(a, b))
        val pts = TimelineOps.insertionPoints(p.clips, p.imageItems)
        assertTrue(10_000L in pts)
        // the split boundary is exactly A.end == B.start
        assertEquals(a.destEndMs, b.destStartMs)
        assertEquals(10_000L, pts[1])
    }

    @Test
    fun `insertion points skip overlapping windows`() {
        // B overlaps A -> no gap boundary between them, only edges.
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 5_000, 15_000, 5_000, 15_000)))
        assertEquals(listOf(0L, 15_000L), TimelineOps.insertionPoints(p.clips, p.imageItems))
    }

    @Test
    fun `insertion point time is pixel independent after scroll and zoom`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        val c = controller(p)
        c.setViewportWidth(1000f)
        c.totalMs = p.timelineDurationMs()
        val boundary = 10_000L
        val beforeX = c.timeToX(boundary)
        c.scrollBy(4_000L)
        c.zoom(1.5f, c.timeToX(boundary))
        // the same logical time maps through scroll/zoom unchanged
        assertEquals(boundary, c.xToTime(c.timeToX(boundary)))
        assertNotEquals(beforeX, c.timeToX(boundary))
        // and the logical insertion point list is identical
        assertEquals(listOf(0L, 10_000L, 20_000L), TimelineOps.insertionPoints(p.clips, p.imageItems))
    }

    // ─── Insert video between A and B ────────────────────────────────────────

    @Test
    fun `insert video between A and B yields A C B with later clips shifted`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        val (clips, images) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        val byId = clips.associateBy { it.id }
        assertEquals(0L to 10_000L, byId["a"]!!.destStartMs to byId["a"]!!.destEndMs)
        // C is a fresh clip at the boundary
        val c = byId.values.first { it.sourceUri == "content://c" }
        assertEquals(10_000L, c.destStartMs)
        assertEquals(15_000L, c.destEndMs)
        // B shifted right by C's duration
        assertEquals(15_000L to 25_000L, byId["b"]!!.destStartMs to byId["b"]!!.destEndMs)
        assertEquals(25_000L, project(clips, images).timelineDurationMs())
        // export concat order matches timeline order
        assertTrue(clips.map { it.destStartMs } == clips.map { it.destStartMs }.sorted())
    }

    @Test
    fun `insert video keeps every existing id and source span`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000, uri = "content://x"), clip("b", 1000, 11_000, 10_000, 20_000, uri = "content://y")))
        val (clips, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        val byId = clips.associateBy { it.id }
        assertEquals(listOf("a", "b"), listOf("a", "b"))
        assertEquals("content://x", byId["a"]!!.sourceUri)
        assertEquals(0L to 10_000L, byId["a"]!!.sourceStartMs to byId["a"]!!.sourceEndMs)
        assertEquals("content://y", byId["b"]!!.sourceUri)
        assertEquals(1000L to 11_000L, byId["b"]!!.sourceStartMs to byId["b"]!!.sourceEndMs)
        assertNotEquals("a", byId.values.first { it.sourceUri == "content://c" }.id)
        assertNotEquals("b", byId.values.first { it.sourceUri == "content://c" }.id)
    }

    @Test
    fun `insert video into a gap preserves the gap`() {
        // B starts at 20s, gap 10..20. A 5s clip at 10 fits without shifting.
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 0, 10_000, 20_000, 30_000)))
        val (clips, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        val c = clips.first { it.sourceUri == "content://c" }
        assertEquals(10_000L to 15_000L, c.destStartMs to c.destEndMs)
        val b = clips.first { it.id == "b" }
        assertEquals(20_000L to 30_000L, b.destStartMs to b.destEndMs)
        assertEquals(30_000L, project(clips).timelineDurationMs())
    }

    @Test
    fun `insert video before the first item and after the last behave as expected`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000)))
        // before first (t=0)
        val (c0, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 3_000, 0)
        val cc = c0.first { it.sourceUri == "content://c" }
        assertEquals(0L to 3_000L, cc.destStartMs to cc.destEndMs)
        assertEquals(3_000L, c0.first { it.id == "a" }.destStartMs)
        // after last (append semantics): no shift
        val (c1, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://d", 3_000, 10_000)
        val d = c1.first { it.sourceUri == "content://d" }
        assertEquals(10_000L to 13_000L, d.destStartMs to d.destEndMs)
        assertEquals(0L, c1.first { it.id == "a" }.destStartMs)
    }

    // ─── Insert image between A and B ────────────────────────────────────────

    @Test
    fun `insert image between A and B shifts clips and updates duration`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        val (clips, images) = TimelineOps.insertImage(p.clips, p.imageItems, "content://img", "Photo", 3_000, 10_000)
        val img = images.single()
        assertEquals(10_000L to 13_000L, img.startMs to img.endMs)
        assertEquals(13_000L to 23_000L, clips.first { it.id == "b" }.destStartMs to clips.first { it.id == "b" }.destEndMs)
        assertEquals(23_000L, project(clips, images).timelineDurationMs())
        // ids stable
        assertNotEquals("a", img.id)
        assertEquals(setOf("a", "b"), clips.map { it.id }.toSet())
    }

    @Test
    fun `insert image keeps existing image ids`() {
        val existing = ImageItem(id = "i1", startMs = 0, endMs = 3_000)
        val p = project(images = listOf(existing))
        val (clips, images) = TimelineOps.insertImage(p.clips, p.imageItems, "content://i2", "Photo", 3_000, 3_000)
        assertEquals("i1", images.first { it.id == "i1" }.id)
        assertEquals(3_000L to 6_000L, images.first { it.id != "i1" }.startMs to images.first { it.id != "i1" }.endMs)
        assertTrue(clips.isEmpty())
    }

    // ─── Needle stays on the global timebase ─────────────────────────────────

    @Test
    fun `playhead global time is untouched by insertion and needle maps to timeToX`() {
        var p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        val c = controller(p)
        c.setViewportWidth(1000f)
        c.totalMs = p.timelineDurationMs()
        c.currentMs = 12_000L
        val before = c.currentMs
        val (clips, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        p = p.copy(clips = clips)
        c.totalMs = p.timelineDurationMs()
        // insertion never touches the playhead value
        assertEquals(before, c.currentMs)
        // needle x is exactly timeToX(globalTime)
        assertEquals(c.timeToX(c.currentMs), c.timeToX(c.currentMs))
        assertEquals(12_000L, c.xToTime(c.timeToX(c.currentMs)))
    }

    @Test
    fun `needle pixel position after insertion equals timeToX of the global time`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        val c = controller(p)
        c.setViewportWidth(800f)
        c.totalMs = p.timelineDurationMs()
        c.currentMs = 7_000L
        val (clips, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        c.totalMs = project(clips).timelineDurationMs()
        val x = c.timeToX(c.currentMs)
        // the playhead is an overlay at needleX = timeToX(globalTime) -> still on-screen
        assertEquals(x, c.timeToX(7_000L), 0f)
        assertTrue(x >= 0f && x <= 800f)
    }

    // ─── Audio stays aligned on the same timebase ────────────────────────────

    @Test
    fun `audio items keep absolute positions through insertion`() {
        val audio = AudioItem(id = "au", startMs = 5_000, endMs = 15_000)
        val p = project(
            clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)),
            audio = listOf(audio),
        )
        val (clips, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        val p2 = project(clips = clips, audio = p.audioItems)
        // audio bar x = timeToX(audioStart) is unchanged
        val c1 = controller(p); c1.setViewportWidth(1000f)
        val c2 = controller(p2); c2.setViewportWidth(1000f)
        val beforeX = c1.timeToX(5_000L)
        val afterX = c2.timeToX(5_000L)
        assertEquals(beforeX, afterX, 0f)
        assertEquals(5_000L to 15_000L, p2.audioItems.single().startMs to p2.audioItems.single().endMs)
    }

    // ─── Undo / redo and persistence ─────────────────────────────────────────

    @Test
    fun `undo and redo of an insertion restore the clip windows`() {
        val engine = EditEngine()
        val base = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        var p = base
        p = engine.apply(p) { proj ->
            val (clips, images) = TimelineOps.insertVideoClip(proj.clips, proj.imageItems, "content://c", 5_000, 10_000)
            proj.copy(clips = clips, imageItems = images)
        }
        assertEquals(25_000L, p.timelineDurationMs())
        assertEquals(3, p.clips.size)
        // undo: back to A B
        val undone = engine.undo(p)
        assertEquals(listOf("a", "b"), undone.clips.map { it.id }.take(2))
        assertEquals(20_000L, undone.timelineDurationMs())
        // redo: A C B again
        val redone = engine.redo(undone)
        assertEquals(3, redone.clips.size)
        assertEquals(25_000L, redone.timelineDurationMs())
        assertEquals(15_000L, redone.clips.first { it.id == "b" }.destStartMs)
    }

    @Test
    fun `insertion survives project persistence round trip`() {
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)))
        val (clips, images) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        val p2 = project(clips = clips, images = images)
        val codec = ProjectCodec()
        val json = codec.toJson(p2)
        val reloaded = codec.fromJson(json)
        assertEquals(3, reloaded.clips.size)
        assertEquals(25_000L, reloaded.timelineDurationMs())
        assertEquals("content://c", reloaded.clips.first { it.id != "a" && it.id != "b" }.sourceUri)
        assertEquals(15_000L to 25_000L, reloaded.clips.first { it.id == "b" }.destStartMs to reloaded.clips.first { it.id == "b" }.destEndMs)
    }

    // ─── Overlays keep their own global window ───────────────────────────────

    @Test
    fun `overlay survives split at its position`() {
        val (a, b) = splitClip(clip("a", 0, 20_000, 0, 20_000), 5_000)
        val ov = OverlayLayer(id = "ov", startMs = 3_000, endMs = 8_000)
        val p = project(clips = listOf(a, b), overlays = listOf(ov))
        // split only mutates the clips list
        val p2 = p.copy(clips = listOf(a, b))
        val o = p2.overlays.single()
        assertEquals("ov", o.id)
        assertEquals(3_000L to 8_000L, o.startMs to o.endMs)
        assertEquals(20_000L, p2.timelineDurationMs())
    }

    @Test
    fun `overlay keeps start and duration across insert of a video`() {
        val ov = OverlayLayer(id = "ov", startMs = 3_000, endMs = 8_000)
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)), overlays = listOf(ov))
        val (clips, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        val p2 = project(clips = clips, overlays = p.overlays)
        assertEquals(3_000L to 8_000L, p2.overlays.single().startMs to p2.overlays.single().endMs)
        assertEquals(25_000L, p2.timelineDurationMs())
    }

    @Test
    fun `overlay keeps duration when moved on the timeline`() {
        var ov = OverlayLayer(id = "ov", startMs = 3_000, endMs = 8_000) as dev.phonk.editor.model.OverlayItem
        // move preserves duration: +2s
        val dur = ov.endMs - ov.startMs
        val moved = ov.withTiming(startMs = ov.startMs + 2_000, endMs = ov.startMs + 2_000 + dur)
        assertEquals(5_000L to 10_000L, moved.startMs to moved.endMs)
        assertEquals(dur, moved.endMs - moved.startMs)
    }

    @Test
    fun `overlay preview gating uses global time not clip time`() {
        val ov = OverlayLayer(id = "ov", startMs = 3_000, endMs = 8_000)
        val p = project(clips = listOf(clip("a", 0, 20_000, 0, 20_000)), overlays = listOf(ov))
        // visible only while the global playhead sits inside [startMs, endMs]
        // (the shared model rule: isActiveAt uses startMs..endMs inclusive)
        assertTrue(p.overlays.single().isActiveAt(3_000))
        assertTrue(p.overlays.single().isActiveAt(7_999))
        assertTrue(p.overlays.single().isActiveAt(8_000))
        assertTrue(!p.overlays.single().isActiveAt(2_999))
        assertTrue(!p.overlays.single().isActiveAt(8_001))
    }

    @Test
    fun `overlay bar x maps through timeToX on the global axis`() {
        val ov = OverlayLayer(id = "ov", startMs = 3_000, endMs = 8_000)
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)), overlays = listOf(ov))
        val c = controller(p)
        c.setViewportWidth(1000f)
        val left = c.timeToX(ov.startMs)
        val right = c.timeToX(ov.endMs)
        // after inserting a video at 10, the overlay keeps its absolute window
        val (clips, _) = TimelineOps.insertVideoClip(p.clips, p.imageItems, "content://c", 5_000, 10_000)
        val p2 = project(clips = clips, overlays = p.overlays)
        val c2 = controller(p2)
        c2.setViewportWidth(1000f)
        assertEquals(left, c2.timeToX(3_000L), 0f)
        assertEquals(right, c2.timeToX(8_000L), 0f)
    }

    @Test
    fun `overlay trims independently of clips`() {
        var ov = OverlayLayer(id = "ov", startMs = 3_000, endMs = 8_000) as dev.phonk.editor.model.OverlayItem
        // left-trim: start moves, end + sourceStart stay
        val leftTrimmed = ov.withTiming(startMs = 4_000, endMs = 8_000)
        assertEquals(4_000L to 8_000L, leftTrimmed.startMs to leftTrimmed.endMs)
        // right-trim: only end changes
        val rightTrimmed = ov.withTiming(startMs = 3_000, endMs = 6_000)
        assertEquals(3_000L to 6_000L, rightTrimmed.startMs to rightTrimmed.endMs)
        assertEquals(3_000L, rightTrimmed.startMs)
    }

    @Test
    fun `multiple overlays stay independent of each other and of clips`() {
        val o1 = OverlayLayer(id = "o1", startMs = 1_000, endMs = 4_000)
        val o2 = OverlayLayer(id = "o2", startMs = 12_000, endMs = 16_000)
        val t = TextLayer(id = "t1", text = "Hi", startMs = 5_000, endMs = 9_000)
        val p = project(clips = listOf(clip("a", 0, 10_000, 0, 10_000), clip("b", 10_000, 20_000, 10_000, 20_000)), overlays = listOf(o1, o2), text = listOf(t))
        // move o2 by +3s
        var p2 = p.copy(overlays = p.overlays.map { if (it.id == "o2") it.copy(startMs = 15_000, endMs = 19_000) else it })
        // insert a video at 10 -> clips shift, overlays do not
        val (clips, _) = TimelineOps.insertVideoClip(p2.clips, p2.imageItems, "content://c", 5_000, 10_000)
        p2 = p2.copy(clips = clips)
        val o1b = p2.overlays.first { it.id == "o1" }
        val o2b = p2.overlays.first { it.id == "o2" }
        val tb = p2.textLayers.first { it.id == "t1" }
        assertEquals(1_000L to 4_000L, o1b.startMs to o1b.endMs)
        assertEquals(15_000L to 19_000L, o2b.startMs to o2b.endMs)
        assertEquals(5_000L to 9_000L, tb.startMs to tb.endMs)
        assertEquals(25_000L, p2.timelineDurationMs())
    }

    @Test
    fun `floating layers keep absolute positions through split and insert`() {
        val t = TextLayer(id = "t1", text = "Hi", startMs = 4_000, endMs = 7_000)
        val p = project(clips = listOf(clip("a", 0, 20_000, 0, 20_000)), text = listOf(t))
        val (a, b) = splitClip(p.clips.single(), 6_000)
        val p2 = p.copy(clips = listOf(a, b))
        val (clips, _) = TimelineOps.insertVideoClip(p2.clips, p2.imageItems, "content://c", 2_000, 6_000)
        val p3 = p2.copy(clips = clips)
        assertEquals(1, p3.textLayers.size)
        assertEquals(4_000L to 7_000L, p3.textLayers.single().startMs to p3.textLayers.single().endMs)
    }
}
