package dev.phonk.editor.editor

import androidx.annotation.StringRes
import dev.phonk.editor.R
import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.EditKind
import dev.phonk.editor.model.PhonkProject
import kotlin.math.abs
import kotlin.math.roundToLong

/** Cut pattern presets (A..F from the spec). */
enum class CutPattern(@StringRes val labelRes: Int, val beatsPerCut: Double) {
    A(R.string.pattern_a, 1.0),
    B(R.string.pattern_b, 2.0),
    C(R.string.pattern_c, 4.0),
    D(R.string.pattern_d, 8.0),
    E_FAST_PHONK(R.string.pattern_e_fast, 0.5),
    F_DROP_EMPHASIS(R.string.pattern_f_drop, 1.0),
}

/**
 * Pure-JVM `shape of the C++ timeline engine. Given an [AnalysisResult] and a
 * pattern it produces [ClipSegment] backup; identical results are expected
 * from the native engine so tests can validate determinism.
 */
object CutPlanner {

    data class Plan(
        val clips: List<ClipSegment>,
        val totalDurationMs: Long,
    )

    fun planPattern(analysis: AnalysisResult, pattern: CutPattern, effectsEnabled: Boolean = true, maxSourceMs: Long? = null): Plan {
        val emphasizeDrops = pattern == CutPattern.F_DROP_EMPHASIS
        return planInternal(analysis, pattern.beatsPerCut, emphasizeDrops, effectsEnabled, maxSourceMs)
    }

    /** Custom subdivision (e.g. 0.5, 1, 2, 4, 8). */
    fun planCustom(analysis: AnalysisResult, beatsPerCut: Double, effectsEnabled: Boolean = true, maxSourceMs: Long? = null): Plan =
        planInternal(analysis, beatsPerCut, false, effectsEnabled, maxSourceMs)

    fun planInternal(
        analysis: AnalysisResult,
        beatsPerCut: Double,
        emphasizeDrops: Boolean,
        effectsEnabled: Boolean,
        maxSourceMs: Long? = null,
    ): Plan {
        val times = analysis.beats.map { it.timestampMs }.sorted()
        if (times.isEmpty()) return Plan(emptyList(), 0L)

        val bpm = analysis.bpm
        val beatMs = if (bpm > 0) 60000.0 / bpm else 500.0
        val step = (beatsPerCut * beatMs).coerceAtLeast(40.0)

        val start = times.first()
        // Never plan cuts past the real video length: the decoded audio
        // duration (analysis.durationMs) can exceed the video track, which
        // used to make exports run longer than the source clip.
        val sourceCap = when {
            maxSourceMs != null && maxSourceMs > 0 -> (maxSourceMs - 1).toDouble()
            else -> (analysis.durationMs - 1).toDouble()
        }
        val end = times.last().coerceAtMost(sourceCap)

        val cuts = ArrayList<Double>()
        fun add(t: Double) {
            if (t in (start - 2)..(end + 2) && (cuts.isEmpty() || t > cuts.last() + 2)) cuts.add(t)
        }
        var t = start
        while (t < end) {
            add(t)
            t += step
        }
        add(end)

        val drops = analysis.drops.sortedBy { it.timestampMs }
        if (emphasizeDrops) {
            for (d in drops) {
                add(d.timestampMs)
                var pre = step
                var k = 0
                while (k < 4 && pre > 40) {
                    add(d.timestampMs - pre)
                    pre *= 0.5
                    k++
                }
                add(d.timestampMs + step * 0.5)
            }
        } else {
            for (d in drops) add(d.timestampMs)
        }

        val sorted = cuts.distinct().sorted()
        val clips = ArrayList<ClipSegment>()
        var dest = 0L
        for (i in 0 until sorted.lastIndex) {
            val sStart = sorted[i].roundToLong()
            val sEnd = sorted[i + 1].roundToLong().coerceAtMost(end.roundToLong())
            if (sEnd <= sStart) continue
            var dropTransition = false
            var effect = EffectKind.NONE
            var strength = 0f
            val nearDrop = drops.firstOrNull { abs(it.timestampMs - sEnd.toDouble()) < step * 0.5 }
            if (nearDrop != null) {
                dropTransition = true
                if (effectsEnabled) {
                    // ZOOM/SHAKE are no-ops in the bundled renderer (crop/scale
                    // with 'enable' is unsupported), so every drop maps to a
                    // visible FLASH window regardless of strength. Otherwise the
                    // majority of drops (strength <= 0.85) would render nothing.
                    effect = EffectKind.FLASH
                }
                strength = nearDrop.strength
            }
            clips.add(
                ClipSegment(
                    sourceStartMs = sStart,
                    sourceEndMs = sEnd,
                    destStartMs = dest,
                    destEndMs = dest + (sEnd - sStart),
                    effect = effect,
                    effectStrength = strength,
                    dropTransition = dropTransition,
                    dropSourceMs = nearDrop?.timestampMs?.roundToLong(),
                )
            )
            dest += (sEnd - sStart)
        }
        return Plan(clips, dest)
    }

    /** Snaps symmetry; returns the nearest beat or drop timestamp, else no change. */
    fun snap(valueMs: Long, analysis: AnalysisResult, includeDrops: Boolean = true): Long {
        val grid = if (analysis.bpm > 0) (60000.0 / analysis.bpm).roundToLong() else 0L
        if (grid <= 0) return valueMs
        var best = valueMs
        var bestDist = Long.MAX_VALUE
        val beatMs = analysis.beats.map { it.timestampMs.roundToLong() }
        for (b in beatMs) {
            val d = abs(b - valueMs)
            if (d < bestDist) {
                bestDist = d
                best = b
            }
        }
        if (includeDrops) {
            for (dr in analysis.drops) {
                val d = abs(dr.timestampMs.roundToLong() - valueMs)
                if (d < bestDist) {
                    bestDist = d
                    best = dr.timestampMs.roundToLong()
                }
            }
        }
        if (bestDist < grid / 3) return best
        val snapped = Math.round(valueMs.toDouble() / grid) * grid
        return snapped.coerceAtLeast(0L)
    }
}