package dev.phonk.editor.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.phonk.editor.analysis.AnalysisManager
import dev.phonk.editor.analysis.AnalysisState
import dev.phonk.editor.editor.CutPattern
import dev.phonk.editor.editor.CutPlanner
import dev.phonk.editor.editor.EditEngine
import dev.phonk.editor.export.ExportRunner
import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.DropType
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.preview.PlayerController
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Editor-scoped ViewModel. Owns the mutable project, analysis state machine,
 * preview player, export runner and the timeline editing commands.
 */
class EditorViewModel(
    private val app: Context,
) : ViewModel() {

    private val _project = MutableStateFlow<PhonkProject?>(null)
    val project: StateFlow<PhonkProject?> = _project.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisResult?>(null)
    val analysis: StateFlow<AnalysisResult?> = _analysis.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    val analysisManager: AnalysisManager =
        AnalysisManager(app.contentResolver, viewModelScope)

    val exportRunner: ExportRunner = ExportRunner(app, viewModelScope)

    val editEngine = EditEngine()

    val player: PlayerController = PlayerController(app)

    init {
        viewModelScope.launch {
            analysisManager.state.collect { s ->
                if (s is AnalysisState.Done) {
                    _analysis.value = s.result
                    updateProject {
                        it.copy(
                            bpm = s.result.bpm,
                            beats = s.result.beats,
                            drops = s.result.drops,
                            sections = s.result.sections,
                            waveform = s.result.compactEnergy(400).toList(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    persist()
                }
            }
        }
    }

    /** Persists the current project so edits and analysis survive a restart. */
    private fun persist() {
        _project.value?.let { ProjectStore(app).save(it) }
    }

    /** Applies an edit through the undo stack and persists. */
    private fun commit(transform: (PhonkProject) -> PhonkProject) {
        val p = _project.value ?: return
        _project.value = editEngine.apply(p) { it.let(transform) }
        refreshUndoState()
        persist()
    }

    private fun refreshUndoState() {
        _canUndo.value = editEngine.canUndo
        _canRedo.value = editEngine.canRedo
    }

    fun setProject(p: PhonkProject) {
        _project.value = p
        if (p.beats.isNotEmpty()) {
            _analysis.value = AnalysisResult(
                bpm = p.bpm,
                sampleRate = 0,
                durationMs = p.videoDurationMs,
                beats = p.beats,
                drops = p.drops,
                sections = p.sections,
                beatConfidence = 0f,
                dropConfidence = 0f,
                energyCurve = FloatArray(0),
                fluxCurve = FloatArray(0),
            )
        }
        player.setVideo(Uri.parse(p.videoUri ?: p.audioUri ?: ""))
        player.pause()
        _playheadMs.value = 0L
        refreshUndoState()
    }

    fun updateProject(transform: (PhonkProject) -> PhonkProject) {
        _project.update { it?.let(transform) }
    }

    fun beginAnalysis() {
        val p = _project.value ?: return
        analysisManager.analyze(Uri.parse(p.audioUri ?: p.videoUri ?: return))
    }

    /** Imports a video URI and (re)builds a single full-length clip. */
    fun importVideo(uri: Uri, name: String, durationMs: Long) {
        val p = _project.value ?: return
        val dur = if (durationMs > 0) durationMs else p.videoDurationMs
        val clip = ClipSegment(
            sourceStartMs = 0L,
            sourceEndMs = dur,
            destStartMs = 0L,
            destEndMs = dur,
        )
        val updated = p.copy(
            name = name.ifBlank { p.name },
            videoUri = uri.toString(),
            videoDurationMs = dur,
            clips = listOf(clip),
            selectedClipId = clip.id,
            updatedAt = System.currentTimeMillis(),
        )
        _project.value = editEngine.apply(p) { updated }
        refreshUndoState()
        persist()
        player.setVideo(uri)
        player.pause()
        _playheadMs.value = 0L
    }

    /** Imports a separate audio track for analysis and playback. */
    fun importAudio(uri: Uri) {
        val p = _project.value ?: return
        _project.value = editEngine.apply(p) {
            it.copy(audioUri = uri.toString(), updatedAt = System.currentTimeMillis())
        }
        refreshUndoState()
        persist()
        if (p.audioUri == null) {
            player.setVideo(uri)
            player.pause()
        }
    }

    fun selectClip(id: String?) {
        val p = _project.value ?: return
        if (p.selectedClipId == id) return
        _project.value = p.copy(selectedClipId = id, updatedAt = System.currentTimeMillis())
        persist()
    }

    /** Clip under [ms] on the destination timeline, preferring the selection. */
    fun clipAt(ms: Long): ClipSegment? =
        _project.value?.clips?.firstOrNull { ms in it.destStartMs until it.destEndMs }

    fun selectedClip(): ClipSegment? {
        val p = _project.value ?: return null
        val sel = p.clips.firstOrNull { it.id == p.selectedClipId }
        if (sel != null) return sel
        return clipAt(sourceToDest(player.pollPosition()))
    }

    /** Trims the clip under the playhead (or the selection) to [destStartMs..destEndMs]. */
    fun trimClip(destStartMs: Long, destEndMs: Long) {
        val p = _project.value ?: return
        val clip = selectedClip() ?: return
        if (destEndMs <= destStartMs || destStartMs < clip.destStartMs || destEndMs > clip.destEndMs) return
        val srcDur = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(0L)
        val destDur = (clip.destEndMs - clip.destStartMs).coerceAtLeast(1L)
        val newDestDur = destEndMs - destStartMs
        val ratio = newDestDur.toDouble() / destDur
        val newSrcDur = (srcDur * ratio).toLong().coerceAtLeast(1L)
        val offsetRatio = (destStartMs - clip.destStartMs).toDouble() / destDur
        val newSrcStart = clip.sourceStartMs + (srcDur * offsetRatio).toLong()
        commit { proj ->
            val trimmed = clip.copy(
                sourceStartMs = newSrcStart,
                sourceEndMs = newSrcStart + newSrcDur,
                destStartMs = destStartMs,
                destEndMs = destEndMs,
            )
            proj.copy(clips = proj.clips.map { if (it.id == clip.id) trimmed else it }, updatedAt = System.currentTimeMillis())
        }
    }

    fun deleteSelectedClip() {
        val p = _project.value ?: return
        val clip = selectedClip() ?: return
        val removedDur = clip.destDurationMs
        commit { proj ->
            val remaining = proj.clips.filterNot { it.id == clip.id }
            val shifted = shiftAfter(remaining, clip.destStartMs, -removedDur)
            proj.copy(
                clips = shifted,
                selectedClipId = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun duplicateSelectedClip() {
        val p = _project.value ?: return
        val clip = selectedClip() ?: return
        val dur = clip.destDurationMs
        val copy = clip.copy(
            id = java.util.UUID.randomUUID().toString().take(8),
            destStartMs = clip.destEndMs,
            destEndMs = clip.destEndMs + dur,
        )
        commit { proj ->
            val remaining = proj.clips.filterNot { it.id == clip.id }
            val shifted = shiftAfter(remaining, copy.destStartMs, dur)
            val rebuilt = (shifted + copy).sortedBy { it.destStartMs }
            proj.copy(clips = rebuilt, selectedClipId = copy.id, updatedAt = System.currentTimeMillis())
        }
    }

    /** Sets playback speed on the clip under the playhead (or selection). */
    fun setClipSpeed(speed: Float) {
        val p = _project.value ?: return
        val clip = selectedClip() ?: return
        val safe = speed.coerceIn(0.25f, 4f)
        val srcDur = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val newDestDur = (srcDur / safe).toLong().coerceAtLeast(1L)
        val oldDestDur = clip.destDurationMs
        val delta = newDestDur - oldDestDur
        commit { proj ->
            val updated = clip.copy(speed = safe, destEndMs = clip.destStartMs + newDestDur)
            val clips = proj.clips.map { if (it.id == clip.id) updated else it }
            val shifted = shiftAfter(clips, clip.destEndMs, delta)
            proj.copy(clips = shifted, updatedAt = System.currentTimeMillis())
        }
    }

    fun setClipTransition(name: String?) {
        val clip = selectedClip() ?: return
        commit { proj ->
            proj.copy(
                clips = proj.clips.map { if (it.id == clip.id) it.copy(transition = name) else it },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun setTransitionDuration(ms: Long) {
        val v = ms.coerceIn(0L, 3000L)
        commit { proj -> proj.copy(transitionDurationMs = v, updatedAt = System.currentTimeMillis()) }
    }

    fun setVolume(v: Float) {
        val safe = v.coerceIn(0f, 1f)
        player.player.volume = if (_project.value?.muted == true) 0f else safe
        commit { proj -> proj.copy(volume = safe, updatedAt = System.currentTimeMillis()) }
    }

    fun setMuted(m: Boolean) {
        player.player.volume = if (m) 0f else (_project.value?.volume ?: 1f)
        commit { proj -> proj.copy(muted = m, updatedAt = System.currentTimeMillis()) }
    }

    fun setFadeIn(ms: Long) {
        commit { proj -> proj.copy(fadeInMs = ms.coerceAtLeast(0L), updatedAt = System.currentTimeMillis()) }
    }

    fun setFadeOut(ms: Long) {
        commit { proj -> proj.copy(fadeOutMs = ms.coerceAtLeast(0L), updatedAt = System.currentTimeMillis()) }
    }

    fun setPitch(pitch: Float) {
        player.setPitch(pitch.coerceIn(0.5f, 2f))
        commit { proj -> proj.copy(pitch = pitch.coerceIn(0.5f, 2f), updatedAt = System.currentTimeMillis()) }
    }

    fun setBrightness(v: Float) {
        commit { proj -> proj.copy(brightness = v.coerceIn(-1f, 1f), updatedAt = System.currentTimeMillis()) }
    }

    fun setContrast(v: Float) {
        commit { proj -> proj.copy(contrast = v.coerceIn(-1f, 1f), updatedAt = System.currentTimeMillis()) }
    }

    fun setSaturation(v: Float) {
        commit { proj -> proj.copy(saturation = v.coerceIn(-1f, 1f), updatedAt = System.currentTimeMillis()) }
    }

    fun applyPattern(pattern: CutPattern) {
        val p = _project.value ?: return
        val a = _analysis.value ?: return
        val plan = CutPlanner.planPattern(a, pattern, maxSourceMs = p.videoDurationMs.takeIf { it > 0 })
        commit { it.copy(clips = plan.clips, selectedClipId = null, updatedAt = System.currentTimeMillis()) }
    }

    /** Applies a beat subdivision (1/4, 1/2, 1, 2, 4, 8 beats per cut). */
    fun applyBeatSubdivision(beatsPerCut: Double) {
        val p = _project.value ?: return
        val a = _analysis.value ?: return
        val plan = CutPlanner.planCustom(a, beatsPerCut, maxSourceMs = p.videoDurationMs.takeIf { it > 0 })
        commit { it.copy(clips = plan.clips, selectedClipId = null, updatedAt = System.currentTimeMillis()) }
    }

    /** Adds a manual drop marker snapped to the current playhead (destination time). */
    fun addDropAt(ms: Long, type: DropType = DropType.SECTION_DROP) {
        val src = destToSource(ms)
        val snap = _analysis.value?.let { CutPlanner.snap(src, it) } ?: src
        commit { it.copy(drops = it.drops + dev.phonk.editor.model.DropMarker(
            timestampMs = snap.toDouble(), confidence = 1f, strength = 0.8f, type = type),
            updatedAt = System.currentTimeMillis()) }
    }

    fun removeDropAt(ms: Long) {
        val src = destToSource(ms)
        commit { it.copy(drops = it.drops.filterNot { d -> abs(d.timestampMs - src) < 250.0 }, updatedAt = System.currentTimeMillis()) }
    }

    fun addTextLayer(text: String, startMs: Long, endMs: Long, fontSize: Float, opacity: Float, animation: String) {
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceAtLeast(start + 500L)
        commit { proj ->
            proj.copy(textLayers = proj.textLayers + TextLayer(
                text = text,
                startMs = start,
                endMs = end,
                fontSize = fontSize.coerceIn(8f, 120f),
                opacity = opacity.coerceIn(0.05f, 1f),
                animation = animation,
            ), updatedAt = System.currentTimeMillis())
        }
    }

    fun removeTextLayer(id: String) {
        commit { proj -> proj.copy(textLayers = proj.textLayers.filterNot { it.id == id }, updatedAt = System.currentTimeMillis()) }
    }

    fun addOverlay(kind: String, label: String, uri: String?, startMs: Long, endMs: Long) {
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceAtLeast(start + 500L)
        commit { proj ->
            proj.copy(overlays = proj.overlays + OverlayLayer(
                kind = kind, label = label, uri = uri, startMs = start, endMs = end,
            ), updatedAt = System.currentTimeMillis())
        }
    }

    fun removeOverlay(id: String) {
        commit { proj -> proj.copy(overlays = proj.overlays.filterNot { it.id == id }, updatedAt = System.currentTimeMillis()) }
    }

    fun undo() {
        val p = _project.value ?: return
        _project.value = editEngine.undo(p)
        refreshUndoState()
        persist()
    }

    fun redo() {
        val p = _project.value ?: return
        _project.value = editEngine.redo(p)
        refreshUndoState()
        persist()
    }

    /** Splits the clip under [ms] exactly at the playhead. */
    fun splitAt(ms: Long) {
        val p = _project.value ?: return
        if (p.clips.isEmpty()) {
            _project.value = p.copy(clips = listOf(
                ClipSegment(sourceStartMs = 0, sourceEndMs = p.videoDurationMs, destStartMs = 0, destEndMs = p.videoDurationMs)),
                updatedAt = System.currentTimeMillis())
            persist()
            return
        }
        val idx = p.clips.indexOfFirst { ms in it.destStartMs until it.destEndMs }
        if (idx < 0) return
        val clip = p.clips[idx]
        val splitPoint = ms.coerceIn(clip.destStartMs + 1, clip.destEndMs - 1)
        val srcRatio = (splitPoint - clip.destStartMs).toFloat() / (clip.destEndMs - clip.destStartMs).coerceAtLeast(1)
        val srcSplit = clip.sourceStartMs + ((clip.sourceEndMs - clip.sourceStartMs) * srcRatio).toLong()
        val left = clip.copy(sourceEndMs = srcSplit, destEndMs = splitPoint)
        val right = clip.copy(
            id = java.util.UUID.randomUUID().toString().take(8),
            sourceStartMs = srcSplit,
            destStartMs = splitPoint,
            destEndMs = clip.destEndMs,
        )
        val newClips = p.clips.toMutableList()
        newClips[idx] = left
        newClips.add(idx + 1, right)
        _project.value = editEngine.apply(p) { it.copy(clips = newClips, selectedClipId = right.id, updatedAt = System.currentTimeMillis()) }
        refreshUndoState()
        persist()
    }

    fun addEffect(kind: EffectKind) {
        val p = _project.value ?: return
        if (kind == EffectKind.NONE) return
        val pos = player.pollPosition()
        val clip = p.clips.firstOrNull { pos in it.destStartMs..it.destEndMs }
        if (clip != null) {
            _project.value = p.copy(
                clips = p.clips.map { c ->
                    if (c.id == clip.id) c.copy(effect = kind, effectStrength = 0.7f) else c
                },
                updatedAt = System.currentTimeMillis(),
            )
        } else {
            _project.value = p.copy(
                effects = p.effects + dev.phonk.editor.model.ClipEffect(
                    clipId = "fx_${System.currentTimeMillis()}",
                    kind = kind,
                    t0Ms = pos,
                    t1Ms = pos + 200,
                    amount = 0.7f,
                ),
                updatedAt = System.currentTimeMillis(),
            )
        }
        persist()
    }

    fun clearClipEffect() {
        val clip = selectedClip() ?: return
        commit { proj ->
            proj.copy(clips = proj.clips.map { if (it.id == clip.id) it.copy(effect = EffectKind.NONE, effectStrength = 0f) else it }, updatedAt = System.currentTimeMillis())
        }
    }

    fun requestExport(pattern: CutPattern, config: ExportConfig) {
        val p = _project.value
        val a = _analysis.value
        if (p == null) {
            exportRunner.setFailed(app.getString(R.string.export_need_analysis))
            return
        }
        if (a == null) {
            exportRunner.setFailed(app.getString(R.string.export_need_analysis))
            return
        }
        exportRunner.export(p, a, pattern, config)
    }

    fun resetExport() {
        exportRunner.reset()
    }

    fun cancelExport() {
        exportRunner.cancel()
    }

    fun setCurrentPosition(ms: Long) {
        _playheadMs.value = ms
        player.scrubTo(destToSource(ms), 0L, _project.value?.videoDurationMs ?: 0L)
    }

    /** Maps a destination (timeline) timestamp to the source media timestamp. */
    fun destToSource(destMs: Long): Long {
        val p = _project.value ?: return destMs
        if (p.clips.isEmpty()) return destMs
        val clip = p.clips.firstOrNull { destMs in it.destStartMs until it.destEndMs }
            ?: if (destMs >= p.timelineDurationMs()) p.clips.last() else return destMs.coerceIn(0L, p.videoDurationMs)
        val destDur = (clip.destEndMs - clip.destStartMs).coerceAtLeast(1L)
        val srcDur = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(0L)
        val ratio = srcDur.toDouble() / destDur
        return (clip.sourceStartMs + ((destMs - clip.destStartMs) * ratio).toLong())
            .coerceIn(clip.sourceStartMs, clip.sourceEndMs)
    }

    /** Maps a source (media) timestamp to the destination timeline timestamp. */
    fun sourceToDest(srcMs: Long): Long {
        val p = _project.value ?: return srcMs
        if (p.clips.isEmpty()) return srcMs
        val clip = p.clips.firstOrNull { srcMs in it.sourceStartMs until it.sourceEndMs }
            ?: p.clips.lastOrNull() ?: return srcMs.coerceIn(0L, p.timelineDurationMs())
        val srcDur = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val destDur = (clip.destEndMs - clip.destStartMs).coerceAtLeast(0L)
        val ratio = destDur.toDouble() / srcDur
        return (clip.destStartMs + ((srcMs - clip.sourceStartMs) * ratio).toLong())
            .coerceIn(clip.destStartMs, clip.destEndMs)
    }

    fun playPause() {
        if (player.player.isPlaying) player.pause() else player.play()
    }

    /** Syncs the timeline playhead with the preview (called on a timer).
     *  Exposes destination timeline time so UI coordinates always match clips. */
    fun pumpPosition() {
        val pos = player.pollPosition()
        _playheadMs.value = sourceToDest(pos)
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        fun factory(app: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel(app) }
        }
    }

    private fun shiftAfter(clips: List<ClipSegment>, fromMs: Long, deltaMs: Long): List<ClipSegment> =
        clips.map {
            if (it.destStartMs >= fromMs) {
                it.copy(
                    destStartMs = (it.destStartMs + deltaMs).coerceAtLeast(0L),
                    destEndMs = (it.destEndMs + deltaMs).coerceAtLeast(0L),
                )
            } else it
        }
}
