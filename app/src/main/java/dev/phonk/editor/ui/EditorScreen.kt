package dev.phonk.editor.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.phonk.editor.R
import dev.phonk.editor.editor.CutPattern
import dev.phonk.editor.export.ExportDialog
import dev.phonk.editor.export.ExportState
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.timeline.TimelineController
import dev.phonk.editor.timeline.TimelineView
import dev.phonk.editor.ui.components.BottomToolHeight
import dev.phonk.editor.ui.components.PanelHeight
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkIconButton
import dev.phonk.editor.ui.components.PhonkPanel
import dev.phonk.editor.ui.components.PhonkToolButton
import dev.phonk.editor.ui.components.ToolbarHeight
import dev.phonk.editor.ui.editor.EditorPreview
import dev.phonk.editor.ui.editor.TextEditDialog
import dev.phonk.editor.ui.editor.panels.AdjustPanel
import dev.phonk.editor.ui.editor.panels.AudioTrackPanel
import dev.phonk.editor.ui.editor.panels.BeatPanel
import dev.phonk.editor.ui.editor.panels.EffectsPanel
import dev.phonk.editor.ui.editor.panels.FiltersPanel
import dev.phonk.editor.ui.editor.panels.MediaPanel
import dev.phonk.editor.ui.editor.panels.OverlayPanel
import dev.phonk.editor.ui.editor.panels.SpeedPanel
import dev.phonk.editor.ui.editor.panels.SpeedPreset
import dev.phonk.editor.ui.editor.panels.TextPanel
import dev.phonk.editor.ui.editor.panels.TransitionsPanel
import kotlinx.coroutines.delay

private enum class Tool(val labelRes: Int) {
    MEDIA(R.string.tool_media),
    AUDIO(R.string.tool_audio),
    TEXT(R.string.tool_text),
    EFFECTS(R.string.tool_effects),
    FILTERS(R.string.tool_filters),
    SPEED(R.string.tool_speed),
    BEAT(R.string.tool_beat),
    TRANSITION(R.string.tool_transition),
    OVERLAY(R.string.tool_overlay),
    ADJUST(R.string.tool_adjust),
}

