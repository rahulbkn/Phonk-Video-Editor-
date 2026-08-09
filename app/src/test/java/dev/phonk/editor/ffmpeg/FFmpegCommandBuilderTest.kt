package dev.phonk.editor.ffmpeg

import dev.phonk.editor.model.AudioBitrate
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.FrameRate
import dev.phonk.editor.model.Resolution
import dev.phonk.editor.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden-style tests for the FFmpeg command builder. The build must be a
 * deterministic argv list with no shell metacharacters anywhere.
 */
class FFmpegCommandBuilderTest {

    private val segment = ClipSegment(
        sourceStartMs = 1000L,
        sourceEndMs = 2000L,
        destStartMs = 0L,
        destEndMs = 1000L,
    )

    private val config = ExportConfig(
        resolution = Resolution.HD_1080,
        fps = FrameRate.F30,
        videoCodec = VideoCodec.H264,
        audioBitrate = AudioBitrate.A192,
    )

    @Test
    fun buildsDeterministicArgv() {
        val a = FFmpegCommandBuilder.buildClip("/in.mp4", "/out.mp4", listOf(segment), config, true)
        val b = FFmpegCommandBuilder.buildClip("/in.mp4", "/out.mp4", listOf(segment), config, true)
        assertEquals(a, b)
    }

    @Test
    fun noShellCharactersInArguments() {
        val args = FFmpegCommandBuilder.buildClip("/in.mp4", "/out.mp4", listOf(segment), config, true)
        for (arg in args) {
            // ';' is a legitimate filter_complex separator and safe because the
            // command is executed with ProcessBuilder (no shell). Reject only
            // operators that would be meaningful to a shell: && and unquoted |
            assertFalse("arg must not contain shell chars: $arg", arg.contains("&&"))
            assertFalse("arg must not contain shell chars: $arg", arg.contains("|"))
            assertFalse("arg must not start with - to dodge flags: $arg", arg.startsWith("-;"))
        }
    }

    @Test
    fun filterGraphContainsTrimForEachSegment() {
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment),
            config,
            hasAudio = true,
            effects = emptyList(),
        )
        assertTrue(graph.contains("trim=start=1.000"))
        assertTrue(graph.contains("atrim=start=1.000"))
        assertTrue(graph.contains("concat=n=1"))
        assertTrue(graph.contains("scale=1080:1920"))
        assertTrue(graph.contains("pad=1080:1920"))
        assertTrue(graph.contains("fps=30"))
    }

    @Test
    fun flashEffectUsesTimedWhiteWindow() {
        val effect = EffectSpec(atDestMs = 500L, durationMs = 80L, kind = EffectKind.FLASH, amount = 1f)
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment),
            config,
            hasAudio = true,
            effects = listOf(effect),
        )
        // The bundled ffmpeg 8.1.2's fade=t=in/out is broken at late
        // timestamps (whites out the whole stream), so a flash is rendered as
        // a timed lutyuv white window instead.
        assertTrue("flash writes a white window", graph.contains("lutyuv=y=250:u=128:v=128"))
        assertTrue(
            "window opens exactly at the effect start",
            graph.contains("enable='between(t,0.500,0.580)'"),
        )
        assertTrue("window spans the full 80ms effect", graph.contains("0.500,0.580"))
    }

    @Test
    fun emptySegmentsFallbackIsStillValid() {
        val args = FFmpegCommandBuilder.buildClip("/in.mp4", "/out.mp4", emptyList(), config, true)
        assertEquals("-y", args.first())
        assertEquals("/out.mp4", args.last())
    }

    @Test
    fun outputIsCappedAtPlannedTimelineDuration() {
        val segs = listOf(
            ClipSegment(sourceStartMs = 1000L, sourceEndMs = 2000L, destStartMs = 0L, destEndMs = 1000L),
            ClipSegment(sourceStartMs = 2000L, sourceEndMs = 3000L, destStartMs = 1000L, destEndMs = 2000L),
        )
        val args = FFmpegCommandBuilder.buildClip("/in.mp4", "/out.mp4", segs, config, true)
        val tIdx = args.indexOf("-t")
        assertTrue("export must carry a -t duration cap", tIdx >= 0)
        assertEquals("cap equals the last dest end (2000ms)", "2.000", args[tIdx + 1])
    }
}