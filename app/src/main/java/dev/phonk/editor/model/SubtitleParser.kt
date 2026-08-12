package dev.phonk.editor.model

import java.io.InputStream

/**
 * Parses SRT or WebVTT subtitle content. Ported from LibreCuts
 * (com.tharunbirla.librecuts.utils.SubtitleParser, MIT).
 */
object SubtitleParser {

    /** Parses an SRT or WebVTT input stream into sorted [SubtitleCue]s. */
    fun parse(inputStream: InputStream): List<SubtitleCue> =
        inputStream.bufferedReader().use { parse(it.readText()) }

    /** Parses SRT or WebVTT content string into sorted [SubtitleCue]s. */
    fun parse(content: String): List<SubtitleCue> {
        val cleanContent = content.replace("\uFEFF", "").trim()
        val cues = mutableListOf<SubtitleCue>()
        // Split into blocks by double newlines or multiple newlines.
        val blocks = cleanContent.split(Regex("(?:\\r?\\n\\s*){2,}"))

        for (block in blocks) {
            val lines = block.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            // Find the line containing the time arrow "-->".
            val arrowIndex = lines.indexOfFirst { it.contains("-->") }
            if (arrowIndex == -1 || arrowIndex + 1 > lines.size) continue

            val timeLine = lines[arrowIndex]
            val timeParts = timeLine.split("-->").map { it.trim() }
            if (timeParts.size != 2) continue

            val startStr = timeParts[0]
            val endStr = timeParts[1].split(Regex("\\s+"))[0]

            val startTimeMs = parseTime(startStr)
            val endTimeMs = parseTime(endStr)

            if (startTimeMs != -1L && endTimeMs != -1L) {
                // All lines after the arrow line form the subtitle text.
                val text = lines.drop(arrowIndex + 1).joinToString("\n")
                cues.add(SubtitleCue(startTimeMs, endTimeMs, text))
            }
        }
        return cues.sortedBy { it.startMs }
    }

    /** Parses time format: hh:mm:ss,ms or hh:mm:ss.ms or mm:ss.ms. */
    private fun parseTime(timeStr: String): Long {
        try {
            val cleaned = timeStr.trim().replace(',', '.')
            val parts = cleaned.split(":")

            var hours = 0L
            var minutes = 0L
            var secondsWithMs = ""

            when (parts.size) {
                3 -> {
                    hours = parts[0].toLong()
                    minutes = parts[1].toLong()
                    secondsWithMs = parts[2]
                }
                2 -> {
                    minutes = parts[0].toLong()
                    secondsWithMs = parts[1]
                }
                1 -> {
                    secondsWithMs = parts[0]
                }
                else -> return -1L
            }

            val secMsParts = secondsWithMs.split(".")
            val seconds = secMsParts[0].toLong()
            val ms = if (secMsParts.size == 2) {
                var msStr = secMsParts[1]
                if (msStr.length > 3) msStr = msStr.substring(0, 3)
                while (msStr.length < 3) msStr += "0"
                msStr.toLong()
            } else {
                0L
            }

            return hours * 3600000L + minutes * 60000L + seconds * 1000L + ms
        } catch (e: Exception) {
            return -1L
        }
    }
}
