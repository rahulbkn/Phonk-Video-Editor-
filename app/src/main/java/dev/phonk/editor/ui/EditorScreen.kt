package dev.phonk.editor.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.phonk.editor.model.CanvasBackground
import dev.phonk.editor.model.CropConfig
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.timeline.TimelineController
import dev.phonk.editor.timeline.TimelineView
import dev.phonk.editor.ui.components.EditorChip
import dev.phonk.editor.ui.components.EditorIconButton
import dev.phonk.editor.ui.components.EditorTokens
import dev.phonk.editor.ui.components.EditorToolButton
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkProgressBar
import dev.phonk.editor.ui.components.PhonkSlider
import dev.phonk.editor.ui.components.SectionHeader
import dev.phonk.editor.ui.components.ToolbarHeight
import dev.phonk.editor.ui.components.AppDimens
import dev.phonk.editor.ui.editor.EditorPreview
import dev.phonk.editor.ui.editor.TextEditDialog
import dev.phonk.editor.ui.editor.panels.AudioMixPanel
import dev.phonk.editor.ui.editor.panels.AudioTrackPanel
import dev.phonk.editor.ui.editor.panels.BackgroundPanel
import dev.phonk.editor.ui.editor.panels.CropPanel
import dev.phonk.editor.ui.editor.panels.EffectsPanel
import dev.phonk.editor.ui.editor.panels.FiltersPanel
import dev.phonk.editor.ui.editor.panels.MediaPanel
import dev.phonk.editor.ui.editor.panels.OverlayKeyPanel
import dev.phonk.editor.ui.editor.panels.OverlayPanel
import dev.phonk.editor.ui.editor.panels.RatioPanel
import dev.phonk.editor.ui.editor.panels.ReversePanel
import dev.phonk.editor.ui.editor.panels.SpeedPreset
import dev.phonk.editor.ui.editor.panels.SpeedPanel
import dev.phonk.editor.ui.editor.panels.BeatPanel
import dev.phonk.editor.ui.editor.panels.SubtitlePanel
import dev.phonk.editor.ui.editor.panels.TextPanel
import dev.phonk.editor.ui.editor.panels.TransitionsPanel
import dev.phonk.editor.ui.editor.panels.VolumePanel
import dev.phonk.editor.ui.editor.panels.FadeInPanel
import dev.phonk.editor.ui.editor.panels.FadeOutPanel
import dev.phonk.editor.ui.editor.panels.PitchPanel
import dev.phonk.editor.ui.editor.panels.FontPanel
import dev.phonk.editor.ui.editor.panels.ColorPanel
import dev.phonk.editor.ui.editor.panels.TextAnimationPanel
import dev.phonk.editor.ui.editor.panels.OpacityPanel
import dev.phonk.editor.ui.editor.panels.GradeSlidersPanel
import dev.phonk.editor.util.TimeUtils.formatClock
import kotlinx.coroutines.delay

// ─── Main Navigation Categories ──────────────────────────────────────────────

private enum class MainTool(val labelRes: Int) {
    EDIT(R.string.tool_edit),
    AUDIO(R.string.tool_audio),
    TEXT(R.string.tool_text),
    OVERLAY(R.string.tool_overlay),
    EFFECTS(R.string.tool_effects),
    MORE(R.string.tool_more),
}

private fun toolIcon(tool: MainTool): ImageVector = when (tool) {
    MainTool.EDIT -> Icons.Filled.ContentCut
    MainTool.AUDIO -> Icons.Filled.MusicNote
    MainTool.TEXT -> Icons.Filled.TextFields
    MainTool.OVERLAY -> Icons.Filled.Layers
    MainTool.EFFECTS -> Icons.Filled.AutoFixHigh
    MainTool.MORE -> Icons.Filled.MoreHoriz
}

// ─── Contextual Tool Definitions ─────────────────────────────────────────────

