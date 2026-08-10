package dev.phonk.editor.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a single representative frame from a project's video for use as a
 * list thumbnail. Frames are cached in memory (keyed by project id) so
 * scrolling the home list doesn't re-decode on every recomposition.
 */
object ThumbnailLoader {

    private val cache = LruCache<String, Bitmap>(24)

    /** Returns a cached bitmap immediately if present, else null. */
    fun peek(projectId: String): Bitmap? = cache.get(projectId)

    suspend fun load(context: Context, projectId: String, videoUri: String?, atMs: Long = 0L): Bitmap? {
        if (videoUri.isNullOrBlank()) return null
        cache.get(projectId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(videoUri))
                val frame = retriever.getFrameAtTime(
                    atMs.coerceAtLeast(0L) * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: retriever.frameAtTime
                frame?.let { downscale(it) }?.also { cache.put(projectId, it) }
            } catch (t: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    /** Caps the longest edge so cached thumbnails stay small in memory. */
    private fun downscale(bitmap: Bitmap, maxEdge: Int = 480): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        val nw = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val nh = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    fun clear() = cache.evictAll()
}