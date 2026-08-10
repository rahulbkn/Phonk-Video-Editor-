package dev.phonk.editor.timeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache

/**
 * Filmstrip thumbnails for the timeline video track. Each cell is one real
 * video frame at a given source time, downscaled and cached by uri+time so
 * pan/zoom/scrub never re-decodes the same frame. Decoding is driven from a
 * background executor (see TimelineView) because MediaMetadataRetriever is
 * blocking and must not run on the UI thread.
 */
object TimelineThumbnailer {

    private const val MAX_EDGE = 240
    private val cache = LruCache<String, Bitmap>(768)

    private fun key(uri: String, sourceMs: Long) = "$uri#$sourceMs"

    /** Synchronous cache lookup; null when the frame is not decoded yet. */
    fun peek(uri: String?, sourceMs: Long): Bitmap? {
        if (uri.isNullOrBlank()) return null
        return cache.get(key(uri, sourceMs))
    }

    /**
     * Decodes every source time in [times] that is not cached yet using a single
     * retriever for [uri]. Safe to call on a background thread.
     */
    fun decodeBatch(context: Context, uri: String?, times: List<Long>) {
        if (uri.isNullOrBlank()) return
        val missing = times.filter { cache.get(key(uri, it)) == null }
        if (missing.isEmpty()) return
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uri))
            for (sourceMs in missing) {
                val frame = try {
                    retriever.getFrameAtTime(
                        sourceMs.coerceAtLeast(0L) * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    ) ?: retriever.frameAtTime
                } catch (t: Throwable) {
                    null
                }
                frame?.let { cache.put(key(uri, sourceMs), downscale(it)) }
            }
        } catch (t: Throwable) {
            // Source unreadable at this time; cells simply stay blank.
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun clear() = cache.evictAll()

    /** Caps the longest edge so cached cells stay small in memory. */
    private fun downscale(bitmap: Bitmap, maxEdge: Int = MAX_EDGE): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}