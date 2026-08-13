package dev.phonk.editor.timeline

import dev.phonk.editor.model.ClipSegment

/**
 * Pure global-time mapping between the player's SOURCE timeline and the
 * project's DESTINATION timeline.
 *
 * Architecture rule: the needle/playhead is GLOBAL TIMELINE TIME. Its value
 * must always be a valid destination timestamp inside [0, timelineDuration],
 * even when the player position sits in a source gap (content removed by a
 * trim/split/delete) or the playhead is parked in a destination gap. Both
 * directions therefore fall back to the NEXT clip's boundary instead of
 * jumping to an arbitrary clip (e.g. the last clip), which used to teleport
 * the needle off-viewport after split/trim/move operations.
 *
 * The needle X coordinate is then always needleX = timeToX(globalTime); it
 * never derives from a selected/current/first/last clip.
 */
object TimelineTime {

    /** Global destination time for a player source position [srcMs]. */
    fun sourceToDest(
        clips: List<ClipSegment>,
        srcMs: Long,
        mediaEndMs: Long,
        timelineDurationMs: Long,
    ): Long {
        if (clips.isEmpty()) return srcMs.coerceIn(0L, timelineDurationMs)
        val clip = clips.firstOrNull { srcMs in it.sourceStartMs until it.sourceEndMs }
        if (clip != null) return mapToDest(clip, srcMs).coerceIn(0L, timelineDurationMs)
        // Source gap: point at the start of the next clip's content so the
        // needle keeps advancing and never leaves the timeline.
        val next = clips.filter { it.sourceStartMs > srcMs }.minByOrNull { it.sourceStartMs }
        return if (next != null) next.destStartMs.coerceIn(0L, timelineDurationMs)
        else timelineDurationMs
    }

    /** Source position for a global destination time [destMs] (seek target). */
    fun destToSource(
        clips: List<ClipSegment>,
        destMs: Long,
        mediaEndMs: Long,
        timelineDurationMs: Long,
    ): Long {
        if (clips.isEmpty()) return destMs.coerceIn(0L, mediaEndMs)
        val clip = clips.firstOrNull { destMs in it.destStartMs until it.destEndMs }
        if (clip != null) return mapToSource(clip, destMs).coerceIn(0L, mediaEndMs)
        // Destination gap: seek to the start of the next clip's content.
        val next = clips.filter { it.destStartMs > destMs }.minByOrNull { it.destStartMs }
        return if (next != null) next.sourceStartMs.coerceIn(0L, mediaEndMs)
        else mediaEndMs
    }

    private fun mapToDest(clip: ClipSegment, srcMs: Long): Long {
        val src = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val dest = (clip.destEndMs - clip.destStartMs).coerceAtLeast(1L)
        val ratio = dest.toDouble() / src
        return (clip.destStartMs + ((srcMs - clip.sourceStartMs) * ratio).toLong())
            .coerceIn(clip.destStartMs, clip.destEndMs)
    }

    private fun mapToSource(clip: ClipSegment, destMs: Long): Long {
        val dest = (clip.destEndMs - clip.destStartMs).coerceAtLeast(1L)
        val src = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val ratio = src.toDouble() / dest
        return (clip.sourceStartMs + ((destMs - clip.destStartMs) * ratio).toLong())
            .coerceIn(clip.sourceStartMs, clip.sourceEndMs)
    }
}
