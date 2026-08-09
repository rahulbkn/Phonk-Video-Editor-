package dev.phonk.editor.ffmpeg

import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.TextLayer
import java.util.Locale

/** A beat-aware effect trigger on the destination timeline. */
data class EffectSpec(
    val atDestMs: Long,
    val durationMs: Long,
    val kind: EffectKind,
    val amount: Float,
)

/** Color-grade offsets applied to the whole picture at render time. */
data class ColorGrade(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
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
 *   [cv]scale..pad..setsar,fps{grade}{transitions}{texts}{overlays}[outv]
 */
object FFmpegCommandBuilder {

    /**
     * @param segments ordered source spans to cut and concatenate
     * @param effects beat-aligned effect triggers (usually from EffectScheduler)
     * @param colorGrade global brightness/contrast/saturation adjustments
     * @param texts text overlays rendered with drawtext (requires [fontPath])
     * @param overlays image overlays (each needs an entry in [overlayFiles])
     * @param overlayFiles overlay id -> local file path (copied to cache already)
     * @param transitionDurationMs flash window used at clip transitions
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
        texts: List<TextLayer> = emptyList(),
        overlays: List<OverlayLayer> = emptyList(),
        overlayFiles: Map<String, String> = emptyMap(),
        transitionDurationMs: Long = 400L,
        fontPath: String? = null,
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
        val localOverlays = overlays.filter { overlayFiles.containsKey(it.id) }
        for (ov in localOverlays) {
            args.add("-loop")
            args.add("1")
            args.add("-i")
            args.add(overlayFiles.getValue(ov.id))
        }
        args.add("-filter_complex")
        args.add(buildFilterGraph(segments, config, hasAudio, effects, colorGrade, texts, localOverlays, transitionDurationMs, fontPath))
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
        texts: List<TextLayer> = emptyList(),
        overlays: List<OverlayLayer> = emptyList(),
        transitionDurationMs: Long = 400L,
        fontPath: String? = null,
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
        if (colorGrade != null && (colorGrade.brightness != 0f || colorGrade.contrast != 0f || colorGrade.saturation != 0f)) {
            val br = (colorGrade.brightness * 0.5).coerceIn(-0.5, 0.5)
            val ct = (1.0 + colorGrade.contrast * 0.6).coerceIn(0.4, 1.9)
            val sat = (1.0 + colorGrade.saturation * 0.7).coerceIn(0.0, 2.0)
            sb.append(",eq=brightness=").append(fmt(br.toDouble()))
                .append(":contrast=").append(fmt(ct))
                .append(":saturation=").append(fmt(sat))
        }
        for (fx in effects) appendEffect(sb, fx, w, h)
        appendTransitions(sb, segments, transitionDurationMs)
        appendTextOverlays(sb, texts, fontPath, w, h)
        if (overlays.isEmpty()) {
            sb.append("[outv]")
        } else {
            sb.append("[p0]")
            appendImageOverlays(sb, overlays)
        }
        return sb.toString()
    }

    /** Flash window at clip boundaries that carry a transition. */
    private fun appendTransitions(sb: StringBuilder, segments: List<ClipSegment>, durationMs: Long) {
        if (durationMs <= 0L) return
        for (seg in segments) {
            if (seg.transition.isNullOrBlank() || seg.destStartMs <= 0L) continue
            val t0 = seg.destStartMs / 1000.0
            val t1 = (seg.destStartMs + durationMs) / 1000.0
            sb.append(",lutyuv=y=250:u=128:v=128:enable='between(t,")
                .append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
        }
    }

    private fun appendTextOverlays(sb: StringBuilder, texts: List<TextLayer>, fontPath: String?, w: Int, h: Int) {
        if (fontPath.isNullOrBlank()) return
        texts.forEachIndexed { i, t ->
            val t0 = t.startMs / 1000.0
            val t1 = t.endMs / 1000.0
            if (t1 <= t0) return@forEachIndexed
            val textEsc = escapeDrawText(t.text)
            val fontSize = t.fontSize.toInt().coerceIn(8, 200)
            val alpha = (t.opacity.coerceIn(0f, 1f) * 255).toInt()
            val colorHex = String.format(Locale.US, "0x%02X%06X", alpha, (t.colorArgb and 0xFFFFFF))
            sb.append(",drawtext=fontfile=").append(fontPath)
                .append(":text='").append(textEsc).append("'")
                .append(":fontsize=").append(fontSize)
                .append(":fontcolor=").append(colorHex)
                .append(":x=(w-text_w)/2:y=(h-text_h)/2")
                .append(":enable='between(t,").append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
        }
    }

    private fun appendImageOverlays(sb: StringBuilder, overlays: List<OverlayLayer>) {
        if (overlays.isEmpty()) return
        var prev = "p0"
        overlays.forEachIndexed { i, ov ->
            val t0 = ov.startMs / 1000.0
            val t1 = ov.endMs / 1000.0
            if (t1 <= t0) return@forEachIndexed
            val isLast = i == overlays.lastIndex
            val next = if (isLast) "outv" else "p${i + 1}"
            sb.append(";[").append(prev).append("][").append(i + 1).append(":v]overlay=x=0:y=0:eof_action=repeat:enable='between(t,")
                .append(fmt(t0)).append(",").append(fmt(t1)).append(")'[").append(next).append("]")
            prev = next
        }
    }

    private fun appendEffect(sb: StringBuilder, e: EffectSpec, w: Int, h: Int) {
        val t0 = e.atDestMs / 1000.0
        val t1 = (e.atDestMs + e.durationMs) / 1000.0
        when (e.kind) {
            EffectKind.FLASH -> {
                sb.append(",lutyuv=y=250:u=128:v=128:enable='between(t,")
                    .append(fmt(t0)).append(",").append(fmt(t1)).append(")'")
            }
            // ZOOM/SHAKE rely on timeline-safe techniques not available in the
            // bundled build; keep them as no-ops so export can complete.
            EffectKind.ZOOM -> Unit
            EffectKind.SHAKE -> Unit
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
                sb.append(",hue=H=0.02*sin(2*PI*t*30):enable='between(t,").append(fmt(t0)).append(",").append(fmt(t0 + e.durationMs / 1000.0)).append(")'")
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

    /** Escape a string for drawtext (two layers: graph quoting + drawtext). */
    private fun escapeDrawText(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\'' -> sb.append("\\'")
                ':' -> sb.append("\\:")
                '%' -> sb.append("%%")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
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
