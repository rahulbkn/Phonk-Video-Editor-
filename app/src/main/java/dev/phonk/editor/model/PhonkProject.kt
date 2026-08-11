package dev.phonk.editor.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved project. Media is referenced by URI string only; the JSON never
 * embeds media binaries.
 */
data class PhonkProject(
    val version: Int = 1,
    val id: String = System.currentTimeMillis().toString(16),
    val name: String = "Untitled",
    val videoUri: String? = null,
    val audioUri: String? = null,
    val videoDurationMs: Long = 0,
    val audioDurationMs: Long = 0,
    val bpm: Double = 0.0,
    val beats: List<BeatMarker> = emptyList(),
    val drops: List<DropMarker> = emptyList(),
    val sections: List<AudioSection> = emptyList(),
    val clips: List<ClipSegment> = emptyList(),
    val effects: List<ClipEffect> = emptyList(),
    val waveform: List<Float> = emptyList(),
    val export: ExportConfig = ExportConfig(),
    /** Currently selected timeline clip, if any. */
    val selectedClipId: String? = null,
    /** Master audio settings. */
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val pitch: Float = 1f,
    /** Color-grade settings applied at render time. */
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
    /** Automation keyframes for the color grade (destination timeline ms). */
    val gradeKeyframes: List<GradeKeyframe> = emptyList(),
    /** When true, keyframes animate the grade during playback/export. */
    val gradeKeyframesEnabled: Boolean = false,
    /** Live beat-synced reaction (zoom/flash/shake) driven by the beat engine. */
    val beatSync: Boolean = false,
    val beatSyncStrength: Float = 0.8f,
    /** Default transition duration between clips (ms). */
    val transitionDurationMs: Long = 400L,
    val textLayers: List<TextLayer> = emptyList(),
    val overlays: List<OverlayLayer> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun analysisEnergyCurve(): FloatArray =
        if (waveform.isEmpty()) FloatArray(0) else FloatArray(waveform.size) { waveform[it] }

    /** Total destination timeline length in ms, derived from clips. */
    fun timelineDurationMs(): Long = clips.maxOfOrNull { it.destEndMs } ?: 0L

    /** The full shared grade (single source of truth for preview + export). */
    fun colorGrade(): ColorGrade = ColorGradeMaps.of(this)

    /** Evaluates the grade at a destination timestamp (keyframes aware). */
    fun gradeAt(destMs: Long): ColorGrade = evaluateColorGrade(
        base = colorGrade(),
        keyframes = gradeKeyframes,
        destMs = destMs,
        enabled = gradeKeyframesEnabled,
    )
}

/** Source span placed on the destination timeline. */
data class ClipSegment(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val destStartMs: Long,
    val destEndMs: Long,
    val effect: EffectKind = EffectKind.NONE,
    val effectStrength: Float = 0f,
    val dropTransition: Boolean = false,
    /** Source timestamp (ms) of the drop this clip is transitioning to, if any. */
    val dropSourceMs: Long? = null,
    /** Playback speed multiplier (0.25..4). Source span is preserved; destination
     *  duration reflects the speed so the timeline stays accurate. */
    val speed: Float = 1f,
    /** Transition applied at the start of this clip (next edge), null = cut. */
    val transition: String? = null,
) {
    val destDurationMs: Long get() = (destEndMs - destStartMs).coerceAtLeast(0L)
}

/**
 * A keyframe that animates an overlay's transform on the destination timeline.
 * Values are the same normalized project-space coordinates the item uses.
 */
data class OverlayKeyframe(
    val atMs: Long,
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val opacity: Float,
)

/**
 * Common contract shared by every overlay variant (text, image, sticker, emoji,
 * shape, ...). Position is normalized to the video content rectangle:
 * x = y = 0.5 is the exact centre, so overlays stay resolution-independent
 * across 720p/1080p/4K and every aspect ratio. The project JSON is the single
 * source of truth for all of these fields — nothing is kept inside Views.
 */
