package dev.phonk.editor.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import dev.phonk.editor.R
import dev.phonk.editor.model.PhonkProject
import kotlin.math.abs
import kotlin.math.roundToLong

private data class TrackDef(val label: String, val color: Int)

private const val TRIM_SLOP = 14f

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

    private val scaleDetector: ScaleGestureDetector
    private var lastX = 0f
    private var dragging = false
    private val trackLabelWidth = 28f

    private enum class DragMode { NONE, TRIM_START, TRIM_END }
    private var dragMode = DragMode.NONE
    private var lastTapTime = 0L
    private var lastTapX = 0f

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
        drawClips(canvas, labelZone, 8f + 0 * trackH, trackH - 4f, primary)

        // overlay bars on overlay track (index 2)
        drawBars(canvas, labelZone, 8f + 2 * trackH, trackH - 4f, Color.parseColor("#FF7EB6"),
            project.overlays.map { it.startMs to it.endMs })
        // text bars on text track (index 3)
        drawBars(canvas, labelZone, 8f + 3 * trackH, trackH - 4f, Color.parseColor("#FFC1E3"),
            project.textLayers.map { it.startMs to it.endMs })
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

    private fun drawClips(canvas: Canvas, left: Float, top: Float, trackH: Float, primary: Int) {
        val w = width.toFloat() - 4f
        clipBorder.color = withAlpha(primary, 180)
        val selectedId = project.selectedClipId
        project.clips.forEach { clip ->
            val x0 = controller.timeToX(clip.destStartMs)
            val x1 = controller.timeToX(clip.destEndMs)
            if (x1 < left || x0 > w) return@forEach
            val leftC = x0.coerceAtLeast(left)
            val rightC = x1.coerceAtMost(w)
            val selected = clip.id == selectedId
            trackPaint.color = withAlpha(if (selected) primary else Color.parseColor("#8B5CF6"), if (selected) 120 else 60)
            canvas.drawRect(leftC, top, rightC, top + trackH, trackPaint)
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
                dragging = true
                dragMode = detectTrim(event.x, event.y)
                if (dragMode == DragMode.NONE && event.y > height * 0.5f && !scaleDetector.isInProgress) seekFromX(event.x)
                if (dragMode == DragMode.NONE && event.y < height * 0.5f && event.y < height / 5f) {
                    // tap zone on the video track row
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && dragMode != DragMode.NONE) {
                    val t = controller.xToTime(event.x, width.toFloat())
                    controller.currentMs = t
                    invalidate()
                } else if (dragging && !scaleDetector.isInProgress && dragMode == DragMode.NONE) {
                    val dxPx = event.x - lastX
                    lastX = event.x
                    val ms = (dxPx / width.toFloat() * controller.viewportMs)
                    controller.scrollBy(-ms.toLong())
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode == DragMode.TRIM_START && !scaleDetector.isInProgress) {
                    onTrimStart?.invoke(controller.xToTime(event.x, width.toFloat()))
                } else if (dragMode == DragMode.TRIM_END && !scaleDetector.isInProgress) {
                    onTrimEnd?.invoke(controller.xToTime(event.x, width.toFloat()))
                } else if (dragMode == DragMode.NONE && abs(event.x - lastX) < 8f) {
                    handleTap(event.x, event.y)
                }
                dragging = false
                dragMode = DragMode.NONE
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
        if (abs(x - x0) <= TRIM_SLOP) return DragMode.TRIM_START
        if (abs(x - x1) <= TRIM_SLOP) return DragMode.TRIM_END
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
