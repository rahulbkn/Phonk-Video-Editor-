package dev.phonk.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression tests for the 00:00 duration bug: when the MediaStore probe fails
 * at import time the project is stored with videoDurationMs = 0 and an empty
 * (or zero-length) clip list, which makes the header, player and timeline all
 * report 00:00 even though the video plays. [PhonkProject.withMediaDuration]
 * folds the player's real duration back in and materializes a full-length clip.
 */
class MediaDurationBackfillTest {

    private val emptyProject = PhonkProject(
        name = "import",
        videoUri = "content://com.android.providers.media.documents/document/video%3A1",
        videoDurationMs = 0L,
    )

    @Test
    fun `backfills duration and materializes full-length clip`() {
        val updated = emptyProject.withMediaDuration(120_000L)

        assertEquals(120_000L, updated.videoDurationMs)
        assertEquals(1, updated.clips.size)
        val clip = updated.clips.single()
        assertEquals(0L, clip.sourceStartMs)
        assertEquals(120_000L, clip.sourceEndMs)
        assertEquals(0L, clip.destStartMs)
        assertEquals(120_000L, clip.destEndMs)
        assertEquals(120_000L, updated.timelineDurationMs())
        assertNotNull(updated.selectedClipId)
    }

    @Test
    fun `replaces zero-length clip with full-length one`() {
        val zeroClip = emptyProject.copy(
            clips = listOf(ClipSegment(sourceStartMs = 0L, sourceEndMs = 0L, destStartMs = 0L, destEndMs = 0L)),
        )
        val updated = zeroClip.withMediaDuration(90_000L)

        assertEquals(90_000L, updated.videoDurationMs)
        assertEquals(90_000L, updated.clips.single().destEndMs)
    }

    @Test
    fun `keeps real user clips but backfills duration`() {
        val edited = emptyProject.copy(
            clips = listOf(ClipSegment(sourceStartMs = 0L, sourceEndMs = 50_000L, destStartMs = 0L, destEndMs = 25_000L)),
            selectedClipId = "keep",
        )
        val updated = edited.withMediaDuration(60_000L)

        assertEquals(60_000L, updated.videoDurationMs)
        assertEquals(1, updated.clips.size)
        assertEquals(25_000L, updated.clips.single().destEndMs)
        assertEquals("keep", updated.selectedClipId)
    }

    @Test
    fun `non-positive real duration leaves project untouched`() {
        assertSame(emptyProject, emptyProject.withMediaDuration(0L))
        assertSame(emptyProject, emptyProject.withMediaDuration(-1L))
    }

    @Test
    fun `already healthy project is not mutated`() {
        val healthy = emptyProject.copy(
            videoDurationMs = 60_000L,
            clips = listOf(ClipSegment(sourceStartMs = 0L, sourceEndMs = 60_000L, destStartMs = 0L, destEndMs = 60_000L)),
            selectedClipId = "c1",
        )
        assertEquals(healthy, healthy.withMediaDuration(60_000L))
    }
}