private fun queryName(resolver: android.content.ContentResolver, uri: Uri): String {
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx) ?: "Untitled"
        }
    }
    return "Untitled"
}

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
) {
    val appCtx = LocalContext.current.applicationContext
    val vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(appCtx))
    var selectedTool by remember { mutableStateOf(Tool.MEDIA) }
    var showExport by remember { mutableStateOf(false) }
    var exportConfig by remember { mutableStateOf(ExportConfig()) }
    var pattern by remember { mutableStateOf(CutPattern.B) }
    var showTextDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editOverlayId by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val durationMs = runCatching {
                dev.phonk.editor.analysis.AudioExtractor.queryDuration(appCtx.contentResolver, uri)
            }.getOrDefault(0L)
            vm.importVideo(uri, queryName(appCtx.contentResolver, uri), durationMs)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.importAudio(uri)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val start = vm.playheadMs.value
            vm.addOverlay("Image", queryName(appCtx.contentResolver, uri), uri.toString(), start, start + 3000)
        }
    }

    val p by vm.project.collectAsStateWithLifecycle()
    val analysisState by vm.analysisManager.state.collectAsStateWithLifecycle()
    val exportState by vm.exportRunner.state.collectAsStateWithLifecycle()
    val playhead by vm.playheadMs.collectAsStateWithLifecycle()
    val isPlayingState by vm.isPlaying.collectAsStateWithLifecycle()
    val selectedOverlayId by vm.selectedOverlayId.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()

    val controller = remember { TimelineController { vm.project.value ?: PhonkProject() } }

    LaunchedEffect(Unit) {
        val loaded = ProjectStore(appCtx).load(projectId)
        if (loaded != null) {
            vm.setProject(loaded)
            controller.totalMs = loaded.timelineDurationMs().takeIf { it > 0 } ?: loaded.videoDurationMs
        }
    }

    // Playhead sync: poll the player and push into the timeline controller.
    LaunchedEffect(Unit) {
        while (true) {
            vm.pumpPosition()
            controller.currentMs = playhead.coerceIn(0L, controller.totalMs)
            delay(100)
        }
    }

    // Per-clip speed preview: mirror the speed of the clip under the playhead.
    LaunchedEffect(playhead, p, isPlayingState) {
        val clip = vm.clipAt(playhead)
        if (clip != null && isPlayingState && clip.speed != 1f) {
            vm.player.setPreviewSpeed(clip.speed)
        } else if (!isPlayingState) {
            vm.player.resetPlaybackParameters()
        }
    }

    val analysis = vm.analysis.value

    // Back: close dialogs/panels first, only then leave the editor.
    BackHandler(enabled = true) {
        when {
            showExport -> showExport = false
            showTextDialog -> showTextDialog = false
            editOverlayId != null -> editOverlayId = null
            selectedTool != Tool.MEDIA -> selectedTool = Tool.MEDIA
            message != null -> message = null
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .background(MaterialTheme.colorScheme.background),
        ) {
        // ==================== TOP TOOLBAR ====================
        Row(
            Modifier
                .fillMaxWidth()
                .height(ToolbarHeight)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhonkIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            Text(
                p?.name ?: stringResource(R.string.editor_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
            )
            PhonkIconButton(
                icon = Icons.Filled.Undo,
                contentDescription = stringResource(R.string.undo),
                onClick = { vm.undo() },
                background = if (canUndo) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color.Transparent,
            )
            PhonkIconButton(
                icon = Icons.Filled.Redo,
                contentDescription = stringResource(R.string.redo),
                onClick = { vm.redo() },
                background = if (canRedo) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color.Transparent,
            )
            Spacer(Modifier.width(6.dp))
            PhonkButton(
                label = stringResource(R.string.export),
                onClick = {
                    vm.resetExport()
                    showExport = true
                },
                primary = true,
            )
        }

        // ==================== PREVIEW ====================
        EditorPreview(
            playerController = vm.player,
            project = p,
            isPlaying = isPlayingState,
            positionMs = vm.destToSource(playhead),
            destPlayheadMs = playhead,
            onPlayPause = { vm.playPause() },
            selectedOverlayId = selectedOverlayId,
            onOverlaySelect = { vm.selectOverlay(it) },
            onOverlayTransformBegin = { vm.beginOverlayGesture() },
            onOverlayTransformLive = { id, x, y, sx, sy, rot, op ->
                vm.transformOverlayLive(id, x, y, sx, sy, rot, op)
            },
            onOverlayTransformEnd = { vm.endOverlayTransform() },
            onOverlayTransformCancel = { vm.cancelOverlayTransform() },
            onEditText = { editOverlayId = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // ==================== MULTI-TRACK TIMELINE ====================
        AndroidView(
            factory = { ctx ->
                TimelineView(ctx).also { tv ->
                    tv.controller = controller
                    tv.project = vm.project.value ?: PhonkProject()
                    tv.onSeekTo = { ms -> vm.setCurrentPosition(ms) }
                    tv.onClipSplit = { ms -> vm.splitAt(ms) }
                    tv.onSelectClip = { id -> vm.selectClip(id) }
                    tv.onTrimStart = { newStart -> vm.trimClip(newStart, vm.selectedClip()?.destEndMs ?: newStart) }
                    tv.onTrimEnd = { newEnd -> vm.trimClip(vm.selectedClip()?.destStartMs ?: newEnd, newEnd) }
                    tv.onSelectOverlay = { id -> vm.selectOverlay(id) }
                    tv.onSetOverlayTiming = { id, start, end -> vm.setOverlayTiming(id, start, end) }
                }
            },
            update = { tv ->
                val proj = vm.project.value ?: PhonkProject()
                tv.project = proj
                tv.selectedOverlayId = selectedOverlayId
                tv.controller.totalMs = proj.timelineDurationMs().takeIf { it > 0 } ?: proj.videoDurationMs
                tv.controller.currentMs = playhead.coerceIn(0L, tv.controller.totalMs)
                tv.refresh()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )

        // ==================== BOTTOM TOOL PANEL ====================
        PhonkPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(PanelHeight),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (selectedTool) {
                    Tool.MEDIA -> MediaPanel(
                        onImportVideo = { videoPicker.launch(arrayOf("video/*", "application/mp4")) },
                        onImportAudio = { audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/mp4", "audio/x-wav")) },
                        onImportPhoto = { imagePicker.launch(arrayOf("image/*")) },
                    )
                    Tool.AUDIO -> AudioTrackPanel(
                        volume = p?.volume ?: 1f,
                        muted = p?.muted == true,
                        fadeInMs = p?.fadeInMs ?: 0L,
                        fadeOutMs = p?.fadeOutMs ?: 0L,
                        pitch = p?.pitch ?: 1f,
                        onVolume = { vm.setVolume(it) },
                        onMuted = { vm.setMuted(it) },
                        onFadeIn = { vm.setFadeIn(it) },
                        onFadeOut = { vm.setFadeOut(it) },
                        onPitch = { vm.setPitch(it) },
                    )
                    Tool.TEXT -> TextPanel(
                        layers = p?.textLayers ?: emptyList(),
                        onAdd = { showTextDialog = true },
                        onRemove = { vm.removeTextLayer(it) },
                    )
                    Tool.EFFECTS -> EffectsPanel(
                        onAdd = { vm.addEffect(it) },
                        hasClipEffect = vm.selectedClip()?.effect != EffectKind.NONE,
                        onClear = { vm.clearClipEffect() },
                    )
                    Tool.FILTERS -> FiltersPanel(
                        grade = p?.colorGrade() ?: ColorGrade(),
                        keyframesEnabled = p?.gradeKeyframesEnabled == true,
                        keyframeCount = p?.gradeKeyframes?.size ?: 0,
                        beatSync = p?.beatSync == true,
                        beatSyncStrength = p?.beatSyncStrength ?: 0.8f,
                        onGrade = { param, v -> vm.setGrade(param, v) },
                        onResetAll = { vm.resetGrade() },
                        onAddKeyframe = { vm.addGradeKeyframe(playhead) },
                        onClearKeyframes = { vm.clearGradeKeyframes() },
                        onKeyframesEnabled = { vm.setGradeKeyframesEnabled(it) },
                        onBeatSync = { vm.setBeatSync(it) },
                        onBeatSyncStrength = { vm.setBeatSyncStrength(it) },
                    )
                    Tool.SPEED -> SpeedPanel(
                        speed = vm.selectedClip()?.speed ?: 1f,
                        onSpeed = { vm.setClipSpeed(it) },
                        onPreset = { presets ->
                            when (presets) {
                                SpeedPreset.NORMAL -> vm.setClipSpeed(1f)
                                SpeedPreset.HYPER -> vm.setClipSpeed(2f)
                                SpeedPreset.SLOW -> vm.setClipSpeed(0.5f)
                                SpeedPreset.FAST -> vm.setClipSpeed(1.5f)
                                SpeedPreset.BEAT_DROP -> vm.setClipSpeed(0.75f)
                            }
                        },
                    )
                    Tool.BEAT -> BeatPanel(
                        bpm = analysis?.bpm ?: 0.0,
                        onDetect = { vm.beginAnalysis() },
                        onSubdivision = { vm.applyBeatSubdivision(it) },
                        onAddDrop = { vm.addDropAt(playhead) },
                        onRemoveDrop = { vm.removeDropAt(playhead) },
                        dropCount = p?.drops?.size ?: 0,
                        onPattern = { vm.applyPattern(it) },
                    )
                    Tool.TRANSITION -> TransitionsPanel(
                        durationMs = p?.transitionDurationMs ?: 400L,
                        onDuration = { vm.setTransitionDuration(it) },
                        onSelect = { vm.setClipTransition(it) },
                        current = vm.selectedClip()?.transition,
                    )
                    Tool.OVERLAY -> OverlayPanel(
                        items = vm.overlayItems(),
                        selectedId = selectedOverlayId,
                        onAddImage = { imagePicker.launch(arrayOf("image/*")) },
                        onAddSymbol = { symbol, label ->
                            val start = playhead
                            vm.addTextLayer(symbol, start, start + 3000, 48f, 1f, "Fade")
                        },
                        onSelect = { vm.selectOverlay(it) },
                        onDuplicate = { vm.duplicateOverlay(it) },
                        onRemove = { vm.deleteOverlay(it) },
                        onLock = { id, locked -> vm.setOverlayLocked(id, locked) },
                        onHide = { id, visible -> vm.setOverlayVisible(id, visible) },
                        onFront = { vm.bringOverlayToFront(it) },
                        onBack = { vm.sendOverlayToBack(it) },
                        onEditText = { editOverlayId = it },
                    )
                    Tool.ADJUST -> AdjustPanel(
                        onSplit = { vm.splitAt(playhead) },
                        onTrim = { vm.trimClip(playhead, (vm.selectedClip()?.destEndMs ?: playhead)) },
                        onDelete = { vm.deleteSelectedClip() },
                        onDuplicate = { vm.duplicateSelectedClip() },
                        brightness = p?.brightness ?: 0f,
                        contrast = p?.contrast ?: 0f,
                        saturation = p?.saturation ?: 0f,
                        onBrightness = { vm.setBrightness(it) },
                        onContrast = { vm.setContrast(it) },
                        onSaturation = { vm.setSaturation(it) },
                    )
                }
            }
        }

        // ==================== BOTTOM TOOLBAR ====================
        Row(
            Modifier
                .fillMaxWidth()
                .height(BottomToolHeight)
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tool.entries.forEach { tool ->
                PhonkToolButton(
                    icon = toolIcon(tool),
                    label = stringResource(tool.labelRes),
                    active = selectedTool == tool,
                    onClick = { selectedTool = tool },
                )
            }
        }
        }

        message?.let { msg ->
            androidx.compose.material3.Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    androidx.compose.material3.TextButton(onClick = { message = null }) {
                        Text(stringResource(R.string.ok))
                    }
                },
            ) { Text(msg) }
        }
    }

    // ==================== TEXT EDIT DIALOG ====================
    if (showTextDialog || editOverlayId != null) {
        val editing = editOverlayId?.let { vm.overlayById(it) as? TextLayer }
        TextEditDialog(
            initial = editing?.text ?: "",
            initialSize = editing?.fontSize ?: 24f,
            initialOpacity = editing?.opacity ?: 1f,
            initialAnimation = editing?.animation ?: "Fade",
            initialColorArgb = editing?.colorArgb ?: 0xFFFFFFFFL,
            onDismiss = {
                showTextDialog = false
                editOverlayId = null
            },
            onSave = { text, size, opacity, anim, color ->
                if (editing != null) {
                    vm.updateTextOverlay(editing.id, text, size, opacity, anim, color)
                } else {
                    val start = playhead
                    vm.addTextLayer(text, start, start + 3000, size, opacity, anim)
                }
                showTextDialog = false
                editOverlayId = null
            },
        )
    }

    // ==================== EXPORT DIALOG ====================
    if (showExport) {
        ExportDialogFlow(
            exportState = exportState,
            exportConfig = exportConfig,
            onConfigChange = { exportConfig = it },
            onStart = { vm.requestExport(pattern, exportConfig) },
            onCancel = { vm.cancelExport() },
            onDismiss = { showExport = false },
            onRetry = { vm.requestExport(pattern, exportConfig) },
        )
    }
}

