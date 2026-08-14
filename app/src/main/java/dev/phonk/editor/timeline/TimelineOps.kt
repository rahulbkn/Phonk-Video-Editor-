package dev.phonk.editor.timeline

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ImageItem
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.PhonkProject

/**
 * Pure, side-effect-free timeline operations (move/append) that mutate ONLY the
 * affected item's destination window. They are the single source of truth for
 * multi-track edits so the UI and tests exercise identical logic.
 *
 * Rule: an item's destination window is {destStartMs, destStartMs + duration}.
 * Moving an item changes its start (>= 0, no upper clamp) and leaves every
 * other item untouched — the global timeline length is re-derived afterwards
 * from the maximum item end, so moving/clipping into empty space (gaps) and
 * appending new items both extend the canvas automatically.
 */
object TimelineOps {

    /** Moves the clip with [id] so its destination window starts at [newStartMs]
     *  (clamped to >= 0). Duration and source span are preserved. Returns the
     *  list unchanged when no clip matches [id]. */
    fun moveClip(clips: List<ClipSegment>, id: String, newStartMs: Long): List<ClipSegment> {
        val clip = clips.firstOrNull { it.id == id } ?: return clips
        val start = newStartMs.coerceAtLeast(0L)
        val duration = clip.destDurationMs
        return clips.map {
            if (it.id == id) it.copy(destStartMs = start, destEndMs = start + duration) else it
        }
    }

    /** Appends a full-length clip from [sourceUri] starting at destination
     *  [startMs]. [sourceUri] null = the project's main video. */
    fun appendVideoClip(
        clips: List<ClipSegment>,
        sourceUri: String?,
        sourceDurationMs: Long,
        startMs: Long,
    ): List<ClipSegment> {
        val dur = sourceDurationMs.coerceAtLeast(0L)
        return clips + ClipSegment(
            sourceUri = sourceUri,
            sourceStartMs = 0L,
            sourceEndMs = dur,
            destStartMs = startMs,
            destEndMs = startMs + dur,
        )
    }

    /** Appends an image item at destination [startMs] with [durationMs]. */
    fun appendImage(
        images: List<ImageItem>,
        uri: String?,
        label: String,
        startMs: Long,
        durationMs: Long,
    ): List<ImageItem> {
        val dur = durationMs.coerceAtLeast(1L)
        return images + ImageItem(
            uri = uri,
            label = label.ifBlank { "Image" },
            startMs = startMs,
            endMs = startMs + dur,
        )
    }

    /** Preview translation: the source timestamp shown while the playhead sits
     *  at destination [destMs] inside [clip]. This is the spec's
     *  `clipLocalTime = timelineTime - item.timelineStart + item.sourceStart`;
     *  [TimelineTime.destToSource] is the speed-aware equivalent. */
    fun clipLocalTime(clip: ClipSegment, destMs: Long): Long =
        clip.sourceStartMs + (destMs - clip.destStartMs).coerceAtLeast(0L)

    // ─── Insertion between items ────────────────────────────────────────────

    /**
     * Global-timeline positions of every video-track insertion point:
     *
     * - before the first item (t = 0),
     * - at each boundary between consecutive non-overlapping items
     *   (t = the left item's end),
     * - after the last item (t = the last item's end).
     *
     * Positions are derived from the actual timeline coordinates (never from
     * list indexes or pixel positions), so they follow scroll/zoom exactly and
     * a split boundary [A][B] yields the insertion point at A.end == B.start.
     * Boundaries inside overlapping items are skipped (no meaningful gap).
     */
    fun insertionPoints(clips: List<ClipSegment>, images: List<ImageItem>): List<Long> {
        val items = (clips.map { it.destStartMs to it.destEndMs } +
            images.map { it.startMs to it.endMs }).sortedBy { it.first }
        if (items.isEmpty()) return emptyList()
        val pts = ArrayList<Long>()
        pts.add(0L)
        for (i in 1 until items.size) {
            val prevEnd = items[i - 1].second
            val nextStart = items[i].first
            if (prevEnd <= nextStart) pts.add(prevEnd)
        }
        pts.add(items.last().second)
        return pts.distinct().sorted()
    }

