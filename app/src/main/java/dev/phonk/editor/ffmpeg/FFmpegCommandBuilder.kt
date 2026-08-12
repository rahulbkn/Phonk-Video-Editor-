package dev.phonk.editor.ffmpeg

import dev.phonk.editor.model.BackgroundType
import dev.phonk.editor.model.CanvasBackground
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.CropConfig
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.GradeKeyframe
import dev.phonk.editor.model.MaskConfig
import dev.phonk.editor.model.MaskShape
import dev.phonk.editor.model.OverlayFx
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.OverlayKeyframe
import dev.phonk.editor.model.evaluateColorGrade
import dev.phonk.editor.model.evaluateOverlayFx
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A beat-aware effect trigger on the destination timeline. */
data class EffectSpec(
    val atDestMs: Long,
    val durationMs: Long,
    val kind: EffectKind,
    val amount: Float,
)

/** A time-slice of the color grade (seconds on the destination timeline). */
data class GradeWindow(
    val startSec: Double,
    val endSec: Double,
    val grade: ColorGrade,
)

/**
 * A fully-resolved overlay for the ffmpeg graph. Both text (rasterized to PNG
 * ahead of time) and image/shape overlays funnel through this, so the export
 * applies exactly the same position/scale/rotation/opacity/timing/zIndex the
 * editor preview shows. Coordinates are normalized (0..1, 0.5,0.5 = centre of
 * the content rect). [baseW]/[baseH] is the pixel size of the source at
 * scale = 1.0 in export-resolution pixels.
 */
data class OverlayRender(
    override val id: String,
    val file: String,
    val baseW: Int,
    val baseH: Int,
    override val startMs: Long,
    override val endMs: Long,
    override val x: Float,
    override val y: Float,
    override val scaleX: Float,
    override val scaleY: Float,
    override val rotation: Float,
    override val opacity: Float,
    override val zIndex: Int,
    override val keyframes: List<OverlayKeyframe> = emptyList(),
    val chromaKeyColor: Int? = null,
    val chromaKeySimilarity: Float = 0.3f,
    val mask: MaskConfig = MaskConfig(),
) : OverlayItem {
    override val visible: Boolean get() = true
    override val locked: Boolean get() = false
    override val type: String get() = "Render"
    override val label: String get() = id
}

/** A constant-transform slice of an overlay's lifetime (seconds). */
data class OverlayWindow(
    val startSec: Double,
    val endSec: Double,
    val fx: OverlayFx,
)

/**
 * Builds safe ffmpeg command lines. Pure functions, no I/O; unit-testable on
 * the JVM. Every argument is a separate list element (never a shell string).
 *
 * Graph per segment i:
 *   [0:v]trim=start=S:end=E,setpts=PTS-STARTPTS[..]  (then /SPEED when sped)
 *   [0:a]atrim=start=S:end=E,asetpts=PTS-STARTPTS[..]  (then atempo when sped)
 * then:
 *   [v0]...[vN]concat=n=N:v=1:a=0[cv]
 *   [a0]...[aN]concat=n=N:v=0:a=1[outa]
 *   [cv]scale..pad..setsar,fps{grade}{transitions}{overlays}[outv]
 *
 * Overlay graphs chain in zIndex order:
 *   [k:v]format=rgba,scale..pad..,scale..,rotate..,colorchannelmixer=..[qN]
 *   [pN][qN]overlay=x=..:y=..:eof_action=repeat:enable='between(t,..,..)'[pN+1]
 */
object FFmpegCommandBuilder {

