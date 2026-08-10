package dev.phonk.editor.model

import kotlin.math.abs

/**
 * The single source of truth for the global color grade. Every continuous
 * adjustment the user can make lives in this one value object, and the same
 * instance drives both the real-time preview pipeline and the export filter
 * graph so the two can never drift apart.
 *
 * Range conventions follow the existing UI: bipolar params are -1..1 around a
 * neutral 0, unipolar (fade/blur/vignette/grain) are 0..1 around none.
 */
data class ColorGrade(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val fade: Float = 0f,
    val sharpness: Float = 0f,
    val blur: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
) {
    val isNeutral: Boolean
        get() = all(zero = true)

    private fun all(zero: Boolean): Boolean {
        val threshold = if (zero) 0.0005f else -1f
        return listOf(
            brightness, contrast, saturation, exposure, temperature, tint,
            highlights, shadows, fade, sharpness, blur, vignette, grain,
        ).all { if (zero) abs(it) < threshold else it >= threshold }
    }

    fun get(param: GradeParam): Float = param.get(this)

    fun with(param: GradeParam, value: Float): ColorGrade = param.set(this, value.coerceIn(param.range))

    /** Linearly interpolates every channel between two grades. */
    fun lerp(other: ColorGrade, t: Float): ColorGrade {
        val f = t.coerceIn(0f, 1f)
        fun l(a: Float, b: Float) = a + (b - a) * f
        return ColorGrade(
            brightness = l(brightness, other.brightness),
            contrast = l(contrast, other.contrast),
            saturation = l(saturation, other.saturation),
            exposure = l(exposure, other.exposure),
            temperature = l(temperature, other.temperature),
            tint = l(tint, other.tint),
            highlights = l(highlights, other.highlights),
            shadows = l(shadows, other.shadows),
            fade = l(fade, other.fade),
            sharpness = l(sharpness, other.sharpness),
            blur = l(blur, other.blur),
            vignette = l(vignette, other.vignette),
            grain = l(grain, other.grain),
        )
    }
}

/** Every continuous grade parameter plus its allowed UI range. */
enum class GradeParam(val range: ClosedFloatingPointRange<Float>) {
    BRIGHTNESS(-1f..1f),
    CONTRAST(-1f..1f),
    SATURATION(-1f..1f),
    EXPOSURE(-1f..1f),
    TEMPERATURE(-1f..1f),
    TINT(-1f..1f),
    HIGHLIGHTS(-1f..1f),
    SHADOWS(-1f..1f),
    FADE(0f..1f),
    SHARPNESS(-1f..1f),
    BLUR(0f..1f),
    VIGNETTE(0f..1f),
    GRAIN(0f..1f);

    fun get(g: ColorGrade): Float = when (this) {
        BRIGHTNESS -> g.brightness
        CONTRAST -> g.contrast
        SATURATION -> g.saturation
        EXPOSURE -> g.exposure
        TEMPERATURE -> g.temperature
        TINT -> g.tint
        HIGHLIGHTS -> g.highlights
        SHADOWS -> g.shadows
        FADE -> g.fade
        SHARPNESS -> g.sharpness
        BLUR -> g.blur
        VIGNETTE -> g.vignette
        GRAIN -> g.grain
    }

    fun set(g: ColorGrade, v: Float): ColorGrade = when (this) {
        BRIGHTNESS -> g.copy(brightness = v)
        CONTRAST -> g.copy(contrast = v)
        SATURATION -> g.copy(saturation = v)
        EXPOSURE -> g.copy(exposure = v)
        TEMPERATURE -> g.copy(temperature = v)
        TINT -> g.copy(tint = v)
        HIGHLIGHTS -> g.copy(highlights = v)
        SHADOWS -> g.copy(shadows = v)
        FADE -> g.copy(fade = v)
        SHARPNESS -> g.copy(sharpness = v)
        BLUR -> g.copy(blur = v)
        VIGNETTE -> g.copy(vignette = v)
        GRAIN -> g.copy(grain = v)
    }

    companion object {
        val entries: List<GradeParam> = listOf(
            BRIGHTNESS, CONTRAST, SATURATION, EXPOSURE, TEMPERATURE, TINT,
            HIGHLIGHTS, SHADOWS, FADE, SHARPNESS, BLUR, VIGNETTE, GRAIN,
        )
    }
}

/** Automation node: a full color-grade snapshot at a destination timestamp. */
data class GradeKeyframe(
    val atMs: Long,
    val grade: ColorGrade,
)

/** Projection of a project's grade fields into the shared model. */
object ColorGradeMaps {
    fun of(project: PhonkProject): ColorGrade = ColorGrade(
        brightness = project.brightness,
        contrast = project.contrast,
        saturation = project.saturation,
        exposure = project.exposure,
        temperature = project.temperature,
        tint = project.tint,
        highlights = project.highlights,
        shadows = project.shadows,
        fade = project.fade,
        sharpness = project.sharpness,
        blur = project.blur,
        vignette = project.vignette,
        grain = project.grain,
    )