    /** The video-track item whose window starts at/after [atMs], if any. */
    private fun nextVideoStart(clips: List<ClipSegment>, images: List<ImageItem>, atMs: Long): Long? {
        var next: Long? = null
        clips.forEach { if (it.destStartMs >= atMs && (next == null || it.destStartMs < next!!)) next = it.destStartMs }
        images.forEach { if (it.startMs >= atMs && (next == null || it.startMs < next!!)) next = it.startMs }
        return next
    }

    /**
     * Shifts every video-track item (clip or image) starting at/after [atMs]
     * just far enough that the new item placed at [atMs] with duration
     * [insertedDurMs] never overlaps it. Returns the pair unchanged (no shift)
     * when an existing gap already absorbs the new item, so a destination gap
     * is preserved instead of being collapsed. Overlays, text and audio items
     * are floating layers on the global time axis and keep their absolute
     * positions — they are never shifted by a video insertion.
     */
    private fun shiftVideoTrack(
        clips: List<ClipSegment>,
        images: List<ImageItem>,
        atMs: Long,
        insertedDurMs: Long,
    ): Pair<List<ClipSegment>, List<ImageItem>> {
        val nextStart = nextVideoStart(clips, images, atMs) ?: return clips to images
        val shift = maxOf(0L, atMs + insertedDurMs - nextStart)
        if (shift == 0L) return clips to images
        val sc = clips.map {
            if (it.destStartMs >= atMs) it.copy(
                destStartMs = it.destStartMs + shift,
                destEndMs = it.destEndMs + shift,
            ) else it
        }
        val si = images.map {
            if (it.startMs >= atMs) it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) else it
        }
        return sc to si
    }

    /**
     * Inserts a full-length clip from [sourceUri] at destination [atMs],
     * shifting later video-track items to make room (see [shiftVideoTrack]).
     * The new clip is unique and stable (fresh id); existing ids never change.
     * Returned clip list is sorted by destination start so the export's
     * concat order always matches the timeline order.
     */
    fun insertVideoClip(
        clips: List<ClipSegment>,
        images: List<ImageItem>,
        sourceUri: String?,
        sourceDurationMs: Long,
        atMs: Long,
    ): Pair<List<ClipSegment>, List<ImageItem>> {
        val at = atMs.coerceAtLeast(0L)
        val dur = sourceDurationMs.coerceAtLeast(0L)
        val (shiftedClips, shiftedImages) = shiftVideoTrack(clips, images, at, dur)
        val inserted = ClipSegment(
            sourceUri = sourceUri,
            sourceStartMs = 0L,
            sourceEndMs = dur,
            destStartMs = at,
            destEndMs = at + dur,
        )
        return (shiftedClips + inserted).sortedBy { it.destStartMs } to shiftedImages
    }

    /** Inserts an image item at destination [atMs] with [durationMs], shifting
     *  later video-track items to make room. Ids are preserved/unique. */
    fun insertImage(
        clips: List<ClipSegment>,
        images: List<ImageItem>,
        uri: String?,
        label: String,
        durationMs: Long,
        atMs: Long,
    ): Pair<List<ClipSegment>, List<ImageItem>> {
        val at = atMs.coerceAtLeast(0L)
        val dur = durationMs.coerceAtLeast(1L)
        val (shiftedClips, shiftedImages) = shiftVideoTrack(clips, images, at, dur)
        val inserted = ImageItem(uri = uri, label = label.ifBlank { "Image" }, startMs = at, endMs = at + dur)
        return shiftedClips to (shiftedImages + inserted).sortedBy { it.startMs }
    }

    /** Overlay/text/audio items that intersect the destination time [ms]. Used
     *  by tests to prove floating layers keep absolute positions. */
    fun floatingAt(project: PhonkProject, ms: Long): List<OverlayItem> =
        (project.textLayers as List<OverlayItem> + project.overlays).filter { it.isActiveAt(ms) }
}
