package dev.phonk.editor.timeline

import dev.phonk.editor.model.PhonkProject

/**
 * Viewport state for the timeline: which time window is visible, zoom level
 * and playhead position. Time<->x conversions only depend on window and width,
 * never on dpi.
 */
class TimelineController(
    private val project: () -> PhonkProject,
) {
    var totalMs: Long = 0L
        set(value) {
            field = maxOf(0L, value)
            viewportStartMs = viewportStartMs.coerceIn(0L, maxStart())
        }

    var viewportStartMs: Long = 0L
        private set

    var viewportMs: Long = 30_000L
        private set

    var viewportWidthPx: Float = 1f
        private set

    var currentMs: Long = 0L
        set(value) {
            field = value.coerceIn(0L, totalMs)
        }

    private fun maxStart(): Long = maxOf(0L, totalMs - viewportMs)

    fun setViewportWidth(widthPx: Float) {
        if (widthPx > 0 && widthPx.isFinite()) viewportWidthPx = widthPx
    }

    fun timeToX(ms: Long, width: Float = viewportWidthPx): Float {
        if (viewportMs <= 0L || width <= 0f) return 0f
        return ((ms - viewportStartMs).toFloat() / viewportMs) * width
    }

    fun xToTime(x: Float, width: Float = viewportWidthPx): Long {
        if (viewportMs <= 0L || width <= 0f) return 0L
        val rel = (x / width).coerceIn(0f, 1f)
        return (viewportStartMs + (rel * viewportMs).toLong()).coerceIn(0L, totalMs)
    }

    fun scrollBy(deltaMs: Long) {
        viewportStartMs = (viewportStartMs + deltaMs).coerceIn(0L, maxStart())
    }

    /** Move the playhead to the time under [x]. */
    fun seekTo(x: Float, width: Float) {
        currentMs = xToTime(x, width)
    }

    /** factor > 1 zooms in; keeps the time under [pivotX] stationary. */
    fun zoom(factor: Float, pivotX: Float, width: Float = viewportWidthPx) {
        if (factor <= 0f || !factor.isFinite()) return
        val pivotMs = xToTime(pivotX, width)
        val oldRange = viewportMs
        val pivotRel = if (oldRange > 0L) {
            (pivotMs - viewportStartMs).toDouble() / oldRange
        } else {
            0.5
        }
        val newRange = (viewportMs.toDouble() / factor).toLong()
            .coerceIn(500L, maxOf(600_000L, totalMs))
        viewportMs = newRange
        viewportStartMs = (pivotMs - (pivotRel * newRange).toLong()).coerceIn(0L, maxStart())
    }

    fun visibleRange(): LongRange =
        viewportStartMs..(viewportStartMs + viewportMs).coerceAtMost(totalMs)

    /** Total playback duration in ms across the whole project. */
    fun projectDurationMs(): Long = totalMs.coerceAtLeast(project().clips
        .maxOfOrNull { it.destEndMs } ?: project().beats.lastOrNull()?.timestampMs?.toLong() ?: 0L)
}