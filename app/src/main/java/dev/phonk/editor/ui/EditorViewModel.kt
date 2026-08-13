package dev.phonk.editor.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import dev.phonk.editor.analysis.AnalysisManager
import dev.phonk.editor.analysis.AnalysisState
import dev.phonk.editor.editor.CutPattern
import dev.phonk.editor.editor.CutPlanner
import dev.phonk.editor.editor.EditEngine
import dev.phonk.editor.export.ExportRunner
import dev.phonk.editor.model.AnalysisResult
import dev.phonk.editor.model.BackgroundType
import dev.phonk.editor.model.CanvasBackground
import dev.phonk.editor.model.ClipSegment
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.ColorGradeMaps
import dev.phonk.editor.model.CropConfig
import dev.phonk.editor.model.DropType
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.GradeKeyframe
import dev.phonk.editor.model.GradeParam
import dev.phonk.editor.model.MaskConfig
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.SubtitleCue
import dev.phonk.editor.model.SubtitleTrack
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.model.withTiming
import dev.phonk.editor.model.withTransform
import dev.phonk.editor.preview.PlayerController
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.timeline.TimelineTime
import dev.phonk.editor.timeline.TimelineTrim
import dev.phonk.editor.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import dev.phonk.editor.model.AudioItem
import dev.phonk.editor.model.ClipEffect

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

    /** Destination ms of the most recent explicit user seek (timeline tap/drag).
     *  While set, [pumpPosition] keeps the playhead exactly where the user put
     *  it instead of echoing a stale/derived player position. Cleared once the
     *  player has actually arrived at the requested source time. */
    private var pendingSeekDestMs: Long? = null

    private val _selectedOverlayId = MutableStateFlow<String?>(null)
    val selectedOverlayId: StateFlow<String?> = _selectedOverlayId.asStateFlow()

    /** Tracks the pre-gesture snapshot + liveness for the current overlay drag. */
    private val overlayGesture = OverlayGestureTracker()

    /** Decides whether a newly set project is a real switch vs. the same project
     *  being re-emitted/reloaded, so per-project state is only wiped on a switch. */
    private val projectTracker = ProjectSwitchTracker()

    /** Identity of the project an in-flight analysis run belongs to. */
    private var analysisProjectId: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** True once the player's real duration has been folded back into the
     *  project. Reset whenever the media URI changes (setProject/importVideo)
     *  so a reloaded or replaced source is re-validated. */
    private var durationSynced = false

    val analysisManager: AnalysisManager =
        AnalysisManager(app.contentResolver, viewModelScope)

    val exportRunner: ExportRunner = ExportRunner(app, viewModelScope)

    val editEngine = EditEngine()

    val player: PlayerController = PlayerController(app)

    init {
        player.onEnded = {
            val d = playPauseDecision(
                playbackState = Player.STATE_ENDED,
                playWhenReady = player.player.playWhenReady,
                isPlaying = player.player.isPlaying,
                playPauseToggled = false,
            )
            _isPlaying.value = d.newIsPlaying
        }
        viewModelScope.launch {
            analysisManager.state.collect { s ->
                if (s is AnalysisState.Done) {
                    val target = analysisProjectId
                    if (target == null || target != projectTracker.currentIdentity) {
                        // Stale result: either no analysis is in flight for the current
                        // project, or the result belongs to a previous project. Never
                        // apply it to the currently loaded project.
                        return@collect
                    }
                    // Re-check that analysis is still in flight for THIS project
                    if (analysisProjectId != target) return@collect
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

    /** Applies an edit through the undo stack and persists.
 *  [mergeKey] coalesces a continuous gesture (e.g. slider drag) into one
 *  undo step. Trailing-lambda callers can use `commit { ... }`. */
    private fun commit(
        mergeKey: String? = null,
        transform: (PhonkProject) -> PhonkProject,
    ) {
        val p = _project.value ?: return
        _project.value = editEngine.apply(p, mergeKey) { it.let(transform) }
        refreshUndoState()
        persist()
    }

    private fun refreshUndoState() {
        _canUndo.value = editEngine.canUndo
        _canRedo.value = editEngine.canRedo
    }

    fun setProject(p: PhonkProject) {
        if (projectTracker.onProjectSet(projectIdentity(p))) {
            // Genuine switch to a different project: wipe every piece of state that
            // belongs to the previous project so it cannot leak into the new one.
            editEngine.clear()
            overlayGesture.reset()
            _analysis.value = null
            _selectedOverlayId.value = null
            analysisManager.cancel()
            analysisProjectId = null
        }
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
        val mediaUri = p.videoUri ?: p.audioUri
        if (mediaUri != null) player.setVideo(Uri.parse(mediaUri)) else player.setVideo(null)
        player.pause()
        durationSynced = false
        _isPlaying.value = false
        _playheadMs.value = 0L
        pendingSeekDestMs = null
        refreshUndoState()
    }

    fun updateProject(transform: (PhonkProject) -> PhonkProject) {
        _project.update { it?.let(transform) }
    }

    fun beginAnalysis() {
        val p = _project.value ?: return
        val uri = Uri.parse(p.audioUri ?: p.videoUri ?: return)
        analysisProjectId = projectIdentity(p)
        analysisManager.analyze(uri)
    }

    /** Imports a video URI and (re)builds a single full-length clip. */
    fun importVideo(uri: Uri, name: String, durationMs: Long, videoWidth: Int = 0, videoHeight: Int = 0) {
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
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            clips = listOf(clip),
            selectedClipId = clip.id,
            updatedAt = System.currentTimeMillis(),
        )
        _project.value = editEngine.apply(p) { updated }
        refreshUndoState()
        persist()
        player.setVideo(uri)
        player.pause()
        durationSynced = false
        _playheadMs.value = 0L
        pendingSeekDestMs = null
        beginAnalysis()
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
        if (destEndMs <= destStartMs) return
        // Clamp into the valid window (previous clip end / next clip start /
        // source media bounds) instead of rejecting the gesture, so a handle
        // drag can never snap back after the model refuses the request.
        val bounds = TimelineTrim.bounds(clip, p)
        val (start, end) = TimelineTrim.clamp(destStartMs, destEndMs, bounds)
        val (newSrcStart, newSrcEnd) = TimelineTrim.toSource(clip, start, end, p.videoDurationMs)
        commit { proj ->
            val trimmed = clip.copy(
                sourceStartMs = newSrcStart,
                sourceEndMs = newSrcEnd,
                destStartMs = start,
                destEndMs = end,
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

    /** Toggles reverse playback on the selected clip. */
    fun setClipReversed(reversed: Boolean) {
        val clip = selectedClip() ?: return
        commit { proj ->
            proj.copy(
                clips = proj.clips.map { if (it.id == clip.id) it.copy(reversed = reversed) else it },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /** Sets the canvas background behind the letterboxed video. */
    fun setCanvasBackground(bg: CanvasBackground) {
        commit { proj -> proj.copy(canvasBackground = bg, updatedAt = System.currentTimeMillis()) }
    }

    fun setCanvasBackgroundType(type: BackgroundType) {
        commit { proj -> proj.copy(canvasBackground = proj.canvasBackground.copy(type = type), updatedAt = System.currentTimeMillis()) }
    }

    fun setCanvasBackgroundColor(argb: Long) {
        commit { proj -> proj.copy(canvasBackground = proj.canvasBackground.copy(colorArgb = argb), updatedAt = System.currentTimeMillis()) }
    }

    fun setCanvasBackgroundImage(uri: String?) {
        commit { proj -> proj.copy(canvasBackground = proj.canvasBackground.copy(imageUri = uri), updatedAt = System.currentTimeMillis()) }
    }

    /** Sets the custom crop region (fractions of the content rect). */
    fun setCrop(enabled: Boolean, x: Float = 0f, y: Float = 0f, w: Float = 1f, h: Float = 1f) {
        commit { proj ->
            proj.copy(
                crop = CropConfig(enabled = enabled, xFraction = x, yFraction = y, wFraction = w, hFraction = h),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /** Toggles audio ducking (music ducks under voice-over peaks). */
    fun setAudioDucking(enabled: Boolean) {
        commit { proj -> proj.copy(audioDucking = enabled, updatedAt = System.currentTimeMillis()) }
    }

    fun setVoiceOverUri(uri: String?) {
        commit { proj -> proj.copy(voiceOverUri = uri, updatedAt = System.currentTimeMillis()) }
    }

    /** Sets the chroma key (green screen) for the selected image overlay. */
    fun setOverlayChromaKey(id: String, colorArgb: Int?, similarity: Float = 0.3f) {
        commit { p -> applyOverlayItem(p, id) { ov ->
            if (ov is OverlayLayer) ov.copy(chromaKeyColor = colorArgb?.toLong(), chromaKeySimilarity = similarity)
            else ov
        } }
    }

    /** Sets the shape mask for the selected image overlay. */
    fun setOverlayMask(id: String, mask: MaskConfig) {
        commit { p -> applyOverlayItem(p, id) { ov ->
            if (ov is OverlayLayer) ov.copy(mask = mask)
            else ov
        } }
    }

    fun setTransitionDuration(ms: Long) {
        val v = ms.coerceIn(0L, 3000L)
        commit { proj -> proj.copy(transitionDurationMs = v, updatedAt = System.currentTimeMillis()) }
    }

    fun setAspectRatio(label: String) {
        commit { proj -> proj.copy(aspectRatio = label, updatedAt = System.currentTimeMillis()) }
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
        setGrade(GradeParam.BRIGHTNESS, v)
    }

    fun setContrast(v: Float) {
        setGrade(GradeParam.CONTRAST, v)
    }

    fun setSaturation(v: Float) {
        setGrade(GradeParam.SATURATION, v)
    }

    /** Single path for every continuous grade parameter (coalesced undo). */
    fun setGrade(param: GradeParam, value: Float) {
        val v = value.coerceIn(param.range)
        commit("grade:$param") { proj ->
            ColorGradeMaps.apply(proj, ColorGradeMaps.of(proj).with(param, v))
        }
    }

    /** Resets every grade parameter to neutral in one undo step. */
    fun resetGrade() {
        commit { proj -> ColorGradeMaps.apply(proj, ColorGrade()) }
    }

    /** Captures the current grade as an automation keyframe at [atMs]. */
    fun addGradeKeyframe(atMs: Long = _playheadMs.value) {
        val p = _project.value ?: return
        val at = atMs.coerceIn(0L, p.timelineDurationMs().takeIf { it > 0 } ?: p.videoDurationMs)
        commit {
            val existing = it.gradeKeyframes.filter { it.atMs != at }
            it.copy(gradeKeyframes = (existing + GradeKeyframe(at, it.gradeAt(at))).sortedBy { k -> k.atMs })
        }
    }

    fun clearGradeKeyframes() {
        commit { it.copy(gradeKeyframes = emptyList()) }
    }

    fun setGradeKeyframesEnabled(enabled: Boolean) {
        commit { it.copy(gradeKeyframesEnabled = enabled) }
    }

    fun setBeatSync(enabled: Boolean) {
        commit { it.copy(beatSync = enabled) }
    }

    fun setBeatSyncStrength(strength: Float) {
        commit("beatStrength") { it.copy(beatSyncStrength = strength.coerceIn(0f, 1f)) }
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
        val total = _project.value?.let { it.timelineDurationMs().takeIf { t -> t > 0 } ?: it.videoDurationMs } ?: endMs
        val end = endMs.coerceIn(start + MIN_OVERLAY_DURATION, total.takeIf { it > start } ?: start + MIN_OVERLAY_DURATION)
        commit { proj ->
            proj.copy(textLayers = proj.textLayers + TextLayer(
                text = text,
                startMs = start,
                endMs = end,
                fontSize = fontSize.coerceIn(8f, 120f),
                opacity = opacity.coerceIn(0.05f, 1f),
                animation = animation,
                zIndex = proj.textLayers.size + proj.overlays.size,
            ), updatedAt = System.currentTimeMillis())
        }
        _selectedOverlayId.value = _project.value?.textLayers?.lastOrNull()?.id
    }

    fun removeTextLayer(id: String) {
        commit { proj -> proj.copy(textLayers = proj.textLayers.filterNot { it.id == id }, updatedAt = System.currentTimeMillis()) }
        if (_selectedOverlayId.value == id) _selectedOverlayId.value = null
    }

    fun addOverlay(kind: String, label: String, uri: String?, startMs: Long, endMs: Long) {
        val start = startMs.coerceAtLeast(0L)
        val total = _project.value?.let { it.timelineDurationMs().takeIf { t -> t > 0 } ?: it.videoDurationMs } ?: endMs
        val end = endMs.coerceIn(start + MIN_OVERLAY_DURATION, total.takeIf { it > start } ?: start + MIN_OVERLAY_DURATION)
        commit { proj ->
            proj.copy(overlays = proj.overlays + OverlayLayer(
                kind = kind, label = label, uri = uri, startMs = start, endMs = end,
                zIndex = proj.textLayers.size + proj.overlays.size,
            ), updatedAt = System.currentTimeMillis())
        }
        _selectedOverlayId.value = _project.value?.overlays?.lastOrNull()?.id
    }

    fun removeOverlay(id: String) {
        commit { proj ->
            proj.copy(
                overlays = proj.overlays.filterNot { it.id == id },
                textLayers = proj.textLayers.filterNot { it.id == id },
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (_selectedOverlayId.value == id) _selectedOverlayId.value = null
    }

    // ==================== Overlay editing system ====================

    /** Every overlay (text + image/sticker/emoji/shape) as one unified list. */
    fun overlayItems(): List<OverlayItem> {
        val p = _project.value ?: return emptyList()
        return (p.textLayers as List<OverlayItem>) + (p.overlays as List<OverlayItem>)
    }

    fun overlayById(id: String): OverlayItem? {
        val p = _project.value ?: return null
        return p.textLayers.firstOrNull { it.id == id }
            ?: p.overlays.firstOrNull { it.id == id }
    }

    fun selectOverlay(id: String?) {
        overlayGesture.reset()
        _selectedOverlayId.value = id
    }

    /** Default end time for a new overlay: 3s from the playhead, capped by the
     *  video/timeline duration (never full-video by default). */
    fun defaultOverlayEnd(startMs: Long): Long {
        val p = _project.value ?: return startMs + DEFAULT_OVERLAY_DURATION
        val total = p.timelineDurationMs().takeIf { it > 0 } ?: p.videoDurationMs
        return minOf(startMs + DEFAULT_OVERLAY_DURATION, total)
    }

    /** Marks the start of a transform gesture so it becomes ONE undo step. */
    fun beginOverlayGesture() {
        val p = _project.value ?: return
        overlayGesture.begin(p)
    }

    /**
     * Live transform during a drag/pinch/rotate. Updates the project in memory
     * only (no undo entry, no disk write per frame); [endOverlayTransform] seals
     * a single undo step and persists once.
     */
    fun transformOverlayLive(id: String, x: Float, y: Float, sx: Float, sy: Float, rot: Float, opacity: Float) {
        overlayGesture.markDirty()
        _project.update { p ->
            p?.let { applyOverlayItem(it, id) { it.withTransform(x, y, sx, sy, rot, opacity) } }
        }
    }

    /** Seals the active gesture: one undo entry (from before the gesture) + persist. */
    fun endOverlayTransform() {
        val before = overlayGesture.end() ?: return
        val after = _project.value ?: return
        _project.value = editEngine.apply(before) { after }
        refreshUndoState()
        persist()
    }

    /**
     * Aborts the active gesture WITHOUT an undo entry and clears the stale
     * pre-gesture snapshot. Called from the overlay editor's cancellation path
     * (ACTION_CANCEL / pointer coroutine cancelled mid-drag by navigation away,
     * recomposition or the editor leaving composition), where the normal
     * on-end path never runs.
     */
    fun cancelOverlayTransform() {
        overlayGesture.cancel()
    }

    /** Committed (single-step) transform update — for buttons/handles, not drags. */
    fun setOverlayTransform(id: String, x: Float, y: Float, sx: Float, sy: Float, rot: Float, opacity: Float) {
        commit { p -> applyOverlayItem(p, id) { it.withTransform(x, y, sx, sy, rot, opacity) } }
    }

    fun setOverlayOpacity(id: String, opacity: Float) {
        commit { p -> applyOverlayItem(p, id) { it.withTransform(opacity = opacity.coerceIn(0f, 1f)) } }
    }

    fun setOverlayRotation(id: String, rotation: Float) {
        commit { p -> applyOverlayItem(p, id) { it.withTransform(rotation = rotation) } }
    }

    /** Sets the overlay's timeline window (drags on the overlay track). */
    fun setOverlayTiming(id: String, startMs: Long, endMs: Long) {
        val p = _project.value ?: return
        val total = p.timelineDurationMs().takeIf { it > 0 } ?: p.videoDurationMs
        val minDur = MIN_OVERLAY_DURATION
        val s = startMs.coerceIn(0L, (endMs - minDur).coerceAtLeast(0L))
        val e = endMs.coerceIn(s + minDur, total.takeIf { it > 0 } ?: s + minDur)
        commit { proj -> applyOverlayItem(proj, id) { it.withTiming(startMs = s, endMs = e) } }
    }

    /** Shifts an overlay's window by [deltaMs], clamped to the timeline. */
    fun moveOverlayTimeline(id: String, deltaMs: Long) {
        val p = _project.value ?: return
        val total = p.timelineDurationMs().takeIf { it > 0 } ?: p.videoDurationMs
        val item = overlayById(id) ?: return
        val dur = (item.endMs - item.startMs).coerceAtLeast(MIN_OVERLAY_DURATION)
        val s = (item.startMs + deltaMs).coerceIn(0L, (total - dur).coerceAtLeast(0L))
        val e = (s + dur).coerceAtMost(total.takeIf { it > 0 } ?: s + dur)
        commit { proj -> applyOverlayItem(proj, id) { it.withTiming(startMs = s, endMs = e) } }
    }

    /** Duplicates an overlay (new id, same content/transform/duration), offset so
     *  both copies are visible. */
    fun duplicateOverlay(id: String) {
        val p = _project.value ?: return
        val item = overlayById(id) ?: return
        val dup = when (item) {
            is TextLayer -> item.copy(
                id = java.util.UUID.randomUUID().toString().take(8),
                x = (item.x + 0.06f).coerceAtMost(0.94f),
                y = (item.y + 0.06f).coerceAtMost(0.94f),
                zIndex = item.zIndex + 1,
            )
            is OverlayLayer -> item.copy(
                id = java.util.UUID.randomUUID().toString().take(8),
                x = (item.x + 0.06f).coerceAtMost(0.94f),
                y = (item.y + 0.06f).coerceAtMost(0.94f),
                zIndex = item.zIndex + 1,
            )
            else -> return
        }
        commit { proj ->
            when (dup) {
                is TextLayer -> proj.copy(
                    textLayers = proj.textLayers + dup,
                    updatedAt = System.currentTimeMillis(),
                )
                is OverlayLayer -> proj.copy(
                    overlays = proj.overlays + dup,
                    updatedAt = System.currentTimeMillis(),
                )
                else -> proj
            }
        }
        _selectedOverlayId.value = dup.id
    }

    fun deleteOverlay(id: String) { removeOverlay(id)
    }

    fun setOverlayLocked(id: String, locked: Boolean) {
        commit { p ->
            val t = p.textLayers.map { if (it.id == id) it.copy(locked = locked) else it }
            val o = p.overlays.map { if (it.id == id) it.copy(locked = locked) else it }
            p.copy(textLayers = t, overlays = o, updatedAt = System.currentTimeMillis())
        }
    }

    fun addSubtitleTrack(fileName: String, cues: List<SubtitleCue>) {
        commit { p ->
            p.copy(subtitles = p.subtitles + SubtitleTrack(fileName = fileName, cues = cues), updatedAt = System.currentTimeMillis())
        }
    }

    fun setSubtitleVisible(id: String, visible: Boolean) {
        commit { p ->
            p.copy(subtitles = p.subtitles.map { if (it.id == id) it.copy(visible = visible) else it }, updatedAt = System.currentTimeMillis())
        }
    }

    fun clearSubtitles() {
        commit { p -> p.copy(subtitles = emptyList(), updatedAt = System.currentTimeMillis()) }
    }

    fun setOverlayVisible(id: String, visible: Boolean) {
        commit { p ->
            val t = p.textLayers.map { if (it.id == id) it.copy(visible = visible) else it }
            val o = p.overlays.map { if (it.id == id) it.copy(visible = visible) else it }
            p.copy(textLayers = t, overlays = o, updatedAt = System.currentTimeMillis())
        }
    }

    /** Reassigns z-indexes so [id] draws above everything else. */
    fun bringOverlayToFront(id: String) {
        val items = overlayItems()
        if (items.size <= 1) return
        val sorted = items.sortedBy { it.zIndex }
        val top = sorted.lastOrNull()?.zIndex ?: return
        commit { p ->
            val rest = sorted.filter { it.id != id }
            val next = (top + 1).coerceAtMost(Int.MAX_VALUE - 1)
            applyOverlayZIndex(p, id, next)
        }
    }

    /** Reassigns z-indexes so [id] draws below everything else. */
    fun sendOverlayToBack(id: String) {
        val items = overlayItems()
        if (items.size <= 1) return
        val sorted = items.sortedBy { it.zIndex }
        val bottom = sorted.firstOrNull()?.zIndex ?: return
        commit { p ->
            applyOverlayZIndex(p, id, (bottom - 1).coerceAtLeast(Int.MIN_VALUE + 1))
        }
    }

    /** Edits an existing text overlay in place (never a new layer per edit). */
    fun updateTextOverlay(
        id: String,
        text: String,
        fontSize: Float,
        opacity: Float,
        animation: String,
        colorArgb: Long,
    ) {
        commit { p ->
            p.copy(
                textLayers = p.textLayers.map {
                    if (it.id == id) it.copy(
                        text = text.ifBlank { it.text },
                        fontSize = fontSize.coerceIn(8f, 120f),
                        opacity = opacity.coerceIn(0f, 1f),
                        animation = animation,
                        colorArgb = colorArgb,
                    ) else it
                },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun applyOverlayItem(p: PhonkProject, id: String, f: (OverlayItem) -> OverlayItem): PhonkProject {
        val changed = f(overlayById(id) ?: return p)
        return when (changed) {
            is TextLayer -> p.copy(textLayers = p.textLayers.map { if (it.id == id) changed else it })
            is OverlayLayer -> p.copy(overlays = p.overlays.map { if (it.id == id) changed else it })
            else -> p
        }
    }

    private fun applyOverlayZIndex(p: PhonkProject, id: String, z: Int): PhonkProject {
        val t = p.textLayers.map { if (it.id == id) it.copy(zIndex = z) else it }
        val o = p.overlays.map { if (it.id == id) it.copy(zIndex = z) else it }
        return p.copy(textLayers = t, overlays = o, updatedAt = System.currentTimeMillis())
    }

    fun undo() {
        val p = _project.value ?: return
        overlayGesture.reset()
        _project.value = editEngine.undo(p)
        refreshUndoState()
        persist()
    }

    fun redo() {
        val p = _project.value ?: return
        overlayGesture.reset()
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
        val splitPoint = splitPointFor(clip.destStartMs, clip.destEndMs, ms) ?: return
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
                effects = p.effects + ClipEffect(
                    id = java.util.UUID.randomUUID().toString().take(8),
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

    // ==================== Independent timeline items (multi-item support) ====================

    /** Adds another independent audio clip to its own timeline row. */
    fun addAudioItem(uri: String, label: String, startMs: Long, endMs: Long) {
        val p = _project.value ?: return
        val start = startMs.coerceAtLeast(0L)
        val end = endMs.coerceIn(start + MIN_OVERLAY_DURATION, p.timelineDurationMs().takeIf { it > start } ?: start + MIN_OVERLAY_DURATION)
        val next = (p.audioItems.maxOfOrNull { it.rowOrder } ?: -1) + 1
        commit { proj ->
            proj.copy(
                audioItems = proj.audioItems + AudioItem(
                    uri = uri, label = label.ifBlank { "Audio" }, startMs = start, endMs = end, rowOrder = next,
                ),
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun removeAudioItem(id: String) {
        commit { p -> p.copy(audioItems = p.audioItems.filterNot { it.id == id }, updatedAt = System.currentTimeMillis()) }
    }

    /** Moves/resizes an independent audio item on its timeline row. */
    fun setAudioItemTiming(id: String, startMs: Long, endMs: Long) {
        val p = _project.value ?: return
        val total = p.timelineDurationMs().takeIf { it > 0 } ?: p.videoDurationMs
        val minDur = MIN_OVERLAY_DURATION
        val s = startMs.coerceIn(0L, (endMs - minDur).coerceAtLeast(0L))
        val e = endMs.coerceIn(s + minDur, total.takeIf { it > 0 } ?: s + minDur)
        commit { proj -> proj.copy(audioItems = proj.audioItems.map { if (it.id == id) it.copy(startMs = s, endMs = e) else it }, updatedAt = System.currentTimeMillis()) }
    }

    /** Moves/resizes an independent effect item on its timeline row. */
    fun setEffectTiming(id: String, startMs: Long, endMs: Long) {
        val p = _project.value ?: return
        val total = p.timelineDurationMs().takeIf { it > 0 } ?: p.videoDurationMs
        val minDur = MIN_OVERLAY_DURATION
        val s = startMs.coerceIn(0L, (endMs - minDur).coerceAtLeast(0L))
        val e = endMs.coerceIn(s + minDur, total.takeIf { it > 0 } ?: s + minDur)
        commit { proj ->
            proj.copy(
                effects = proj.effects.map { if (it.id == id) it.copy(t0Ms = s, t1Ms = e) else it },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /** Removes an independent effect item (not clip-attached effects). */
    fun removeEffect(id: String) {
        commit { p -> p.copy(effects = p.effects.filterNot { it.id == id }, updatedAt = System.currentTimeMillis()) }
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
        pendingSeekDestMs = ms
        player.scrubTo(destToSource(ms), 0L, _project.value?.videoDurationMs ?: 0L)
    }

    /** Maps a destination (timeline) timestamp to the source media timestamp. */
    fun destToSource(destMs: Long): Long {
        val p = _project.value ?: return destMs
        return TimelineTime.destToSource(p.clips, destMs, p.videoDurationMs, p.timelineDurationMs())
    }

    /** Maps a source (media) timestamp to the destination timeline timestamp. */
    fun sourceToDest(srcMs: Long): Long {
        val p = _project.value ?: return srcMs
        return TimelineTime.sourceToDest(p.clips, srcMs, p.videoDurationMs, p.timelineDurationMs())
    }

    fun playPause() {
        val d = playPauseDecision(
            playbackState = player.player.playbackState,
            playWhenReady = player.player.playWhenReady,
            isPlaying = player.player.isPlaying,
            playPauseToggled = true,
        )
        if (d.shouldSeekToZero) {
            player.player.seekTo(0L)
        }
        if (d.newIsPlaying) {
            player.play()
        } else {
            player.pause()
        }
        _isPlaying.value = d.newIsPlaying
    }

    /** Back-fills the real media duration from the player when the stored
     *  metadata was missing (the MediaStore probe failed at import time, e.g.
     *  for SAF document URIs). Without it the header, player controls and
     *  timeline all read 00:00 and every seek collapses to zero while the video
     *  still plays. Also materializes the full-length clip so split/trim work. */
    private fun ensureVideoDuration() {
        if (durationSynced) return
        val p = _project.value ?: return
        if (p.videoDurationMs > 0L && p.clips.any { it.destEndMs > 0L }) {
            durationSynced = true
            return
        }
        val real = player.player.duration
        if (real <= 0L) return
        durationSynced = true
        val updated = p.withMediaDuration(real)
        if (updated != p) {
            _project.value = updated
            persist()
        }
    }

    /** Syncs the timeline playhead with the preview (called on a timer).
     *  Exposes destination timeline time so UI coordinates always match clips. */
    fun pumpPosition() {
        ensureVideoDuration()
        val pos = player.pollPosition()
        val pending = pendingSeekDestMs
        if (pending != null) {
            // Protect the user's manual seek: media3 applies seekTo asynchronously,
            // so a poll between the tap and the applied seek would read the OLD
            // position and stomp the playhead back to zero / anywhere else.
            val target = destToSource(pending)
            if (pos >= target - 150L && pos <= target + 150L) {
                pendingSeekDestMs = null
            }
            _playheadMs.value = pending
        } else {
            _playheadMs.value = sourceToDest(pos)
        }
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        /** Default duration for a newly added overlay (3s, capped by video length). */
        const val DEFAULT_OVERLAY_DURATION = 3000L

        /** Minimum overlay window width when trimming on the timeline. */
        const val MIN_OVERLAY_DURATION = 100L

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

/**
 * Tracks the pre-gesture project snapshot for a single overlay transform drag.
 *
 * A completed gesture produces EXACTLY ONE undo entry: [end] returns the
 * pre-gesture snapshot once and clears all state. A cancelled gesture (the
 * overlay editor's pointer-input coroutine is killed mid-drag by navigation
 * away, recomposition or leaving composition, so the on-end path never runs)
 * must NEVER create an undo entry and MUST clear the stale snapshot — otherwise
 * a later gesture (even a plain tap) would seal an undo entry rooted at an
 * arbitrarily old project. Pure Kotlin so it is unit-testable without Android.
 */
internal class OverlayGestureTracker {

    private var start: PhonkProject? = null
    private var active = false
    private var dirty = false

    /** Captures the pre-gesture snapshot at the start of a drag. */
    fun begin(before: PhonkProject) {
        start = before
        active = true
        dirty = false
    }

    /** Records that a live transform actually fired during the gesture. */
    fun markDirty() {
        dirty = true
    }

    /**
     * Seals a completed gesture: returns the pre-gesture snapshot exactly once
     * (the caller pushes one undo entry), or null when no transform gesture was
     * actually in progress. A stale snapshot left by a previously cancelled
     * gesture is never allowed to become an undo entry.
     */
    fun end(): PhonkProject? {
        val before = start
        val seal = active && dirty && before != null
        reset()
        return if (seal) before else null
    }

    /** Discards a cancelled gesture. Never produces an undo entry. */
    fun cancel() {
        reset()
    }

    /** Defensive reset on project/selection change. */
    fun reset() {
        start = null
        active = false
        dirty = false
    }
}

/**
 * Reports whether [identity] marks a genuine project switch (vs. the same project
 * being re-emitted/reloaded), so per-project editor state is only wiped on an
 * actual switch. Extracted into a plain-JUnit-testable seam.
 */
class ProjectSwitchTracker {
    private var current: String? = null

    val currentIdentity: String? get() = current

    fun onProjectSet(identity: String): Boolean {
        val switched = current != identity
        current = identity
        return switched
    }
}

/** Stable identity used to tell projects apart across editor sessions. */
fun projectIdentity(p: PhonkProject): String =
    p.id.ifBlank { p.videoUri ?: p.audioUri ?: p.name }

/** Outcome of a play/pause toggle (or player-state sync) for the preview. */
data class PlayPauseDecision(
    /** True when playback must be seeked back to the start before resuming. */
    val shouldSeekToZero: Boolean,
    /** The `_isPlaying` UI flag the caller must adopt. */
    val newIsPlaying: Boolean,
)

/**
 * Pure, JVM-testable decision for the preview play/pause behavior.
 *
 * [playPauseToggled] is true when the user pressed play/pause, false when this
 * is a player-state notification (e.g. playback reached the end).
 *
 * Media3 quirk: at [Player.STATE_ENDED] the player keeps `playWhenReady=true`
 * while `isPlaying=false`, so a plain `play()` is a no-op and the video stays
 * frozen on the last frame. When the toggle fires in that state the playback
 * must first be seeked back to the start, otherwise it can never restart.
 */
fun playPauseDecision(
    playbackState: Int,
    playWhenReady: Boolean,
    isPlaying: Boolean,
    playPauseToggled: Boolean,
): PlayPauseDecision {
    if (!playPauseToggled) {
        // State-change path: keep the UI flag in sync with the real player. At
        // ENDED the player is not playing even though playWhenReady may still be
        // true, so the UI must show Play, not Pause.
        return PlayPauseDecision(
            shouldSeekToZero = false,
            newIsPlaying = playbackState != Player.STATE_ENDED && playWhenReady && isPlaying,
        )
    }
    return if (isPlaying) {
        PlayPauseDecision(shouldSeekToZero = false, newIsPlaying = false)
    } else if (playbackState == Player.STATE_ENDED) {
        PlayPauseDecision(shouldSeekToZero = true, newIsPlaying = true)
    } else {
        PlayPauseDecision(shouldSeekToZero = false, newIsPlaying = true)
    }
}

/**
 * Computes the split position for a clip that starts at [destStartMs] and ends
 * at [destEndMs], splitting at [ms]. Returns null when the clip is too short to
 * split (needs at least 2ms so the coerce range is not empty), which would
 * otherwise throw IllegalArgument cast on clips trimmed down to ~1ms.
 */
internal fun splitPointFor(destStartMs: Long, destEndMs: Long, ms: Long): Long? {
    if (destEndMs - destStartMs < 2) return null
    return ms.coerceIn(destStartMs + 1, destEndMs - 1)
}