    /**
     * @param segments ordered source spans to cut and concatenate
     * @param effects beat-aligned effect triggers (usually from EffectScheduler)
     * @param colorGrade global brightness/contrast/saturation adjustments
     * @param overlayRenders resolved overlays (text rasterized, images copied);
     *        each render must have a [OverlayRender.file] on disk already
     * @param transitionDurationMs flash window used at clip transitions
     * @param keyframes grade automation keyframes on the destination timeline
     * @param keyframesEnabled whether keyframe animation is active for export
     */
    fun buildClip(
        input: String,
        output: String,
        segments: List<ClipSegment>,
        config: ExportConfig,
        hasAudio: Boolean,
        effects: List<EffectSpec> = emptyList(),
        hwEncode: String? = null,
        colorGrade: ColorGrade? = null,
        overlayRenders: List<OverlayRender> = emptyList(),
        transitionDurationMs: Long = 400L,
        keyframes: List<GradeKeyframe> = emptyList(),
        keyframesEnabled: Boolean = false,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        canvasBackground: CanvasBackground = CanvasBackground(),
        crop: CropConfig = CropConfig(),
        masterVolume: Float = 1f,
        audioFadeInMs: Long = 0L,
        audioFadeOutMs: Long = 0L,
        audioDucking: Float = 0f,
        voiceOverUri: String? = null,
    ): List<String> {
        if (segments.isEmpty()) {
            return listOf("-y", "-f", "lavfi", "-i", "color=c=black:s=16x16:d=0.1",
                "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo",
                "-shortest", "-c:v", codecFor(config, hwEncode), "-c:a", "aac",
                "-t", "0.1", output)
        }
        val args = ArrayList<String>()
        if (hwEncode != null) {
            args.add("-hwaccel")
            args.add("mediacodec")
        }
        args.add("-i")
        args.add(input)
        val bgImage = if (canvasBackground.type == BackgroundType.IMAGE &&
            !canvasBackground.imageUri.isNullOrBlank()
        ) canvasBackground.imageUri else null
        if (bgImage != null) {
            args.add("-loop")
            args.add("1")
            args.add("-i")
            args.add(bgImage)
        }
        val orderedRenders = orderedOverlays(overlayRenders)
        for (r in orderedRenders) {
            args.add("-loop")
            args.add("1")
            args.add("-i")
            args.add(r.file)
        }
        val voiceOn = !voiceOverUri.isNullOrBlank()
        if (voiceOn) {
            args.add("-i")
            args.add(voiceOverUri)
        }
        args.add("-filter_complex")
        args.add(buildFilterGraph(
            segments, config, hasAudio, effects, colorGrade, orderedRenders,
            transitionDurationMs, keyframes, keyframesEnabled, sourceWidth, sourceHeight,
            canvasBackground, crop, masterVolume, audioFadeInMs, audioFadeOutMs, audioDucking,
            voiceOverUri,
        ))
        args.add("-map")
        args.add("[outv]")
        if (hasAudio || voiceOn) {
            args.add("-map")
            args.add("[outa]")
        }
        args.add("-c:v")
        args.add(codecFor(config, hwEncode))
        args.add("-preset")
        args.add("veryfast")
        args.add("-c:a")
        args.add("aac")
        args.add("-b:a")
        args.add("${config.audioBitrate.kbps}k")
        args.add("-ar")
        args.add("44100")
        args.add("-pix_fmt")
        args.add("yuv420p")
        args.add("-movflags")
        args.add("+faststart")
        // Cap the output at the planned timeline length so a stream that runs
        // longer than the source video (e.g. an audio track that outlives the
        // picture) cannot push the export past the expected duration.
        val totalSec = segments.maxOfOrNull { it.destEndMs }?.div(1000.0) ?: 0.0
        if (totalSec > 0.0) {
            args.add("-t")
            args.add(fmt(totalSec))
        }
        args.add("-y")
        args.add(output)
        return args
    }

    fun buildFilterGraph(
        segments: List<ClipSegment>,
        config: ExportConfig,
        hasAudio: Boolean,
        effects: List<EffectSpec> = emptyList(),
        colorGrade: ColorGrade? = null,
        overlayRenders: List<OverlayRender> = emptyList(),
        transitionDurationMs: Long = 400L,
        keyframes: List<GradeKeyframe> = emptyList(),
        keyframesEnabled: Boolean = false,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        canvasBackground: CanvasBackground = CanvasBackground(),
        crop: CropConfig = CropConfig(),
        masterVolume: Float = 1f,
        audioFadeInMs: Long = 0L,
        audioFadeOutMs: Long = 0L,
        audioDucking: Float = 0f,
        voiceOverUri: String? = null,
    ): String {
        val sb = StringBuilder()
        val w = config.resolution.width
        val h = config.resolution.height

        segments.forEachIndexed { i, seg ->
            val s = seg.sourceStartMs / 1000.0
            val e = seg.sourceEndMs / 1000.0
            val d = e - s
            sb.append("[0:v]trim=start=").append(fmt(s)).append(":duration=").append(fmt(d))
                .append(",setpts=PTS-STARTPTS")
            if (seg.reversed) {
                sb.append(",reverse,setpts=PTS-STARTPTS")
            }
            if (seg.speed != 1f && seg.speed > 0f) {
                sb.append(",setpts=PTS/").append(fmt(seg.speed.toDouble()))
            }
            sb.append("[v").append(i).append("];")
        }
        if (hasAudio) {
            segments.forEachIndexed { i, seg ->
                val s = seg.sourceStartMs / 1000.0
                val e = seg.sourceEndMs / 1000.0
                val d = e - s
                sb.append("[0:a]atrim=start=").append(fmt(s)).append(":end=").append(fmt(e))
                    .append(",asetpts=PTS-STARTPTS")
                if (seg.reversed) {
                    sb.append(",areverse,asetpts=PTS-STARTPTS")
                }
                if (seg.speed != 1f && seg.speed > 0f) {
                    // atempo supports [0.5, 100]; normalize into that range.
                    val atempos = atempoChain(seg.speed)
                    for (a in atempos) sb.append(",atempo=").append(fmt(a))
                }
                sb.append("[a").append(i).append("];")
            }
        }
        val n = segments.size
        sb.append(segments.indices.joinToString("") { "[v$it]" })
            .append("concat=n=").append(n).append(":v=1:a=0[base];")
        if (hasAudio) {
            sb.append(segments.indices.joinToString("") { "[a$it]" })
                .append("concat=n=").append(n).append(":v=0:a=1[mixa];")
        }
        appendBackground(sb, canvasBackground, w, h, config.fps.fps, segments)
        val windows = gradeWindows(colorGrade, keyframes, keyframesEnabled, segments)
        windows.forEach { win ->
            val enable = if (windowsUseEnable(win, segments)) "between(t,${fmt(win.startSec)},${fmt(win.endSec)})" else null
            appendColorGrade(sb, win.grade, enable)
        }
        for (fx in effects) appendEffect(sb, fx, w, h, config.fps.fps)
        appendClipEffects(sb, segments, config.fps.fps, w, h)
        appendTransitions(sb, segments, transitionDurationMs)
        val ordered = orderedOverlays(overlayRenders)
        val totalMs = segments.maxOfOrNull { it.destEndMs } ?: 0L
        val voiceOn = !voiceOverUri.isNullOrBlank()
        val bgOffset = if (canvasBackground.type == BackgroundType.IMAGE &&
            !canvasBackground.imageUri.isNullOrBlank()
        ) 1 else 0
        val voiceIndex = 1 + bgOffset + ordered.size
        appendOverlayGraphs(sb, ordered, w, h, totalMs, sourceWidth, sourceHeight, crop, bgOffset)
        appendAudioTail(sb, segments, hasAudio, voiceOverUri, masterVolume, audioFadeInMs, audioFadeOutMs, audioDucking, voiceIndex)
        return sb.toString()
    }