interface OverlayItem {
    val id: String
    val startMs: Long
    val endMs: Long
    /** Normalized centre X in the video content rect (0..1). */
    val x: Float
    /** Normalized centre Y in the video content rect (0..1). */
    val y: Float
    val scaleX: Float
    val scaleY: Float
    /** Rotation in degrees (free rotation, snaps offered by the editor UI). */
    val rotation: Float
    /** 0..1 opacity. */
    val opacity: Float
    /** Draw order; higher = on top. */
    val zIndex: Int
    /** Project-level visibility; hidden overlays do not render or export. */
    val visible: Boolean
    /** Locked overlays cannot be dragged/resized/rotated in the preview. */
    val locked: Boolean
    val keyframes: List<OverlayKeyframe>

    /** Overlay family: "Text", "Image", "Sticker", "Emoji", "Shape", ... */
    val type: String
    /** Human readable name shown in the timeline / overlay panel. */
    val label: String

    fun isActiveAt(ms: Long): Boolean = ms in startMs..endMs && visible
}

/** A text overlay anchored on the destination timeline. */
data class TextLayer(
    override val id: String = java.util.UUID.randomUUID().toString().take(8),
    val text: String = "Text",
    override val startMs: Long = 0L,
    override val endMs: Long = 3000L,
    val fontSize: Float = 24f,
    override val opacity: Float = 1f,
    val animation: String = "Fade",
    val colorArgb: Long = 0xFFFFFFFFL,
    override val x: Float = 0.5f,
    override val y: Float = 0.5f,
    override val scaleX: Float = 1f,
    override val scaleY: Float = 1f,
    override val rotation: Float = 0f,
    override val zIndex: Int = 0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val keyframes: List<OverlayKeyframe> = emptyList(),
) : OverlayItem {
    override val type: String get() = "Text"
    override val label: String get() = text
}

/** An image/shape overlay anchored on the destination timeline. */
data class OverlayLayer(
    override val id: String = java.util.UUID.randomUUID().toString().take(8),
    val kind: String = "Image",
    override val label: String = "Overlay",
    val uri: String? = null,
    override val startMs: Long = 0L,
    override val endMs: Long = 3000L,
    override val x: Float = 0.5f,
    override val y: Float = 0.5f,
    override val scaleX: Float = 1f,
    override val scaleY: Float = 1f,
    override val rotation: Float = 0f,
    override val opacity: Float = 1f,
    override val zIndex: Int = 0,
    override val visible: Boolean = true,
    override val locked: Boolean = false,
    override val keyframes: List<OverlayKeyframe> = emptyList(),
) : OverlayItem {
    override val type: String get() = kind
}

/** Returns a copy with the transform fields replaced (works for any variant). */
fun OverlayItem.withTransform(
    x: Float = this.x,
    y: Float = this.y,
    scaleX: Float = this.scaleX,
    scaleY: Float = this.scaleY,
    rotation: Float = this.rotation,
    opacity: Float = this.opacity,
): OverlayItem = when (this) {
    is TextLayer -> copy(x = x, y = y, scaleX = scaleX, scaleY = scaleY, rotation = rotation, opacity = opacity)
    is OverlayLayer -> copy(x = x, y = y, scaleX = scaleX, scaleY = scaleY, rotation = rotation, opacity = opacity)
    else -> this
}

/** Returns a copy with the timeline duration fields replaced. */
fun OverlayItem.withTiming(startMs: Long = this.startMs, endMs: Long = this.endMs): OverlayItem = when (this) {
    is TextLayer -> copy(startMs = startMs, endMs = endMs)
    is OverlayLayer -> copy(startMs = startMs, endMs = endMs)
    else -> this
}

enum class EffectKind(val wire: String) {
    NONE("none"),
    FLASH("flash"),
    ZOOM("zoom"),
    SHAKE("shake"),
    GLITCH("glitch"),
    FADE("fade"),
    BRIGHTNESS("brightness"),
    CONTRAST("contrast"),
    RGBSPLIT("rgbsplit"),
    BLUR("blur"),
    FAST("speed");

