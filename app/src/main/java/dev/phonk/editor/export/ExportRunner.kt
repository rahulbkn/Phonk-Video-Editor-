package dev.phonk.editor.export

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Environment
import android.provider.MediaStore
import dev.phonk.editor.editor.CutPattern
import dev.phonk.editor.editor.CutPlanner
import dev.phonk.editor.editor.EffectScheduler
import dev.phonk.editor.ffmpeg.EffectSpec
import dev.phonk.editor.ffmpeg.FFmpegEngine
import dev.phonk.editor.ffmpeg.FfmpegRenderer
import dev.phonk.editor.ffmpeg.OverlayRender
import dev.phonk.editor.ffmpeg.ProcessFFmpegEngine
import dev.phonk.editor.ffmpeg.RenderCancellable
import dev.phonk.editor.ffmpeg.RenderState
import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.DropType
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.PhonkProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float, val stage: String) : ExportState
    data class Done(val uri: Uri, val path: String) : ExportState
    data class Failed(val message: String) : ExportState
}

/**
 * Export pipeline: validate inputs -> generate clips from analysis ->
 * render with FFmpeg -> publish through MediaStore so the video lands in the
 * gallery without needing storage WRITE permission.
 */
private const val TAG = "ExportRunner"

/** A rasterized overlay source file plus its pixel size at scale = 1.0. */
private data class Rasterized(val path: String, val width: Int, val height: Int)

