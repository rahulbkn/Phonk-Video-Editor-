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
import dev.phonk.editor.ui.editor.EditorPreview
import dev.phonk.editor.ui.editor.TextEditDialog
import dev.phonk.editor.ui.editor.panels.AudioMixPanel
import dev.phonk.editor.ui.editor.panels.AudioTrackPanel
import dev.phonk.editor.ui.editor.panels.BackgroundPanel
import dev.phonk.editor.ui.editor.panels.CropPanel
import dev.phonk.editor.ui.editor.panels.EffectsPanel
import dev.phonk.editor.ui.editor.panels.FiltersPanel
import dev.phonk.editor.ui.editor.panels.MediaPanel
import dev.phonk.editor.ui.editor.panels.MorePanel
import dev.phonk.editor.ui.editor.panels.OverlayKeyPanel
import dev.phonk.editor.ui.editor.panels.OverlayPanel
import dev.phonk.editor.ui.editor.panels.RatioPanel
import dev.phonk.editor.ui.editor.panels.ReversePanel
import dev.phonk.editor.ui.editor.panels.SpeedPreset
import dev.phonk.editor.ui.editor.panels.StickerPanel
import dev.phonk.editor.ui.editor.panels.SubtitlePanel
import dev.phonk.editor.ui.editor.panels.TextPanel
import dev.phonk.editor.ui.editor.panels.TransitionsPanel
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
        if (loaded != null) { vm.setProject(loaded); controller.totalMs = loaded.timelineDurationMs().takeIf { it > 0 } ?: loaded.videoDurationMs }
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

    Box(Modifier.fillMaxSize().background(Color(0xFF09090E))) {
        if (fullscreen) {
            FullscreenPreview(vm, p, isPlaying, playhead, selectedOverlayId, fullscreen, onToggleFullscreen = { fullscreen = false }, onEditText = { editOverlayId = it })
        } else {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).background(Color(0xFF09090E))) {
                // ─── Top Bar ─────────────────────────────────────────────────
                TopBar(onBack = onBack, p = p, totalDur = totalDur, canUndo = canUndo, canRedo = canRedo, onUndo = { vm.undo() }, onRedo = { vm.redo() }, onExport = { vm.resetExport(); showExport = true })

                // ─── Aspect / Canvas Toolbar ─────────────────────────────────
                AspectToolbar(selectedAspect = selectedAspect, onAspectSelected = { selectedAspect = it })

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
                            onAspectSelected = { selectedAspect = it },
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
        Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF0A0A0F))
            .border(0.5.dp, Color(0xFF282833))
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
                    .background(if (active) Color(0xFFA855F7).copy(alpha = bgAlpha) else Color.Transparent)
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
                        tint = if (active) Color(0xFFA855F7) else Color(0xFF8F8F9D),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        stringResource(tool.labelRes),
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Color(0xFFA855F7) else Color(0xFF8F8F9D),
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
        Modifier.fillMaxWidth().height(52.dp).background(Color(0xFF0C0C12))
            .border(0.5.dp, Color(0xFF282833))
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
                    activeColor = Color(0xFFA855F7),
                )
            }
        }
        EditorIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Close sub-toolbar",
            onClick = onBack,
            background = Color(0xFF17171F),
            tint = Color(0xFF8F8F9D),
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
        Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF111117))
            .border(0.5.dp, Color(0xFF25252E)),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = EditorTokens.Space16, vertical = EditorTokens.Space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                toolId.replace("_", " ").replaceFirstChar { it.uppercase() },
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF5F5F7),
                modifier = Modifier.weight(1f),
            )
            EditorIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                background = Color(0xFF17171F),
                tint = Color(0xFFF7F7FB),
            )
        }

        // Content
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            when (toolId) {
                "speed" -> SpeedPanelInline(speed = selectedClip?.speed ?: 1f, onSpeed = { vm.setClipSpeed(it) }, onPreset = { pr -> when (pr) { SpeedPreset.NORMAL -> vm.setClipSpeed(1f); SpeedPreset.HYPER -> vm.setClipSpeed(2f); SpeedPreset.SLOW -> vm.setClipSpeed(0.5f); SpeedPreset.FAST -> vm.setClipSpeed(1.5f); SpeedPreset.BEAT_DROP -> vm.setClipSpeed(0.75f) } })
                "volume" -> VolumePanelInline(volume = p?.volume ?: 1f, muted = p?.muted == true, onVolume = { vm.setVolume(it) }, onMuted = { vm.setMuted(it) })
                "transition" -> TransitionsPanel(durationMs = p?.transitionDurationMs ?: 400L, onDuration = { vm.setTransitionDuration(it) }, onSelect = { vm.setClipTransition(it) }, current = selectedClip?.transition)
                "fade_in" -> FadeInPanelInline(fadeInMs = p?.fadeInMs ?: 0L, onFadeIn = { vm.setFadeIn(it) })
                "fade_out" -> FadeOutPanelInline(fadeOutMs = p?.fadeOutMs ?: 0L, onFadeOut = { vm.setFadeOut(it) })
                "pitch" -> PitchPanelInline(pitch = p?.pitch ?: 1f, onPitch = { vm.setPitch(it) })
                "beat" -> BeatInlinePanel(bpm = analysis?.bpm ?: 0.0, onDetect = { vm.beginAnalysis() }, onSubdivision = { vm.applyBeatSubdivision(it) }, onAddDrop = { vm.addDropAt(playhead) }, onRemoveDrop = { vm.removeDropAt(playhead) }, dropCount = p?.drops?.size ?: 0, onPattern = { vm.applyPattern(it) })
                "font" -> FontPanelInline(layer = selectedOverlayId?.let { vm.overlayById(it) as? TextLayer }, onSize = { id, size -> vm.updateTextOverlay(id, vm.overlayById(id)?.let { (it as? TextLayer)?.text ?: "" } ?: "", size, (vm.overlayById(id) as? TextLayer)?.opacity ?: 1f, (vm.overlayById(id) as? TextLayer)?.animation ?: "Fade", (vm.overlayById(id) as? TextLayer)?.colorArgb ?: 0xFFFFFFFFL) })
                "color" -> ColorPanelInline(layer = selectedOverlayId?.let { vm.overlayById(it) as? TextLayer }, onColor = { id, color -> vm.updateTextOverlay(id, vm.overlayById(id)?.let { (it as? TextLayer)?.text ?: "" } ?: "", (vm.overlayById(id) as? TextLayer)?.fontSize ?: 24f, (vm.overlayById(id) as? TextLayer)?.opacity ?: 1f, (vm.overlayById(id) as? TextLayer)?.animation ?: "Fade", color) })
                "text_animation" -> TextAnimationPanelInline(layer = selectedOverlayId?.let { vm.overlayById(it) as? TextLayer }, onAnimation = { id, anim -> vm.updateTextOverlay(id, vm.overlayById(id)?.let { (it as? TextLayer)?.text ?: "" } ?: "", (vm.overlayById(id) as? TextLayer)?.fontSize ?: 24f, (vm.overlayById(id) as? TextLayer)?.opacity ?: 1f, anim, (vm.overlayById(id) as? TextLayer)?.colorArgb ?: 0xFFFFFFFFL) })
                "opacity" -> OpacityPanelInline(item = selectedOverlayId?.let { vm.overlayById(it) }, onOpacity = { id, op -> vm.setOverlayOpacity(id, op) })
                "effects" -> EffectsPanel(onAdd = { vm.addEffect(it) }, hasClipEffect = selectedClip?.effect != EffectKind.NONE && selectedClip?.effect != null, onClear = { vm.clearClipEffect() })
                "filters" -> FiltersPanel(grade = p?.colorGrade() ?: ColorGrade(), keyframesEnabled = p?.gradeKeyframesEnabled == true, keyframeCount = p?.gradeKeyframes?.size ?: 0, beatSync = p?.beatSync == true, beatSyncStrength = p?.beatSyncStrength ?: 0.8f, onGrade = { param, v -> vm.setGrade(param, v) }, onResetAll = { vm.resetGrade() }, onAddKeyframe = { vm.addGradeKeyframe(playhead) }, onClearKeyframes = { vm.clearGradeKeyframes() }, onKeyframesEnabled = { vm.setGradeKeyframesEnabled(it) }, onBeatSync = { vm.setBeatSync(it) }, onBeatSyncStrength = { vm.setBeatSyncStrength(it) })
                "adjust" -> GradeSlidersInline(grade = p?.colorGrade() ?: ColorGrade(), onGrade = { param, v -> vm.setGrade(param, v) }, onResetAll = { vm.resetGrade() })
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

// ─── Inline Panel Components ─────────────────────────────────────────────────

@Composable
private fun VolumePanelInline(volume: Float, muted: Boolean, onVolume: (Float) -> Unit, onMuted: (Boolean) -> Unit) {
    Column {
        SectionHeader("Volume")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PhonkSlider(value = volume, onValueChange = onVolume, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            Text("%d%%".format((volume * 100).toInt()), fontSize = 11.sp, color = Color(0xFF8F8F9D), modifier = Modifier.width(40.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton(if (muted) "Unmute" else "Mute", onClick = { onMuted(!muted) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FadeInPanelInline(fadeInMs: Long, onFadeIn: (Long) -> Unit) {
    Column {
        SectionHeader("Fade In")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            listOf(0L to "0ms", 250L to "250ms", 500L to "500ms", 1000L to "1000ms").forEach { (ms, label) ->
                EditorChip(label, onClick = { onFadeIn(ms) }, selected = fadeInMs == ms)
            }
        }
    }
}

@Composable
private fun FadeOutPanelInline(fadeOutMs: Long, onFadeOut: (Long) -> Unit) {
    Column {
        SectionHeader("Fade Out")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            listOf(0L to "0ms", 250L to "250ms", 500L to "500ms", 1000L to "1000ms").forEach { (ms, label) ->
                EditorChip(label, onClick = { onFadeOut(ms) }, selected = fadeOutMs == ms)
            }
        }
    }
}

@Composable
private fun PitchPanelInline(pitch: Float, onPitch: (Float) -> Unit) {
    Column {
        SectionHeader("Pitch")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PhonkSlider(value = pitch, onValueChange = onPitch, valueRange = 0.5f..2f, modifier = Modifier.weight(1f))
            Text("%.2fx".format(pitch), fontSize = 11.sp, color = Color(0xFF8F8F9D), modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun BeatInlinePanel(bpm: Double, onDetect: () -> Unit, onSubdivision: (Double) -> Unit, onAddDrop: () -> Unit, onRemoveDrop: () -> Unit, dropCount: Int, onPattern: (CutPattern) -> Unit) {
    Column {
        SectionHeader("Beat")
        if (bpm > 0) {
            Text("BPM: %.1f".format(bpm), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA855F7))
        } else {
            PhonkButton("Detect Beats", onClick = onDetect, primary = true, modifier = Modifier.padding(vertical = EditorTokens.Space4))
            Text("Detect beats first to enable auto-cut", fontSize = EditorTokens.FontLabel, color = Color(0xFF8F8F9D), modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(EditorTokens.Space4))
        Text("Auto Cut", fontSize = EditorTokens.FontLabel, color = Color(0xFF8F8F9D))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            listOf(0.25 to "1/4", 0.5 to "1/2", 1.0 to "1", 2.0 to "2", 4.0 to "4", 8.0 to "8").forEach { (sub, label) ->
                EditorChip(label, onClick = { onSubdivision(sub) }, enabled = bpm > 0)
            }
        }
        Spacer(Modifier.height(EditorTokens.Space8))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            PhonkButton("Add Drop ($dropCount)", onClick = onAddDrop, modifier = Modifier.weight(1f))
            PhonkButton("Remove Drop", onClick = onRemoveDrop, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FontPanelInline(layer: TextLayer?, onSize: (String, Float) -> Unit) {
    val id = layer?.id
    Column {
        SectionHeader("Font Size")
        if (id != null && layer != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PhonkSlider(value = layer.fontSize, onValueChange = { onSize(id, it) }, valueRange = 8f..120f, modifier = Modifier.weight(1f))
                Text("${layer.fontSize.toInt()}pt", fontSize = 11.sp, color = Color(0xFF8F8F9D), modifier = Modifier.width(40.dp))
            }
        } else {
            Text("Select a text layer first", fontSize = 10.sp, color = Color(0xFF8F8F9D))
        }
    }
}

@Composable
private fun ColorPanelInline(layer: TextLayer?, onColor: (String, Long) -> Unit) {
    val id = layer?.id
    val currentColor = layer?.colorArgb ?: 0xFFFFFFFFL
    Column {
        SectionHeader("Text Color")
        if (id != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
                listOf(
                    0xFFFFFFFFL to "White", 0xFF000000L to "Black", 0xFFA855F7L to "Purple",
                    0xFFFF6B6BL to "Red", 0xFF39D7B1L to "Teal", 0xFFFB923CL to "Orange",
                    0xFF3B82F6L to "Blue", 0xFF22C55EL to "Green",
                ).forEach { (color, label) ->
                    val sel = currentColor == color
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                        .clip(RoundedCornerShape(EditorTokens.CornerButton))
                        .clickable { onColor(id, color) }
                        .padding(EditorTokens.Space4)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(color.toInt())).border(1.5.dp, if (sel) Color(0xFFA855F7) else Color(0xFF292934), CircleShape))
                        Text(label, fontSize = EditorTokens.FontLabel, color = if (sel) Color(0xFFA855F7) else Color(0xFF8F8F9D))
                    }
                }
            }
        } else {
            Text("Select a text layer first", fontSize = EditorTokens.FontLabel, color = Color(0xFF8F8F9D))
        }
    }
}

@Composable
private fun TextAnimationPanelInline(layer: TextLayer?, onAnimation: (String, String) -> Unit) {
    val id = layer?.id
    Column {
        SectionHeader("Text Animation")
        if (id != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
                listOf("Fade", "Slide", "Zoom", "Typewriter", "Bounce", "Glitch").forEach { anim ->
                    EditorChip(anim, onClick = { onAnimation(id, anim) }, selected = layer?.animation == anim)
                }
            }
        } else {
            Text("Select a text layer first", fontSize = EditorTokens.FontLabel, color = Color(0xFF8F8F9D))
        }
    }
}

@Composable
private fun OpacityPanelInline(item: OverlayItem?, onOpacity: (String, Float) -> Unit) {
    val id = item?.id
    Column {
        SectionHeader("Opacity")
        if (id != null && item != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PhonkSlider(value = item.opacity, onValueChange = { onOpacity(id, it) }, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
                Text("%d%%".format((item.opacity * 100).toInt()), fontSize = 11.sp, color = Color(0xFF8F8F9D), modifier = Modifier.width(40.dp))
            }
        } else {
            Text("Select an overlay first", fontSize = 10.sp, color = Color(0xFF8F8F9D))
        }
    }
}

@Composable
private fun GradeSlidersInline(grade: dev.phonk.editor.model.ColorGrade, onGrade: (dev.phonk.editor.model.GradeParam, Float) -> Unit, onResetAll: () -> Unit) {
    Column {
        SectionHeader("Adjust")
        listOf(
            dev.phonk.editor.model.GradeParam.BRIGHTNESS to "Brightness",
            dev.phonk.editor.model.GradeParam.CONTRAST to "Contrast",
            dev.phonk.editor.model.GradeParam.SATURATION to "Saturation",
            dev.phonk.editor.model.GradeParam.EXPOSURE to "Exposure",
            dev.phonk.editor.model.GradeParam.TEMPERATURE to "Temperature",
            dev.phonk.editor.model.GradeParam.SHARPNESS to "Sharpness",
        ).forEach { (param, label) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 10.sp, color = Color(0xFF8F8F9D), modifier = Modifier.width(72.dp))
                PhonkSlider(value = grade.get(param), onValueChange = { onGrade(param, it) }, valueRange = param.range, modifier = Modifier.weight(1f))
                Text("%+.2f".format(grade.get(param).toDouble()), fontSize = 9.sp, color = Color(0xFF8F8F9D), modifier = Modifier.width(36.dp))
            }
        }
        PhonkButton("Reset All", onClick = onResetAll, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SpeedPanelInline(speed: Float, onSpeed: (Float) -> Unit, onPreset: (SpeedPreset) -> Unit) {
    Column {
        SectionHeader("Speed")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            listOf(
                "Normal" to SpeedPreset.NORMAL, "Slow" to SpeedPreset.SLOW, "Fast" to SpeedPreset.FAST,
                "Beat Drop" to SpeedPreset.BEAT_DROP, "Hyper" to SpeedPreset.HYPER,
            ).forEach { (label, preset) ->
                val sel = when (preset) {
                    SpeedPreset.NORMAL -> speed == 1f
                    SpeedPreset.SLOW -> speed == 0.5f
                    SpeedPreset.FAST -> speed == 1.5f
                    SpeedPreset.BEAT_DROP -> speed == 0.75f
                    SpeedPreset.HYPER -> speed == 2f
                }
                EditorChip(label, onClick = { onPreset(preset) }, selected = sel)
            }
        }
        Spacer(Modifier.height(EditorTokens.Space8))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PhonkSlider(value = speed, onValueChange = onSpeed, valueRange = 0.25f..4f, modifier = Modifier.weight(1f))
            Text("%.2fx".format(speed), fontSize = EditorTokens.FontCompact, color = Color(0xFF8F8F9D), modifier = Modifier.width(40.dp))
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
                .background(Color(0xFF111119).copy(alpha = 0.75f)).clickable { vm.playPause() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color(0xFFF5F5F7), modifier = Modifier.size(19.dp))
                Text(if (isPlaying) "Pause" else "Play", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF5F5F7))
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111119).copy(alpha = 0.75f)).clickable { onToggleFullscreen() },
        ) {
            Icon(Icons.Filled.FullscreenExit, "Exit fullscreen", tint = Color(0xFFF5F5F7), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, p: PhonkProject?, totalDur: Long, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).background(Color(0xFF0B0B10))
        .border(0.5.dp, Color(0xFF282833)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        EditorIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            background = Color.Transparent,
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(p?.name ?: "Editor", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF5F5F7), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("1080×1920 · ${p?.export?.fps?.fps ?: 30}fps · ${formatClock(totalDur)}", fontSize = 10.sp, color = Color(0xFF8F8F9D), maxLines = 1)
        }
        EditorIconButton(
            icon = Icons.Filled.Undo,
            contentDescription = "Undo",
            onClick = onUndo,
            enabled = canUndo,
            background = Color.Transparent,
            tint = Color(0xFFF5F5F7),
        )
        EditorIconButton(
            icon = Icons.Filled.Redo,
            contentDescription = "Redo",
            onClick = onRedo,
            enabled = canRedo,
            background = Color.Transparent,
            tint = Color(0xFFF5F5F7),
        )
        Button(onClick = onExport, colors = ButtonDefaults.buttonColors(Color(0xFFA855F7), Color.White),
            shape = RoundedCornerShape(EditorTokens.CornerButton),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Export", fontWeight = FontWeight.Bold, fontSize = EditorTokens.FontTool)
        }
    }
}

@Composable
private fun PreviewSection(vm: EditorViewModel, p: PhonkProject?, isPlaying: Boolean, playhead: Long, selectedOverlayId: String?, selectedAspect: String, fullscreen: Boolean, onToggleFullscreen: () -> Unit, onEditText: (String) -> Unit) {
    val config = LocalConfiguration.current
    val previewMaxH = (config.screenHeightDp * 0.32f).dp
    Box(
        Modifier.fillMaxWidth().height(previewMaxH).padding(horizontal = 8.dp, vertical = 2.dp).background(Color(0xFF09090E)),
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
    Row(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF0B0B10))
        .border(0.5.dp, Color(0xFF282833)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        EditorIconButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Prev",
            onClick = onSeekBack,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = Color(0xFFB8B8C4),
        )
        EditorIconButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onPlayPause,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = Color(0xFFA855F7),
        )
        EditorIconButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next",
            onClick = onSeekForward,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = Color(0xFFB8B8C4),
        )
        Spacer(Modifier.weight(1f))
        Text(formatClock(playhead), fontSize = EditorTokens.FontTool, fontWeight = FontWeight.Bold, color = Color(0xFFA855F7))
        Text(" / ", fontSize = EditorTokens.FontTool, color = Color(0xFF8F8F9D))
        Text(formatClock(totalDur), fontSize = EditorTokens.FontTool, color = Color(0xFF8F8F9D))
        Spacer(Modifier.weight(1f))
        EditorIconButton(
            icon = Icons.Filled.Fullscreen,
            contentDescription = "Fullscreen",
            onClick = onToggleFullscreen,
            target = EditorTokens.ToolTarget,
            background = Color.Transparent,
            tint = if (fullscreen) Color(0xFFA855F7) else Color(0xFFB8B8C4),
        )
    }
}

