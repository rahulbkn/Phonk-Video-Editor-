package dev.phonk.editor.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import dev.phonk.editor.R
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.PhonkProject
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToLong

private data class TrackDef(val label: String, val color: Int)

/**
 * Pan/zoom multi-track waveform + beat/drop/cut editor.
 * Tracks (top→bottom): Video, Audio (waveform), Overlay, Text, Effect.
 * Interactions:
 *  - tap a clip -> select it (onSelectClip)
 *  - double-tap a clip -> split at that position (onClipSplit)
 *  - drag the trim handles of the selected clip -> onTrimStart/onTrimEnd
 *  - touch lower half -> seek (onSeekTo)
 */
class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    lateinit var controller: TimelineController
    lateinit var project: PhonkProject

    var onSeekTo: ((ms: Long) -> Unit)? = null
    var onClipSplit: ((ms: Long) -> Unit)? = null
    var onSelectClip: ((String) -> Unit)? = null
    var onTrimStart: ((ms: Long) -> Unit)? = null
    var onTrimEnd: ((ms: Long) -> Unit)? = null

    /** Selected overlay (text/sticker/image) whose bars show trim handles. */
    var selectedOverlayId: String? = null

    /** Taps an overlay/text bar -> select that item. */
    var onSelectOverlay: ((String) -> Unit)? = null

    /** Commits a bar drag (move or trim) with the final window. One call per gesture. */
    var onSetOverlayTiming: ((id: String, startMs: Long, endMs: Long) -> Unit)? = null

    private val tracks = listOf(
        TrackDef("V", Color.parseColor("#8B5CF6")),
        TrackDef("A", Color.parseColor("#E879F9")),
        TrackDef("O", Color.parseColor("#FF7EB6")),
        TrackDef("T", Color.parseColor("#FFC1E3")),
        TrackDef("E", Color.parseColor("#6D28D9")),
    )

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val beatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; isFakeBoldText = true }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Filmstrip thumbnails on the video track. Each cell is a fixed-width real
     * frame preview; the cell count adapts to the zoom level so thumbnails never
     * overlap (cells are capped to the clip's pixel span and one-per-second).
     */
    private val thumbW = 28f * resources.displayMetrics.density
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "timeline-thumbs").apply { priority = Thread.MIN_PRIORITY }
    }
    private val requestedKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val thumbGen = AtomicInteger(0)

    private val scaleDetector: ScaleGestureDetector
    private var lastX = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val trackLabelWidth = 28f

    // Density-aware touch slop (Android's real guidance) instead of raw pixels.
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val trimSlopPx get() = touchSlop * 1.6f

    private enum class DragMode { NONE, TRIM_START, TRIM_END }
    private var dragMode = DragMode.NONE
    private var lastTapTime = 0L
    private var lastTapX = 0f

    /** Minimum overlay window width when trimming on the timeline (matches the VM). */
    private companion object {
        const val MIN_OVERLAY_DURATION = 100L
    }

    private enum class OverlayDragMode { MOVE, TRIM_START, TRIM_END }

    private class OverlayDrag(
        val id: String,
        val mode: OverlayDragMode,
        val downX: Float,
        val downStart: Long,
        val downEnd: Long,
    ) {
        var liveStart = downStart
        var liveEnd = downEnd
    }

    private var overlayDrag: OverlayDrag? = null

    init {
        isFocusable = true
        scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    controller.zoom(detector.scaleFactor, detector.focusX, width.toFloat())
                    invalidate()
                    return true
                }
            },
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::controller.isInitialized) controller.setViewportWidth(w.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::controller.isInitialized) return
        val w = width.toFloat()
        val h = height.toFloat()
        val primary = context.getColor(R.color.primary)
        val accent = context.getColor(R.color.accent)
        val textPrimary = context.getColor(R.color.text_primary)
        val textSecondary = context.getColor(R.color.text_secondary)
        val surfaceVariant = context.getColor(R.color.surface_variant)

        val trackH = (h - 16f) / tracks.size
        val labelZone = trackLabelWidth + 4f

        // track backgrounds
        tracks.forEachIndexed { i, track ->
            val top = 8f + i * trackH
            trackPaint.color = surfaceVariant
            canvas.drawRect(labelZone, top, w - 4f, top + trackH - 4f, trackPaint)
            labelPaint.color = track.color
            canvas.drawText(track.label, 6f, top + trackH / 2f + 6f, labelPaint)
        }

        // waveform on audio track (track index 1)
        drawWaveform(canvas, labelZone, 8f + 1 * trackH, w - 4f, trackH - 4f)

        // clip segments on video track
        val wantThumbs = mutableListOf<Pair<String?, Long>>()
        drawClips(canvas, labelZone, 8f + 0 * trackH, trackH - 4f, primary, wantThumbs)
        requestThumbnails(wantThumbs)

        // overlay bars on overlay track (index 2)
        drawOverlayBars(canvas, labelZone, 8f + 2 * trackH, trackH - 4f, Color.parseColor("#FF7EB6"),
            project.overlays)
        // text bars on text track (index 3)
        drawOverlayBars(canvas, labelZone, 8f + 3 * trackH, trackH - 4f, Color.parseColor("#FFC1E3"),
            project.textLayers)
        // effects on effect track (index 4)
        drawBars(canvas, labelZone, 8f + 4 * trackH, trackH - 4f, Color.parseColor("#6D28D9"),
            project.clips.filter { it.effect != dev.phonk.editor.model.EffectKind.NONE }.map { it.destStartMs to it.destEndMs })

        // beat ticks across all tracks
        beatPaint.color = withAlpha(primary, 140)
        project.beats.forEach { beat ->
            val x = controller.timeToX(beat.timestampMs.roundToLong())
            if (x in labelZone..w) {
                val amp = beat.confidence.coerceIn(0f, 1f)
                val top = 8f
                val bottom = h - 16f
                val tickH = (bottom - top) * (0.3f + 0.7f * amp)
                canvas.drawLine(x, bottom - tickH, x, bottom, beatPaint)
            }
        }

        // drop markers
        dropPaint.color = accent
        project.drops.forEach { drop ->
            val x = controller.timeToX(drop.timestampMs.roundToLong())
            if (x in labelZone..w) {
                val cy = 8f + 1 * trackH + (trackH - 4f) / 2f
                canvas.drawCircle(x, cy, 7f, dropPaint)
            }
        }

        // time ruler
        drawTimeRuler(canvas, labelZone, w, h - 12f, textSecondary)

        // playhead
        val playX = controller.timeToX(controller.currentMs)
        playPaint.color = textPrimary
        canvas.drawLine(playX, 4f, playX, h - 8f, playPaint)
        handlePaint.color = primary
        canvas.drawCircle(playX, 6f, 8f, handlePaint)
    }

    private fun drawWaveform(canvas: Canvas, left: Float, top: Float, right: Float, height: Float) {
        val curve = project.analysisEnergyCurve()
        if (curve.isEmpty()) return
        val mid = top + height / 2f
        val halfH = height * 0.42f
        val w = right - left
        wavePaint.color = withAlpha(context.getColor(R.color.accent), 90)
        val step = w / curve.size.coerceAtLeast(1)
        for (i in curve.indices) {
            val x = left + i * step
            val amp = maxOf(0f, minOf(1f, curve[i]))
            val barH = halfH * amp
            canvas.drawRect(x, mid - barH, x + step.coerceAtLeast(1f), mid + barH, wavePaint)
        }
    }

    private fun drawClips(
        canvas: Canvas,
        left: Float,
        top: Float,
        trackH: Float,
        primary: Int,
        wantThumbs: MutableList<Pair<String?, Long>>,
    ) {
        val w = width.toFloat() - 4f
        clipBorder.color = withAlpha(primary, 180)
        val selectedId = project.selectedClipId
        val videoUri = project.videoUri
        // No cuts applied yet -> the whole source plays as one implicit segment,
        // so the filmstrip is still drawn for the full destination timeline.
        val segments = if (project.clips.isEmpty()) {
            val end = controller.projectDurationMs().coerceAtLeast(1L)
            listOf(
                dev.phonk.editor.model.ClipSegment(
                    id = "__implicit",
                    sourceStartMs = 0L,
                    sourceEndMs = end,
                    destStartMs = 0L,
                    destEndMs = end,
                ),
            )
        } else {
            project.clips
        }
        segments.forEach { clip ->
            val x0 = controller.timeToX(clip.destStartMs)
            val x1 = controller.timeToX(clip.destEndMs)
            if (x1 < left || x0 > w) return@forEach
            val leftC = x0.coerceAtLeast(left)
            val rightC = x1.coerceAtMost(w)
            val selected = clip.id == selectedId
            trackPaint.color = withAlpha(if (selected) primary else Color.parseColor("#8B5CF6"), if (selected) 120 else 60)
            canvas.drawRect(leftC, top, rightC, top + trackH, trackPaint)

            // filmstrip: fixed-size real frame previews, one per second, capped so
            // they always fit inside the clip span (no overlap at any zoom level)
            drawFilmstrip(canvas, clip, videoUri, x0, x1, leftC, rightC, top, trackH, wantThumbs)

            canvas.drawRect(leftC, top, rightC, top + trackH, clipBorder)
            if (selected) {
                selPaint.color = primary
                canvas.drawRect(leftC, top, rightC, top + trackH, selPaint)
            }
            // transition marker at clip start
            if (!clip.transition.isNullOrBlank() && clip.destStartMs > 0L) {
                dropPaint.color = Color.parseColor("#FFC1E3")
                canvas.drawCircle(x0, top + trackH / 2f, 5f, dropPaint)
            }
            // trim handles
            handlePaint.color = primary
            canvas.drawRect(leftC, top, leftC + 4f, top + trackH, handlePaint)
            canvas.drawRect(rightC - 4f, top, rightC, top + trackH, handlePaint)
        }
    }

    /**
     * Draws the actual video frame previews across the clip span. One cell per
     * video-second, each a fixed [thumbW] wide; the count is capped by the clip's
     * on-screen pixel span so thumbnails never overlap, even fully zoomed out.
     * Missing frames are handed back in [wantThumbs] for background decoding.
     */
    private fun drawFilmstrip(
        canvas: Canvas,
        clip: dev.phonk.editor.model.ClipSegment,
        videoUri: String?,
        x0: Float,
        x1: Float,
        leftC: Float,
        rightC: Float,
        top: Float,
        trackH: Float,
        wantThumbs: MutableList<Pair<String?, Long>>,
    ) {
        val spanPx = rightC - leftC
        if (spanPx < 2f) return
        if (videoUri.isNullOrBlank()) return

        val durMs = (clip.destEndMs - clip.destStartMs).coerceAtLeast(1L)
        val onePerSec = (durMs / 1000L).toInt().coerceAtLeast(1)
        val maxCells = (spanPx / thumbW).toInt().coerceAtLeast(1)
        val count = minOf(onePerSec, maxCells)
        val srcStart = clip.sourceStartMs
        val cellH = trackH

        canvas.save()
        canvas.clipRect(leftC, top, rightC, top + cellH)
        for (i in 0 until count) {
            val cellX = leftC + i * thumbW
            val srcMs = srcStart + (durMs * (i + 0.5f) / count).toLong()
            val cellKey = "$videoUri#$srcMs"
            val bmp = TimelineThumbnailer.peek(videoUri, srcMs)
            if (bmp != null) {
                val scale = minOf(thumbW / bmp.width, cellH / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dx = cellX + (thumbW - dw) / 2f
                val dy = top + (cellH - dh) / 2f
                thumbPaint.color = Color.BLACK
                canvas.drawRect(cellX, top, cellX + thumbW, top + cellH, thumbPaint)
                canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), thumbPaint)
            } else {
                thumbPaint.color = withAlpha(Color.parseColor("#8B5CF6"), 30)
                canvas.drawRect(cellX, top, minOf(cellX + thumbW, rightC), top + cellH, thumbPaint)
                synchronized(requestedKeys) {
                    if (cellKey !in requestedKeys) {
                        requestedKeys.add(cellKey)
                        wantThumbs.add(videoUri to srcMs)
                    }
                }
            }
        }
        canvas.restore()
    }

    /**
     * Kick off background decoding for any missing filmstrip cells collected in
     * [wantThumbs]. A generation counter drops stale invalidations while the user
     * keeps panning/zooming.
     */
    private fun requestThumbnails(want: List<Pair<String?, Long>>) {
        if (want.isEmpty()) return
        val byUri: Map<String?, List<Long>> = want.distinctBy { "${it.first}#${it.second}" }
            .groupBy({ it.first }, { it.second })
        android.util.Log.d("TMB", "request cells=${want.size} uris=${byUri.keys.count { it != null }}")
        val gen = thumbGen.incrementAndGet()
        thumbExecutor.execute {
            // Cells already decoded while this job queued are skipped inside decodeBatch.
            byUri.forEach { (uri, times) ->
                TimelineThumbnailer.decodeBatch(context, uri, times)
            }
            // Return to the main thread: clear requested marks and repaint if the
            // user hasn't zoomed/panned us into a newer generation.
            post {
                byUri.forEach { (uri, times) ->
                    times.forEach { ms ->
                        synchronized(requestedKeys) { requestedKeys.remove("$uri#$ms") }
                    }
                }
                if (thumbGen.get() == gen) invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        thumbExecutor.shutdownNow()
    }

    private fun drawBars(canvas: Canvas, left: Float, top: Float, trackH: Float, color: Int, bars: List<Pair<Long, Long>>) {
        val w = width.toFloat() - 4f
        trackPaint.color = withAlpha(color, 80)
        for ((startMs, endMs) in bars) {
            val x0 = controller.timeToX(startMs)
            val x1 = controller.timeToX(endMs)
            if (x1 < left || x0 > w) continue
            canvas.drawRect(x0.coerceAtLeast(left), top, x1.coerceAtMost(w), top + trackH - 4f, trackPaint)
        }
    }

    /**
     * Interactive overlay/text bars. The selected item is highlighted and gets
     * left/right trim handles; hidden items render dimmed; a label is drawn when
     * the bar is wide enough. While a bar is being dragged, its live window is
     * drawn instead of the committed one so the user sees the change as it
     * happens (the commit fires once on release).
     */
    private fun drawOverlayBars(canvas: Canvas, left: Float, top: Float, trackH: Float, color: Int, items: List<OverlayItem>) {
        val w = width.toFloat() - 4f
        clipBorder.color = withAlpha(color, 180)
        for (item in items) {
            val dragging = overlayDrag?.id == item.id
            val startMs = if (dragging) overlayDrag!!.liveStart else item.startMs
            val endMs = if (dragging) overlayDrag!!.liveEnd else item.endMs
            val x0 = controller.timeToX(startMs)
            val x1 = controller.timeToX(endMs)
            if (x1 < left || x0 > w) continue
            val selected = item.id == selectedOverlayId
            val alpha = when {
                !item.visible -> 28
                selected -> 150
                else -> 78
            }
            trackPaint.color = withAlpha(color, alpha)
            canvas.drawRect(x0.coerceAtLeast(left), top, x1.coerceAtMost(w), top + trackH - 4f, trackPaint)
            canvas.drawRect(x0.coerceAtLeast(left), top, x1.coerceAtMost(w), top + trackH - 4f, clipBorder)
            if (selected) {
                selPaint.color = color
                canvas.drawRect(x0.coerceAtLeast(left), top, x1.coerceAtMost(w), top + trackH - 4f, selPaint)
                handlePaint.color = color
                canvas.drawRect(x0, top, x0 + 4f, top + trackH - 4f, handlePaint)
                canvas.drawRect(x1 - 4f, top, x1, top + trackH - 4f, handlePaint)
            }
            if (x1 - x0 > 42f) {
                textPaint.color = Color.WHITE
                textPaint.textSize = 18f
                val maxChars = ((x1 - x0) / 22f).toInt().coerceIn(1, 10)
                val label = item.label.ifBlank { item.type }
                canvas.drawText(label.take(maxChars), x0 + 6f, top + trackH / 2f + 6f, textPaint)
            }
        }
    }

    /** Topmost overlay in the given row (2 = image/sticker, 3 = text) at time [t]. */
    private fun overlayAt(t: Long, row: Int): OverlayItem? {
        val items: List<OverlayItem> = when (row) {
            2 -> project.overlays
            3 -> project.textLayers
            else -> return null
        }
        return items
            .asSequence()
            .filter { it.visible && t >= it.startMs && t <= it.endMs }
            .maxByOrNull { it.zIndex }
    }

    /** Starts an overlay bar drag (move or trim) when the touch is on row 2/3. */
    private fun detectOverlay(x: Float, y: Float): OverlayDrag? {
        val trackH = (height - 16f) / tracks.size
        val row = ((y - 8f) / trackH).toInt()
        if (row != 2 && row != 3) return null
        val t = controller.xToTime(x, width.toFloat())
        val item = overlayAt(t, row) ?: return null
        val x0 = controller.timeToX(item.startMs)
        val x1 = controller.timeToX(item.endMs)
        val mode = when {
            abs(x - x0) <= trimSlopPx -> OverlayDragMode.TRIM_START
            abs(x - x1) <= trimSlopPx -> OverlayDragMode.TRIM_END
            else -> OverlayDragMode.MOVE
        }
        return OverlayDrag(item.id, mode, x, item.startMs, item.endMs)
    }

    private fun applyOverlayDrag(x: Float) {
        val d = overlayDrag ?: return
        val total = controller.projectDurationMs().coerceAtLeast(0L)
        val dx = ((x - d.downX) / width.toFloat() * controller.viewportMs).toLong()
        when (d.mode) {
            OverlayDragMode.MOVE -> {
                val dur = (d.downEnd - d.downStart).coerceAtLeast(MIN_OVERLAY_DURATION)
                val s = (d.downStart + dx).coerceIn(0L, (total - dur).coerceAtLeast(0L))
                d.liveStart = s
                d.liveEnd = (s + dur).coerceAtMost(if (total > 0) total else s + dur)
            }
            OverlayDragMode.TRIM_START -> {
                d.liveStart = (d.downStart + dx).coerceIn(0L, d.downEnd - MIN_OVERLAY_DURATION)
                d.liveEnd = d.downEnd
            }
            OverlayDragMode.TRIM_END -> {
                d.liveStart = d.downStart
                d.liveEnd = (d.downEnd + dx).coerceIn(d.downStart + MIN_OVERLAY_DURATION, if (total > 0) total else d.downStart + MIN_OVERLAY_DURATION)
            }
        }
        invalidate()
    }

    private fun commitOverlayDrag(x: Float) {
        val d = overlayDrag ?: return
        applyOverlayDrag(x)
        onSelectOverlay?.invoke(d.id)
        onSetOverlayTiming?.invoke(d.id, d.liveStart, d.liveEnd)
        overlayDrag = null
        invalidate()
    }

    private fun drawTimeRuler(canvas: Canvas, left: Float, right: Float, y: Float, color: Int) {
        val step = pickRulerStep(controller.viewportMs)
        val start = (controller.visibleRange().first / step) * step
        var t = start
        rulerPaint.color = withAlpha(color, 110)
        while (t <= controller.visibleRange().last) {
            val x = controller.timeToX(t)
            if (x in left..right) canvas.drawLine(x, y, x, y + 8f, rulerPaint)
            t += step
        }
    }

    private fun pickRulerStep(viewportMs: Long): Long = when {
        viewportMs < 5_000 -> 100L
        viewportMs < 15_000 -> 500L
        viewportMs < 60_000 -> 1_000L
        else -> 5_000L
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                downX = event.x
                downY = event.y
                dragging = true
                dragMode = detectTrim(event.x, event.y)
                overlayDrag = if (dragMode == DragMode.NONE) detectOverlay(event.x, event.y) else null
                if (dragMode == DragMode.NONE && overlayDrag == null &&
                    event.y > height * 0.5f && !scaleDetector.isInProgress
                ) {
                    seekFromX(event.x)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when {
                    !scaleDetector.isInProgress && overlayDrag != null ->
                        applyOverlayDrag(event.x)
                    !scaleDetector.isInProgress && dragMode != DragMode.NONE -> {
                        val t = controller.xToTime(event.x, width.toFloat())
                        controller.currentMs = t
                        invalidate()
                    }
                    dragging && !scaleDetector.isInProgress && dragMode == DragMode.NONE -> {
                        val dxPx = event.x - lastX
                        lastX = event.x
                        val ms = (dxPx / width.toFloat() * controller.viewportMs)
                        controller.scrollBy(-ms.toLong())
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (overlayDrag != null && !scaleDetector.isInProgress) {
                    commitOverlayDrag(event.x)
                } else if (dragMode == DragMode.TRIM_START && !scaleDetector.isInProgress) {
                    onTrimStart?.invoke(controller.xToTime(event.x, width.toFloat()))
                } else if (dragMode == DragMode.TRIM_END && !scaleDetector.isInProgress) {
                    onTrimEnd?.invoke(controller.xToTime(event.x, width.toFloat()))
                } else if (
                    dragMode == DragMode.NONE && overlayDrag == null &&
                    abs(event.x - downX) < touchSlop &&
                    abs(event.y - downY) < touchSlop
                ) {
                    handleTap(event.x, event.y)
                }
                dragging = false
                dragMode = DragMode.NONE
                overlayDrag = null
            }
        }
        return true
    }

    private fun detectTrim(x: Float, y: Float): DragMode {
        val selectedId = project.selectedClipId ?: return DragMode.NONE
        // only the video track row (top ~20% of height)
        if (y > height * 0.22f) return DragMode.NONE
        val clip = project.clips.firstOrNull { it.id == selectedId } ?: return DragMode.NONE
        val x0 = controller.timeToX(clip.destStartMs)
        val x1 = controller.timeToX(clip.destEndMs)
        if (abs(x - x0) <= trimSlopPx) return DragMode.TRIM_START
        if (abs(x - x1) <= trimSlopPx) return DragMode.TRIM_END
        return DragMode.NONE
    }

    private fun handleTap(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val isDoubleTap = now - lastTapTime < 350L && abs(x - lastTapX) < 40f
        lastTapTime = now
        lastTapX = x

        val t = controller.xToTime(x, width.toFloat())
        // video track row (top 20%) selects the clip under the tap
        if (y < height * 0.22f) {
            val clip = project.clips.firstOrNull { t in it.destStartMs until it.destEndMs }
            if (clip != null) {
                onSelectClip?.invoke(clip.id)
                if (isDoubleTap) {
                    onClipSplit?.invoke(t)
                }
                return
            }
        }
        // overlay (2) / text (3) tracks select the topmost bar under the tap
        val trackH = (height - 16f) / tracks.size
        val row = ((y - 8f) / trackH).toInt()
        if (row == 2 || row == 3) {
            val item = overlayAt(t, row)
            if (item != null) {
                onSelectOverlay?.invoke(item.id)
                return
            }
        }
        if (isDoubleTap) {
            onClipSplit?.invoke(t)
        }
    }

    private fun seekFromX(x: Float) {
        controller.seekTo(x, width.toFloat())
        onSeekTo?.invoke(controller.currentMs)
        invalidate()
    }

    fun refresh() = invalidate()
}
