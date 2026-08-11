package dev.phonk.editor.ffmpeg

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.GradeKeyframe
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
        val orderedRenders = orderedOverlays(overlayRenders)
        for (r in orderedRenders) {
            args.add("-loop")
            args.add("1")
            args.add("-i")
            args.add(r.file)
        }
        args.add("-filter_complex")
        args.add(buildFilterGraph(segments, config, hasAudio, effects, colorGrade, orderedRenders, transitionDurationMs, keyframes, keyframesEnabled, sourceWidth, sourceHeight))
        args.add("-map")
        args.add("[outv]")
        if (hasAudio) {
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
                .append("concat=n=").append(n).append(":v=0:a=1[outa];")
        }
        sb.append("[base]scale=").append(w).append(':').append(h)
            .append(":force_original_aspect_ratio=decrease,pad=").append(w).append(':').append(h)
            .append(":(ow-iw)/2:(oh-ih)/2,setsar=1,fps=").append(config.fps.fps)
            // Force software frames before grade/effect/overlay filters: with
            // mediacodec hwaccel the decoded stream is hardware-backed and the
            // `overlay` (and several other) filters reject hw frames.
            .append(",format=yuv420p")
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
        appendOverlayGraphs(sb, ordered, w, h, totalMs, sourceWidth, sourceHeight)
        return sb.toString()
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
    private fun appendOverlayGraphs(sb: StringBuilder, renders: List<OverlayRender>, w: Int, h: Int, totalMs: Long, sourceWidth: Int = 0, sourceHeight: Int = 0) {
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
            sb.append("[outv]")
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
            val next = if (idx == composites.lastIndex) "outv" else "p${idx + 1}"
            val sw2 = render.baseW * win.fx.scaleX
            val sh2 = render.baseH * win.fx.scaleY
            sb.append(";[${k + 1}:v]format=rgba,scale=").append(render.baseW).append(':').append(render.baseH)
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

    private fun codecFor(config: ExportConfig, hwEncode: String?): String {
        val base = if (config.videoCodec.name == "H265") "265" else "264"
        return "libx$base"
    }
}