    /** Reads a grade back into the project fields (single source of truth). */
    fun apply(project: PhonkProject, grade: ColorGrade): PhonkProject = project.copy(
        brightness = grade.brightness,
        contrast = grade.contrast,
        saturation = grade.saturation,
        exposure = grade.exposure,
        temperature = grade.temperature,
        tint = grade.tint,
        highlights = grade.highlights,
        shadows = grade.shadows,
        fade = grade.fade,
        sharpness = grade.sharpness,
        blur = grade.blur,
        vignette = grade.vignette,
        grain = grade.grain,
    )
}

/**
 * Evaluates the active color grade at a destination timestamp, applying the
 * project's keyframes when automation is enabled (linear interpolation; the
 * base grade is used outside the keyframe span or when disabled).
 */
fun evaluateColorGrade(
    base: ColorGrade,
    keyframes: List<GradeKeyframe>,
    destMs: Long,
    enabled: Boolean,
): ColorGrade {
    if (!enabled || keyframes.size < 2) return base
    val sorted = keyframes.sortedBy { it.atMs }
    val first = sorted.first()
    val last = sorted.last()
    // Outside the keyframe span the static base grade stays active.
    if (destMs < first.atMs || destMs > last.atMs) return base
    for (i in 0 until sorted.lastIndex) {
        val a = sorted[i]
        val b = sorted[i + 1]
        if (destMs in a.atMs..b.atMs) {
            val span = (b.atMs - a.atMs).coerceAtLeast(1L)
            return a.grade.lerp(b.grade, (destMs - a.atMs).toFloat() / span)
        }
    }
    return base
}

/** Snapshot of what the beat engine is doing at a given media time. */
data class BeatFrame(
    val beatProgress: Float = 0f,
    val beatIndex: Int = 0,
    val beatStrength: Float = 0f,
    val isBeat: Boolean = false,
    val isDrop: Boolean = false,
    val dropStrength: Float = 0f,
    val timeToNextBeatMs: Long = Long.MAX_VALUE,
    val timeToNextDropMs: Long = Long.MAX_VALUE,
)

/** Pure, allocation-light beat/drop evaluation against the media clock. */
object BeatSyncEngine {
    /** Windows (ms) after the marker within which a beat/drop is "active". */
    private const val BEAT_WINDOW_MS = 90L
    private const val DROP_WINDOW_MS = 220L

    /**
     * @param mediaMs timestamp on the SAME clock as [BeatMarker.timestampMs]
     *                and [DropMarker.timestampMs] (source time).
     */
    fun frame(
        beats: List<BeatMarker>,
        drops: List<DropMarker>,
        mediaMs: Long,
    ): BeatFrame {
        // Fast scan: beats/drops are sorted by analysis; fall back to a linear
        // scan for the rare case they are not.
        var prev = -1
        var next = -1
        var prevStrength = 0f
        var beatIndex = 0
        var prevMs = mediaMs
        for (i in beats.indices) {
            val t = beats[i].timestampMs.toLong()
            if (t <= mediaMs) {
                prev = i
                prevMs = t
                prevStrength = beats[i].confidence
                beatIndex = beats[i].beatIndex
            } else {
                next = i
                break
            }
        }
        val beatProgress = if (prev < 0) 0f else {
            val until = if (next in beats.indices) beats[next].timestampMs.toLong() - prevMs else 600L
            ((mediaMs - prevMs).toFloat() / until.coerceAtLeast(1L)).coerceIn(0f, 1f)
        }
        val timeToNextBeat = if (next in beats.indices) beats[next].timestampMs.toLong() - mediaMs else Long.MAX_VALUE

        var dropIdx = -1
        var dropStrength = 0f
        for (i in drops.indices) {
            val t = drops[i].timestampMs.toLong()
            if (t <= mediaMs) {
                dropIdx = i
                dropStrength = drops[i].strength
            }
        }
        val timeToNextDrop = if (dropIdx in 0 until drops.lastIndex) {
            drops[dropIdx + 1].timestampMs.toLong() - mediaMs
        } else {
            Long.MAX_VALUE
        }
        val dropActive = dropIdx >= 0 && (mediaMs - drops[dropIdx].timestampMs.toLong()) <= DROP_WINDOW_MS

        return BeatFrame(
            beatProgress = beatProgress,
            beatIndex = beatIndex,
            beatStrength = if (prev >= 0 && (mediaMs - prevMs) <= BEAT_WINDOW_MS) prevStrength else 0f,
            isBeat = prev >= 0 && (mediaMs - prevMs) <= BEAT_WINDOW_MS,
            isDrop = dropActive,
            dropStrength = if (dropActive) dropStrength else 0f,
            timeToNextBeatMs = timeToNextBeat,
            timeToNextDropMs = timeToNextDrop,
        )
    }
}