class ExportRunner(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    @Volatile
    var cancelRequested = false

    /**
     * The renderer of the export currently in flight, if any. Set as soon as a
     * renderer is created and cleared when that export finishes, so a cancel
     * can signal the live ffmpeg process instead of only flagging a later
     * post-render check.
     */
    @Volatile
    internal var activeRenderer: RenderCancellable? = null

    /** Clears any previous result so the dialog starts fresh. */
    fun reset() {
        _state.value = ExportState.Idle
    }

    /** Requests cancellation of an in-flight export. */
    fun cancel() {
        cancelRequested = true
        activeRenderer?.cancel()
    }

    /** Reports a failure from outside the export pipeline (e.g. missing inputs). */
    fun setFailed(message: String) {
        _state.value = ExportState.Failed(message)
    }

    fun export(
        project: PhonkProject,
        analysis: AnalysisResult,
        pattern: CutPattern,
        config: ExportConfig,
    ) {
        cancelRequested = false
        Log.i(TAG, "export start clips=" + project.clips.size + " analysisBeats=" + analysis.beats.size)
        scope.launch {
            _state.value = ExportState.Running(0f, "planning")
            runCatching {
                val videoUri = project.videoUri ?: error("Video not selected")
                if (cancelRequested) return@runCatching

                val clips: List<ClipSegment> =
                    project.clips.ifEmpty {
                        CutPlanner.planPattern(
                            analysis, pattern,
                            maxSourceMs = project.videoDurationMs.takeIf { it > 0 },
                        ).clips
                    }
                if (clips.isEmpty()) error("No clips to render from the beat grid")

                val effective = project.copy(clips = clips)

                val effectSpecs = EffectScheduler.schedule(clips, analysis.beats)
                    .map { EffectSpec(it.atDestMs, it.durationMs, it.kind, it.amount) }

                // Beat sync is exported as short brightness pulses on each beat
                // (proportional to the strength slider), the only component of
                // the preview's beat boost that is representable in this build.
                val beatPulses = if (project.beatSync) {
                    buildList {
                        for (b in analysis.beats) {
                            if (b.confidence > 0f) {
                                add(
                                    EffectSpec(
                                        sourceToDestForExport(clips, b.timestampMs.toLong()),
                                        90,
                                        EffectKind.BRIGHTNESS,
                                        project.beatSyncStrength * 0.4f,
                                    )
                                )
                            }
                        }
                        for (d in analysis.drops) {
                            if (d.strength > 0f) {
                                add(
                                    EffectSpec(
                                        sourceToDestForExport(clips, d.timestampMs.toLong()),
                                        180,
                                        EffectKind.BRIGHTNESS,
                                        project.beatSyncStrength * 0.55f,
                                    )
                                )
                            }
                        }
                    }.toList()
                } else {
                    emptyList()
                }
                val allEffects = effectSpecs + beatPulses

                // stage 1: resolve engine
                val engine = ffmpegEngine()
                if (!engine.available) {
                    Log.e(TAG, "export FAILED: ffmpeg engine unavailable")
                    _state.value = ExportState.Failed("FFmpeg engine unavailable on this device")
                    return@runCatching
                }
                val renderer = FfmpegRenderer(engine)
                activeRenderer = renderer
                try {
                    // stage 2: render to internal cache
                    val outFile = File(context.cacheDir, "phonk_export_${System.currentTimeMillis()}.mp4")
                    val overlayRenders = buildOverlayRenders(project, config)
                    // A cancel that arrived before the renderer was created must
                    // still prevent the render from ever starting.
                    if (cancelRequested) {
                        outFile.delete()
                        _state.value = ExportState.Idle
                        return@runCatching
                    }
                    val completed = renderer.render(
                        input = inputPath(videoUri),
                        output = outFile.absolutePath,
                        segments = clips,
                        config = config,
                        hasAudio = true,
                        effects = allEffects,
                        hwEncode = if (config.hardwareAccel) "h264" else null,
                        videoDurationMs = clips.map { it.destEndMs }.maxOrNull() ?: 0L,
                        onProgress = { p ->
                            _state.value = ExportState.Running(p, "rendering")
                        },
                        colorGrade = project.colorGrade(),
                        overlayRenders = overlayRenders,
                        transitionDurationMs = project.transitionDurationMs,
                        keyframes = project.gradeKeyframes,
                        keyframesEnabled = project.gradeKeyframesEnabled,
                    )
                    if (cancelRequested) {
                        outFile.delete()
                        _state.value = ExportState.Idle
                        return@runCatching
                    }
                    when (completed) {
                        is RenderState.Failed -> { Log.e(TAG, "export FAILED: " + completed.message); _state.value = ExportState.Failed(completed.message) }
                        is RenderState.Done -> {
                            Log.i(TAG, "export render done")
                            // A completion racing a late cancel must not
                            // resurrect a Done state.
                            if (cancelRequested) {
                                outFile.delete()
                                _state.value = ExportState.Idle
                                return@runCatching
                            }
                            _state.value = ExportState.Running(0.9f, "saving")
                            val uri = saveToGallery(context, outFile, config)
                            _state.value = ExportState.Done(uri, outFile.absolutePath)
                        }
                        is RenderState.Running, is RenderState.Idle -> Unit
                    }
                } finally {
                    // Only clear our own reference; a newer export may already
                    // have registered a fresh renderer.
                    if (activeRenderer === renderer) activeRenderer = null
                }
            }.onFailure { t ->
                Log.e(TAG, "export FAILED", t)
                _state.value = ExportState.Failed(t.message ?: "Export failed")
            }
        }
    }

    private fun inputPath(raw: String?): String {
        val uri = Uri.parse(raw)
        val copy = File(context.cacheDir, "export_input_${System.currentTimeMillis()}.mp4")
        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
            copy.outputStream().use { out -> input.copyTo(out) }
            true
        } ?: false
        if (!ok || copy.length() == 0L) error("Cannot read the source video (permission lost?)")
        return copy.absolutePath
    }

    /**
     * Resolves every visible overlay into a render ready for the ffmpeg graph.
     * Text layers and label-only shapes (sticker/emoji) are rasterized to a
     * transparent PNG at the preview's base sizing; image overlays are copied
     * from their content URI and given the preview's square base box. Anything
     * hidden, outside the timeline, or missing its source is skipped — the
     * export must match the preview exactly.
     */
    private fun buildOverlayRenders(project: PhonkProject, config: ExportConfig): List<OverlayRender> {
        val w = config.resolution.width
        val h = config.resolution.height
        val refW = 1080
        val minSide = minOf(w, h)
        val imageFiles = copyOverlayFiles(context, project)
        val out = ArrayList<OverlayRender>()
        for (t in project.textLayers) {
            if (!t.visible || t.endMs <= t.startMs) continue
            val raster = rasterizeText(t, w, refW) ?: continue
            out += OverlayRender(
                id = t.id, file = raster.path, baseW = raster.width, baseH = raster.height,
                startMs = t.startMs, endMs = t.endMs,
                x = t.x, y = t.y, scaleX = t.scaleX, scaleY = t.scaleY,
                rotation = t.rotation, opacity = t.opacity, zIndex = t.zIndex,
                keyframes = t.keyframes,
            )
        }
        for (ov in project.overlays) {
            if (!ov.visible || ov.endMs <= ov.startMs) continue
            val file: String
            val baseW: Int
            val baseH: Int
            val fileForOv = ov.uri?.let { imageFiles[ov.id] }
            if (fileForOv != null) {
                val side = (minSide * 0.4f).roundToInt().coerceAtLeast(2)
                file = fileForOv
                baseW = side
                baseH = side
            } else {
                val raster = rasterizeShape(ov, minSide) ?: continue
                file = raster.path
                baseW = raster.width
                baseH = raster.height
            }
            out += OverlayRender(
                id = ov.id, file = file, baseW = baseW, baseH = baseH,
                startMs = ov.startMs, endMs = ov.endMs,
                x = ov.x, y = ov.y, scaleX = ov.scaleX, scaleY = ov.scaleY,
                rotation = ov.rotation, opacity = ov.opacity, zIndex = ov.zIndex,
                keyframes = ov.keyframes,
            )
        }
        return out
    }

    /** Copies overlay content-URIs into cache files keyed by overlay id. */
    private fun copyOverlayFiles(context: Context, project: PhonkProject): Map<String, String> {
        val resolver = context.contentResolver
        val out = HashMap<String, String>()
        for (ov in project.overlays) {
            val uriStr = ov.uri ?: continue
            val uri = Uri.parse(uriStr)
            val ext = when {
                uriStr.contains("png", ignoreCase = true) -> ".png"
                else -> ".jpg"
            }
            val f = File(context.cacheDir, "overlay_${System.currentTimeMillis()}_${ov.id}$ext")
            try {
                val ok = resolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { o -> input.copyTo(o) }
                    true
                } ?: false
                if (ok && f.length() > 0L) out[ov.id] = f.absolutePath
            } catch (t: Throwable) {
                Log.w(TAG, "overlay copy failed for ${ov.id}", t)
            }
        }
        return out
    }

    /**
     * Rasterizes a text layer into a transparent PNG sized to its measured
     * layout at the preview's font pixel size (fontSize * W / 1080), bold with
     * the authored colour and drop shadow, so the export text matches the
     * editor's rendered text.
     */
    private fun rasterizeText(layer: dev.phonk.editor.model.TextLayer, canvasW: Int, refW: Int): Rasterized? {
        val fontPx = (layer.fontSize * canvasW / refW).coerceAtLeast(8f)
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPx
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            color = layer.colorArgb.toInt()
            setShadowLayer(3f, 0f, 2f, 0x8C000000.toInt())
        }
        return runCatching {
            val layout = android.text.StaticLayout.Builder
                .obtain(layer.text, 0, layer.text.length, paint, canvasW)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
            val bw = layout.width.coerceAtLeast(1)
            val bh = layout.height.coerceAtLeast(1)
            val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).let { layout.draw(it) }
            val f = File(context.cacheDir, "text_${System.currentTimeMillis()}_${layer.id}.png")
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Rasterized(f.absolutePath, bw, bh)
        }.getOrNull()
    }

    /**
     * Rasterizes a label-only overlay (sticker/emoji/shape) centred on a
     * square canvas matching the preview's 0.22 * min side box.
     */
    private fun rasterizeShape(ov: dev.phonk.editor.model.OverlayLayer, minSide: Int): Rasterized? {
        val label = ov.label.ifBlank { "⬤" }
        val glyph = if (ov.kind.equals("Emoji", ignoreCase = true) || label.length <= 4) label else "⬤"
        val side = (minSide * 0.22f).roundToInt().coerceAtLeast(2)
        return runCatching {
            val bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = side.toFloat()
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(3f, 0f, 2f, 0x8C000000.toInt())
            }
            val fm = paint.fontMetrics
            val baseline = side / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(glyph, side / 2f, baseline, paint)
            val f = File(context.cacheDir, "shape_${System.currentTimeMillis()}_${ov.id}.png")
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Rasterized(f.absolutePath, side, side)
        }.getOrNull()
    }

    private fun saveToGallery(context: Context, file: File, config: ExportConfig): Uri {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "phonk_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Phonk")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: error("Storage unavailable")
        resolver.openOutputStream(uri, "w").use { out ->
            if (out == null) error("no stream")
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun sourceToDestForExport(clips: List<ClipSegment>, srcMs: Long): Long {
        if (clips.isEmpty()) return srcMs
        val clip = clips.firstOrNull { srcMs in it.sourceStartMs until it.sourceEndMs }
            ?: return srcMs
        val srcDur = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val destDur = (clip.destEndMs - clip.destStartMs).coerceAtLeast(0L)
        val ratio = destDur.toDouble() / srcDur
        return (clip.destStartMs + ((srcMs - clip.sourceStartMs) * ratio).toLong())
            .coerceIn(clip.destStartMs, clip.destEndMs)
    }

    private fun ffmpegEngine(): FFmpegEngine {
        val candidate = File(context.filesDir, "ffmpeg/ffmpeg")
        if (candidate.exists()) return ProcessFFmpegEngine(candidate.absolutePath)
        // also check Termux-friendly locations
        val t1 = File(context.getExternalFilesDir(null), "ffmpeg")
        if (t1.exists()) return ProcessFFmpegEngine(t1.absolutePath)
        return object : FFmpegEngine {
            override val available: Boolean = false
            override fun run(args: List<String>, cancel: java.util.concurrent.atomic.AtomicBoolean, onNewSecond: (Float) -> Unit) = Unit
        }
    }
}