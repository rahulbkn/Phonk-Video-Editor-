package dev.phonk.editor.model

/**
 * Ported feature models from LibreCuts (MIT, tharunbirla/LibreCuts). These are
 * plain serializable data classes so the existing ProjectCodec (org.json) and
 * undo/redo engine can persist and restore them unchanged.
 */

/** One subtitle/caption entry on the destination timeline. */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** Style + cue list for a subtitle track (SRT / WebVTT import). */
data class SubtitleTrack(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val fileName: String = "",
    val cues: List<SubtitleCue> = emptyList(),
    val fontSize: Float = 36f,
    val colorArgb: Long = 0xFFFFFFFFL,
    val backgroundColorArgb: Long = 0x80000000L,
    /** Normalized centre in the video content rect (0..1, 0.5 = centre). */
    val x: Float = 0.5f,
    val y: Float = 0.92f,
    val visible: Boolean = true,
)

/** Mask shapes supported by the export geq expressions (ported from LibreCuts). */
enum class MaskShape(val wire: String) {
    NONE("none"),
    RECTANGLE("rectangle"),
    ELLIPSE("ellipse"),
    SPLIT("split"),
    SHUTTER("shutter"),
    HEART("heart"),
    STAR("star");

    companion object {
        fun fromWire(value: String?): MaskShape =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

/** Mask configuration attached to an overlay or the main video. */
data class MaskConfig(
    val shape: MaskShape = MaskShape.NONE,
    /** Normalized centre in the video content rect. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** Relative size (0..1 fraction of the content rect). */
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    val rotation: Float = 0f,
    val inverted: Boolean = false,
    val feather: Float = 0f,
) {
    val isActive: Boolean get() = shape != MaskShape.NONE
}

/** Canvas background behind the letterboxed video (ported from LibreCuts). */
enum class BackgroundType(val wire: String) {
    NONE("none"),
    COLOR("color"),
    IMAGE("image"),
    BLUR("blur");

    companion object {
        fun fromWire(value: String?): BackgroundType =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

data class CanvasBackground(
    val type: BackgroundType = BackgroundType.NONE,
    val colorArgb: Long = 0xFF000000L,
    val imageUri: String? = null,
    val blurRadius: Float = 25f,
)

/** Custom crop region relative to the content rect (fractions 0..1). */
data class CropConfig(
    val enabled: Boolean = false,
    val xFraction: Float = 0f,
    val yFraction: Float = 0f,
    val wFraction: Float = 1f,
    val hFraction: Float = 1f,
    val aspectRatio: String? = null,
)
