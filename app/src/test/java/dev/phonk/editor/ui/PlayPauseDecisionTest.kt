package dev.phonk.editor.ui

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the play/pause decision.
 *
 * Flow under test: playing -> playback reaches ENDED -> user taps Play ->
 * playback restarts from the start and the state becomes playing again.
 *
 * At STATE_ENDED Media3 keeps playWhenReady=true while isPlaying=false, so a
 * plain play() is a no-op. The decision must seek back to zero before resuming,
 * otherwise the Play button is stuck showing Pause with a frozen video.
 */
class PlayPauseDecisionTest {

    private val toggled = true
    private val notified = false

    @Test
    fun endedStateToggleRestartsPlaybackFromStart() {
        val d = playPauseDecision(
            playbackState = Player.STATE_ENDED,
            playWhenReady = true,
            isPlaying = false,
            playPauseToggled = toggled,
        )
        assertTrue("play from ENDED must seek back to zero", d.shouldSeekToZero)
        assertTrue("play from ENDED must resume playing", d.newIsPlaying)
    }

    @Test
    fun endedStateNotificationShowsPlayButton() {
        val d = playPauseDecision(
            playbackState = Player.STATE_ENDED,
            playWhenReady = true,
            isPlaying = false,
            playPauseToggled = notified,
        )
        assertFalse("at ENDED the UI must show Play, not Pause", d.newIsPlaying)
        assertFalse(d.shouldSeekToZero)
    }

    @Test
    fun pauseWhilePlayingKeepsPosition() {
        val d = playPauseDecision(
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            isPlaying = true,
            playPauseToggled = toggled,
        )
        assertFalse("pause must not seek", d.shouldSeekToZero)
        assertFalse("pause must stop playing", d.newIsPlaying)
    }

    @Test
    fun playWhilePausedMidVideoDoesNotSeek() {
        val d = playPauseDecision(
            playbackState = Player.STATE_READY,
            playWhenReady = false,
            isPlaying = false,
            playPauseToggled = toggled,
        )
        assertFalse("resume from pause must not seek", d.shouldSeekToZero)
        assertTrue("resume from pause must play", d.newIsPlaying)
    }
}