    /**
     * Background handling. NONE: classic black letterbox pad. COLOR: pad with
     * the requested ARGB. BLUR: fill the canvas with a heavily blurred copy of
     * the video then composite the letterboxed content on top. IMAGE: an extra
     * [1:v] input (added in [buildClip] before the overlay inputs) fills the
     * canvas behind the letterboxed video. The scale/pad/fps/setsar chain is
     * always emitted so the downstream grade/effect/overlay chain sees a
     * canvas-size yuv420p stream exactly as before.
     */
    private fun appendBackground(
        sb: StringBuilder,
        bg: CanvasBackground,
        w: Int,
        h: Int,
        fps: Int,
        segments: List<ClipSegment>,
    ) {
        val base = "base"
        val padColor = when {
            bg.type == BackgroundType.COLOR -> colorHex(bg.colorArgb)
            else -> null
        }
        val padOpt = if (padColor != null) ":color=$padColor" else ""
        val scalePad = "[$base]scale=$w:$h:force_original_aspect_ratio=decrease," +
            "pad=$w:$h:(ow-iw)/2:(oh-ih)/2$padOpt,setsar=1,fps=$fps,format=yuv420p"
        when (bg.type) {
            BackgroundType.BLUR -> {
                val r = (bg.blurRadius * 0.4).coerceIn(2.0, 60.0)
                // [base] -> blurred full-bleed backdrop + letterboxed content copy
                sb.append("[$base]split[blursrc][content0];")
                sb.append("[blursrc]scale=$w:$h:force_original_aspect_ratio=increase,")
                    .append("crop=$w:$h,boxblur=luma_radius=").append(fmt(r))
                    .append(":luma_power=2[blurbg];")
                sb.append("[content0]scale=$w:$h:force_original_aspect_ratio=decrease,")
                    .append("pad=$w:$h:(ow-iw)/2:(oh-ih)/2,setsar=1[content];")
                sb.append("[blurbg][content]")
                    .append("overlay=0:0,setsar=1,fps=$fps,format=yuv420p")
            }
            BackgroundType.IMAGE -> {
                // [1:v] is the background image looped input; scale to fill then
                // place the letterboxed content on top of it.
                sb.append("[1:v]scale=$w:$h:force_original_aspect_ratio=increase,")
                    .append("crop=$w:$h,setsar=1[bgimg];")
                sb.append("[$base]scale=$w:$h:force_original_aspect_ratio=decrease,")
                    .append("pad=$w:$h:(ow-iw)/2:(oh-ih)/2,setsar=1[content];")
                sb.append("[bgimg][content]overlay=0:0,setsar=1,fps=$fps,format=yuv420p")
            }
            else -> sb.append(scalePad)
        }
    }

    /**
     * Drops renders that can never appear (no duration, missing file, empty
     * canvas) and orders them by draw order (zIndex ascending) so the ffmpeg
     * overlay chain composites bottom-to-top exactly like the preview.
     */
    internal fun orderedOverlays(renders: List<OverlayRender>): List<OverlayRender> =
        renders.asSequence()
            .filter { it.endMs > it.startMs && it.file.isNotBlank() && it.baseW > 0 && it.baseH > 0 }
            .sortedBy { it.zIndex }
            .toList()

    /**
     * Slices an overlay's lifetime into constant-transform windows for export.
     * With no (or a single) keyframe a render exports with its static transform.
     * With automation, the transform is sampled across the item window at a
     * bounded step and each change becomes a separate windowed composite.
     */
    internal fun overlayWindows(render: OverlayRender, totalMs: Long): List<OverlayWindow> {
        val start = render.startMs.coerceIn(0, totalMs)
        val end = render.endMs.coerceIn(0, totalMs)
        if (end <= start) return emptyList()
        if (render.keyframes.size < 2) {
            return listOf(OverlayWindow(start / 1000.0, end / 1000.0, evaluateOverlayFx(render, start)))
        }
        val spanStart = render.keyframes.minOf { it.atMs }.coerceIn(start, end)
        val spanEnd = render.keyframes.maxOf { it.atMs }.coerceIn(start, end)
        val spanMs = (spanEnd - spanStart).coerceAtLeast(1L)
        val step = spanMs / 300 + 80
        val out = ArrayList<OverlayWindow>()
        var anchor = start
        var cur = evaluateOverlayFx(render, anchor)
        var next = anchor + step
        while (next < end) {
            val fx = evaluateOverlayFx(render, next)
            if (fx != cur) {
                out += OverlayWindow(anchor / 1000.0, next / 1000.0, cur)
                anchor = next
                cur = fx
            }
            next += step
        }
        out += OverlayWindow(anchor / 1000.0, end / 1000.0, cur)
        return out
    }

