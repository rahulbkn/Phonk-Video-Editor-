package dev.phonk.editor.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
 * Pan/zoom multi-track editing timeline.
 * Tracks (top→bottom): Video (filmstrip), Audio (real waveform), Overlay, Text,
 * Effects, Audio FX. All time↔pixel conversions flow through [TimelineController]
 * so every element (clips, bars, keyframes, markers, ruler, playhead) stays
 * aligned on one shared time axis.
 *
 * Gesture priority (per the editor spec):
 *   1. selected clip left trim handle
 *   2. selected clip right trim handle
 *   3. selected overlay/text bar (trim handles, then body move)
 *   4. playhead
 *   5. empty-timeline seek
 *   6. ruler drag = horizontal scroll
 * The first match owns the whole gesture until release.
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
        TrackDef("Video", context.getColor(R.color.primary)),
        TrackDef("Audio", context.getColor(R.color.primary)),
        TrackDef("Overlay", context.getColor(R.color.primary)),
        TrackDef("Text", context.getColor(R.color.primary)),
        TrackDef("Effects", context.getColor(R.color.accent)),
        TrackDef("Audio FX", context.getColor(R.color.border_default)),
    )

    private val density get() = resources.displayMetrics.density

    // Ruler occupies the top of the view; tracks render below it.
    private val rulerH = 24f
    private val trackAreaTop = rulerH + 4f
    private val trackLabelWidth = 68f
    private val labelZone get() = trackLabelWidth + 4f

    private fun trackH(): Float = (height - trackAreaTop - 4f) / tracks.size
    private fun trackTop(i: Int): Float = trackAreaTop + i * trackH()
    private fun rowAt(y: Float): Int = if (y < trackAreaTop) -1
        else ((y - trackAreaTop) / trackH()).toInt().coerceIn(0, tracks.size - 1)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val beatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f * density; isFakeBoldText = true }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handlePath = Path()

    /**
     * Filmstrip thumbnails on the video track. Each cell is a fixed-width real
     * frame preview; the cell count adapts to the zoom level so thumbnails never
     * overlap (cells are capped to the clip's pixel span and one-per-second).
     */
    private val thumbW = 28f * density
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

    // Density-aware touch slop (Android's real guidance) instead of raw pixels.
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val trimSlopPx get() = 10f * density
    private val playSlopPx get() = 18f * density

    private enum class Gesture { NONE, CLIP_TRIM_START, CLIP_TRIM_END, OVERLAY_TRIM_START, OVERLAY_TRIM_END, OVERLAY_MOVE, PLAYHEAD, SEEK, SCROLL }

    private var gesture = Gesture.NONE

    private var lastTapTime = 0L
    private var lastTapX = 0f

    /** Minimum overlay window width when trimming on the timeline (matches the VM). */
    private companion object {
        const val MIN_DURATION = 100L
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

    /** Live state while the selected clip is being trimmed (both handles). */
    private var clipTrimId: String? = null
    private var clipTrimLiveStart = 0L
    private var clipTrimLiveEnd = 0L

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
        val tH = trackH()

        // 1. Track backgrounds (subtle dark) + labels.
        tracks.forEachIndexed { i, track ->
            val top = trackTop(i)
            trackPaint.color = context.getColor(R.color.surface_track)
            canvas.drawRect(labelZone, top, w - 4f, top + tH - 2f, trackPaint)
            trackPaint.color = context.getColor(R.color.border_default)
            canvas.drawLine(labelZone, top + tH - 2f, w - 4f, top + tH - 2f, trackPaint)
            labelPaint.color = context.getColor(R.color.text_ruler)
            canvas.drawText(track.label, 6f, top + tH / 2f + 6f, labelPaint)
        }

        // 2. Real (time-mapped) waveform on the audio track.
        drawWaveform(canvas, labelZone, trackTop(1), w - 4f, tH - 4f)

        // 3. Video clips + filmstrip (live trim window when trimming).
        val wantThumbs = mutableListOf<Pair<String?, Long>>()
        drawClips(canvas, labelZone, trackTop(0), tH - 4f, primary, wantThumbs)
        requestThumbnails(wantThumbs)

        // 4. Overlay + text bars (with their own keyframes).
        drawOverlayBars(canvas, labelZone, trackTop(2), tH - 4f, context.getColor(R.color.primary), project.overlays)
        drawOverlayBars(canvas, labelZone, trackTop(3), tH - 4f, context.getColor(R.color.primary), project.textLayers)

        // 5. Effect bars on the effects track.
        drawBars(canvas, labelZone, trackTop(4), tH - 4f, context.getColor(R.color.accent),
            project.clips.filter { it.effect != dev.phonk.editor.model.EffectKind.NONE }
                .map { it.destStartMs to it.destEndMs })

        // 6. Beat ticks, drop markers, grade keyframes.
        drawBeatsAndDrops(canvas, labelZone, w, h, tH)
        drawGradeKeyframes(canvas, labelZone, trackTop(4), tH - 4f)

        // 7. Time ruler.
        drawTimeRuler(canvas, labelZone, w, 4f, 18f, context.getColor(R.color.text_ruler))

        // 8. Playhead: thin needle + compact handle (drawn above tracks but
        //    below the trim handles so a nearby handle stays visible).
        drawPlayhead(canvas, h)

        // 9. Selection trim handles on top so the playhead can never hide them.
        drawTrimHandles(canvas, trackTop(0), tH - 4f, primary)
        drawOverlayTrimHandles(canvas, trackTop(2), tH - 4f, context.getColor(R.color.primary), project.overlays)
        drawOverlayTrimHandles(canvas, trackTop(3), tH - 4f, context.getColor(R.color.primary), project.textLayers)

        // 10. Overlay/text titles re-drawn last so the playhead needle (and
        //     handle) never obscures a layer's label.
        drawOverlayLabels(canvas, labelZone, trackTop(2), tH - 4f, project.overlays)
        drawOverlayLabels(canvas, labelZone, trackTop(3), tH - 4f, project.textLayers)
    }

    private fun drawPlayhead(canvas: Canvas, h: Float) {
        val playX = controller.timeToX(controller.currentMs)
        playPaint.strokeWidth = 2f * density
        playPaint.color = context.getColor(R.color.overlay_handle)
        canvas.drawLine(playX, 4f, playX, h - 8f, playPaint)
        // small diamond handle at the top
        val hs = 5f * density
        val topY = 5f
        handlePath.reset()
        handlePath.moveTo(playX, topY)
        handlePath.lineTo(playX - hs, topY + hs * 1.7f)
        handlePath.lineTo(playX + hs, topY + hs * 1.7f)
        handlePath.close()
        handlePaint.color = context.getColor(R.color.primary)
        canvas.drawPath(handlePath, handlePaint)
    }

    /** Bright compact handles for the selected video clip (always on top). */
    private fun drawTrimHandles(canvas: Canvas, top: Float, height: Float, primary: Int) {
        val clip = selectedClip() ?: return
        val x0 = controller.timeToX(if (clipTrimId == clip.id) clipTrimLiveStart else clip.destStartMs)
        val x1 = controller.timeToX(if (clipTrimId == clip.id) clipTrimLiveEnd else clip.destEndMs)
        val hw = 5f * density
        handlePaint.color = context.getColor(R.color.overlay_handle)
        canvas.drawRect(x0 - hw, top, x0, top + height, handlePaint)
        canvas.drawRect(x1, top, x1 + hw, top + height, handlePaint)
        clipBorder.color = primary
        canvas.drawRect(x0 - hw, top, x1 + hw, top + height, clipBorder)
    }

    /** Trim handles for the selected overlay/text bar. */
    private fun drawOverlayTrimHandles(canvas: Canvas, top: Float, height: Float, color: Int, items: List<OverlayItem>) {
        val id = selectedOverlayId ?: return
        val item = items.firstOrNull { it.id == id } ?: return
        val x0 = controller.timeToX(if (overlayDrag?.id == id) overlayDrag!!.liveStart else item.startMs)
        val x1 = controller.timeToX(if (overlayDrag?.id == id) overlayDrag!!.liveEnd else item.endMs)
        val hw = 5f * density
        handlePaint.color = context.getColor(R.color.overlay_handle)
        canvas.drawRect(x0 - hw, top, x0, top + height, handlePaint)
        canvas.drawRect(x1, top, x1 + hw, top + height, handlePaint)
        clipBorder.color = color
        canvas.drawRect(x0 - hw, top, x1 + hw, top + height, clipBorder)
    }

    private fun drawBeatsAndDrops(canvas: Canvas, left: Float, right: Float, h: Float, tH: Float) {
        beatPaint.color = withAlpha(context.getColor(R.color.primary), 180)
        project.beats.forEach { beat ->
            val x = controller.timeToX(beat.timestampMs.roundToLong())
            if (x in left..right) {
                val amp = beat.confidence.coerceIn(0f, 1f)
                val top = 8f
                val bottom = h - 16f
                val tickH = (bottom - top) * (0.3f + 0.7f * amp)
                canvas.drawLine(x, bottom - tickH, x, bottom, beatPaint)
            }
        }
        // drop markers as diamonds on the audio row
        dropPaint.color = context.getColor(R.color.accent)
        project.drops.forEach { drop ->
            val x = controller.timeToX(drop.timestampMs.roundToLong())
            if (x in left..right) {
                val cy = trackTop(1) + tH / 2f
                drawDiamond(canvas, x, cy, 6f * density, dropPaint)
            }
        }
    }

    /** Grade automation keyframes (real project timestamps) on the effects row. */
    private fun drawGradeKeyframes(canvas: Canvas, left: Float, top: Float, height: Float) {
        keyPaint.color = context.getColor(R.color.keyframe)
        project.gradeKeyframes.forEach { k ->
            val x = controller.timeToX(k.atMs)
            if (x in left..width.toFloat()) {
                drawDiamond(canvas, x, top + height / 2f, 5f * density, keyPaint)
            }
        }
    }

    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, half: Float, paint: Paint) {
        handlePath.reset()
        handlePath.moveTo(cx, cy - half)
        handlePath.lineTo(cx + half, cy)
        handlePath.lineTo(cx, cy + half)
        handlePath.lineTo(cx - half, cy)
        handlePath.close()
        canvas.drawPath(handlePath, paint)
    }

    /**
     * Real audio energy curve drawn on the SAME time axis as the clips: each
     * sample is mapped through source→destination→pixel so the waveform scrolls,
     * zooms and aligns with the video exactly.
     */
    private fun drawWaveform(canvas: Canvas, left: Float, top: Float, right: Float, height: Float) {
        val curve = project.analysisEnergyCurve()
        if (curve.isEmpty()) return
        val total = project.videoDurationMs.coerceAtLeast(1L)
        val sd = total.toDouble() / curve.size
        val mid = top + height / 2f
        val halfH = height * 0.42f
        wavePaint.color = withAlpha(context.getColor(R.color.accent), 90)
        for (i in curve.indices) {
            val dest = sourceToDest((i * sd).toLong())
            val x = controller.timeToX(dest)
            if (x < left || x > right) continue
            val amp = maxOf(0f, minOf(1f, curve[i]))
            val barH = (halfH * amp).coerceAtLeast(1f)
            canvas.drawRect(x - 1f, mid - barH, x + 1f, mid + barH, wavePaint)
        }
    }

    private fun sourceToDest(srcMs: Long): Long {
        val clips = project.clips
        if (clips.isEmpty()) return srcMs.coerceIn(0L, controller.totalMs)
        val clip = clips.firstOrNull { srcMs in it.sourceStartMs until it.sourceEndMs }
            ?: clips.lastOrNull { srcMs >= it.sourceStartMs } ?: return srcMs
        val srcDur = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val destDur = (clip.destEndMs - clip.destStartMs).coerceAtLeast(0L)
        val ratio = destDur.toDouble() / srcDur
        return (clip.destStartMs + ((srcMs - clip.sourceStartMs) * ratio).toLong())
            .coerceIn(clip.destStartMs, clip.destEndMs)
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
            val trimming = clipTrimId == clip.id
            val d0 = if (trimming) clipTrimLiveStart else clip.destStartMs
            val d1 = if (trimming) clipTrimLiveEnd else clip.destEndMs
            val x0 = controller.timeToX(d0)
            val x1 = controller.timeToX(d1)
            if (x1 < left || x0 > w) return@forEach
            val leftC = x0.coerceAtLeast(left)
            val rightC = x1.coerceAtMost(w)
            val selected = clip.id == selectedId
            trackPaint.color = withAlpha(if (selected) primary else context.getColor(R.color.primary), if (selected) 160 else 100)
            canvas.drawRect(leftC, top, rightC, top + trackH, trackPaint)

            drawFilmstrip(canvas, clip, videoUri, x0, x1, leftC, rightC, top, trackH, wantThumbs)

            canvas.drawRect(leftC, top, rightC, top + trackH, clipBorder)
            if (selected) {
                selPaint.color = primary
                canvas.drawRect(leftC, top, rightC, top + trackH, selPaint)
            }
            if (!clip.transition.isNullOrBlank() && d0 > 0L) {
                dropPaint.color = context.getColor(R.color.accent)
                canvas.drawCircle(x0, top + trackH / 2f, 5f, dropPaint)
            }
        }
    }

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
                thumbPaint.color = context.getColor(R.color.preview_bg)
                canvas.drawRect(cellX, top, cellX + thumbW, top + cellH, thumbPaint)
                canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), thumbPaint)
            } else {
                thumbPaint.color = withAlpha(context.getColor(R.color.primary), 30)
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

    private fun requestThumbnails(want: List<Pair<String?, Long>>) {
        if (want.isEmpty()) return
        val byUri: Map<String?, List<Long>> = want.distinctBy { "${it.first}#${it.second}" }
            .groupBy({ it.first }, { it.second })
        val gen = thumbGen.incrementAndGet()
        thumbExecutor.execute {
            byUri.forEach { (uri, times) ->
                TimelineThumbnailer.decodeBatch(context, uri, times)
            }
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
            }
            // item keyframes at their real project times
            keyPaint.color = context.getColor(R.color.keyframe)
            item.keyframes.forEach { k ->
                val kx = controller.timeToX(k.atMs)
                if (kx >= x0 && kx <= x1) {
                    drawDiamond(canvas, kx, top + trackH / 2f, 4f * density, keyPaint)
                }
            }
        }
    }

    /** Titles for overlay/text bars, drawn after the playhead so the needle
     * never covers them. Mirrors the geometry used by [drawOverlayBars]. */
    private fun drawOverlayLabels(canvas: Canvas, left: Float, top: Float, trackH: Float, items: List<OverlayItem>) {
        val w = width.toFloat() - 4f
        for (item in items) {
            val dragging = overlayDrag?.id == item.id
            val startMs = if (dragging) overlayDrag!!.liveStart else item.startMs
            val endMs = if (dragging) overlayDrag!!.liveEnd else item.endMs
            val x0 = controller.timeToX(startMs)
            val x1 = controller.timeToX(endMs)
            if (x1 < left || x0 > w) continue
            if (x1 - x0 <= 42f) continue
            textPaint.color = context.getColor(R.color.overlay_handle)
            textPaint.textSize = 18f * density
            val maxChars = ((x1 - x0) / 22f).toInt().coerceIn(1, 10)
            val label = item.label.ifBlank { item.type }
            canvas.drawText(label.take(maxChars), x0 + 6f, top + trackH / 2f + 6f, textPaint)
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

    private fun overlayById(id: String): OverlayItem? =
        project.textLayers.firstOrNull { it.id == id } ?: project.overlays.firstOrNull { it.id == id }

    private fun selectedClip(): dev.phonk.editor.model.ClipSegment? =
        project.clips.firstOrNull { it.id == project.selectedClipId }

    /**
     * Full gesture detection with the spec priority: clip trim handles, then
     * selected overlay bar (trim handles → body move), then playhead, then seek.
     * The ruler row maps to horizontal scroll.
     */
    private fun detectGesture(x: Float, y: Float): Gesture {
        if (y < trackAreaTop) {
            // Ruler zone: the playhead handle is drawn inside it, so grabbing
            // near the playhead seeks; anywhere else on the ruler scrolls.
            if (abs(x - controller.timeToX(controller.currentMs)) <= playSlopPx) return Gesture.PLAYHEAD
            return Gesture.SCROLL
        }
        val row = rowAt(y)
        val nearPlayhead = abs(x - controller.timeToX(controller.currentMs)) <= playSlopPx
        if (row == 0) {
            val clip = selectedClip()
            if (clip != null) {
                val x0 = controller.timeToX(clip.destStartMs)
                val x1 = controller.timeToX(clip.destEndMs)
                if (abs(x - x0) <= trimSlopPx) return Gesture.CLIP_TRIM_START
                if (abs(x - x1) <= trimSlopPx) return Gesture.CLIP_TRIM_END
            }
        } else if (row == 2 || row == 3) {
            val d = detectOverlay(x, y, row)
            if (d != null) {
                overlayDrag = d
                return when (d.mode) {
                    OverlayDragMode.TRIM_START -> Gesture.OVERLAY_TRIM_START
                    OverlayDragMode.TRIM_END -> Gesture.OVERLAY_TRIM_END
                    OverlayDragMode.MOVE -> Gesture.OVERLAY_MOVE
                }
            }
        }
        return if (nearPlayhead) Gesture.PLAYHEAD else Gesture.SEEK
    }

    /** Starts an overlay bar drag (move or trim) when the touch is on row 2/3. */
    private fun detectOverlay(x: Float, y: Float, row: Int): OverlayDrag? {
        val t = controller.xToTime(x, width.toFloat())
        // selected item's handles have priority over every other item
        val selId = selectedOverlayId
        if (selId != null) {
            val sel = overlayById(selId)
            if (sel != null && row == if (sel.type == "Text") 3 else 2) {
                val x0 = controller.timeToX(sel.startMs)
                val x1 = controller.timeToX(sel.endMs)
                val mode = when {
                    abs(x - x0) <= trimSlopPx -> OverlayDragMode.TRIM_START
                    abs(x - x1) <= trimSlopPx -> OverlayDragMode.TRIM_END
                    t >= sel.startMs && t <= sel.endMs -> OverlayDragMode.MOVE
                    else -> null
                }
                if (mode != null) {
                    onSelectOverlay?.invoke(selId)
                    return OverlayDrag(sel.id, mode, x, sel.startMs, sel.endMs)
                }
            }
        }
        val item = overlayAt(t, row) ?: return null
        onSelectOverlay?.invoke(item.id)
        return OverlayDrag(item.id, OverlayDragMode.MOVE, x, item.startMs, item.endMs)
    }

    private fun applyOverlayDrag(x: Float) {
        val d = overlayDrag ?: return
        val total = controller.projectDurationMs().coerceAtLeast(0L)
        val dx = ((x - d.downX) / width.toFloat() * controller.viewportMs).toLong()
        when (d.mode) {
            OverlayDragMode.MOVE -> {
                val dur = (d.downEnd - d.downStart).coerceAtLeast(MIN_DURATION)
                val s = (d.downStart + dx).coerceIn(0L, (total - dur).coerceAtLeast(0L))
                d.liveStart = s
                d.liveEnd = (s + dur).coerceAtMost(if (total > 0) total else s + dur)
            }
            OverlayDragMode.TRIM_START -> {
                d.liveStart = (d.downStart + dx).coerceIn(0L, d.downEnd - MIN_DURATION)
                d.liveEnd = d.downEnd
            }
            OverlayDragMode.TRIM_END -> {
                d.liveStart = d.downStart
                d.liveEnd = (d.downEnd + dx).coerceIn(d.downStart + MIN_DURATION, if (total > 0) total else d.downStart + MIN_DURATION)
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

    private fun drawTimeRuler(canvas: Canvas, left: Float, right: Float, y: Float, textY: Float, color: Int) {
        val step = pickRulerStep(controller.viewportMs)
        val start = (controller.visibleRange().first / step) * step
        var t = start
        rulerPaint.color = withAlpha(color, 160)
        labelPaint.textSize = 16f * density
        while (t <= controller.visibleRange().last) {
            val x = controller.timeToX(t)
            if (x in left..right) {
                canvas.drawLine(x, y, x, y + 10f, rulerPaint)
                val label = formatTimeTick(t)
                labelPaint.color = withAlpha(color, 200)
                canvas.drawText(label, x + 2f, textY, labelPaint)
            }
            t += step
        }
    }

    private fun formatTimeTick(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min > 0) "%d:%02d".format(min, sec) else "0:%02d".format(sec)
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
                gesture = detectGesture(event.x, event.y)
                when (gesture) {
                    Gesture.CLIP_TRIM_START, Gesture.CLIP_TRIM_END -> {
                        val clip = selectedClip() ?: return true
                        clipTrimId = clip.id
                        clipTrimLiveStart = clip.destStartMs
                        clipTrimLiveEnd = clip.destEndMs
                    }
                    Gesture.PLAYHEAD, Gesture.SEEK -> seekFromX(event.x)
                    Gesture.OVERLAY_TRIM_START, Gesture.OVERLAY_TRIM_END, Gesture.OVERLAY_MOVE ->
                        applyOverlayDrag(event.x)
                    else -> Unit
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                when (gesture) {
                    Gesture.SCROLL -> {
                        val dxPx = event.x - lastX
                        lastX = event.x
                        val ms = (dxPx / width.toFloat() * controller.viewportMs).toLong()
                        controller.scrollBy(-ms)
                        invalidate()
                    }
                    Gesture.CLIP_TRIM_START -> {
                        val t = controller.xToTime(event.x, width.toFloat())
                        clipTrimLiveStart = t.coerceIn(0L, clipTrimLiveEnd - MIN_DURATION)
                        seekToClipEdge(clipTrimLiveStart)
                    }
                    Gesture.CLIP_TRIM_END -> {
                        val t = controller.xToTime(event.x, width.toFloat())
                        clipTrimLiveEnd = t.coerceIn(clipTrimLiveStart + MIN_DURATION, controller.totalMs)
                        seekToClipEdge(clipTrimLiveEnd)
                    }
                    Gesture.OVERLAY_TRIM_START, Gesture.OVERLAY_TRIM_END, Gesture.OVERLAY_MOVE ->
                        applyOverlayDrag(event.x)
                    Gesture.PLAYHEAD, Gesture.SEEK -> seekFromX(event.x)
                    else -> Unit
                }
            }
            MotionEvent.ACTION_UP -> {
                val tapped = abs(event.x - downX) < touchSlop && abs(event.y - downY) < touchSlop && !scaleDetector.isInProgress
                when (gesture) {
                    Gesture.CLIP_TRIM_START -> { onTrimStart?.invoke(clipTrimLiveStart); onSeekTo?.invoke(clipTrimLiveStart) }
                    Gesture.CLIP_TRIM_END -> { onTrimEnd?.invoke(clipTrimLiveEnd); onSeekTo?.invoke(clipTrimLiveEnd) }
                    Gesture.OVERLAY_TRIM_START, Gesture.OVERLAY_TRIM_END, Gesture.OVERLAY_MOVE ->
                        commitOverlayDrag(event.x)
                    Gesture.PLAYHEAD, Gesture.SEEK -> if (tapped) handleTap(event.x, event.y)
                    else -> if (tapped) handleTap(event.x, event.y)
                }
                resetGesture()
            }
            MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return true
    }

    /** Seeks the player/preview to a trim edge so the frame updates live. */
    private fun seekToClipEdge(edgeMs: Long) {
        controller.currentMs = edgeMs.coerceIn(0L, controller.totalMs)
        onSeekTo?.invoke(edgeMs.coerceIn(0L, controller.totalMs))
        invalidate()
    }

    private fun resetGesture() {
        gesture = Gesture.NONE
        overlayDrag = null
        clipTrimId = null
    }

    private fun handleTap(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val isDoubleTap = now - lastTapTime < 350L && abs(x - lastTapX) < 40f
        lastTapTime = now
        lastTapX = x

        val t = controller.xToTime(x, width.toFloat())
        val row = rowAt(y)
        if (row == 0) {
            val clip = project.clips.firstOrNull { t in it.destStartMs until it.destEndMs }
            if (clip != null) {
                onSelectClip?.invoke(clip.id)
                if (isDoubleTap) {
                    onClipSplit?.invoke(t)
                }
                return
            }
        }
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