@Composable
private fun ExportDialogFlow(
    exportState: ExportState,
    exportConfig: ExportConfig,
    onConfigChange: (ExportConfig) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {
            if (exportState !is ExportState.Running) onDismiss()
        },
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                when (val es = exportState) {
                    is ExportState.Running -> {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { es.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.export_progress, (es.progress * 100).toInt()))
                    }
                    is ExportState.Done -> {
                        Text(
                            stringResource(R.string.export_saved, es.path),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            PhonkButton(
                                label = stringResource(R.string.export_open),
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(es.uri, "video/mp4")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PhonkButton(
                                label = stringResource(R.string.export_share),
                                onClick = {
                                    runCatching {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, es.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(send, null))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    is ExportState.Failed -> Text(
                        es.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    ExportState.Idle -> ExportDialog(
                        config = exportConfig,
                        onChange = onConfigChange,
                    )
                }
            }
        },
        confirmButton = {
            when (val es = exportState) {
                is ExportState.Running -> Unit
                is ExportState.Done -> PhonkButton(stringResource(R.string.ok), onClick = onDismiss)
                is ExportState.Failed -> PhonkButton(stringResource(R.string.retry), onClick = onRetry)
                ExportState.Idle -> PhonkButton(stringResource(R.string.render), onClick = onStart)
            }
        },
        dismissButton = {
            when (exportState) {
                is ExportState.Running -> {
                    androidx.compose.material3.TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.export_cancel))
                    }
                }
                else -> {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        },
    )
}

private fun toolIcon(tool: Tool) = when (tool) {
    Tool.MEDIA -> Icons.Filled.VideoLibrary
    Tool.AUDIO -> Icons.Filled.Audiotrack
    Tool.TEXT -> Icons.Filled.TextFields
    Tool.EFFECTS -> Icons.Filled.AutoFixHigh
    Tool.FILTERS -> Icons.Filled.Tune
    Tool.SPEED -> Icons.Filled.Speed
    Tool.BEAT -> Icons.Filled.GraphicEq
    Tool.TRANSITION -> Icons.Filled.Animation
    Tool.OVERLAY -> Icons.Filled.Layers
    Tool.ADJUST -> Icons.Filled.Settings
}
