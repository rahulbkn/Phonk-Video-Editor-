package dev.phonk.editor.util

import java.util.Locale

object TimeUtils {
    /** 34259 -> "00:34" */
    fun formatClock(ms: Long): String {
        val totalSeconds = ms / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    /** 34259 -> "00:00:34.259" for ffmpeg timestamps. */
    fun toSeconds(ms: Long): String {
        return String.format(Locale.US, "%.3f", ms / 1000.0)
    }

    /** Parses "123.45" seconds back to ms, guarding against drift. */
    fun fromSecondsFloat(value: Double): Long = Math.round(value * 1000.0)

    /** Snap a timestamp to the nearest beat (used by snap-to-beat). */
    fun snapToNearest(valueMs: Long, gridMs: Long): Long {
        if (gridMs <= 0) return valueMs
        return Math.round(valueMs.toDouble() / gridMs) * gridMs
    }

    fun clamp(value: Long, min: Long, max: Long): Long =
        if (value < min) min else if (value > max) max else value
}