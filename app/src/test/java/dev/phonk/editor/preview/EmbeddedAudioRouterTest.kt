package dev.phonk.editor.preview

import dev.phonk.editor.model.ClipSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Embedded-audio routing regressions for the CRITICAL BUG "main video audio
 * does not follow cut clips".
 *
 * The single preview player must only ever hear the ACTIVE clip's embedded
 * audio, mirroring the export's per-clip atrim+concat:
 *
 * 1. Split audio boundaries (source 0->10 / 10->30) stay in-window with no
 *    action, so the soundtrack follows the clips exactly.
 * 2. An inserted clip's audio owns its timeline region: the main video stops
 *    at the boundary, the inserted file plays, then the main video resumes at
 *    the right source position — no leak of the old player.
 * 3. Removed source content (trims) is never heard: the player seeks back into
 *    the active clip's source window.
 * 4. Gaps: embedded audio is off (no seek into removed content while parked),
 *    independent audio is never disturbed (no stop mid-timeline), and playback
 *    stops at the very end of the timeline.
 * 5. Repeated play/pause and repeated seeks never re-switch or re-seek when the
 *    player is already aligned (no stale players, no duplicated sources).
 */
class EmbeddedAudioRouterTest {

    private val MAIN = "content://main"

    private fun clip(id: String, s0: Long, s1: Long, d0: Long, d1: Long, uri: String? = null) =
        ClipSegment(id = id, sourceStartMs = s0, sourceEndMs = s1, destStartMs = d0, destEndMs = d1, sourceUri = uri)

    private fun align(
        clips: List<ClipSegment>,
        destMs: Long,
        playerPosMs: Long,
        currentMediaUri: String?,
        timelineDurationMs: Long,
    ) = EmbeddedAudioRouter.align(
        clips = clips,
        videoUri = MAIN,
        destMs = destMs,
        playerPosMs = playerPosMs,
        currentMediaUri = currentMediaUri,
        timelineDurationMs = timelineDurationMs,
    )

    private fun noop(a: PlayerAlignment) {
        assertNull("unexpected media switch", a.switchMediaUri)
        assertNull("unexpected seek", a.seekToSourceMs)
        assertFalse("unexpected stop", a.stop)
    }

    // ─── Split boundaries (same file, contiguous source) ────────────────────