    /**
     * Emits a color grade as an ffmpeg filter chain, matching the same
     * [ColorGrade] the preview consumes. Only core filters are used so the
     * lightweight runtime binary can always render it.
     *
     *  - eq:        brightness (brightness + exposure), contrast, saturation,
     *               per-channel gamma for temperature/tint
     *  - boxblur:   uniform blur (blur != 0)
     *  - vignette:  darkened corners (vignette != 0)
     *  - noise:     film grain (grain != 0)
     *  - unsharp:   sharpness (sharpness != 0)
     *
     * When [enableExpr] is supplied (a raw ffmpeg source expression without the
     * quotes) every emitted filter is windowed with :enable='...'. This is how
     * keyframe animation is exported: one grade chain per timeline slice.
     */
    private fun appendColorGrade(sb: StringBuilder, colorGrade: ColorGrade?, enableExpr: String? = null) {
        if (colorGrade == null) return
        val en = if (enableExpr != null) ":enable='$enableExpr'" else ""
        val brightness = colorGrade.brightness + colorGrade.exposure * 0.5f
        val ct0 = 1.0 + colorGrade.contrast * 0.6 + colorGrade.highlights * 0.15
        val ct = ct0.coerceIn(0.4, 1.9)
        val sat = (1.0 + colorGrade.saturation * 0.7).coerceIn(0.05, 2.0)
        val hasGrade = abs(brightness) > 0.0005f || abs(colorGrade.contrast) > 0.0005f ||
            abs(colorGrade.saturation) > 0.0005f || abs(colorGrade.temperature) > 0.0005f ||
            abs(colorGrade.tint) > 0.0005f || abs(colorGrade.highlights) > 0.0005f ||
            abs(colorGrade.shadows) > 0.0005f || abs(colorGrade.fade) > 0.0005f
        if (hasGrade) {
            val br = (brightness + colorGrade.shadows * 0.3f + colorGrade.fade * 0.35f)
                .coerceIn(-0.5f, 0.6f)
            val finalSat = sat * (1f - colorGrade.fade * 0.5f)
            val gammaR = (1.0 + colorGrade.temperature * 0.18 - colorGrade.tint * 0.1).coerceIn(0.7, 1.3)
            val gammaG = (1.0 + colorGrade.tint * 0.18).coerceIn(0.7, 1.3)
            val gammaB = (1.0 - colorGrade.temperature * 0.18 - colorGrade.tint * 0.1).coerceIn(0.7, 1.3)
            sb.append(",eq=brightness=").append(fmt(br.toDouble()))
                .append(":contrast=").append(fmt(ct))
                .append(":saturation=").append(fmt(finalSat.toDouble()))
                .append(":gamma_r=").append(fmt(gammaR))
                .append(":gamma_g=").append(fmt(gammaG))
                .append(":gamma_b=").append(fmt(gammaB))
                .append(en)
        }
        if (abs(colorGrade.blur) > 0.0005f) {
            val r = (colorGrade.blur * 8.0 + 0.5).coerceAtMost(20.0)
            sb.append(",boxblur=luma_radius=").append(fmt(r)).append(":luma_power=1")
                .append(en)
        }
        if (abs(colorGrade.vignette) > 0.0005f) {
            val angle = (Math.PI / 5.0 * (1.0 - colorGrade.vignette * 0.8)).coerceIn(Math.PI / 18.0, Math.PI / 2.0)
            sb.append(",vignette=angle=").append(fmt(angle))
                .append(en)
        }
        if (abs(colorGrade.grain) > 0.0005f) {
            sb.append(",noise=alls=").append(fmt(colorGrade.grain * 26.0)).append(":allf=t+u")
                .append(en)
        }
        if (abs(colorGrade.sharpness) > 0.0005f) {
            sb.append(",unsharp=luma_msize_x=5:luma_msize_y=5:luma_amount=")
                .append(fmt((colorGrade.sharpness * 0.6).toDouble()))
                .append(en)
        }
    }

    /**
     * Slices the destination timeline into [GradeWindow]s that reproduce the
     * keyframe interpolation at export. When automation is off (or too few
     * keyframes) a single full-range window with the base grade is returned so
     * the exported chain matches the classic (non-animated) behavior exactly.
     *
     * Sampling step shrinks with the animated span and is capped so the graph
     * never exceeds ~500 filters regardless of project length.
     */
    private fun gradeWindows(
        base: ColorGrade?,
        keyframes: List<GradeKeyframe>,
        enabled: Boolean,
        segments: List<ClipSegment>,
    ): List<GradeWindow> {
        val totalMs = segments.maxOfOrNull { it.destEndMs } ?: 0L
        val fallback = GradeWindow(0.0, totalMs / 1000.0, base ?: ColorGrade())
        if (totalMs <= 0L || !enabled || keyframes.size < 2) return listOf(fallback)

        val spanStart = keyframes.minOf { it.atMs }.coerceIn(0, totalMs)
        val spanEnd = keyframes.maxOf { it.atMs }.coerceIn(0, totalMs)
        val spanMs = (spanEnd - spanStart).coerceAtLeast(1L)
        val step = spanMs / 500 + 50
        val start = base ?: ColorGrade()
        val out = ArrayList<GradeWindow>()
        var anchor = 0L
        var cur = evaluateColorGrade(start, keyframes, anchor, true)
        var next = step
        while (next < totalMs) {
            val g = evaluateColorGrade(start, keyframes, next, true)
            if (g != cur) {
                out += GradeWindow(anchor / 1000.0, next / 1000.0, cur)
                anchor = next
                cur = g
            }
            next += step
        }
        out += GradeWindow(anchor / 1000.0, totalMs / 1000.0, cur)
        return out
    }