@Composable
private fun AspectToolbar(selectedAspect: String, onAspectSelected: (String) -> Unit) {
    val aspects = listOf("1:1", "4:5", "9:16", "16:9", "2.35:1")
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF0B0B10))
            .border(0.5.dp, Color(0xFF282833)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Aspect", fontSize = 10.sp, color = Color(0xFF8F8F9D), modifier = Modifier.weight(1f))
        aspects.forEach { label ->
            val sel = selectedAspect == label
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Color(0xFFA855F7).copy(alpha = 0.22f) else Color(0xFF17171F))
                    .clickable { onAspectSelected(label) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) Color(0xFFA855F7) else Color(0xFFC9C9D2))
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
        Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF0B0B10))
            .border(0.5.dp, Color(0xFF282833)).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TimelineToolButton(Icons.Filled.ContentCut, "Split", onSplit)
        TimelineToolButton(Icons.Filled.Tune, "Keyframe", onKeyframe)
        TimelineToolButton(Icons.Filled.MusicNote, "Marker", onMarker)
        Spacer(Modifier.weight(1f))
        ZoomIconButton("−", "Zoom out", onZoomOut)
        Text("$zoomPercent%", fontSize = EditorTokens.FontLabel, color = Color(0xFFC9C9D2), modifier = Modifier.padding(horizontal = 4.dp))
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
            .background(Color(0xFF17171F))
            .clickable { onClick() },
    ) {
        Text(glyph, fontSize = EditorTokens.FontTool, color = Color(0xFFC9C9D2))
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
    Row(Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF0B0B10))
        .border(0.5.dp, Color(0xFF292934)).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF39D7B1)))
        Text("Saved", fontSize = 9.sp, color = Color(0xFF39D7B1))
        Text("•", fontSize = 9.sp, color = Color(0xFF858692))
        Text(selectedAspect, fontSize = 9.sp, color = Color(0xFF858692))
        Text("•", fontSize = 9.sp, color = Color(0xFF858692))
        Text("${p?.export?.fps?.fps ?: 30} FPS", fontSize = 9.sp, color = Color(0xFF858692))
        Spacer(Modifier.weight(1f))
        Text("GPU", fontSize = 9.sp, color = Color(0xFF858692))
    }
}