private data class ContextTool(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

private fun contextToolIcon(id: String): ImageVector = when (id) {
    "split" -> Icons.Filled.ContentCut
    "speed" -> Icons.Filled.Speed
    "volume" -> Icons.Filled.VolumeUp
    "transition" -> Icons.Filled.Animation
    "fade_in" -> Icons.Filled.VolumeUp
    "fade_out" -> Icons.Filled.VolumeDown
    "pitch" -> Icons.Filled.Tune
    "beat" -> Icons.Filled.MusicNote
    "add_text" -> Icons.Filled.Add
    "font" -> Icons.Filled.TextFormat
    "color" -> Icons.Filled.ColorLens
    "text_animation" -> Icons.Filled.Animation
    "add_overlay" -> Icons.Filled.Add
    "opacity" -> Icons.Filled.Opacity
    "duplicate" -> Icons.Filled.ContentCopy
    "delete" -> Icons.Filled.Delete
    "effects" -> Icons.Filled.AutoFixHigh
    "filters" -> Icons.Filled.Tune
    "adjust" -> Icons.Filled.Tune
    "ratio" -> Icons.Filled.AspectRatio
    "media" -> Icons.Filled.VideoLibrary
    "edit_text" -> Icons.Filled.Edit
    "background" -> Icons.Filled.Image
    "crop" -> Icons.Filled.Crop
    "reverse" -> Icons.Filled.Replay
    "subtitles" -> Icons.Filled.Subtitles
    "chroma" -> Icons.Filled.RemoveRedEye
    "voiceover" -> Icons.Filled.Mic
    else -> Icons.Filled.Info
}

// Default contextual tools for each main category
private val EDIT_CONTEXT_TOOLS = listOf(
    ContextTool("split", "Split", Icons.Filled.ContentCut),
    ContextTool("speed", "Speed", Icons.Filled.Speed),
    ContextTool("volume", "Volume", Icons.Filled.VolumeUp),
    ContextTool("transition", "Transition", Icons.Filled.Animation),
)

private val AUDIO_CONTEXT_TOOLS = listOf(
    ContextTool("volume", "Volume", Icons.Filled.VolumeUp),
    ContextTool("fade_in", "Fade In", Icons.Filled.VolumeUp),
    ContextTool("fade_out", "Fade Out", Icons.Filled.VolumeDown),
    ContextTool("pitch", "Pitch", Icons.Filled.Tune),
    ContextTool("beat", "Beat", Icons.Filled.MusicNote),
    ContextTool("voiceover", "Voice", Icons.Filled.Mic),
)

private val TEXT_CONTEXT_TOOLS = listOf(
    ContextTool("add_text", "Add", Icons.Filled.Add),
    ContextTool("font", "Font", Icons.Filled.TextFormat),
    ContextTool("color", "Color", Icons.Filled.ColorLens),
    ContextTool("text_animation", "Anim", Icons.Filled.Animation),
)

private val OVERLAY_CONTEXT_TOOLS = listOf(
    ContextTool("add_overlay", "Add", Icons.Filled.Add),
    ContextTool("opacity", "Opacity", Icons.Filled.Opacity),
    ContextTool("duplicate", "Dup", Icons.Filled.ContentCopy),
)

private val EFFECTS_CONTEXT_TOOLS = listOf(
    ContextTool("effects", "Effects", Icons.Filled.AutoFixHigh),
    ContextTool("filters", "Filters", Icons.Filled.Tune),
    ContextTool("adjust", "Adjust", Icons.Filled.Tune),
)

private val MORE_CONTEXT_TOOLS = listOf(
    ContextTool("ratio", "Ratio", Icons.Filled.AspectRatio),
    ContextTool("background", "Background", Icons.Filled.Image),
    ContextTool("crop", "Crop", Icons.Filled.Crop),
    ContextTool("reverse", "Reverse", Icons.Filled.Replay),
    ContextTool("subtitles", "Subtitles", Icons.Filled.Subtitles),
    ContextTool("chroma", "Chroma/Mask", Icons.Filled.RemoveRedEye),
    ContextTool("beat", "Beat", Icons.Filled.MusicNote),
    ContextTool("transition", "Transition", Icons.Filled.Animation),
    ContextTool("media", "Media", Icons.Filled.VideoLibrary),
)

// Selection-specific tools shown within the matching main category
private val TEXT_SELECTED_TOOLS = listOf(
    ContextTool("edit_text", "Edit", Icons.Filled.Edit),
    ContextTool("font", "Font", Icons.Filled.TextFormat),
    ContextTool("color", "Color", Icons.Filled.ColorLens),
    ContextTool("text_animation", "Anim", Icons.Filled.Animation),
)

private val OVERLAY_SELECTED_TOOLS = listOf(
    ContextTool("opacity", "Opacity", Icons.Filled.Opacity),
    ContextTool("duplicate", "Dup", Icons.Filled.ContentCopy),
    ContextTool("delete", "Del", Icons.Filled.Delete),
)

private fun getContextTools(
    mainTool: MainTool?,
    selectedOverlayId: String?,
    overlayById: (String) -> OverlayItem?,
): List<ContextTool> {
    val overlay = selectedOverlayId?.let { overlayById(it) }
    return when (mainTool) {
        MainTool.EDIT -> EDIT_CONTEXT_TOOLS
        MainTool.AUDIO -> AUDIO_CONTEXT_TOOLS
        MainTool.TEXT ->
            if (overlay is TextLayer) TEXT_SELECTED_TOOLS else TEXT_CONTEXT_TOOLS
        MainTool.OVERLAY ->
            if (overlay is OverlayLayer) OVERLAY_SELECTED_TOOLS else OVERLAY_CONTEXT_TOOLS
        MainTool.EFFECTS -> EFFECTS_CONTEXT_TOOLS
        MainTool.MORE -> MORE_CONTEXT_TOOLS
        null -> EDIT_CONTEXT_TOOLS
    }
}

// ─── Main Editor Screen ──────────────────────────────────────────────────────

@Composable
fun EditorScreen(projectId: String, onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    val vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(appCtx))
    var selectedMainTool by remember { mutableStateOf<MainTool?>(MainTool.EDIT) }
    var selectedContextTool by remember { mutableStateOf<String?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var exportConfig by remember { mutableStateOf(ExportConfig()) }
    var pattern by remember { mutableStateOf(CutPattern.B) }
    var showTextDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editOverlayId by remember { mutableStateOf<String?>(null) }
    var selectedAspect by remember { mutableStateOf("9:16") }
    var fullscreen by remember { mutableStateOf(false) }
    var zoomTick by remember { mutableStateOf(0) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val dur = runCatching { dev.phonk.editor.analysis.AudioExtractor.queryDuration(appCtx.contentResolver, uri) }.getOrDefault(0L)
            val (vw, vh) = runCatching { dev.phonk.editor.analysis.AudioExtractor.queryVideoSize(appCtx.contentResolver, uri) }.getOrDefault(0 to 0)
            vm.importVideo(uri, queryName(appCtx.contentResolver, uri), dur, vw, vh)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; vm.importAudio(uri) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val s = vm.playheadMs.value; vm.addOverlay("Image", queryName(appCtx.contentResolver, uri), uri.toString(), s, s + 3000)
        }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching {
                val name = queryName(appCtx.contentResolver, uri)
                val ins = appCtx.contentResolver.openInputStream(uri)
                if (ins == null) {
                    message = "Could not open $name"
                } else {
                    ins.use { stream ->
                        val cues = dev.phonk.editor.model.SubtitleParser.parse(stream)
                        if (cues.isEmpty()) message = "No captions found in $name"
                        else vm.addSubtitleTrack(name, cues)
                    }
                }
            }.onFailure { message = "Subtitle import failed" }
        }
    }
    val voiceOverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setVoiceOverUri(uri.toString())
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { appCtx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setCanvasBackgroundImage(uri.toString())
        }
    }

    val p by vm.project.collectAsStateWithLifecycle()
    val analysisState by vm.analysisManager.state.collectAsStateWithLifecycle()
    val exportState by vm.exportRunner.state.collectAsStateWithLifecycle()
    val playhead by vm.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val selectedOverlayId by vm.selectedOverlayId.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val controller = remember { TimelineController { vm.project.value ?: PhonkProject() } }

    LaunchedEffect(Unit) {
        val loaded = ProjectStore(appCtx).load(projectId)
        if (loaded != null) {
            vm.setProject(loaded)
            selectedAspect = loaded.aspectRatio
            controller.totalMs = loaded.timelineDurationMs().takeIf { it > 0 } ?: loaded.videoDurationMs
        }
    }
    LaunchedEffect(Unit) { while (true) { vm.pumpPosition(); controller.currentMs = playhead.coerceIn(0L, controller.totalMs); delay(100) } }
    LaunchedEffect(playhead, p, isPlaying) {
        val clip = vm.clipAt(playhead)
        if (clip != null && isPlaying && clip.speed != 1f) vm.player.setPreviewSpeed(clip.speed)
        else if (!isPlaying) vm.player.resetPlaybackParameters()
    }

    val analysis = vm.analysis.value
    val totalDur = p?.timelineDurationMs()?.takeIf { it > 0 } ?: p?.videoDurationMs ?: 0L

    // ─── Hierarchical Back Navigation ────────────────────────────────────────
    BackHandler(enabled = true) {
        when {
            fullscreen -> fullscreen = false
            showExport -> showExport = false
            showTextDialog -> showTextDialog = false
            editOverlayId != null -> editOverlayId = null
            selectedContextTool != null -> selectedContextTool = null
            selectedMainTool != null -> selectedMainTool = null
            message != null -> message = null
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(colorResource(R.color.background))) {
        if (fullscreen) {
            FullscreenPreview(vm, p, isPlaying, playhead, selectedOverlayId, fullscreen, onToggleFullscreen = { fullscreen = false }, onEditText = { editOverlayId = it })
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).background(colorResource(R.color.background))) {
                // ─── Top Bar ─────────────────────────────────────────────────
                TopBar(onBack = onBack, p = p, totalDur = totalDur, canUndo = canUndo, canRedo = canRedo, onUndo = { vm.undo() }, onRedo = { vm.redo() }, onExport = { vm.resetExport(); showExport = true })

                // ─── Aspect / Canvas Toolbar ─────────────────────────────────
                AspectToolbar(selectedAspect = selectedAspect, onAspectSelected = {
                    selectedAspect = it
                    vm.setAspectRatio(it)
                })

                // ─── Preview ─────────────────────────────────────────────────
                PreviewSection(vm, p, isPlaying, playhead, selectedOverlayId, selectedAspect, fullscreen, onToggleFullscreen = { fullscreen = !fullscreen }, onEditText = { editOverlayId = it })

                // ─── Compact Player Controls ─────────────────────────────────
                PlayerControls(playhead = playhead, totalDur = totalDur, isPlaying = isPlaying, onSeekBack = { vm.setCurrentPosition((playhead - 5000).coerceAtLeast(0)) }, onPlayPause = { vm.playPause() }, onSeekForward = { vm.setCurrentPosition((playhead + 5000).coerceAtMost(totalDur)) }, fullscreen = fullscreen, onToggleFullscreen = { fullscreen = !fullscreen })

                // ─── Timeline Toolbar ────────────────────────────────────────
                TimelineToolbar(
                    vm = vm,
                    playhead = playhead,
                    zoomPercent = controller.zoomPercent,
                    onSplit = { vm.splitAt(playhead) },
                    onKeyframe = { vm.addGradeKeyframe(playhead) },
                    onMarker = { vm.addDropAt(playhead) },
                    onZoomIn = { controller.zoomBy(1.4f); zoomTick++ },
                    onZoomOut = { controller.zoomBy(1f / 1.4f); zoomTick++ },
                )

                // ─── Timeline ────────────────────────────────────────────────
                TimelineSection(vm = vm, controller = controller, p = p, playhead = playhead, selectedOverlayId = selectedOverlayId, zoomTick = zoomTick, modifier = Modifier.weight(1f))

                // ─── Status Bar ──────────────────────────────────────────────
                StatusBar(selectedAspect = selectedAspect, p = p)

                // ─── Tool Panel (above contextual toolbar) ───────────────────
                AnimatedVisibility(
                    visible = selectedContextTool != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(150)),
                ) {
                    selectedContextTool?.let { toolId ->
                    ToolPanel(
                        toolId = toolId,
                        onClose = { selectedContextTool = null },
                        vm = vm,
                        p = p,
                        playhead = playhead,
                        selectedAspect = selectedAspect,
                        onAspectSelected = {
                            selectedAspect = it
                            vm.setAspectRatio(it)
                        },
                        showTextDialog = { showTextDialog = it },
                        imagePickerLaunch = { imagePicker.launch(arrayOf("image/*")) },
                        subtitlePickerLaunch = { subtitlePicker.launch(arrayOf("text/*", "application/*")) },
                        voiceOverPickerLaunch = { voiceOverPicker.launch(arrayOf("audio/*")) },
                        backgroundPickerLaunch = { backgroundPicker.launch(arrayOf("image/*")) },
                        analysis = analysis,
                        selectedOverlayId = selectedOverlayId,
                        selectedClip = vm.selectedClip(),
                    )
                    }
                }

                // ─── Contextual Toolbar (above main toolbar) ──────────────────
                AnimatedVisibility(
                    visible = selectedMainTool != null,
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(100)),
                ) {
                    ContextualToolbar(
                        tools = getContextTools(selectedMainTool, selectedOverlayId, vm::overlayById),
                        selectedTool = selectedContextTool,
                        onToolSelected = { tool ->
                            when (tool.id) {
                                "split" -> vm.splitAt(playhead)
                                "add_text" -> showTextDialog = true
                                "add_overlay" -> imagePicker.launch(arrayOf("image/*"))
                        "duplicate" -> { val oid = selectedOverlayId; if (oid != null) vm.duplicateOverlay(oid) }
                        "delete" -> { val oid = selectedOverlayId; if (oid != null) vm.deleteOverlay(oid) }
                                "edit_text" -> { val oid = selectedOverlayId; if (oid != null) editOverlayId = oid }
                                else -> selectedContextTool = tool.id
                            }
                        },
                        onBack = {
                            selectedContextTool = null
                            selectedMainTool = null
                        }
                    )
                }

                // ─── Main Navigation Toolbar (always visible) ─────────────────
                MainToolbar(
                    selectedTool = selectedMainTool,
                    onToolSelected = { tool ->
                        if (selectedMainTool == tool) {
                            selectedMainTool = null
                            selectedContextTool = null
                        } else {
                            selectedMainTool = tool
                            selectedContextTool = null
                        }
                    }
                )
            }
        }

        // ─── Export Dialog ─────────────────────────────────────────────────────
        if (showExport && !fullscreen) {
            ExportOverlay(showExport = showExport, onClose = { showExport = false }, exportState = exportState, p = p, pattern = pattern, exportConfig = exportConfig, onExport = { vm.requestExport(pattern, exportConfig) }, onCancel = { vm.cancelExport() }, onReset = { vm.resetExport() })
        }

        // ─── Text Edit Dialog ──────────────────────────────────────────────────
        if (showTextDialog || editOverlayId != null) {
            val editing = editOverlayId?.let { vm.overlayById(it) as? TextLayer }
            TextEditDialog(
                initial = editing?.text ?: "", initialSize = editing?.fontSize ?: 24f, initialOpacity = editing?.opacity ?: 1f,
                initialAnimation = editing?.animation ?: "Fade", initialColorArgb = editing?.colorArgb ?: 0xFFFFFFFFL,
                onDismiss = { showTextDialog = false; editOverlayId = null },
                onSave = { text, size, opacity, anim, color ->
                    if (editing != null) vm.updateTextOverlay(editing.id, text, size, opacity, anim, color)
                    else { val s = playhead; vm.addTextLayer(text, s, s + 3000, size, opacity, anim) }
                    showTextDialog = false; editOverlayId = null
                }
            )
        }

        // ─── Toast messages ────────────────────────────────────────────────────
        message?.let { msg -> LaunchedEffect(msg) { android.widget.Toast.makeText(appCtx, msg, android.widget.Toast.LENGTH_SHORT).show(); message = null } }
    }
}

