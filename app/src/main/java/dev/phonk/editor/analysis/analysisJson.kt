package dev.phonk.editor.analysis

import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.model.AudioSection
import dev.phonk.editor.model.BeatMarker
import dev.phonk.editor.model.DropMarker
import dev.phonk.editor.model.DropType
import dev.phonk.editor.model.SectionKind
import org.json.JSONArray
import org.json.JSONObject

/** Parses JSON produced by C++ native analysis into typed models. */
object AnalysisJson {

    fun parseResult(json: String): AnalysisResult {
        val o = JSONObject(json)
        val beats = o.optJSONArray("beats")
        val drops = o.optJSONArray("drops")
        val sections = o.optJSONArray("sections")

        val beatList = (0 until (beats?.length() ?: 0)).map { i ->
            val b = beats.getJSONObject(i)
            BeatMarker(
                timestampMs = b.getDouble("timeMs"),
                confidence = b.getDouble("confidence").toFloat(),
                beatIndex = b.optInt("beatIndex", 0),
                downbeat = b.optBoolean("downbeat", false),
            )
        }
        val dropList = (0 until (drops?.length() ?: 0)).map { i ->
            val d = drops.getJSONObject(i)
            DropMarker(
                timestampMs = d.getDouble("timeMs"),
                confidence = d.getDouble("confidence").toFloat(),
                strength = d.getDouble("strength").toFloat(),
                type = DropType.fromWire(d.optString("type", "section_drop")),
            )
        }
        val sectionList = (0 until (sections?.length() ?: 0)).map { i ->
            val s = sections.getJSONObject(i)
            AudioSection(
                type = SectionKind.fromWire(s.optString("type", "energy")),
                startMs = s.getDouble("startMs"),
                endMs = s.getDouble("endMs"),
                energy = s.optDouble("energy", 0.0).toFloat(),
            )
        }
        val energy = parseFloatArray(o.optJSONArray("energyCurve"))
        val flux = parseFloatArray(o.optJSONArray("fluxCurve"))
        return AnalysisResult(
            bpm = o.getDouble("bpm"),
            sampleRate = o.optInt("sampleRate", 11025),
            durationMs = o.optLong("durationMs", 0L),
            beats = beatList,
            drops = dropList,
            sections = sectionList,
            beatConfidence = o.optDouble("beatConfidence", 0.0).toFloat(),
            dropConfidence = o.optDouble("dropConfidence", 0.0).toFloat(),
            energyCurve = energy,
            fluxCurve = flux,
        )
    }

    private fun parseFloatArray(arr: JSONArray?): FloatArray {
        if (arr == null) return FloatArray(0)
        val out = FloatArray(arr.length())
        for (i in 0 until arr.length()) out[i] = arr.getDouble(i).toFloat()
        return out
    }
}