@Composable
private fun ExportOverlay(showExport: Boolean, onClose: () -> Unit, exportState: ExportState, p: PhonkProject?, pattern: CutPattern, exportConfig: ExportConfig, onExport: () -> Unit, onCancel: () -> Unit, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onClose)) {
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = Color(0xFF121219), shadowElevation = 12.dp) {
            Column(Modifier.fillMaxWidth().padding(EditorTokens.Space12).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Export", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFF7F7FB), modifier = Modifier.weight(1f))
                    EditorIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close",
                        onClick = onClose,
                        background = Color(0xFF17171F),
                        tint = Color(0xFFF7F7FB),
                    )
                }
                Spacer(Modifier.height(EditorTokens.Space8))
                Text("${p?.export?.resolution ?: "1080p"} · ${p?.export?.fps?.fps ?: 30} FPS", fontSize = EditorTokens.FontCompact, color = Color(0xFF8F8F9D))
                Spacer(Modifier.height(EditorTokens.Space8))
                when (val es = exportState) {
                    is ExportState.Running -> {
                        PhonkProgressBar(progress = es.progress, activeColor = Color(0xFFA855F7), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(EditorTokens.Space4)); Text("${(es.progress * 100).toInt()}%", fontSize = EditorTokens.FontLabel, color = Color(0xFF8F909D))
                        Spacer(Modifier.height(EditorTokens.Space4))
                        Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(Color(0xFF17171F)), shape = RoundedCornerShape(EditorTokens.CornerButton), modifier = Modifier.fillMaxWidth().height(EditorTokens.PrimaryHeight)) {
                            Text("Cancel", color = Color(0xFFF7F7FB))
                        }
                    }
                    is ExportState.Done -> {
                        Text("Done!", fontSize = EditorTokens.FontTool, color = Color(0xFF39D7B1)); Spacer(Modifier.height(EditorTokens.Space4))
                        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(Color(0xFFA855F7)), shape = RoundedCornerShape(EditorTokens.CornerButton), modifier = Modifier.fillMaxWidth().height(EditorTokens.PrimaryHeight)) {
                            Text("OK", color = Color.White)
                        }
                    }
                    is ExportState.Failed -> {
                        Text(es.message, fontSize = EditorTokens.FontLabel, color = Color(0xFFFF6B6B)); Spacer(Modifier.height(EditorTokens.Space4))
                        Button(onClick = onExport, colors = ButtonDefaults.buttonColors(Color(0xFFA855F7)), shape = RoundedCornerShape(EditorTokens.CornerButton), modifier = Modifier.fillMaxWidth().height(EditorTokens.PrimaryHeight)) {
                            Text("Retry", color = Color.White)
                        }
                    }
                    ExportState.Idle -> {
                        Button(onClick = onExport, colors = ButtonDefaults.buttonColors(Color(0xFFA855F7), Color.White),
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