// ─── Main Navigation Toolbar ─────────────────────────────────────────────────

@Composable
private fun MainToolbar(
    selectedTool: MainTool?,
    onToolSelected: (MainTool?) -> Unit,
) {
    val tools = MainTool.entries
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(colorResource(R.color.surface_timeline))
            .border(0.5.dp, colorResource(R.color.border_default))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        tools.forEach { tool ->
            val active = selectedTool == tool
            val bgAlpha by animateFloatAsState(if (active) 0.25f else 0f, animationSpec = tween(200), label = "mainBg")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha) else Color.Transparent)
                    .clickable { onToolSelected(tool) }
                    .padding(vertical = 4.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        toolIcon(tool),
                        stringResource(tool.labelRes),
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        stringResource(tool.labelRes),
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─── Contextual Toolbar ──────────────────────────────────────────────────────

@Composable
private fun ContextualToolbar(
    tools: List<ContextTool>,
    selectedTool: String?,
    onToolSelected: (ContextTool) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(ToolbarHeight).background(colorResource(R.color.surface_track))
            .border(0.5.dp, colorResource(R.color.border_default))
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tools.forEach { tool ->
                EditorToolButton(
                    label = tool.label,
                    active = selectedTool == tool.id,
                    onClick = { onToolSelected(tool) },
                    icon = tool.icon,
                    activeColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
        EditorIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Close sub-toolbar",
            onClick = onBack,
            background = colorResource(R.color.surface_control),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Tool Panel ──────────────────────────────────────────────────────────────

@Composable
private fun ToolPanel(
    toolId: String,
    onClose: () -> Unit,
    vm: EditorViewModel,
    p: PhonkProject?,
    playhead: Long,
    selectedAspect: String,
    onAspectSelected: (String) -> Unit,
    showTextDialog: (Boolean) -> Unit,
    imagePickerLaunch: () -> Unit,
    subtitlePickerLaunch: () -> Unit,
    voiceOverPickerLaunch: () -> Unit,
    backgroundPickerLaunch: () -> Unit,
    analysis: dev.phonk.editor.model.AnalysisResult?,
    selectedOverlayId: String?,
    selectedClip: dev.phonk.editor.model.ClipSegment?,
) {
    Column(
        Modifier.fillMaxWidth().height(AppDimens.previewMinHeight).background(colorResource(R.color.surface_track))
            .border(0.5.dp, colorResource(R.color.border_panel)),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = EditorTokens.Space16, vertical = EditorTokens.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                toolId.replace("_", " ").replaceFirstChar { it.uppercase() },
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            EditorIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                background = colorResource(R.color.surface_control),
                tint = colorResource(R.color.text_on_surface),
            )
        }

        // Content
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = EditorTokens.Space12)) {
            when (toolId) {
                "speed" -> SpeedPanel(speed = selectedClip?.speed ?: 1f, onSpeed = { vm.setClipSpeed(it) }, onPreset = { pr -> when (pr) { SpeedPreset.NORMAL -> vm.setClipSpeed(1f); SpeedPreset.HYPER -> vm.setClipSpeed(2f); SpeedPreset.SLOW -> vm.setClipSpeed(0.5f); SpeedPreset.FAST -> vm.setClipSpeed(1.5f); SpeedPreset.BEAT_DROP -> vm.setClipSpeed(0.75f) } })
                "volume" -> VolumePanel(volume = p?.volume ?: 1f, muted = p?.muted == true, onVolume = { vm.setVolume(it) }, onMuted = { vm.setMuted(it) })
                "transition" -> TransitionsPanel(durationMs = p?.transitionDurationMs ?: 400L, onDuration = { vm.setTransitionDuration(it) }, onSelect = { vm.setClipTransition(it) }, current = selectedClip?.transition)
                "fade_in" -> FadeInPanel(fadeInMs = p?.fadeInMs ?: 0L, onFadeIn = { vm.setFadeIn(it) })
                "fade_out" -> FadeOutPanel(fadeOutMs = p?.fadeOutMs ?: 0L, onFadeOut = { vm.setFadeOut(it) })
                "pitch" -> PitchPanel(pitch = p?.pitch ?: 1f, onPitch = { vm.setPitch(it) })
                "beat" -> BeatPanel(bpm = analysis?.bpm ?: 0.0, onDetect = { vm.beginAnalysis() }, onSubdivision = { vm.applyBeatSubdivision(it) }, onAddDrop = { vm.addDropAt(playhead) }, onRemoveDrop = { vm.removeDropAt(playhead) }, dropCount = p?.drops?.size ?: 0, onPattern = { vm.applyPattern(it) })
                "font" -> FontPanel(layer = selectedOverlayId?.let { vm.overlayById(it) as? TextLayer }, onSize = { id, size -> vm.updateTextOverlay(id, vm.overlayById(id)?.let { (it as? TextLayer)?.text ?: "" } ?: "", size, (vm.overlayById(id) as? TextLayer)?.opacity ?: 1f, (vm.overlayById(id) as? TextLayer)?.animation ?: "Fade", (vm.overlayById(id) as? TextLayer)?.colorArgb ?: 0xFFFFFFFFL) })
                "color" -> ColorPanel(layer = selectedOverlayId?.let { vm.overlayById(it) as? TextLayer }, onColor = { id, color -> vm.updateTextOverlay(id, vm.overlayById(id)?.let { (it as? TextLayer)?.text ?: "" } ?: "", (vm.overlayById(id) as? TextLayer)?.fontSize ?: 24f, (vm.overlayById(id) as? TextLayer)?.opacity ?: 1f, (vm.overlayById(id) as? TextLayer)?.animation ?: "Fade", color) })
                "text_animation" -> TextAnimationPanel(layer = selectedOverlayId?.let { vm.overlayById(it) as? TextLayer }, onAnimation = { id, anim -> vm.updateTextOverlay(id, vm.overlayById(id)?.let { (it as? TextLayer)?.text ?: "" } ?: "", (vm.overlayById(id) as? TextLayer)?.fontSize ?: 24f, (vm.overlayById(id) as? TextLayer)?.opacity ?: 1f, anim, (vm.overlayById(id) as? TextLayer)?.colorArgb ?: 0xFFFFFFFFL) })
                "opacity" -> OpacityPanel(item = selectedOverlayId?.let { vm.overlayById(it) }, onOpacity = { id, op -> vm.setOverlayOpacity(id, op) })
                "effects" -> EffectsPanel(onAdd = { vm.addEffect(it) }, hasClipEffect = selectedClip?.effect != EffectKind.NONE && selectedClip?.effect != null, onClear = { vm.clearClipEffect() })
                "filters" -> FiltersPanel(grade = p?.colorGrade() ?: ColorGrade(), keyframesEnabled = p?.gradeKeyframesEnabled == true, keyframeCount = p?.gradeKeyframes?.size ?: 0, beatSync = p?.beatSync == true, beatSyncStrength = p?.beatSyncStrength ?: 0.8f, onGrade = { param, v -> vm.setGrade(param, v) }, onResetAll = { vm.resetGrade() }, onAddKeyframe = { vm.addGradeKeyframe(playhead) }, onClearKeyframes = { vm.clearGradeKeyframes() }, onKeyframesEnabled = { vm.setGradeKeyframesEnabled(it) }, onBeatSync = { vm.setBeatSync(it) }, onBeatSyncStrength = { vm.setBeatSyncStrength(it) })
                "adjust" -> GradeSlidersPanel(grade = p?.colorGrade() ?: ColorGrade(), onGrade = { param, v -> vm.setGrade(param, v) }, onResetAll = { vm.resetGrade() })
                "ratio" -> RatioPanel(selectedAspect = selectedAspect, onAspectSelected = onAspectSelected)
                "media" -> MediaPanel(onImportVideo = imagePickerLaunch, onImportAudio = imagePickerLaunch, onImportPhoto = imagePickerLaunch)
                "background" -> BackgroundPanel(
                    background = p?.canvasBackground ?: CanvasBackground(),
                    onType = { vm.setCanvasBackgroundType(it) },
                    onColor = { vm.setCanvasBackgroundColor(it) },
                    onPickImage = backgroundPickerLaunch,
                )
                "crop" -> CropPanel(crop = p?.crop ?: CropConfig(), onCrop = { e, x, y, w, h -> vm.setCrop(e, x, y, w, h) })
                "reverse" -> ReversePanel(reversed = selectedClip?.reversed == true, onReversed = { vm.setClipReversed(it) })
                "subtitles" -> SubtitlePanel(
                    tracks = p?.subtitles ?: emptyList(),
                    onImport = subtitlePickerLaunch,
                    onToggleTrack = { id, vis -> vm.setSubtitleVisible(id, vis) },
                    onClear = { vm.clearSubtitles() },
                )
                "chroma" -> OverlayKeyPanel(
                    layer = selectedOverlayId?.let { vm.overlayById(it) as? OverlayLayer },
                    onChroma = { color, sim -> val id = selectedOverlayId ?: return@OverlayKeyPanel; vm.setOverlayChromaKey(id, color, sim) },
                    onMask = { mask -> val id = selectedOverlayId ?: return@OverlayKeyPanel; vm.setOverlayMask(id, mask) },
                )
                "voiceover" -> AudioMixPanel(
                    ducking = p?.audioDucking == true,
                    onDucking = { vm.setAudioDucking(it) },
                    hasVoiceOver = p?.voiceOverUri != null,
                    onPickVoiceOver = voiceOverPickerLaunch,
                    onClearVoiceOver = { vm.setVoiceOverUri(null) },
                )
            }
        }
    }
}