    companion object {
        fun fromWire(value: String?): EffectKind =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

/** Effects that are attached to a clip (not to the global timeline). */
data class ClipEffect(
    val clipId: String,
    val kind: EffectKind,
    val t0Ms: Long,
    val t1Ms: Long,
    val amount: Float,
)

/** User manual edits applied on top of auto-detection. */
data class ManualEdit(
    val kind: EditKind,
    val atTimeMs: Double,
    val removedIfAnyDropId: String? = null,
)

enum class EditKind {
    ADD_BEAT,
    REMOVE_BEAT,
    ADD_DROP,
    REMOVE_DROP,
    MOVE_DROP,
    ADD_CUT,
    REMOVE_CUT,
}

class ProjectCodec {
    fun toJson(p: PhonkProject): String {
        val o = JSONObject()
        o.put("version", p.version)
        o.put("id", p.id)
        o.put("name", p.name)
        o.put("videoUri", p.videoUri)
        o.put("audioUri", p.audioUri)
        o.put("videoDurationMs", p.videoDurationMs)
        o.put("audioDurationMs", p.audioDurationMs)
        o.put("bpm", p.bpm)
        o.put("beats", JSONArray().also { arr -> p.beats.forEach { b ->
            arr.put(JSONObject()
                .put("timeMs", b.timestampMs)
                .put("confidence", b.confidence.toDouble())
                .put("beatIndex", b.beatIndex)
                .put("downbeat", b.downbeat))
        }})
        o.put("drops", JSONArray().also { arr -> p.drops.forEach { d ->
            arr.put(JSONObject()
                .put("timeMs", d.timestampMs)
                .put("confidence", d.confidence.toDouble())
                .put("strength", d.strength.toDouble())
                .put("type", DropType.wire(d.type)))
        }})
        o.put("sections", JSONArray().also { arr -> p.sections.forEach { s ->
            arr.put(JSONObject()
                .put("startMs", s.startMs)
                .put("endMs", s.endMs)
                .put("energy", s.energy.toDouble())
                .put("type", sectionWire(s.type)))
        }})
        o.put("clips", JSONArray().also { arr -> p.clips.forEach { c ->
            arr.put(JSONObject()
                .put("id", c.id)
                .put("sourceStartMs", c.sourceStartMs)
                .put("sourceEndMs", c.sourceEndMs)
                .put("destStartMs", c.destStartMs)
                .put("destEndMs", c.destEndMs)
                .put("effect", c.effect.wire)
                .put("effectStrength", c.effectStrength.toDouble())
                .put("dropTransition", c.dropTransition)
                .put("dropSourceMs", c.dropSourceMs)
                .put("speed", c.speed.toDouble())
                .put("transition", c.transition))
        }})
        o.put("effects", JSONArray().also { arr -> p.effects.forEach { e ->
            arr.put(JSONObject()
                .put("clipId", e.clipId)
                .put("kind", e.kind.wire)
                .put("t0Ms", e.t0Ms)
                .put("t1Ms", e.t1Ms)
                .put("amount", e.amount.toDouble()))
        }})
        o.put("waveform", JSONArray().also { arr -> p.waveform.forEach { arr.put(it.toDouble()) } })
        o.put("selectedClipId", p.selectedClipId)
        o.put("volume", p.volume.toDouble())
        o.put("muted", p.muted)
        o.put("fadeInMs", p.fadeInMs)
        o.put("fadeOutMs", p.fadeOutMs)
        o.put("pitch", p.pitch.toDouble())
        o.put("brightness", p.brightness.toDouble())
        o.put("contrast", p.contrast.toDouble())
        o.put("saturation", p.saturation.toDouble())
        o.put("exposure", p.exposure.toDouble())
        o.put("temperature", p.temperature.toDouble())
        o.put("tint", p.tint.toDouble())
        o.put("highlights", p.highlights.toDouble())
        o.put("shadows", p.shadows.toDouble())
        o.put("fade", p.fade.toDouble())
        o.put("sharpness", p.sharpness.toDouble())
        o.put("blur", p.blur.toDouble())
        o.put("vignette", p.vignette.toDouble())
        o.put("grain", p.grain.toDouble())
        o.put("gradeKeyframes", JSONArray().also { arr -> p.gradeKeyframes.forEach { k ->
            val gradeObj = JSONObject()
                .put("brightness", k.grade.brightness.toDouble())
                .put("contrast", k.grade.contrast.toDouble())
                .put("saturation", k.grade.saturation.toDouble())
                .put("exposure", k.grade.exposure.toDouble())
                .put("temperature", k.grade.temperature.toDouble())
                .put("tint", k.grade.tint.toDouble())
                .put("highlights", k.grade.highlights.toDouble())
                .put("shadows", k.grade.shadows.toDouble())
                .put("fade", k.grade.fade.toDouble())
                .put("sharpness", k.grade.sharpness.toDouble())
                .put("blur", k.grade.blur.toDouble())
                .put("vignette", k.grade.vignette.toDouble())
                .put("grain", k.grade.grain.toDouble())
            arr.put(JSONObject().put("atMs", k.atMs).put("grade", gradeObj))
        }})
        o.put("gradeKeyframesEnabled", p.gradeKeyframesEnabled)
        o.put("beatSync", p.beatSync)
        o.put("beatSyncStrength", p.beatSyncStrength.toDouble())
        o.put("transitionDurationMs", p.transitionDurationMs)
        o.put("textLayers", JSONArray().also { arr -> p.textLayers.forEach { t ->
            arr.put(JSONObject()
                .put("id", t.id)
                .put("text", t.text)
                .put("startMs", t.startMs)
                .put("endMs", t.endMs)
                .put("fontSize", t.fontSize.toDouble())
                .put("opacity", t.opacity.toDouble())
                .put("animation", t.animation)
                .put("colorArgb", t.colorArgb)
                .put("x", t.x.toDouble())
                .put("y", t.y.toDouble())
                .put("scaleX", t.scaleX.toDouble())
                .put("scaleY", t.scaleY.toDouble())
                .put("rotation", t.rotation.toDouble())
                .put("zIndex", t.zIndex)
                .put("visible", t.visible)
                .put("locked", t.locked)
                .put("keyframes", overlayKeyframesJson(t.keyframes)))
        }})
        o.put("overlays", JSONArray().also { arr -> p.overlays.forEach { ov ->
            arr.put(JSONObject()
                .put("id", ov.id)
                .put("kind", ov.kind)
                .put("label", ov.label)
                .put("uri", ov.uri)
                .put("startMs", ov.startMs)
                .put("endMs", ov.endMs)
                .put("x", ov.x.toDouble())
                .put("y", ov.y.toDouble())
                .put("scaleX", ov.scaleX.toDouble())
                .put("scaleY", ov.scaleY.toDouble())
                .put("rotation", ov.rotation.toDouble())
                .put("opacity", ov.opacity.toDouble())
                .put("zIndex", ov.zIndex)
                .put("visible", ov.visible)
                .put("locked", ov.locked)
                .put("keyframes", overlayKeyframesJson(ov.keyframes)))
        }})
        val ex = JSONObject()
        ex.put("resolution", p.export.resolution.name)
        ex.put("fps", p.export.fps.fps)
        ex.put("videoCodec", p.export.videoCodec.name)
        ex.put("audioBitrate", p.export.audioBitrate.kbps)
        ex.put("maintainAspect", p.export.maintainAspect)
        ex.put("hardwareAccel", p.export.hardwareAccel)
        o.put("export", ex)
        o.put("createdAt", p.createdAt)
        o.put("updatedAt", p.updatedAt)
        return o.toString()
    }

    fun fromJson(text: String): PhonkProject {
        val o = try {
            JSONObject(text)
        } catch (t: Throwable) {
            return PhonkProject()
        }
        val beats = parseBeats(o.optJSONArray("beats"))
        val drops = parseDrops(o.optJSONArray("drops"))
        return PhonkProject(
            version = o.optInt("version", 1),
            id = o.optString("id", ""),
            name = o.optString("name", "Untitled"),
            videoUri = o.optString("videoUri", null),
            audioUri = o.optString("audioUri", null),
            videoDurationMs = o.optLong("videoDurationMs", 0L),
            audioDurationMs = o.optLong("audioDurationMs", 0L),
            bpm = o.optDouble("bpm", 0.0),
            beats = beats,
            drops = drops,
            sections = parseSections(o.optJSONArray("sections")),
            clips = parseClips(o.optJSONArray("clips")),
            effects = parseEffects(o.optJSONArray("effects")),
            waveform = parseFloatList(o.optJSONArray("waveform")),
            export = parseExport(o.optJSONObject("export")),
            selectedClipId = if (o.has("selectedClipId") && !o.isNull("selectedClipId"))
                o.optString("selectedClipId", null) else null,
            volume = o.optDouble("volume", 1.0).toFloat(),
            muted = o.optBoolean("muted", false),
            fadeInMs = o.optLong("fadeInMs", 0L),
            fadeOutMs = o.optLong("fadeOutMs", 0L),
            pitch = o.optDouble("pitch", 1.0).toFloat(),
            brightness = o.optDouble("brightness", 0.0).toFloat(),
            contrast = o.optDouble("contrast", 0.0).toFloat(),
            saturation = o.optDouble("saturation", 0.0).toFloat(),
            exposure = o.optDouble("exposure", 0.0).toFloat(),
            temperature = o.optDouble("temperature", 0.0).toFloat(),
            tint = o.optDouble("tint", 0.0).toFloat(),
            highlights = o.optDouble("highlights", 0.0).toFloat(),
            shadows = o.optDouble("shadows", 0.0).toFloat(),
            fade = o.optDouble("fade", 0.0).toFloat(),
            sharpness = o.optDouble("sharpness", 0.0).toFloat(),
            blur = o.optDouble("blur", 0.0).toFloat(),
            vignette = o.optDouble("vignette", 0.0).toFloat(),
            grain = o.optDouble("grain", 0.0).toFloat(),
            gradeKeyframes = parseGradeKeyframes(o.optJSONArray("gradeKeyframes")),
            gradeKeyframesEnabled = o.optBoolean("gradeKeyframesEnabled", false),
            beatSync = o.optBoolean("beatSync", false),
            beatSyncStrength = o.optDouble("beatSyncStrength", 0.8).toFloat(),
            transitionDurationMs = o.optLong("transitionDurationMs", 400L),
            textLayers = parseTextLayers(o.optJSONArray("textLayers")),
            overlays = parseOverlays(o.optJSONArray("overlays")),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun parseBeats(arr: JSONArray?): List<BeatMarker> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                add(
                    BeatMarker(
                        timestampMs = b.optDouble("timeMs", 0.0),
                        confidence = b.optDouble("confidence", 0.0).toFloat(),
                        beatIndex = b.optInt("beatIndex", 0),
                        downbeat = b.optBoolean("downbeat", false),
                    )
                )
            }
        }
    }

    private fun parseDrops(arr: JSONArray?): List<DropMarker> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val d = arr.optJSONObject(i) ?: continue
                add(
                    DropMarker(
                        timestampMs = d.optDouble("timeMs", 0.0),
                        confidence = d.optDouble("confidence", 0.0).toFloat(),
                        strength = d.optDouble("strength", 0.0).toFloat(),
                        type = DropType.fromWire(d.optString("type", "section_drop")),
                    )
                )
            }
        }
    }

    private fun parseSections(arr: JSONArray?): List<AudioSection> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                add(
                    AudioSection(
                        type = sectionFromWire(s.optString("type", "energy")),
                        startMs = s.optDouble("startMs", 0.0),
                        endMs = s.optDouble("endMs", 0.0),
                        energy = s.optDouble("energy", 0.0).toFloat(),
                    )
                )
            }
        }
    }

    private fun parseClips(arr: JSONArray?): List<ClipSegment> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                add(
                    ClipSegment(
                        id = c.optString("id", ""),
                        sourceStartMs = c.optLong("sourceStartMs", 0L),
                        sourceEndMs = c.optLong("sourceEndMs", 0L),
                        destStartMs = c.optLong("destStartMs", 0L),
                        destEndMs = c.optLong("destEndMs", 0L),
                        effect = EffectKind.fromWire(c.optString("effect", "none")),
                        effectStrength = c.optDouble("effectStrength", 0.0).toFloat(),
                        dropTransition = c.optBoolean("dropTransition", false),
                        dropSourceMs = if (c.has("dropSourceMs") && !c.isNull("dropSourceMs"))
                            c.optLong("dropSourceMs", 0L) else null,
                        speed = c.optDouble("speed", 1.0).toFloat(),
                        transition = if (c.has("transition") && !c.isNull("transition"))
                            c.optString("transition", null) else null,
                    )
                )
            }
        }
    }

    private fun parseEffects(arr: JSONArray?): List<ClipEffect> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                add(
                    ClipEffect(
                        clipId = e.optString("clipId", ""),
                        kind = EffectKind.fromWire(e.optString("kind", "none")),
                        t0Ms = e.optLong("t0Ms", 0L),
                        t1Ms = e.optLong("t1Ms", 0L),
                        amount = e.optDouble("amount", 0.0).toFloat(),
                    )
                )
            }
        }
    }

    private fun parseGradeKeyframes(arr: JSONArray?): List<GradeKeyframe> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val k = arr.optJSONObject(i) ?: continue
                val g = k.optJSONObject("grade")
                val grade = if (g == null) ColorGrade() else ColorGrade(
                    brightness = g.optDouble("brightness", 0.0).toFloat(),
                    contrast = g.optDouble("contrast", 0.0).toFloat(),
                    saturation = g.optDouble("saturation", 0.0).toFloat(),
                    exposure = g.optDouble("exposure", 0.0).toFloat(),
                    temperature = g.optDouble("temperature", 0.0).toFloat(),
                    tint = g.optDouble("tint", 0.0).toFloat(),
                    highlights = g.optDouble("highlights", 0.0).toFloat(),
                    shadows = g.optDouble("shadows", 0.0).toFloat(),
                    fade = g.optDouble("fade", 0.0).toFloat(),
                    sharpness = g.optDouble("sharpness", 0.0).toFloat(),
                    blur = g.optDouble("blur", 0.0).toFloat(),
                    vignette = g.optDouble("vignette", 0.0).toFloat(),
                    grain = g.optDouble("grain", 0.0).toFloat(),
                )
                add(GradeKeyframe(atMs = k.optLong("atMs", 0L), grade = grade))
            }
        }
    }

    private fun parseFloatList(arr: JSONArray?): List<Float> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) add(arr.optDouble(i, 0.0).toFloat())
        }
    }

    private fun parseTextLayers(arr: JSONArray?): List<TextLayer> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                add(
                    TextLayer(
                        id = t.optString("id", ""),
                        text = t.optString("text", "Text"),
                        startMs = t.optLong("startMs", 0L),
                        endMs = t.optLong("endMs", 3000L),
                        fontSize = t.optDouble("fontSize", 24.0).toFloat(),
                        opacity = t.optDouble("opacity", 1.0).toFloat(),
                        animation = t.optString("animation", "Fade"),
                        colorArgb = t.optLong("colorArgb", 0xFFFFFFFFL),
                        x = t.optDouble("x", 0.5).toFloat(),
                        y = t.optDouble("y", 0.5).toFloat(),
                        scaleX = t.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = t.optDouble("scaleY", 1.0).toFloat(),
                        rotation = t.optDouble("rotation", 0.0).toFloat(),
                        zIndex = t.optInt("zIndex", 0),
                        visible = t.optBoolean("visible", true),
                        locked = t.optBoolean("locked", false),
                        keyframes = parseOverlayKeyframes(t.optJSONArray("keyframes")),
                    )
                )
            }
        }
    }

    private fun parseOverlays(arr: JSONArray?): List<OverlayLayer> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val ov = arr.optJSONObject(i) ?: continue
                add(
                    OverlayLayer(
                        id = ov.optString("id", ""),
                        kind = ov.optString("kind", "Image"),
                        label = ov.optString("label", "Overlay"),
                        uri = if (ov.has("uri") && !ov.isNull("uri"))
                            ov.optString("uri", null) else null,
                        startMs = ov.optLong("startMs", 0L),
                        endMs = ov.optLong("endMs", 3000L),
                        x = ov.optDouble("x", 0.5).toFloat(),
                        y = ov.optDouble("y", 0.5).toFloat(),
                        scaleX = ov.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = ov.optDouble("scaleY", 1.0).toFloat(),
                        rotation = ov.optDouble("rotation", 0.0).toFloat(),
                        opacity = ov.optDouble("opacity", 1.0).toFloat(),
                        zIndex = ov.optInt("zIndex", 0),
                        visible = ov.optBoolean("visible", true),
                        locked = ov.optBoolean("locked", false),
                        keyframes = parseOverlayKeyframes(ov.optJSONArray("keyframes")),
                    )
                )
            }
        }
    }

    private fun overlayKeyframesJson(keyframes: List<OverlayKeyframe>): JSONArray =
        JSONArray().also { arr ->
            keyframes.forEach { k ->
                arr.put(JSONObject()
                    .put("atMs", k.atMs)
                    .put("x", k.x.toDouble())
                    .put("y", k.y.toDouble())
                    .put("scaleX", k.scaleX.toDouble())
                    .put("scaleY", k.scaleY.toDouble())
                    .put("rotation", k.rotation.toDouble())
                    .put("opacity", k.opacity.toDouble()))
            }
        }

    private fun parseOverlayKeyframes(arr: JSONArray?): List<OverlayKeyframe> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val k = arr.optJSONObject(i) ?: continue
                add(
                    OverlayKeyframe(
                        atMs = k.optLong("atMs", 0L),
                        x = k.optDouble("x", 0.5).toFloat(),
                        y = k.optDouble("y", 0.5).toFloat(),
                        scaleX = k.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = k.optDouble("scaleY", 1.0).toFloat(),
                        rotation = k.optDouble("rotation", 0.0).toFloat(),
                        opacity = k.optDouble("opacity", 1.0).toFloat(),
                    )
                )
            }
        }
    }

    private fun parseExport(o: JSONObject?): ExportConfig {
        if (o == null) return ExportConfig()
        return ExportConfig(
            resolution = runCatching {
                Resolution.valueOf(o.optString("resolution", "HD_1080"))
            }.getOrDefault(Resolution.HD_1080),
            fps = when (o.optInt("fps", 30)) {
                24 -> FrameRate.F24
                60 -> FrameRate.F60
                else -> FrameRate.F30
            },
            videoCodec = runCatching {
                VideoCodec.valueOf(o.optString("videoCodec", "H264"))
            }.getOrDefault(VideoCodec.H264),
            audioBitrate = when (o.optInt("audioBitrate", 192)) {
                128 -> AudioBitrate.A128
                256 -> AudioBitrate.A256
                320 -> AudioBitrate.A320
                else -> AudioBitrate.A192
            },
            maintainAspect = o.optBoolean("maintainAspect", true),
            hardwareAccel = o.optBoolean("hardwareAccel", true),
        )
    }

    private fun sectionWire(kind: SectionKind): String = when (kind) {
        SectionKind.BUILD -> "build"
        SectionKind.DROP -> "drop"
        SectionKind.SILENCE -> "silence"
        SectionKind.ENERGY -> "energy"
    }

    private fun sectionFromWire(value: String): SectionKind = when (value) {
        "build" -> SectionKind.BUILD
        "drop" -> SectionKind.DROP
        "silence" -> SectionKind.SILENCE
        else -> SectionKind.ENERGY
    }
}