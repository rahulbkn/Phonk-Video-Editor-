package dev.phonk.editor.ffmpeg

import android.content.Context
import java.io.File

/**
 * Extracts the bundled ffmpeg runtime (binary + shared libraries) from the APK
 * assets into the app-private files dir so the export engine can exec it.
 *
 * The binary and its dependency .so files live in `assets/ffmpeg/`. On first
 * use they are copied to `filesDir/ffmpeg/` (execution from APK assets is not
 * permitted by the kernel). The copy is done exactly once per install; the
 * output location is the one the export engine looks for.
 */
object FfmpegBundler {

    fun ensureExtracted(context: Context): File? {
        val dest = File(context.filesDir, "ffmpeg")
        val bin = File(dest, "ffmpeg")
        if (bin.isFile && bin.canExecute()) return bin
        return try {
            extract(context, dest)
            if (bin.isFile && bin.canExecute()) bin else null
        } catch (e: Exception) {
            android.util.Log.e("FFmpegBundler", "extract failed", e)
            null
        }
    }

    private fun extract(context: Context, dest: File): File {
        dest.mkdirs()
        val assets = context.assets.list("ffmpeg") ?: emptyArray()
        for (name in assets) {
            val out = File(dest, name)
            if (out.isFile && out.length() > 0L) continue
            context.assets.open("ffmpeg/$name").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            // The child process executes the binary directly via the system
            // linker; the libs only need to be readable by the app user.
            if (name == "ffmpeg") {
                out.setExecutable(true, true)
            } else {
                out.setReadable(true, false)
            }
        }
        return dest
    }
}