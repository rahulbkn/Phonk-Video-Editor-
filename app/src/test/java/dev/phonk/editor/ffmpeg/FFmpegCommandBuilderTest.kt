package dev.phonk.editor.ffmpeg

import dev.phonk.editor.model.AudioBitrate
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.FrameRate
import dev.phonk.editor.model.OverlayKeyframe
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
    fun clipEffectIsWindowedToClipDuration() {
        val seg = ClipSegment(
            sourceStartMs = 1000L,
            sourceEndMs = 2000L,
            destStartMs = 0L,
            destEndMs = 1000L,
            effect = EffectKind.BRIGHTNESS,
            effectStrength = 1f,
        )
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(seg),
            config,
            hasAudio = true,
            effects = emptyList(),
        )
        assertTrue("clip brightness effect is rendered", graph.contains("eq=brightness=0.500"))
        assertTrue(
            "effect is windowed to the clip duration",
            graph.contains("enable='between(t,0.000,1.000)'"),
        )
    }

    @Test
    fun clipGlitchSaturatesWithinClipWindow() {
        val seg = ClipSegment(
            sourceStartMs = 500L,
            sourceEndMs = 1500L,
            destStartMs = 0L,
            destEndMs = 1000L,
            effect = EffectKind.GLITCH,
            effectStrength = 0.7f,
        )
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(seg),
            config,
            hasAudio = true,
            effects = emptyList(),
        )
        assertTrue("glitch eats contrast", graph.contains("eq=saturation=0.15"))
        assertTrue("hue wobbles during the clip", graph.contains("hue=H=0.03*sin(2*PI*t*30)"))
    }

    @Test
    fun emptySegmentsFallbackIsStillValid() {
        val args = FFmpegCommandBuilder.buildClip("/in.mp4", "/out.mp4", emptyList(), config, true)
        assertEquals("-y", args.first())
        assertEquals("/out.mp4", args.last())
    }

    @Test
    fun colorGradeEmitsFullEqChain() {
        val grade = dev.phonk.editor.model.ColorGrade(
            brightness = 0.2f,
            contrast = 0.3f,
            saturation = -0.2f,
            exposure = 0.4f,
            temperature = 0.5f,
            tint = -0.2f,
        )
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment),
            config,
            hasAudio = true,
            effects = emptyList(),
            colorGrade = grade,
        )
        assertTrue("brightness + exposure fold into eq", graph.contains("eq=brightness="))
        assertTrue("temperature drives red gamma", graph.contains("gamma_r="))
        assertTrue("tint drives green gamma", graph.contains("gamma_g="))
        assertTrue("everything folds into one eq pass", graph.contains(":contrast=") && graph.contains(":saturation="))
    }

    @Test
    fun neutralColorGradeAddsNoFilters() {
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment),
            config,
            hasAudio = true,
            effects = emptyList(),
            colorGrade = dev.phonk.editor.model.ColorGrade(),
        )
        assertFalse(graph.contains("eq=brightness="))
        assertFalse(graph.contains("boxblur"))
        assertFalse(graph.contains("vignette"))
    }

    @Test
    fun blurVignetteGrainAndSharpenEmitCoreFilters() {
        val grade = dev.phonk.editor.model.ColorGrade(
            blur = 0.5f,
            vignette = 1f,
            grain = 0.25f,
            sharpness = 0.3f,
        )
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment),
            config,
            hasAudio = true,
            effects = emptyList(),
            colorGrade = grade,
        )
        assertTrue("blur becomes boxblur", graph.contains("boxblur=luma_radius="))
        assertTrue("vignette becomes vignette filter", graph.contains("vignette=angle="))
        assertTrue("grain becomes noise", graph.contains("noise=alls="))
        assertTrue("sharpness becomes unsharp", graph.contains("unsharp=luma_"))
    }

    @Test
    fun disabledKeyframesEmitSingleFullRangeGrade() {
        val keyframes = listOf(
            dev.phonk.editor.model.GradeKeyframe(
                0L,
                dev.phonk.editor.model.ColorGrade(brightness = 0f),
            ),
            dev.phonk.editor.model.GradeKeyframe(
                2000L,
                dev.phonk.editor.model.ColorGrade(brightness = 1f),
            ),
        )
        // Disabled: identical to the classic path - one eq, no enable guard.
        val off = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, true, colorGrade = dev.phonk.editor.model.ColorGrade(brightness = 0.3f),
            keyframes = keyframes, keyframesEnabled = false,
        )
        assertEquals(1, countOccurrences(off, ",eq=brightness="))
        assertFalse(off.contains(":enable='"))
        // Enabled with keyframes spanning the whole destination range:
        // time-windowed grade slices appear.
        val on = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, true, colorGrade = dev.phonk.editor.model.ColorGrade(brightness = 0.3f),
            keyframes = keyframes, keyframesEnabled = true,
        )
        assertTrue("animation slices carry enable guards", on.contains(":enable='between(t,"))
        assertTrue("brightness ramps through the eq chain", on.contains("gamma_r="))
    }

    @Test
    fun keyframeWindowsRespectDestinationTimeline() {
        val keyframes = listOf(
            dev.phonk.editor.model.GradeKeyframe(0L, dev.phonk.editor.model.ColorGrade(brightness = 0f)),
            dev.phonk.editor.model.GradeKeyframe(1000L, dev.phonk.editor.model.ColorGrade(brightness = 0.5f)),
        )
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, true,
            colorGrade = dev.phonk.editor.model.ColorGrade(),
            keyframes = keyframes, keyframesEnabled = true,
        )
        // segment spans 0..1000ms (destStartMs/destEndMs per fixture). The ramp must
        // be split into several time-windowed grade slices ending at 1.000.
        assertTrue(graph.contains(":enable='between(t,"))
        assertTrue("ramp split into multiple windowed slices", countOccurrences(graph, ":enable='between(t,") >= 2)
        assertTrue("final window reaches timeline end", graph.contains(",1.000)"))
        assertTrue("no durability crash on multiple windows", graph.contains("concat=n="))
    }

    @Test
    fun everyFilterInWindowedGradeCarriesEnableGate() {
        // A keyframed window whose grade emits multiple filter categories
        // (eq + boxblur + noise): every emitted filter must be time-gated with
        // the same :enable='between(...)' expression, not just the last one.
        val keyframes = listOf(
            dev.phonk.editor.model.GradeKeyframe(
                0L,
                dev.phonk.editor.model.ColorGrade(brightness = 0.2f, blur = 0.5f, grain = 0.25f),
            ),
            dev.phonk.editor.model.GradeKeyframe(
                1000L,
                dev.phonk.editor.model.ColorGrade(brightness = 0.5f, blur = 0.8f, grain = 0.5f),
            ),
        )
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, true,
            colorGrade = dev.phonk.editor.model.ColorGrade(),
            keyframes = keyframes, keyframesEnabled = true,
        )
        val eq = countOccurrences(graph, ",eq=brightness=")
        val blur = countOccurrences(graph, ",boxblur=luma_radius=")
        val noise = countOccurrences(graph, ",noise=alls=")
        val gates = countOccurrences(graph, ":enable='between(t,")
        assertTrue("windowed grade must split into multiple slices", eq >= 2)
        assertEquals("each window emits eq, boxblur and noise in lockstep", eq, blur)
        assertEquals("each window emits eq, boxblur and noise in lockstep", eq, noise)
        assertEquals(
            "every emitted filter carries the enable gate (gates == filters)",
            gates, eq * 3,
        )
        assertTrue("eq gate precedes boxblur gate", graph.indexOf(",boxblur=") > graph.indexOf(",eq=brightness="))
        assertTrue("boxblur gate precedes noise gate", graph.indexOf(",noise=alls=") > graph.indexOf(",boxblur="))
        assertTrue("last noise is still gated", graph.lastIndexOf(":enable='between(t,") > graph.lastIndexOf(",noise=alls="))
    }

    @Test
    fun staticMultiFilterGradeHasNoEnableGates() {
        // Non-windowed (static) grades must be unchanged: filters emit in the
        // same order with no :enable gate anywhere.
        val grade = dev.phonk.editor.model.ColorGrade(
            brightness = 0.2f,
            blur = 0.5f,
            grain = 0.25f,
        )
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, true,
            effects = emptyList(),
            colorGrade = grade,
        )
        assertTrue("static grade emits eq", graph.contains("eq=brightness="))
        assertTrue("static grade emits boxblur", graph.contains("boxblur=luma_radius="))
        assertTrue("static grade emits noise", graph.contains("noise=alls="))
        assertFalse("static grade carries no time gate", graph.contains(":enable='"))
        assertTrue("ordering is preserved", graph.indexOf("boxblur=") > graph.indexOf("eq=brightness="))
        assertTrue("ordering is preserved", graph.indexOf("noise=alls=") > graph.indexOf("boxblur="))
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return count
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

    // ---- overlay export parity ----

    private fun render(
        id: String = "o1",
        file: String = "/cache/$id.png",
        baseW: Int = 200,
        baseH: Int = 100,
        startMs: Long = 0L,
        endMs: Long = 1000L,
        x: Float = 0.5f,
        y: Float = 0.5f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotation: Float = 0f,
        opacity: Float = 1f,
        zIndex: Int = 0,
        keyframes: List<OverlayKeyframe> = emptyList(),
    ) = OverlayRender(
        id = id, file = file, baseW = baseW, baseH = baseH, startMs = startMs, endMs = endMs,
        x = x, y = y, scaleX = scaleX, scaleY = scaleY, rotation = rotation, opacity = opacity,
        zIndex = zIndex, keyframes = keyframes,
    )

    @Test
    fun overlayRenderEmitsScaledRotatedPositionedComposite() {
        val r = render(scaleX = 2f, scaleY = 1f, rotation = 90f, opacity = 0.5f)
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, hasAudio = true, overlayRenders = listOf(r),
        )
        assertTrue("render is read from its looped input", graph.contains("[1:v]format=rgba"))
        assertTrue("source fits into the base box", graph.contains("scale=200:100:force_original_aspect_ratio=decrease"))
        assertTrue("base box is padded transparent", graph.contains("pad=200:100:(ow-iw)/2:(oh-ih)/2:color=black@0"))
        assertTrue("authored scale multiplies the base size", graph.contains(",scale=400:100"))
        assertTrue("rotation feeds clockwise radians", graph.contains(",rotate=1.571:c=black@0"))
        assertTrue("opacity scales the alpha channel", graph.contains(",colorchannelmixer=aa=0.500"))
        assertTrue(
            "centre is mapped to 0.5,0.5 minus half the rotated bbox",
            graph.contains("overlay=x=490:y=760:eof_action=repeat:enable='between(t,0.000,1.000)'"),
        )
    }

    @Test
    fun overlayRendersCompositeBottomToTopByZIndex() {
        val low = render(id = "low", x = 0.2f, y = 0.2f, zIndex = 2)
        val high = render(id = "high", x = 0.8f, y = 0.8f, zIndex = 5)
        // high passed first; z-order must still draw low beneath high.
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, hasAudio = true, overlayRenders = listOf(high, low),
        )
        val firstIn = graph.indexOf("[1:v]")
        val secondIn = graph.indexOf("[2:v]")
        assertTrue("inputs are declared in z order", firstIn in 0 until secondIn)
        assertEquals("both renders are composited", 2, countOccurrences(graph, "overlay=x="))
    }

    @Test
    fun zeroDurationOrMissingFileRendersAreDropped() {
        val bad = render(id = "bad", file = "", endMs = 0L)
        val good = render(id = "good", file = "/g.png")
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, hasAudio = true, overlayRenders = listOf(bad, good),
        )
        assertEquals("only the valid render composites", 1, countOccurrences(graph, "overlay=x="))
        assertTrue("valid render is the sole input", graph.contains("[1:v]"))
    }

    @Test
    fun overlayKeyframesSplitIntoWindowedComposites() {
        val kf = listOf(
            OverlayKeyframe(0L, 0.2f, 0.2f, 1f, 1f, 0f, 1f),
            OverlayKeyframe(2000L, 0.8f, 0.8f, 1f, 1f, 0f, 1f),
        )
        val r = render(id = "anim", endMs = 2000L, keyframes = kf)
        val graph = FFmpegCommandBuilder.buildFilterGraph(
            listOf(segment), config, hasAudio = true, overlayRenders = listOf(r),
        )
        assertTrue(
            "animation samples produce multiple windowed composites",
            countOccurrences(graph, "overlay=x=") >= 2,
        )
        assertTrue("windows are gated by between(t,", graph.contains("enable='between(t,"))
    }

    @Test
    fun buildClipAddsLoopInputPerOverlayRender() {
        val r = render(id = "sticker", file = "/cache/sticker.png")
        val args = FFmpegCommandBuilder.buildClip(
            "/in.mp4", "/out.mp4", listOf(segment), config, hasAudio = true,
            overlayRenders = listOf(r),
        )
        assertTrue("loop flag added for the overlay", args.contains("-loop"))
        assertTrue("overlay file is an extra input", args.contains("/cache/sticker.png"))
        val loopIdx = args.indexOf("-loop")
        assertTrue("loop precedes the file path", args[loopIdx + 1] == "1")
    }

    @Test
    fun overlayWindowsClampToTimelineEnd() {
        val r = render(id = "long", endMs = 50_000L)
        val wins = FFmpegCommandBuilder.overlayWindows(r, totalMs = 2_000L)
        assertEquals("single static window", 1, wins.size)
        assertEquals("start in seconds", 0.0, wins[0].startSec, 0.001)
        assertEquals("end clamped to timeline", 2.0, wins[0].endSec, 0.001)
    }
}