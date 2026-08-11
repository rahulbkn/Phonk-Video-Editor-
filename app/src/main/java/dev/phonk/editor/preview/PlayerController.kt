package dev.phonk.editor.preview

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Thin ExoPlayer wrapper for the preview surface. Exposes position updates to
 * drive the timeline playhead; start/end of the trimmed segment is enforced by
 * the caller setting a seek window (see EditorViewModel.loopWindow).
 */
class PlayerController(context: Context) {

    val player: ExoPlayer =
        ExoPlayer.Builder(context).build()

    var onProgress: ((positionMs: Long) -> Unit)? = null
    var onEnded: (() -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && player.duration > 0 && player.currentPosition >= player.duration - 50) {
                    player.seekTo(0L)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onEnded?.invoke()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("PlayerController", "player error", error)
            }
        })
    }

    fun setVideo(uri: Uri?) {
        if (uri == null) {
            player.setMediaItem(MediaItem.fromUri(Uri.EMPTY))
            player.prepare()
            return
        }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
    }

    /** Pitch shift via ExoPlayer playback parameters (keeps speed at 1x). */
    fun setPitch(pitch: Float) {
        val params = player.playbackParameters
        val speed = if (params.speed > 0f) params.speed else 1f
        player.setPlaybackParameters(
            androidx.media3.common.PlaybackParameters(speed, pitch.coerceIn(0.5f, 2f))
        )
    }

    /** Applies a playback speed (preview of per-clip speed ramps). */
    fun setPreviewSpeed(speed: Float) {
        val params = player.playbackParameters
        player.setPlaybackParameters(
            androidx.media3.common.PlaybackParameters(
                speed.coerceIn(0.25f, 4f),
                params.pitch.takeIf { it > 0f } ?: 1f,
            )
        )
    }

    fun resetPlaybackParameters() {
        player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(1f, 1f))
    }

    /** Enter frame-exact scrubbing. Keep volume on to hear edits.
     *  The window clamp is defensive: when the caller has no real duration yet
     *  (videoDurationMs == 0 from a failed probe, or the player is still
     *  preparing), clamping to an empty [0, 0] range would collapse every seek
     *  to 0. Fall back to the player's own duration / an open range so media3
     *  applies the seek and clamps to the real window itself. */
    fun scrubTo(ms: Long, windowStart: Long, windowEnd: Long) {
        val d = player.duration
        val end = when {
            windowEnd > 0L -> windowEnd
            d > 0L -> d
            else -> Long.MAX_VALUE
        }
        player.seekTo(ms.coerceIn(windowStart.coerceAtLeast(0L), end))
    }

    fun play() {
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun release() {
        player.release()
    }

    /** Polled once per UI frame from [pumpPosition]. Reports the real media
     *  position whenever the player has one. Gating on `duration > 0` made the
     *  playhead collapse to 0 while the media was still preparing (duration is
     *  C.TIME_UNSET until then), which fought every seek the user made. */
    fun pollPosition(): Long {
        val pos = player.currentPosition
        return if (pos in 0L..Long.MAX_VALUE) pos else 0L
    }
}