    /** A window only needs the enable guard when it isn't the full timeline. */
    private fun windowsUseEnable(win: GradeWindow, segments: List<ClipSegment>): Boolean {
        val totalSec = segments.maxOfOrNull { it.destEndMs }?.let { it / 1000.0 } ?: 0.0
        return !(win.startSec <= 0.001 && win.endSec >= totalSec - 0.001)
    }

    /** Per-clip effects attached to a timeline segment (from the Effects panel).
     *  Each is windowed to the segment's destination time range. */
    private fun appendClipEffects(sb: StringBuilder, segments: List<ClipSegment>, fps: Int, w: Int, h: Int) {
        for (seg in segments) {
            val kind = seg.effect
            if (kind == EffectKind.NONE) continue
            val t0 = seg.destStartMs / 1000.0
            val t1 = seg.destEndMs / 1000.0
            val amt = seg.effectStrength.coerceIn(0f, 1.5f)
            when (kind) {
                EffectKind.FLASH -> {
                    sb.append(",eq=brightness=0.3:enable='between(t,")
                        .append(fmt(t0)).append(",").append(fmt(t1))
                        .append(")*lt(mod(t,0.6),0.08)'")
                }
                EffectKind.BRIGHTNESS -> {
                    sb.append(",eq=brightness=").append(fmt((amt * 0.5).coerceAtLeast(0.05)))
                        .append(":enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
                }
                EffectKind.CONTRAST -> {
                    sb.append(",eq=contrast=").append(fmt((amt * 0.5 + 1.0).coerceAtMost(1.8)))
                        .append(":enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
                }
                EffectKind.GLITCH -> {
                    sb.append(",eq=saturation=0.15:enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
                    sb.append(",hue=H=0.03*sin(2*PI*t*30):enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
                }
                EffectKind.RGBSPLIT -> {
                    sb.append(",eq=saturation=0.3:enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
                    sb.append(",hue=H=0.02*sin(2*PI*t*24):enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
                }
                EffectKind.ZOOM -> {
                    val zoom = (amt * 0.3f).coerceAtLeast(0.05f)
                    val sF = (t0 * fps).roundToInt() + 1
                    val eF = (t1 * fps).roundToInt().coerceAtLeast(sF + 1)
                    sb.append(",zoompan=z='if(between(on,").append(sF).append(",").append(eF)
                        .append("),1+").append(fmt(zoom.toDouble()))
                        .append("*sin((on-").append(sF).append(")*PI/3.5),1)'")
                        // zoompan defaults its output size to the SOURCE frame
                        // dims, ignoring its input — pin it to the canvas size so
                        // the zoom does not shrink the letterboxed frame back to
                        // source size.
                        .append(":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:fps=30:s=")
                        .append(w).append('x').append(h)
                }
                EffectKind.SHAKE -> {
                    val amp = (amt * 20f).coerceAtLeast(2f)
                    val sF = (t0 * fps).roundToInt()
                    val eF = (t1 * fps).roundToInt().coerceAtLeast(sF + 1)
                    sb.append(",crop=iw:ih:x='if(between(n,").append(sF).append(",").append(eF)
                        .append("),").append(fmt(amp.toDouble())).append("*sin(n*2),0)'")
                        .append(":y='if(between(n,").append(sF).append(",").append(eF)
                        .append("),").append(fmt(amp.toDouble())).append("*cos(n*2.5),0)'")
                }
                else -> Unit
            }
        }
    }

    /** Fade-in window at clip boundaries that carry a transition. */
    private fun appendTransitions(sb: StringBuilder, segments: List<ClipSegment>, durationMs: Long) {
        if (durationMs <= 0L) return
        for (seg in segments) {
            if (seg.transition.isNullOrBlank() || seg.destStartMs <= 0L) continue
            val t0 = seg.destStartMs / 1000.0
            val t1 = (seg.destStartMs + durationMs) / 1000.0
            sb.append(",fade=t=in:st=").append(fmt(t0)).append(":d=").append(fmt(durationMs / 1000.0))
                .append(":enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
        }
    }

    /**
     * Chains every overlay composite bottom-to-top. Each render becomes an
     * rgba stream that is (1) fitted into its base box, (2) scaled by the
     * authored transform, (3) rotated (transparent fill), (4) opacity scaled,
     * then composited at the normalized centre minus half the rotated bbox.
     * With no composites the base stream is labelled [outv] directly.
     */
    private fun appendOverlayGraphs(sb: StringBuilder, renders: List<OverlayRender>, w: Int, h: Int, totalMs: Long, sourceWidth: Int = 0, sourceHeight: Int = 0, crop: CropConfig = CropConfig(), bgOffset: Int = 0) {
        // The base video is scaled into the w×h canvas with aspect preserved and
        // letterboxed ([base]scale=..:force_original_aspect_ratio=decrease,pad=..).
        // Overlay coordinates are RELATIVE TO THE VIDEO CONTENT rect, matching
        // the editor preview, so a source whose aspect differs from the canvas
        // places overlays on the actual video pixels — never the letterbox bars.
        val sw = sourceWidth.toFloat()
        val sh = sourceHeight.toFloat()
        val hasDims = sw > 0f && sh > 0f
        val scale = if (hasDims) minOf(w / sw, h / sh) else 1f
        val vw = sw * scale
        val vh = sh * scale
        val vx = (w - vw) / 2.0
        val vy = (h - vh) / 2.0
        val composites = ArrayList<Pair<Int, OverlayWindow>>()
        renders.forEachIndexed { k, render ->
            overlayWindows(render, totalMs).forEach { composites += k to it }
        }
        if (composites.isEmpty()) {
            if (crop.enabled) {
                appendCrop(sb, crop, w, h, vw.toDouble(), vh.toDouble(), vx, vy, null)
            } else {
                sb.append("[outv]")
            }
            return
        }
        // Label the base/scale/grade/effect chain (which buildFilterGraph left
        // with an anonymous tail). Overlays composite onto [styled] so the
        // letterboxed video rect and any color grade are applied UNDER each
        // overlay, matching the preview. `[styled]` has a single consumer, so
        // the base chain is no longer orphaned/dropped.
        sb.append("[styled]")
        var prev = "styled"
        composites.forEachIndexed { idx, (k, win) ->
            val render = renders[k]
            val next = if (idx == composites.lastIndex) {
                if (crop.enabled) "precrop" else "outv"
            } else "p${idx + 1}"
            val sw2 = render.baseW * win.fx.scaleX
            val sh2 = render.baseH * win.fx.scaleY
            sb.append(";[${bgOffset + k + 1}:v]format=rgba,scale=").append(render.baseW).append(':').append(render.baseH)
                .append(":force_original_aspect_ratio=decrease,pad=").append(render.baseW).append(':').append(render.baseH)
                .append(":(ow-iw)/2:(oh-ih)/2:color=black@0,scale=")
                .append(sw2.roundToInt().coerceAtLeast(1)).append(':').append(sh2.roundToInt().coerceAtLeast(1))
            val rot = win.fx.rotation
            if (abs(rot) > 0.01f) {
                sb.append(",rotate=").append(fmt(Math.toRadians(rot.toDouble()))).append(":c=black@0")
            }
            val op = win.fx.opacity.coerceIn(0f, 1f)
            if (op < 0.999f) {
                sb.append(",colorchannelmixer=aa=").append(fmt(op.toDouble()))
            }
            // Chroma key: punch out the key colour from the overlay content. The
            // key must run after the base scale but before the mask so a colour
            // key + shape mask can both apply (mask alpha wins when combined).
            val chroma = render.chromaKeyColor
            if (chroma != null) {
                sb.append(",colorkey=color=").append(colorHex(chroma.toLong()))
                    .append(":similarity=").append(fmt((render.chromaKeySimilarity.coerceIn(0f, 1f) * 0.3).toDouble()))
                    .append(":blend=0.1")
            }
            // Shape mask: drive the alpha channel with a geq expression so the
            // overlay is visible only inside the mask region (inverted/flip).
            val mask = render.mask
            if (mask.isActive) {
                appendMaskAlpha(sb, mask)
            }
            sb.append("[q").append(idx).append("]")
            // Position the centre at (vx + fx.x * vw, vy + fx.y * vh), i.e.
            // inside the letterboxed video rect; offset the top-left by half
            // the ROTATED bounding box. ffmpeg treats every `[label]` run after
            // a filter as that filter's output labels, so the composite must
            // start a NEW subgraph (`;`) before the input labels.
            val rad = Math.toRadians(rot.toDouble())
            val c = abs(cos(rad))
            val s = abs(sin(rad))
            val bw = sw2 * c + sh2 * s
            val bh = sw2 * s + sh2 * c
            val ox = (vx + win.fx.x * vw - bw / 2.0).roundToInt()
            val oy = (vy + win.fx.y * vh - bh / 2.0).roundToInt()
            sb.append(";[").append(prev).append("][q").append(idx).append("]overlay=x=").append(ox)
                .append(":y=").append(oy).append(":eof_action=repeat:enable='between(t,")
                .append(fmt(win.startSec)).append(",").append(fmt(win.endSec)).append(")'[")
                .append(next).append("]")
            prev = next
        }
        if (crop.enabled) {
            appendCrop(sb, crop, w, h, vw.toDouble(), vh.toDouble(), vx, vy, "precrop")
        }
    }

    /** Custom crop region relative to the letterboxed content rect. */
    private fun appendCrop(
        sb: StringBuilder,
        crop: CropConfig,
        w: Int,
        h: Int,
        vw: Double,
        vh: Double,
        vx: Double,
        vy: Double,
        fromLabel: String?,
    ) {
        if (!crop.enabled) return
        // Fall back to the full canvas when source dims are unknown so a crop
        // configured before the source is probed cannot collapse to 0 pixels.
        val cwBasis = if (vw > 0) vw else w.toDouble()
        val chBasis = if (vh > 0) vh else h.toDouble()
        val cxBasis = if (vw > 0) vx else 0.0
        val cyBasis = if (vh > 0) vy else 0.0
        val cw = (crop.wFraction.coerceIn(0.01f, 1f) * cwBasis).roundToInt().coerceAtLeast(2)
        val ch = (crop.hFraction.coerceIn(0.01f, 1f) * chBasis).roundToInt().coerceAtLeast(2)
        val cx = (cxBasis + crop.xFraction.coerceIn(0f, 1f) * cwBasis).roundToInt()
        val cy = (cyBasis + crop.yFraction.coerceIn(0f, 1f) * chBasis).roundToInt()
        if (fromLabel != null) {
            sb.append(";[").append(fromLabel).append("]")
        } else {
            sb.append(",")
        }
        sb.append("crop=").append(cw).append(':').append(ch).append(':').append(cx).append(':').append(cy)
            .append("[outv]")
    }

    /**
     * Emits a geq expression that drives the overlay's alpha channel so only
     * the masked region is visible. Every shape uses normalized coordinates
     * (X,Y in pixels, W,H the frame size) so the mask tracks the overlay's own
     * box (it is applied after the overlay's scale/rotate stage). Inverted
     * masks flip the region. Feather softens the edge by blending toward 0.
     */
    private fun appendMaskAlpha(sb: StringBuilder, mask: MaskConfig) {
        val x0 = mask.x - mask.width / 2f
        val x1 = mask.x + mask.width / 2f
        val y0 = mask.y - mask.height / 2f
        val y1 = mask.y + mask.height / 2f
        val cx = mask.x
        val cy = mask.y
        val rw = mask.width / 2f
        val rh = mask.height / 2f
        // ffmpeg's expression parser rejects the < / <= operators inside if();
        // use the function forms (lt/between) exclusively.
        val expr: String = when (mask.shape) {
            MaskShape.RECTANGLE ->
                "if(between(X/W,$x0,$x1)*between(Y/H,$y0,$y1),255,0)"
            MaskShape.ELLIPSE ->
                "if(lt(pow((X/W-$cx)/$rw,2)+pow((Y/H-$cy)/$rh,2),1),255,0)"
            MaskShape.SPLIT ->
                "if(lt(X/W+Y/H,${mask.x + mask.height / 2f}),255,0)"
            MaskShape.SHUTTER -> {
                val lines = (mask.height * 10).roundToInt().coerceAtLeast(2)
                "if(lt(mod(floor((Y/H-$cy)*$lines),2),1),255,0)"
            }
            MaskShape.HEART ->
                "if(lt(pow((X/W-$cx)*3.2,2)+pow((Y/H-$cy)*3.2-0.1,2),1),255,0)"
            MaskShape.STAR -> {
                val points = 5
                "if(lt(mod(atan2(Y/H-$cy,X/W-$cx)*$points/6.283,1),${0.28f + mask.width * 0.2f}),255,0)"
            }
            MaskShape.NONE -> "255"
        }
        // Invert flips the mask; feather scales the final alpha so the mask
        // softens toward transparent (deterministic, no neighbour sampling).
        var a = if (mask.inverted) "(255-($expr))" else "($expr)"
        val f = mask.feather.coerceIn(0f, 1f)
        if (f > 0f) {
            a = "($a)*${fmt((1f - f * 0.8).toDouble())}"
        }
        // This ffmpeg build rejects alpha-only geq ("A luminance or RGB
        // expression is mandatory"), so pass the luma through unchanged.
        sb.append(",geq=lum='lum(X,Y)':a='").append(a).append("'")
    }

    /**
     * Final audio stage. The concat loop already produced `[mixa]` when the
     * project has a music/audio track; this appends:
     *  - master volume (0..2, from the loudness slider)
     *  - fade-in / fade-out on the whole mix
     *  - optional voice-over track mixed on top; when ducking is active the
     *    music is sidechain-compressed by the voice track so it ducks under it.
     *
     * When there is no music track but a voice-over exists, the voice track
     * becomes the sole audio stream.
     */
    private fun appendAudioTail(
        sb: StringBuilder,
        segments: List<ClipSegment>,
        hasAudio: Boolean,
        voiceOverUri: String?,
        masterVolume: Float,
        audioFadeInMs: Long,
        audioFadeOutMs: Long,
        audioDucking: Float,
        voiceIndex: Int,
    ) {
        val voiceOn = !voiceOverUri.isNullOrBlank()
        val totalSec = (segments.maxOfOrNull { it.destEndMs } ?: 0L) / 1000.0
        val filters = ArrayList<String>()
        val vol = masterVolume.coerceIn(0f, 2f)
        if (hasAudio) {
            if (abs(vol - 1f) > 0.005f) {
                filters += "volume=${fmt(vol.toDouble())}"
            }
            if (audioFadeInMs > 0L) {
                filters += "afade=t=in:st=0:d=${fmt(audioFadeInMs / 1000.0)}"
            }
            if (audioFadeOutMs > 0L && totalSec > 0.0) {
                val st = (totalSec - audioFadeOutMs / 1000.0).coerceAtLeast(0.0)
                filters += "afade=t=out:st=${fmt(st)}:d=${fmt(audioFadeOutMs / 1000.0)}"
            }
            sb.append(";[mixa]")
            if (filters.isEmpty()) {
                // anull is the identity audio filter used purely to rename the
                // concat output to [music] so the duck/mix stage has a label.
                sb.append("anull")
            } else {
                sb.append(filters.joinToString(","))
            }
            sb.append("[music]")
            if (voiceOn && audioDucking > 0f) {
                // Duck the music under the voice: sidechaincompress reads the
                // voice as the control signal. threshold/ratio tuned so 0..1
                // maps to a tasteful dip, 1.0 = full duck.
                val thresh = 0.02 + (1f - audioDucking.coerceIn(0f, 1f)) * 0.15
                val ratio = 2 + audioDucking.coerceIn(0f, 1f) * 18
                sb.append(";[music][").append(voiceIndex).append(":a]")
                    .append("sidechaincompress=threshold=").append(fmt(thresh.toDouble()))
                    .append(":ratio=").append(fmt(ratio.toDouble()))
                    .append(":attack=5:release=150[ducked];")
                sb.append("[ducked][").append(voiceIndex).append(":a]amix=inputs=2:duration=first[outa]")
            } else if (voiceOn) {
                sb.append(";[music][").append(voiceIndex).append(":a]amix=inputs=2:duration=first[outa]")
            } else {
                sb.append(";[music]anull[outa]")
            }
            sb.append(";")
        } else if (voiceOn) {
            // No music track: the voice track alone becomes the audio stream.
            sb.append(";[").append(voiceIndex).append(":a]volume=")
                .append(fmt(vol.toDouble()))
                .append("[outa];")
        }
    }

    private fun appendEffect(sb: StringBuilder, e: EffectSpec, w: Int, h: Int, fps: Int) {
        val t0 = e.atDestMs / 1000.0
        val t1 = (e.atDestMs + e.durationMs) / 1000.0
        when (e.kind) {
            EffectKind.FLASH -> {
                sb.append(",lutyuv=y=250:u=128:v=128:enable='between(t,")
                    .append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
            }
            EffectKind.ZOOM -> {
                // zoompan does not support the `enable` timeline option in this
                // ffmpeg build, so gate the zoom via the output frame counter
                // `on` inside the z expression instead.
                val zoom = (e.amount * 0.3f).coerceAtLeast(0.05f)
                val sF = (t0 * fps).roundToInt() + 1
                val eF = (t1 * fps).roundToInt().coerceAtLeast(sF + 1)
                sb.append(",zoompan=z='if(between(on,").append(sF).append(",").append(eF)
                    .append("),1+").append(fmt(zoom.toDouble()))
                    .append("*sin((on-").append(sF).append(")*PI/3.5),1)'")
                    // zoompan defaults its output size to the SOURCE frame dims,
                    // ignoring its input — pin it to the canvas size so the zoom
                    // does not shrink the letterboxed frame back to source size.
                    .append(":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:fps=30:s=")
                    .append(w).append('x').append(h)
            }
            EffectKind.SHAKE -> {
                // crop does not support `enable` either (and `on` is not a
                // valid crop expression variable), so gate with `n` directly.
                val amp = (e.amount * 20f).coerceAtLeast(2f)
                val sF = (t0 * fps).roundToInt()
                val eF = (t1 * fps).roundToInt().coerceAtLeast(sF + 1)
                sb.append(",crop=iw:ih:x='if(between(n,").append(sF).append(",").append(eF)
                    .append("),").append(fmt(amp.toDouble())).append("*sin(n*2),0)'")
                    .append(":y='if(between(n,").append(sF).append(",").append(eF)
                    .append("),").append(fmt(amp.toDouble())).append("*cos(n*2.5),0)'")
            }
            EffectKind.BRIGHTNESS -> {
                val amt = (e.amount * 0.5).coerceAtLeast(0.05)
                sb.append(",eq=brightness=").append(fmt(amt)).append(":enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
            }
            EffectKind.CONTRAST -> {
                val amt = (e.amount * 0.5 + 1.0).coerceAtMost(1.8)
                sb.append(",eq=contrast=").append(fmt(amt)).append(":enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
            }
            EffectKind.GLITCH -> {
                sb.append(",eq=saturation=0.2:enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
                sb.append(",hue=H=0.02*sin(2*PI*t*30):enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
            }
            else -> Unit
        }
    }

    /** atempo is limited to [0.5, 100]; chain when speed is below 0.5. */
    private fun atempoChain(speed: Float): List<Double> {
        var s = speed.toDouble().coerceAtLeast(0.0001)
        val out = ArrayList<Double>()
        while (s < 0.5) {
            out.add(0.5)
            s /= 0.5
        }
        out.add(s.coerceIn(0.5, 100.0))
        return out
    }

    /** Hallucination guard: format without trailing junk. */
    private fun fmt(v: Double): String {
        return String.format(Locale.US, "%.3f", v)
    }

    /** ARGB (0xAARRGGBB) -> ffmpeg hex `0xRRGGBB` (alpha ignored for canvas). */
    private fun colorHex(argb: Long): String {
        val rgb = (argb.toInt() and 0xFFFFFF)
        return String.format(Locale.US, "0x%06X", rgb)
    }

    private fun codecFor(config: ExportConfig, hwEncode: String?): String {
        val base = if (config.videoCodec.name == "H265") "265" else "264"
        return "libx$base"
    }
}
