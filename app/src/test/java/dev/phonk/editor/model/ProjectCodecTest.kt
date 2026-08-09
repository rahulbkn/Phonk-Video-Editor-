package dev.phonk.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the project JSON codec: exact round-trip without data loss. */
class ProjectCodecTest {

    @Test
    fun roundTripPreservesAllFields() {
        val original = PhonkProject(
            name = "test drop",
            videoUri = "content://media/external/video/123",
            videoDurationMs = 60000L,
            bpm = 140.0,
            beats = listOf(
                BeatMarker(0.0, 1f, 0, true),
                BeatMarker(428.57, 0.98f, 1, false),
            ),
            drops = listOf(
                DropMarker(3428.57, 0.92f, 0.9f, DropType.HARD_DROP),
                DropMarker(6857.14, 0.7f, 0.6f, DropType.BASS_SWITCH),
            ),
            sections = listOf(
                AudioSection(SectionKind.BUILD, 0.0, 3428.0),
                AudioSection(SectionKind.DROP, 3428.57, 6857.14),
            ),
            clips = listOf(
                ClipSegment(
                    sourceStartMs = 0L,
                    sourceEndMs = 428L,
                    destStartMs = 0L,
                    destEndMs = 428L,
                    effect = EffectKind.FLASH,
                    effectStrength = 0.9f,
                    dropTransition = true,
                )
            ),
            effects = listOf(
                ClipEffect("abc", EffectKind.ZOOM, 10L, 130L, 0.5f)
            ),
            export = ExportConfig(
                resolution = Resolution.UHD_4K,
                fps = FrameRate.F60,
                videoCodec = VideoCodec.HEVC,
                audioBitrate = AudioBitrate.A320,
                maintainAspect = false,
                hardwareAccel = false,
            ),
        )

        val json = ProjectCodec().toJson(original)
        val back = ProjectCodec().fromJson(json)

        assertEquals(original.name, back.name)
        assertEquals(original.videoUri, back.videoUri)
        assertEquals(original.videoDurationMs, back.videoDurationMs)
        assertEquals(original.bpm, back.bpm, 0.001)
        assertEquals(original.beats.size, back.beats.size)
        assertEquals(original.beats[1].timestampMs, back.beats[1].timestampMs, 0.001)
        assertEquals(original.drops.size, back.drops.size)
        assertEquals(original.drops[1].type, back.drops[1].type)
        assertEquals(original.sections.size, back.sections.size)
        assertEquals(original.sections[1].type, back.sections[1].type)
        assertEquals(original.clips.size, back.clips.size)
        assertEquals(original.clips[0].effect, back.clips[0].effect)
        assertEquals(original.clips[0].dropTransition, back.clips[0].dropTransition)
        assertEquals(original.effects.size, back.effects.size)
        assertEquals(original.effects[0].t1Ms, back.effects[0].t1Ms)
        assertEquals(original.export.resolution, back.export.resolution)
        assertEquals(original.export.fps, back.export.fps)
        assertEquals(original.export.videoCodec, back.export.videoCodec)
        assertEquals(original.export.audioBitrate, back.export.audioBitrate)
    }

    @Test
    fun emptyProjectRoundTrips() {
        val p = PhonkProject()
        val back = ProjectCodec().fromJson(ProjectCodec().toJson(p))
        assertEquals(p.name, back.name)
        assertTrue(back.beats.isEmpty())
        assertTrue(back.clips.isEmpty())
    }

    @Test
    fun malformedJsonFallsBackToDefaults() {
        val p = ProjectCodec().fromJson("not json at all")
        assertEquals("Untitled", p.name)
    }
}