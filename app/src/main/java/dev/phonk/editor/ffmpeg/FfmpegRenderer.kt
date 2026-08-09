package dev.phonk.editor.ffmpeg

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ExportConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

sealed interface RenderState {
    data object Idle : RenderState
    data class Running(val progress: Float, val note: String) : RenderState
    data class Done(val outPath: String, val durationMs: Long) : RenderState
    data class Failed(val message: String) : RenderState
}

/**
 * FFmpeg runtime strategy. The app intentionally does not bundle a multi-GB
 * ffmpeg; instead it uses a lightweight arm64 binary dropped into
 * filesDir/ffmpeg when available (see scripts/fetch-ffmpeg.sh). Commands are
 * never assembled into a shell string.
 */
interface FFmpegEngine {
    val available: Boolean
    fun run(args: List<String>, cancel: AtomicBoolean, onNewSecond: (Float) -> Unit)
}

/** Executes ffmpeg as a subprocess with an argv array (no shell involved). */
class ProcessFFmpegEngine(private val binaryPath: String) : FFmpegEngine {
    override val available: Boolean = true

    override fun run(
        args: List<String>,
        cancel: AtomicBoolean,
        onNewSecond: (Float) -> Unit,
    ) {
        val cwd = java.io.File(binaryPath).parentFile
        // Android 10+ SELinux forbids direct exec of binaries under
        // app-data (execute_no_trans denied); invoke via the system linker
        // so the exec target is a trusted system file.
        val cmd = if (android.os.Build.VERSION.SDK_INT >= 29) {
            listOf("/system/bin/linker64", binaryPath) + args
        } else {
            listOf(binaryPath) + args
        }
        android.util.Log.i("FFmpeg", "cmd: " + cmd.joinToString(" ") { "\"$it\"" })
        val proc = ProcessBuilder(cmd)
            .directory(cwd)
            .redirectErrorStream(true)
            .start()
        var exit = -1
        try {
            val reader = proc.inputStream.bufferedReader()
            while (true) {
                if (cancel.get()) break
                val line = reader.readLine() ?: break
                val t = parseSeconds(line)
                if (t != null) onNewSecond(t)
                if (line.isNotBlank()) android.util.Log.i("FFmpeg", line)
            }
            exit = proc.waitFor()
        } catch (e: IOException) {
            // native process may have been killed by cancellation
        } finally {
            if (cancel.get() || !proc.isAlive) proc.destroyForcibly()
        }
        android.util.Log.i("FFmpeg", "exit=" + exit)
    }

    /** Extracts "time=HH:MM:SS.xxx" from a line. */
    private fun parseSeconds(line: String): Float? {
        val idx = line.indexOf("time=")
        if (idx < 0) return null
        val rest = line.substring(idx + 5)
        val end = rest.indexOf(' ')
        val token = if (end > 0) rest.substring(0, end) else rest
        val parts = token.split(":")
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toDoubleOrNull() ?: return null
        return (h * 3600 + m * 60 + s).toFloat()
    }
}

/**
 * Orchestrates a single-invocation render. Streams progress and supports
 * cancellation; never blocks a caller thread (runs on Dispatchers.IO).
 */
class FfmpegRenderer(private val engine: FFmpegEngine) {

    private val _state = MutableStateFlow<RenderState>(RenderState.Idle)
    val state: StateFlow<RenderState> = _state.asStateFlow()

    private val cancelFlag = AtomicBoolean(false)

    fun cancel() {
        cancelFlag.set(true)
    }

    suspend fun render(
        input: String,
        output: String,
        segments: List<ClipSegment>,
        config: ExportConfig,
        hasAudio: Boolean,
        effects: List<EffectSpec>,
        hwEncode: String?,
        videoDurationMs: Long,
        onProgress: (Float) -> Unit = {},
        colorGrade: ColorGrade? = null,
        texts: List<dev.phonk.editor.model.TextLayer> = emptyList(),
        overlays: List<dev.phonk.editor.model.OverlayLayer> = emptyList(),
        overlayFiles: Map<String, String> = emptyMap(),
        transitionDurationMs: Long = 400L,
        fontPath: String? = null,
    ): RenderState = withContext(Dispatchers.IO) {
        if (!engine.available) {
            RenderState.Failed(
                "FFmpeg is not bundled in this APK. Drop an arm64 ffmpeg binary into " +
                    "filesDir/ffmpeg and restart, or use the Android renderer fallback."
            )
        } else {
            _state.value = RenderState.Running(0f, "encoding")
            onProgress(0f)
            val args = FFmpegCommandBuilder.buildClip(
                input, output, segments, config, hasAudio, effects, hwEncode,
                colorGrade, texts, overlays, overlayFiles, transitionDurationMs, fontPath,
            )
            cancelFlag.set(false)
            engine.run(args, cancelFlag) { seconds ->
                val total = videoDurationMs / 1000f
                val p = if (total > 0f) (seconds / total).coerceIn(0f, 1f) else 0f
                _state.value = RenderState.Running(p, "rendering")
                onProgress(p)
            }
            if (cancelFlag.get()) RenderState.Failed("Rendering cancelled") else RenderState.Done(output, videoDurationMs)
        }
    }
}