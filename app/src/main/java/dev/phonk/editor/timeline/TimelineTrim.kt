package dev.phonk.editor.timeline

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.PhonkProject

/**
 * Pure trim math shared by the timeline view (live drag) and the editor VM
 * (commit) so a handle can never be dragged to a value the model rejects.
 *
 * A clip's destination window may shrink freely and may grow back into the
 * free destination space around it (up to the previous clip's end / the next
 * clip's start) but never past the source media bounds, so trims cannot
 * fabricate footage or overlap neighbours. When clips tile the timeline end
 * to end (the normal case) middle clips are therefore shrink-only, which is
 * the intended behaviour of this ripple model.
 */
object TimelineTrim {

    /** Valid destination window for [clip]: [Bounds.minDestStart]..[Bounds.maxDestEnd]. */
    data class Bounds(val minDestStart: Long, val maxDestEnd: Long)

    /** Destination-to-source ratio of [clip] (accounts for playback speed). */
    fun ratio(clip: ClipSegment): Double {
        val src = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L).toDouble()
        val dest = (clip.destEndMs - clip.destStartMs).coerceAtLeast(1L).toDouble()
        return dest / src
    }

    /**
     * Computes the valid destination window for [clip] given its neighbours
     * and the source media bounds. [totalMs] is the timeline duration (the
     * last clip's end by default); passing a different value keeps the view
     * and the VM on the same axis.
     */
    fun bounds(
        clip: ClipSegment,
        project: PhonkProject,
        totalMs: Long = project.timelineDurationMs(),
    ): Bounds {
        val prev = project.clips
            .filter { it.destEndMs <= clip.destStartMs && it.id != clip.id }
            .maxByOrNull { it.destEndMs }
        val next = project.clips
            .filter { it.destStartMs >= clip.destEndMs && it.id != clip.id }
            .minByOrNull { it.destStartMs }

        val ratio = ratio(clip)
        val mediaEnd = project.videoDurationMs.coerceAtLeast(0L)
        val sourceBefore = (clip.sourceStartMs * ratio).toLong()
        val sourceAfter = ((mediaEnd - clip.sourceEndMs).coerceAtLeast(0L) * ratio).toLong()

        val minDestStart = maxOf(
            0L,
            prev?.destEndMs ?: 0L,
            clip.destStartMs - sourceBefore,
        )
        val maxDestEnd = minOf(
            totalMs,
            next?.destStartMs ?: totalMs,
            clip.destEndMs + sourceAfter,
        )

        // Safety: the current window must always remain reachable, even if
        // callers pass a degenerate totalMs / overlapping neighbours.
        return Bounds(
            minDestStart = minOf(minDestStart, (clip.destEndMs - 1L).coerceAtLeast(0L)),
            maxDestEnd = maxOf(maxDestEnd, clip.destStartMs + 1L),
        )
    }

    /**
     * Clamps a requested destination window into the valid [bounds], keeping
     * at least [minDurationMs] so a trim can never collapse a clip to zero.
     */
    fun clamp(
        destStartMs: Long,
        destEndMs: Long,
        bounds: Bounds,
        minDurationMs: Long = 100L,
    ): Pair<Long, Long> {
        var start = destStartMs.coerceIn(bounds.minDestStart, destEndMs - minDurationMs)
        var end = destEndMs.coerceIn(start + minDurationMs, bounds.maxDestEnd)
        if (end - start < minDurationMs) {
            start = start.coerceIn(bounds.minDestStart, end - minDurationMs)
            end = end.coerceIn(start + minDurationMs, bounds.maxDestEnd)
        }
        return start to end
    }

    /**
     * Maps a destination window back onto [clip]'s source span (linear within
     * the source span, matching how [ClipSegment.speed] stretches it). The
     * returned source start/end are clamped to the media ([mediaEndMs]) so an
     * over-drag can never produce a source window outside the real video.
     */
    fun toSource(
        clip: ClipSegment,
        destStartMs: Long,
        destEndMs: Long,
        mediaEndMs: Long = Long.MAX_VALUE,
    ): Pair<Long, Long> {
        val src = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val dest = (clip.destEndMs - clip.destStartMs).coerceAtLeast(1L)
        val destRatio = (destEndMs - destStartMs).toDouble() / dest
        val offsetRatio = (destStartMs - clip.destStartMs).toDouble() / dest
        val srcStart = (clip.sourceStartMs + src * offsetRatio)
            .toLong()
            .coerceIn(0L, clip.sourceEndMs)
        val srcEnd = (srcStart + src * destRatio)
            .toLong()
            .coerceIn(srcStart + 1L, mediaEndMs)
        return srcStart to srcEnd
    }
}
