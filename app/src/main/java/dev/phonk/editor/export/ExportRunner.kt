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
import dev.phonk.editor.ffmpeg.ProcessFFmpegEngine
import dev.phonk.editor.ffmpeg.RenderState
import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.PhonkProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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

class ExportRunner(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    var cancelRequested = false

    /** Clears any previous result so the dialog starts fresh. */
    fun reset() {
        _state.value = ExportState.Idle
    }

    /** Requests cancellation of an in-flight export. */
    fun cancel() {
        cancelRequested = true
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

                // stage 1: resolve engine
                val engine = ffmpegEngine()
                if (!engine.available) {
                    Log.e(TAG, "export FAILED: ffmpeg engine unavailable")
                    _state.value = ExportState.Failed("FFmpeg engine unavailable on this device")
                    return@runCatching
                }
                val renderer = FfmpegRenderer(engine)

                // stage 2: render to internal cache
                val outFile = File(context.cacheDir, "phonk_export_${System.currentTimeMillis()}.mp4")
                val overlayFiles = copyOverlayFiles(context, project)
                val completed = renderer.render(
                    input = inputPath(videoUri),
                    output = outFile.absolutePath,
                    segments = clips,
                    config = config,
                    hasAudio = true,
                    effects = effectSpecs,
                    hwEncode = if (config.hardwareAccel) "h264" else null,
                    videoDurationMs = clips.map { it.destEndMs }.maxOrNull() ?: 0L,
                    onProgress = { p ->
                        _state.value = ExportState.Running(p, "rendering")
                    },
                    colorGrade = dev.phonk.editor.ffmpeg.ColorGrade(
                        brightness = project.brightness,
                        contrast = project.contrast,
                        saturation = project.saturation,
                    ),
                    texts = project.textLayers,
                    overlays = project.overlays,
                    overlayFiles = overlayFiles,
                    transitionDurationMs = project.transitionDurationMs,
                    fontPath = findSystemFont(),
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
                        _state.value = ExportState.Running(0.9f, "saving")
                        val uri = saveToGallery(context, outFile, config)
                        _state.value = ExportState.Done(uri, outFile.absolutePath)
                    }
                    is RenderState.Running, is RenderState.Idle -> Unit
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

    /** Picks a system font the bundled ffmpeg can load, or null. */
    private fun findSystemFont(): String? {
        val candidates = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/RobotoCondensed-Regular.ttf",
        )
        for (p in candidates) {
            if (File(p).exists()) return p
        }
        return null
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