// ─── Layout Sections ─────────────────────────────────────────────────────────

@Composable
private fun FullscreenPreview(vm: EditorViewModel, p: PhonkProject?, isPlaying: Boolean, playhead: Long, selectedOverlayId: String?, fullscreen: Boolean, onToggleFullscreen: () -> Unit, onEditText: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EditorPreview(
            playerController = vm.player, project = p, isPlaying = isPlaying,
            positionMs = vm.destToSource(playhead), destPlayheadMs = playhead,
            onPlayPause = { vm.playPause() }, selectedOverlayId = selectedOverlayId,
            onOverlaySelect = { vm.selectOverlay(it) },
            onOverlayTransformBegin = { vm.beginOverlayGesture() },
            onOverlayTransformLive = { id, x, y, sx, sy, rot, op -> vm.transformOverlayLive(id, x, y, sx, sy, rot, op) },
            onOverlayTransformEnd = { vm.endOverlayTransform() },
            onOverlayTransformCancel = { vm.cancelOverlayTransform() },
            onEditText = onEditText,
            aspectRatio = "16:9",
            fullscreen = true,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp).clip(RoundedCornerShape(22.dp))
                .background(colorResource(R.color.surface_elevated).copy(alpha = 0.75f)).clickable { vm.playPause() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                Text(if (isPlaying) "Pause" else "Play", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(colorResource(R.color.surface_elevated).copy(alpha = 0.75f)).clickable { onToggleFullscreen() },
        ) {
            Icon(Icons.Filled.FullscreenExit, "Exit fullscreen", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, p: PhonkProject?, totalDur: Long, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(ToolbarHeight).background(colorResource(R.color.toolbar_bg))
        .border(0.5.dp, colorResource(R.color.border_default)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        EditorIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            background = Color.Transparent,
        )
        Column(Modifier.weight(1f).padding(horizontal = EditorTokens.Space8)) {
            Text(p?.name ?: "Editor", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("1080×1920 · ${p?.export?.fps?.fps ?: 30}fps · ${formatClock(totalDur)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        EditorIconButton(
            icon = Icons.Filled.Undo,
            contentDescription = "Undo",
            onClick = onUndo,
            enabled = canUndo,
            background = Color.Transparent,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        EditorIconButton(
            icon = Icons.Filled.Redo,
            contentDescription = "Redo",
            onClick = onRedo,
            enabled = canRedo,
            background = Color.Transparent,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Button(onClick = onExport, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary, Color.White),
            shape = RoundedCornerShape(EditorTokens.CornerButton),
            contentPadding = PaddingValues(horizontal = EditorTokens.Space16, vertical = EditorTokens.Space8)) {
            Text("Export", fontWeight = FontWeight.Bold, fontSize = EditorTokens.FontTool)
        }
    }
}

@Composable
private fun PreviewSection(vm: EditorViewModel, p: PhonkProject?, isPlaying: Boolean, playhead: Long, selectedOverlayId: String?, selectedAspect: String, fullscreen: Boolean, onToggleFullscreen: () -> Unit, onEditText: (String) -> Unit) {
    val config = LocalConfiguration.current
    val previewMaxH = (config.screenHeightDp * 0.32f).dp
    Box(
        Modifier.fillMaxWidth().height(previewMaxH).padding(horizontal = EditorTokens.Space8, vertical = 2.dp).background(colorResource(R.color.background)),
        contentAlignment = Alignment.Center,
    ) {
        EditorPreview(
            playerController = vm.player, project = p, isPlaying = isPlaying,
            positionMs = vm.destToSource(playhead), destPlayheadMs = playhead,
            onPlayPause = { vm.playPause() }, selectedOverlayId = selectedOverlayId,
            onOverlaySelect = { vm.selectOverlay(it) },
            onOverlayTransformBegin = { vm.beginOverlayGesture() },
            onOverlayTransformLive = { id, x, y, sx, sy, rot, op -> vm.transformOverlayLive(id, x, y, sx, sy, rot, op) },
            onOverlayTransformEnd = { vm.endOverlayTransform() },
            onOverlayTransformCancel = { vm.cancelOverlayTransform() },
            onEditText = onEditText,
            aspectRatio = selectedAspect,
            fullscreen = fullscreen,
            onToggleFullscreen = onToggleFullscreen,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlayerControls(playhead: Long, totalDur: Long, isPlaying: Boolean, onSeekBack: () -> Unit, onPlayPause: () -> Unit, onSeekForward: () -> Unit, fullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(colorResource(R.color.toolbar_bg))
        .border(0.5.dp, colorResource(R.color.border_default)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        EditorIconButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Prev",
            onClick = onSeekBack,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = colorResource(R.color.text_disabled),
        )
        EditorIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = MaterialTheme.colorScheme.primary,
        )
        EditorIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next",
            onClick = onSeekForward,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = colorResource(R.color.text_disabled),
        )
        Spacer(Modifier.weight(1f))
        Text(formatClock(playhead), fontSize = EditorTokens.FontTool, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(" / ", fontSize = EditorTokens.FontTool, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatClock(totalDur), fontSize = EditorTokens.FontTool, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        EditorIconButton(
            icon = Icons.Filled.Fullscreen,
            contentDescription = "Fullscreen",
            onClick = onToggleFullscreen,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = if (fullscreen) MaterialTheme.colorScheme.primary else colorResource(R.color.text_disabled),
        )
    }
}

@Composable
private fun AspectToolbar(selectedAspect: String, onAspectSelected: (String) -> Unit) {
    val aspects = listOf("1:1", "4:5", "9:16", "16:9", "2.35:1")
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(colorResource(R.color.toolbar_bg))
            .border(0.5.dp, colorResource(R.color.border_default)).padding(horizontal = EditorTokens.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space6),
    ) {
        Text("Aspect", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        aspects.forEach { label ->
            val sel = selectedAspect == label
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(EditorTokens.CornerControl))
                    .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else colorResource(R.color.surface_control))
                    .clickable { onAspectSelected(label) }
                    .padding(horizontal = EditorTokens.Space12, vertical = EditorTokens.Space6),
            ) {
                Text(label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.primary else colorResource(R.color.text_ruler))
            }
        }
    }
}

@Composable
private fun TimelineToolbar(
    vm: EditorViewModel,
    playhead: Long,
    zoomPercent: Int,
    onSplit: () -> Unit,
    onKeyframe: () -> Unit,
    onMarker: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(colorResource(R.color.toolbar_bg))
            .border(0.5.dp, colorResource(R.color.border_default)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TimelineToolButton(Icons.Filled.ContentCut, "Split", onSplit)
        TimelineToolButton(Icons.Filled.Tune, "Keyframe", onKeyframe)
        TimelineToolButton(Icons.Filled.MusicNote, "Marker", onMarker)
        Spacer(Modifier.weight(1f))
        ZoomIconButton("−", "Zoom out", onZoomOut)
        Text("$zoomPercent%", fontSize = EditorTokens.FontLabel, color = colorResource(R.color.text_ruler), modifier = Modifier.padding(horizontal = 4.dp))
        TimelineToolButton(Icons.Filled.Add, "Zoom in", onZoomIn)
    }
}

@Composable
private fun TimelineToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    EditorToolButton(
        label = label,
        active = false,
        onClick = onClick,
        icon = icon,
    )
}

@Composable
private fun ZoomIconButton(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(EditorTokens.CompactTarget)
            .clip(RoundedCornerShape(EditorTokens.CornerButton))
            .background(colorResource(R.color.surface_control))
            .clickable { onClick() },
    ) {
        Text(glyph, fontSize = EditorTokens.FontTool, color = colorResource(R.color.text_ruler))
    }
}

@Composable
private fun TimelineSection(vm: EditorViewModel, controller: TimelineController, p: PhonkProject?, playhead: Long, selectedOverlayId: String?, zoomTick: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            TimelineView(ctx).also { tv ->
                tv.controller = controller; tv.project = vm.project.value ?: PhonkProject()
                tv.onSeekTo = { ms -> vm.setCurrentPosition(ms) }; tv.onClipSplit = { ms -> vm.splitAt(ms) }
                tv.onSelectClip = { id -> vm.selectClip(id) }; tv.onTrimStart = { ns -> vm.trimClip(ns, vm.selectedClip()?.destEndMs ?: ns) }
                tv.onTrimEnd = { ne -> vm.trimClip(vm.selectedClip()?.destStartMs ?: ne, ne) }
                tv.onSelectOverlay = { id -> vm.selectOverlay(id) }; tv.onSetOverlayTiming = { id, s, e -> vm.setOverlayTiming(id, s, e) }
            }
        },
        update = { tv ->
            val pr = vm.project.value ?: PhonkProject()
            tv.project = pr; tv.selectedOverlayId = selectedOverlayId
            tv.controller.totalMs = pr.timelineDurationMs().takeIf { it > 0 } ?: pr.videoDurationMs
            tv.controller.currentMs = playhead.coerceIn(0L, tv.controller.totalMs)
            tv.refresh()
            if (zoomTick > 0) tv.refresh()
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 100.dp),
    )
}

