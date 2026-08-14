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

    /** Reference window treated as "100%" for the zoom readout. */
    private val baseViewportMs = 30_000L

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

    /**
     * Keeps the needle/playhead on screen by scrolling the viewport after it
     * when the playhead has drifted outside the visible window.
     *
     * This is the LibreCuts playhead rule: the needle is an overlay on the
     * timeline coordinate layer at needleX = timeToX(globalTime) and the
     * viewport follows global time, so the needle can never leave the canvas —
     * even when it parks in a destination gap (split/trim/move), or after
     * scroll/zoom pushed it far away. Centring is edge-triggered (only when
     * the playhead is out of view), so a pan that still shows the needle is
     * never fought and pinch/button zoom stays pivot-preserving.
     *
     * @return true when the viewport moved (caller should redraw).
     */
    fun keepPlayheadVisible(): Boolean {
        if (totalMs <= 0L) return false
        val range = visibleRange()
        if (currentMs >= range.first && currentMs <= range.last) return false
        val newStart = (currentMs - viewportMs / 2).coerceIn(0L, maxStart())
        if (newStart == viewportStartMs) return false
        viewportStartMs = newStart
        return true
    }

    /**
     * Zooms in/out around the playhead so the needle never leaves the
     * viewport while using the toolbar buttons. [factor] > 1 zooms in.
     * When the playhead is scrolled out of view, pivots on the nearest
     * visible edge instead so the zoom is still stable.
     */
    fun zoomBy(factor: Float) {
        val pivotX = timeToX(currentMs).coerceIn(0f, viewportWidthPx)
        zoom(factor, pivotX, viewportWidthPx)
    }

    /** Zoom readout relative to the 30s base window, mirroring the actual
     *  viewport clamp in [zoom] (0.5s..max(600s, totalMs)) instead of a
     *  hard-coded range that could disagree with the real zoom level. */
    val zoomPercent: Int
        get() = if (viewportMs <= 0L) 100
            else (baseViewportMs.toDouble() / viewportMs * 100.0).toInt().coerceAtLeast(1)

    /** Total playback duration in ms across the whole project. */
    fun projectDurationMs(): Long = totalMs.coerceAtLeast(project().clips
        .maxOfOrNull { it.destEndMs } ?: project().beats.lastOrNull()?.timestampMs?.toLong() ?: 0L)
}