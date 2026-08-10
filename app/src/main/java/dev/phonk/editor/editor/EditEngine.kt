package dev.phonk.editor.editor

import dev.phonk.editor.model.DropMarker
import dev.phonk.editor.model.DropType
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.PhonkProject
import java.util.ArrayDeque

/**
 * Undo/redo command stack for marker and cut edits. Snap-to-beat and
 * snap-to-drop are applied by the caller before invoking commands.
 */
class EditEngine {

    private class Entry(
        val before: PhonkProject,
        val after: PhonkProject,
        val key: String?,
    )

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Applies [action] and records an undo/redo entry. When [key] matches the
     * top undo entry (e.g. the same slider being dragged continuously), the
     * gesture is coalesced: a single undo restores the state from before the
     * drag started instead of inserting one entry per UI tick.
     */
    fun apply(
        project: PhonkProject,
        key: String? = null,
        action: (PhonkProject) -> PhonkProject,
    ): PhonkProject {
        val after = action(project)
        var before = project
        if (key != null) {
            val top = undoStack.peekFirst()
            if (top != null && top.key == key) {
                undoStack.removeFirst()
                before = top.before
            }
        }
        undoStack.push(Entry(before, after, key))
        redoStack.clear()
        return after
    }

    fun undo(project: PhonkProject): PhonkProject {
        val entry = undoStack.popOrNull() ?: return project
        redoStack.push(Entry(entry.before, entry.after, entry.key))
        return entry.before
    }

    fun redo(project: PhonkProject): PhonkProject {
        val entry = redoStack.popOrNull() ?: return project
        undoStack.push(Entry(entry.before, entry.after, entry.key))
        return entry.after
    }

    private fun <T> ArrayDeque<T>.popOrNull(): T? = if (isEmpty()) null else removeFirst()

    companion object Actions {

        fun addDrop(project: PhonkProject, atMs: Double, type: DropType): PhonkProject =
            project.copy(
                drops = project.drops + DropMarker(
                    timestampMs = atMs,
                    confidence = 1f,
                    strength = 0.7f,
                    type = type,
                ),
                updatedAt = System.currentTimeMillis(),
            )

        fun removeDrop(project: PhonkProject, atMs: Double): PhonkProject =
            project.copy(
                drops = project.drops.filterNot {
                    Math.abs(it.timestampMs - atMs) < 5.0
                },
                updatedAt = System.currentTimeMillis(),
            )

        fun moveDrop(project: PhonkProject, fromMs: Double, toMs: Double): PhonkProject =
            project.copy(
                drops = project.drops.map {
                    if (Math.abs(it.timestampMs - fromMs) < 5.0) it.copy(timestampMs = toMs) else it
                },
                updatedAt = System.currentTimeMillis(),
            )

        fun addCut(project: PhonkProject, clips: List<dev.phonk.editor.model.ClipSegment>): PhonkProject =
            project.copy(clips = clips, updatedAt = System.currentTimeMillis())
    }
}

/** Translates a cut plan into beat-aware effect specs for the renderer. */
object EffectScheduler {

    data class EffectAt(
        val atDestMs: Long,
        val durationMs: Long,
        val kind: EffectKind,
        val amount: Float,
    )

    /**
     * Builds an effect schedule aligned to the destination timeline:
     *  - flash (80 ms) at strong drops
     *  - zoom punch (120 ms) at medium drops
     *  - shake when effects are enabled on a drop clip
     *  - brightness pulse on every downbeat if requested
     */
    fun schedule(
        clips: List<dev.phonk.editor.model.ClipSegment>,
        beats: List<dev.phonk.editor.model.BeatMarker>,
        dropPulseEnabled: Boolean = true,
        downbeatPulse: Boolean = false,
    ): List<EffectAt> {
        val out = ArrayList<EffectAt>()
        for (clip in clips) {
            if (clip.dropTransition && clip.effect != EffectKind.NONE && dropPulseEnabled) {
                val duration = when (clip.effect) {
                    EffectKind.FLASH -> 80L
                    EffectKind.ZOOM -> 120L
                    else -> 90L
                }
                // Anchor on the drop itself, not the clip end: within a clip the
                // destination maps linearly from source, so the drop lands at
                // destStart + (dropSource - sourceStart). Falling back to the
                // clip end would fire the effect up to half a cut-step late.
                val dropDestMs: Long = clip.dropSourceMs?.let { drop ->
                    clip.destStartMs + (drop - clip.sourceStartMs).coerceAtLeast(0L)
                } ?: clip.destEndMs
                out.add(
                    EffectAt(
                        atDestMs = (dropDestMs - duration / 2).coerceAtLeast(0L),
                        durationMs = duration,
                        kind = clip.effect,
                        amount = clip.effectStrength.coerceAtLeast(0.3f),
                    )
                )
            }
        }
        if (downbeatPulse) {
            for (b in beats) {
                if (b.downbeat) {
                    out.add(EffectAt(b.timestampMs.roundToLong(), 40L, EffectKind.BRIGHTNESS, 0.15f))
                }
            }
        }
        return out.sortedBy { it.atDestMs }
    }
}

fun Double.roundToLong(): Long = Math.round(this)