    @Test
    fun `split main video audio stays in window without action`() {
        // Main video 0-30 split at 10: A(src 0-10, dest 0-10) B(src 10-30, dest 10-30).
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("B", 10, 30, 10, 30),
        )
        noop(align(clips, destMs = 5, playerPosMs = 5, currentMediaUri = MAIN, timelineDurationMs = 30))
        // Exact boundary: position 10 sits inside B's half-open window -> free-run.
        noop(align(clips, destMs = 10, playerPosMs = 10, currentMediaUri = MAIN, timelineDurationMs = 30))
        noop(align(clips, destMs = 25, playerPosMs = 25, currentMediaUri = MAIN, timelineDurationMs = 30))
    }

    @Test
    fun `split audio uses the main video uri as the fallback source`() {
        val clips = listOf(clip("A", 0, 10, 0, 10))
        // Loaded with the wrong file -> switch to the main video.
        val a = align(clips, destMs = 5, playerPosMs = 5, currentMediaUri = "content://other", timelineDurationMs = 10)
        assertEquals(MAIN, a.switchMediaUri)
        assertEquals(0L, a.seekToSourceMs)
    }

    // ─── Inserted clip owns its timeline region ─────────────────────────────

    @Test
    fun `inserted clip audio switches media at its region start`() {
        // A(main 0-10, dest 0-10), C(inserted 0-10, dest 10-20).
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("C", 0, 10, 10, 20, uri = "content://inserted"),
        )
        val a = align(clips, destMs = 15, playerPosMs = 5, currentMediaUri = MAIN, timelineDurationMs = 20)
        assertEquals("content://inserted", a.switchMediaUri)
        assertEquals(0L, a.seekToSourceMs)
        // Once aligned, keep free-running.
        noop(align(clips, destMs = 15, playerPosMs = 6, currentMediaUri = "content://inserted", timelineDurationMs = 20))
    }

    @Test
    fun `a to c boundary stops main audio and starts inserted audio`() {
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("C", 0, 10, 10, 20, uri = "content://inserted"),
        )
        // Playhead crossed into C (dest 10) while the player is still at A's
        // source end -> switch to C and land on its source start.
        val a = align(clips, destMs = 10, playerPosMs = 10, currentMediaUri = MAIN, timelineDurationMs = 20)
        assertEquals("content://inserted", a.switchMediaUri)
        assertEquals(0L, a.seekToSourceMs)
    }

    @Test
    fun `c to b boundary resumes main video at its source start`() {
        // A(main 0-10, dest 0-10), C(inserted 0-10, dest 10-20), B(main 10-30, dest 20-30).
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("C", 0, 10, 10, 20, uri = "content://inserted"),
            clip("B", 10, 30, 20, 30),
        )
        val a = align(clips, destMs = 20, playerPosMs = 10, currentMediaUri = "content://inserted", timelineDurationMs = 30)
        assertEquals(MAIN, a.switchMediaUri)
        assertEquals(10L, a.seekToSourceMs)
    }

    // ─── Removed source content is never heard ──────────────────────────────

    @Test
    fun `trim gap seeks over removed source content`() {
        // A(0-10, dest 0-10), B(20-30, dest 10-30): source 10-20 was trimmed out.
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("B", 20, 30, 10, 30),
        )
        // Playhead parked at B.destStart (10) while the player drifted into the
        // removed region -> seek back into B's window, same file.
        val a = align(clips, destMs = 10, playerPosMs = 12, currentMediaUri = MAIN, timelineDurationMs = 30)
        assertNull(a.switchMediaUri)
        assertEquals(20L, a.seekToSourceMs)
    }

    @Test
    fun `seek into a later clip from fresh media lands on its source start`() {
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("B", 10, 30, 20, 30),
        )
        // Fresh media (position 0) but the playhead is deep inside B -> seek to 10.
        val a = align(clips, destMs = 25, playerPosMs = 0, currentMediaUri = MAIN, timelineDurationMs = 30)
        assertNull(a.switchMediaUri)
        assertEquals(10L, a.seekToSourceMs)
    }

    // ─── Gaps: embedded off, independent audio undisturbed, end stops ───────

    @Test
    fun `destination gap while parked does not seek and does not stop`() {
        // A(0-10, dest 0-10), B(10-30, dest 20-30): destination gap 10-20.
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("B", 10, 30, 20, 30),
        )
        // Parked in the gap -> embedded video audio is off, no seek into removed
        // content, and independent audio (if any) is never stopped.
        noop(align(clips, destMs = 15, playerPosMs = 12, currentMediaUri = MAIN, timelineDurationMs = 30))
    }

    @Test
    fun `timeline end stops playback`() {
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("B", 10, 30, 10, 30),
        )
        val a = align(clips, destMs = 30, playerPosMs = 30, currentMediaUri = MAIN, timelineDurationMs = 30)
        assertTrue("must stop at timeline end", a.stop)
    }

    @Test
    fun `inserted clip shorter than the timeline stops at the end`() {
        // A(main 0-10, dest 0-10), C(inserted 0-5, dest 10-15): media ends before
        // the timeline, so after C the player must not play beyond the clip.
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("C", 0, 5, 10, 15, uri = "content://inserted"),
        )
        noop(align(clips, destMs = 12, playerPosMs = 3, currentMediaUri = "content://inserted", timelineDurationMs = 15))
        val end = align(clips, destMs = 15, playerPosMs = 6, currentMediaUri = "content://inserted", timelineDurationMs = 15)
        assertTrue("must stop past the last clip", end.stop)
    }

    // ─── Stability: no stale players / duplicated sources ───────────────────

    @Test
    fun `repeated alignment never re-switches an already aligned player`() {
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("C", 0, 10, 10, 20, uri = "content://inserted"),
        )
        repeat(5) {
            noop(align(clips, destMs = 15, playerPosMs = 5, currentMediaUri = "content://inserted", timelineDurationMs = 20))
        }
    }

    @Test
    fun `repeated alignment never re-seeks an already aligned player`() {
        val clips = listOf(
            clip("A", 0, 10, 0, 10),
            clip("B", 10, 30, 10, 30),
        )
        repeat(5) {
            noop(align(clips, destMs = 25, playerPosMs = 25, currentMediaUri = MAIN, timelineDurationMs = 30))
        }
    }

    @Test
    fun `single full video without clips free-runs untouched`() {
        noop(align(emptyList(), destMs = 5, playerPosMs = 5, currentMediaUri = MAIN, timelineDurationMs = 10))
    }

    @Test
    fun `image region has no embedded audio and no stop`() {
        // Only an image item extends the timeline after the last clip; the
        // playhead sits past the video but before the timeline end.
        val clips = listOf(clip("A", 0, 10, 0, 10))
        noop(align(clips, destMs = 12, playerPosMs = 11, currentMediaUri = MAIN, timelineDurationMs = 14))
    }
}
