package dev.phonk.editor.preview

import dev.phonk.editor.model.ClipSegment

/**
 * Alignment decision for the single preview player so only the ACTIVE clip's
 * embedded audio is ever heard — the preview mirror of the export's per-clip
 * `atrim` + `concat` (see FFmpegCommandBuilder).
 *
 * The preview uses ONE ExoPlayer for every video clip. That player free-runs
 * whatever file it was given, so without intervention a split/trimmed/
 * reordered main video would keep playing its full soundtrack (removed source
 * content included) and an inserted clip would leave the previous file's audio
 * audible. This router recomputes the player target every pump tick:
 *
 * - Inside the active clip's source window on the right media -> free-run.
 * - Active clip references a different file -> switch media and seek to the
 *   clip's source start (the old player is replaced, never leaked).
 * - Player drifted out of the active clip's source window (a trim / source
 *   gap) -> seek back to the clip's source start so removed content is never
 *   heard.
 * - No active clip (destination gap while paused / image region / after the
 *   last clip) -> leave the player alone; at the very end of the timeline,
 *   stop.
 *
 * Pure and JVM-testable; the ViewModel applies the returned action.
 */
data class PlayerAlignment(
    /** Media uri the player must switch to (null = keep the current media). */
    val switchMediaUri: String? = null,
    /** Source position to seek to (null = keep the player position). */
    val seekToSourceMs: Long? = null,
    /** True when the playhead reached the end of the timeline and must stop. */
    val stop: Boolean = false,
)

object EmbeddedAudioRouter {

    /**
     * Computes the alignment action for one playback pump tick.
     *
     * @param destMs current GLOBAL timeline playhead position
     * @param playerPosMs current player position (SOURCE time of its media)
     * @param currentMediaUri media uri currently loaded in the player
     */
    fun align(
        clips: List<ClipSegment>,
        videoUri: String?,
        destMs: Long,
        playerPosMs: Long,
        currentMediaUri: String?,
        timelineDurationMs: Long,
    ): PlayerAlignment {
        val active = clips.firstOrNull { destMs in it.destStartMs until it.destEndMs }
        if (active == null) {
            // Destination gap / image region / timeline end. Embedded video
            // audio is off; only stop at the real end of the timeline.
            if (destMs >= timelineDurationMs) return PlayerAlignment(stop = true)
            return PlayerAlignment()
        }
        val uri = active.sourceUri ?: videoUri
        val mediaChanged = uri != null && uri != currentMediaUri
        val inWindow = playerPosMs >= active.sourceStartMs && playerPosMs < active.sourceEndMs
        if (!mediaChanged && inWindow) return PlayerAlignment()
        return PlayerAlignment(
            switchMediaUri = if (mediaChanged) uri else null,
            seekToSourceMs = active.sourceStartMs,
        )
    }
}
