package dev.phonk.editor.timeline

import dev.phonk.editor.model.PhonkProject

/**
 * Pure, JVM-testable model for the multi-item timeline.
 *
 * One row per logical track/layer:
 *   Video      (all video clips, drawn sequentially on a single row)
 *   Audio      (master waveform row when analysis data exists)
 *   Audio N    (one row per independent [PhonkProject.audioItems] clip)
 *   Overlay N  (one row per [PhonkProject.overlays] item)
 *   Text N     (one row per [PhonkProject.textLayers] item)
 *   Effect N   (one row per independent [PhonkProject.effects] item)
 *
 * Every item lives on exactly one row, so hit-testing is deterministic:
 * y → row → that row's single bar. Overlapping items sit on different rows and
 * can never combine or steal each other's gestures.
 */
enum class TimelineBarKind {
    VIDEO, AUDIO, OVERLAY, TEXT, EFFECT,
}

/**
 * One interactive bar on a [TimelineRow]. On single-item rows there is exactly
 * one bar; the video row carries all clips.
 */
class TimelineBar(
    val kind: TimelineBarKind,
    /** Unique item id (clip / audio item / overlay / text / effect). */
    val itemId: String,
    /** Human label ("Audio 2", "Overlay 1", "Title", ...). */
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val selected: Boolean,
    val visible: Boolean,
    val rowOrder: Int,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

class TimelineRow(
    val barKind: TimelineBarKind,
    val label: String,
    /** True for the master waveform row that carries no selectable bar. */
    val isWaveform: Boolean = false,
    /** Exactly one bar for item rows; the video row may hold many clips. */
    val bars: List<TimelineBar> = emptyList(),
)

/**
 * Builds the dynamic row list for [project]. [selectedOverlayId] selects overlay
 * and text bars, [selectedAudioId] selects audio bars, [selectedEffectId]
 * selects standalone effect bars and [selectedClipId] selects video clips.
 *
 * Rows are ordered Video, master Audio (waveform), per-item Audio, per-item
 * Overlay, per-item Text, per-item Effect — matching the product's layer order.
 */
fun buildTimelineRows(
    project: PhonkProject,
    selectedClipId: String? = null,
    selectedOverlayId: String? = null,
    selectedAudioId: String? = null,
    selectedEffectId: String? = null,
    color: (TimelineBarKind, Int) -> Int = { _, _ -> 0 },
): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>()
    val labelFor: (TimelineBarKind, Int, String) -> String

    // 1. Video row: every clip (or the implicit full-length clip).
    val clips = if (project.clips.isEmpty()) {
        listOf(
            dev.phonk.editor.model.ClipSegment(
                id = "__implicit",
                sourceStartMs = 0L,
                sourceEndMs = project.videoDurationMs.coerceAtLeast(1L),
                destStartMs = 0L,
                destEndMs = project.videoDurationMs.coerceAtLeast(1L),
            ),
        )
    } else {
        project.clips
    }
    rows += TimelineRow(
        barKind = TimelineBarKind.VIDEO,
        label = "Video",
        bars = clips.mapIndexed { i, c ->
            TimelineBar(
                kind = TimelineBarKind.VIDEO,
                itemId = c.id,
                label = c.id.take(8),
                startMs = c.destStartMs,
                endMs = c.destEndMs,
                selected = c.id == selectedClipId,
                visible = true,
                rowOrder = i,
            )
        },
    )

    // 2. Master audio waveform row (analysis of the video/primary audio).
    if (project.waveform.isNotEmpty() || project.audioUri != null) {
        rows += TimelineRow(
            barKind = TimelineBarKind.AUDIO,
            label = "Audio",
            isWaveform = true,
        )
    }

    // 3. One row per independent audio item (stable by rowOrder).
    project.audioItems.sortedBy { it.rowOrder }.forEachIndexed { i, a ->
        rows += TimelineRow(
            barKind = TimelineBarKind.AUDIO,
            label = a.label.ifBlank { "Audio" },
            bars = listOf(
                TimelineBar(
                    kind = TimelineBarKind.AUDIO,
                    itemId = a.id,
                    label = a.label.ifBlank { "Audio" },
                    startMs = a.startMs,
                    endMs = a.endMs,
                    selected = a.id == selectedAudioId,
                    visible = true,
                    rowOrder = i,
                ),
            ),
        )
    }

    // 4. One row per overlay item (topmost last).
    project.overlays.sortedBy { it.zIndex }.forEachIndexed { i, o ->
        rows += TimelineRow(
            barKind = TimelineBarKind.OVERLAY,
            label = "Overlay",
            bars = listOf(
                TimelineBar(
                    kind = TimelineBarKind.OVERLAY,
                    itemId = o.id,
                    label = o.label.ifBlank { o.kind },
                    startMs = o.startMs,
                    endMs = o.endMs,
                    selected = o.id == selectedOverlayId,
                    visible = o.visible,
                    rowOrder = i,
                ),
            ),
        )
    }

    // 5. One row per text item.
    project.textLayers.sortedBy { it.zIndex }.forEachIndexed { i, t ->
        rows += TimelineRow(
            barKind = TimelineBarKind.TEXT,
            label = "Text",
            bars = listOf(
                TimelineBar(
                    kind = TimelineBarKind.TEXT,
                    itemId = t.id,
                    label = t.text.ifBlank { "Text" },
                    startMs = t.startMs,
                    endMs = t.endMs,
                    selected = t.id == selectedOverlayId,
                    visible = t.visible,
                    rowOrder = i,
                ),
            ),
        )
    }

    // 6. One row per independent (non clip-attached) effect item.
    val clipIds = project.clips.map { it.id }.toSet()
    project.effects
        .filter { it.clipId !in clipIds }
        .sortedBy { it.t0Ms }
        .forEachIndexed { i, e ->
            rows += TimelineRow(
                barKind = TimelineBarKind.EFFECT,
                label = "Effect",
                bars = listOf(
                    TimelineBar(
                        kind = TimelineBarKind.EFFECT,
                        itemId = e.id,
                        label = e.kind.wire.ifBlank { "Effect" },
                        startMs = e.t0Ms,
                        endMs = e.t1Ms,
                        selected = e.id == selectedEffectId,
                        visible = true,
                        rowOrder = i,
                    ),
                ),
            )
        }

    return rows
}

/** Finder used by hit-testing / live drag overrides. */
fun TimelineRow.barById(id: String): TimelineBar? = bars.firstOrNull { it.itemId == id }