@Composable
private fun StatusBar(selectedAspect: String, p: PhonkProject?) {
    Row(Modifier.fillMaxWidth().height(EditorTokens.StatusBarHeight).background(colorResource(R.color.toolbar_bg))
        .border(0.5.dp, colorResource(R.color.border_default)).padding(horizontal = EditorTokens.Space8, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(colorResource(R.color.audio_fx)))
        Text("Saved", fontSize = 9.sp, color = colorResource(R.color.audio_fx))
        Text("•", fontSize = 9.sp, color = colorResource(R.color.text_status))
        Text(selectedAspect, fontSize = 9.sp, color = colorResource(R.color.text_status))
        Text("•", fontSize = 9.sp, color = colorResource(R.color.text_status))
        Text("${p?.export?.fps?.fps ?: 30} FPS", fontSize = 9.sp, color = colorResource(R.color.text_status))
        Spacer(Modifier.weight(1f))
        Text("GPU", fontSize = 9.sp, color = colorResource(R.color.text_status))
    }
}

@Composable
private fun ExportOverlay(showExport: Boolean, onClose: () -> Unit, exportState: ExportState, p: PhonkProject?, pattern: CutPattern, exportConfig: ExportConfig, onExport: () -> Unit, onCancel: () -> Unit, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onClose)) {
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = colorResource(R.color.surface_panel), shadowElevation = 12.dp) {
            Column(Modifier.fillMaxWidth().padding(EditorTokens.Space12).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Export", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorResource(R.color.text_on_surface), modifier = Modifier.weight(1f))
                    EditorIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close",
                        onClick = onClose,
                        background = colorResource(R.color.surface_control),
                        tint = colorResource(R.color.text_on_surface),
                    )
                }
                Spacer(Modifier.height(EditorTokens.Space8))
                Text("${p?.export?.resolution ?: "1080p"} · ${p?.export?.fps?.fps ?: 30} FPS", fontSize = EditorTokens.FontCompact, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(EditorTokens.Space8))
                when (val es = exportState) {
                    is ExportState.Running -> {
                        PhonkProgressBar(progress = es.progress, activeColor = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(EditorTokens.Space4)); Text("${(es.progress * 100).toInt()}%", fontSize = EditorTokens.FontLabel, color = colorResource(R.color.text_progress))
                        Spacer(Modifier.height(EditorTokens.Space4))
                        Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(colorResource(R.color.surface_control)), shape = RoundedCornerShape(EditorTokens.CornerButton), modifier = Modifier.fillMaxWidth().height(EditorTokens.PrimaryHeight)) {
                            Text("Cancel", color = colorResource(R.color.text_on_surface))
                        }
                    }
                    is ExportState.Done -> {
                        Text("Done!", fontSize = EditorTokens.FontTool, color = colorResource(R.color.audio_fx)); Spacer(Modifier.height(EditorTokens.Space4))
                        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(EditorTokens.CornerButton), modifier = Modifier.fillMaxWidth().height(EditorTokens.PrimaryHeight)) {
                            Text("OK", color = Color.White)
                        }
                    }
                    is ExportState.Failed -> {
                        Text(es.message, fontSize = EditorTokens.FontLabel, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(EditorTokens.Space4))
                        Button(onClick = onExport, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(EditorTokens.CornerButton), modifier = Modifier.fillMaxWidth().height(EditorTokens.PrimaryHeight)) {
                            Text("Retry", color = Color.White)
                        }
                    }
                    ExportState.Idle -> {
                        Button(onClick = onExport, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary, Color.White),
                            shape = RoundedCornerShape(EditorTokens.CornerButton), modifier = Modifier.fillMaxWidth().height(EditorTokens.PrimaryHeight)) {
                            Text("Start Export", fontWeight = FontWeight.Bold, fontSize = EditorTokens.FontTool)
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun queryName(resolver: android.content.ContentResolver, uri: Uri): String {
    resolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) { val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0) return c.getString(i) ?: "Untitled" } }
    return "Untitled"
}
