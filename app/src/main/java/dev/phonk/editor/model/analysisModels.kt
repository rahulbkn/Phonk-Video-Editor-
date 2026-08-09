package dev.phonk.editor.model

/** A detected beat. Timestamps are always in milliseconds. */
data class BeatMarker(
    val timestampMs: Double,
    val confidence: Float,
    val beatIndex: Int = 0,
    val downbeat: Boolean = false,
)

/** Phonk drop archetypes. Not every loud section is a drop. */
enum class DropType {
    HARD_DROP,
    BASS_DROP,
    BEAT_DROP,
    DOUBLE_DROP,
    HALF_TIME_DROP,
    BUILD_UP_DROP,
    SILENCE_DROP,
    BASS_SWITCH,
    BEAT_SWITCH,
    SECTION_DROP;

    companion object {
        fun fromWire(value: String, fallback: DropType = SECTION_DROP): DropType {
            return when (value) {
                "hard_drop" -> HARD_DROP
                "bass_drop" -> BASS_DROP
                "beat_drop" -> BEAT_DROP
                "double_drop" -> DOUBLE_DROP
                "half_time_drop" -> HALF_TIME_DROP
                "build_up_drop" -> BUILD_UP_DROP
                "silence_drop" -> SILENCE_DROP
                "bass_switch" -> BASS_SWITCH
                "beat_switch" -> BEAT_SWITCH
                "section_drop" -> SECTION_DROP
                else -> fallback
            }
        }

        fun wire(of: DropType): String = when (of) {
            HARD_DROP -> "hard_drop"
            BASS_DROP -> "bass_drop"
            BEAT_DROP -> "beat_drop"
            DOUBLE_DROP -> "double_drop"
            HALF_TIME_DROP -> "half_time_drop"
            BUILD_UP_DROP -> "build_up_drop"
            SILENCE_DROP -> "silence_drop"
            BASS_SWITCH -> "bass_switch"
            BEAT_SWITCH -> "beat_switch"
            SECTION_DROP -> "section_drop"
        }
    }
}

/** A detected drop. */
data class DropMarker(
    val timestampMs: Double,
    val confidence: Float,
    val strength: Float,
    val type: DropType,
)

/** Detected structural section. */
data class AudioSection(
    val type: SectionKind,
    val startMs: Double,
    val endMs: Double,
    val energy: Float = 0f,
)

enum class SectionKind {
    BUILD,
    DROP,
    SILENCE,
    ENERGY;

    companion object {
        fun fromWire(value: String): SectionKind = when (value) {
            "build" -> BUILD
            "drop" -> DROP
            "silence" -> SILENCE
            else -> ENERGY
        }
    }
}

/**
 * Result of the audio analyzer. The [energyCurve] / [fluxCurve] arrays are
 * down-sampled so the UI can draw waveforms without holding raw frames.
 */
data class AnalysisResult(
    val bpm: Double,
    val sampleRate: Int,
    val durationMs: Long,
    val beats: List<BeatMarker>,
    val drops: List<DropMarker>,
    val sections: List<AudioSection>,
    val beatConfidence: Float,
    val dropConfidence: Float,
    val energyCurve: FloatArray,
    val fluxCurve: FloatArray,
) {
    /** Cap curves for UI memory safety. */
    fun compactEnergy(max: Int = 512): FloatArray {
        if (energyCurve.size <= max) return energyCurve
        val out = FloatArray(max)
        for (i in 0 until max) {
            val a = i * energyCurve.size / max
            val b = ((i + 1) * energyCurve.size / max).coerceAtMost(energyCurve.size)
            out[i] = if (b > a) energyCurve.copyOfRange(a, b).average().toFloat() else energyCurve[a]
        }
        return out
    }
}