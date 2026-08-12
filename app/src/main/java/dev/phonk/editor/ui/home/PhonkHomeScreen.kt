package dev.phonk.editor.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import dev.phonk.editor.R
import dev.phonk.editor.analysis.AudioExtractor
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.RenameProjectDialog
import dev.phonk.editor.ui.components.NavTab
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PHONK EDITOR home screen — the app's landing screen.
 *
 * Real, data-driven UI: projects come from [ProjectStore], every card/button
 * maps to a real action (editor, templates, settings, pro, creation sheet)
 * and the layout is fully responsive (dp/sp + LazyRow/LazyColumn + insets).
 */
@Composable
fun PhonkHomeScreen(
    onOpen: (PhonkProject) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
    onOpenBeatAnalyzer: () -> Unit,
    onNavigate: (NavTab) -> Unit,
    openCreateSheet: Boolean = false,
    onCreateSheetHandled: () -> Unit = {},
) {
    val palette = homePalette()
    val context = LocalContext.current
    val store = remember(context) { ProjectStore(context) }

    var projects by remember { mutableStateOf<List<PhonkProject>>(emptyList()) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PhonkProject?>(null) }
    var pendingRecordingUri by remember { mutableStateOf<Uri?>(null) }
    val untitledName = stringResource(R.string.untitled)

    fun refresh() {
        projects = store.listRecent()
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(openCreateSheet) {
        if (openCreateSheet) {
            showCreateSheet = true
            onCreateSheetHandled()
        }
    }

    fun findBy(id: String): PhonkProject? = projects.firstOrNull { it.id == id }

    fun openProject(id: String) {
        findBy(id)?.let { onOpen(it) }
    }

    fun importVideo(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { }
        val durationMs = runCatching {
            AudioExtractor.queryDuration(context.contentResolver, uri)
        }.getOrDefault(0L)
        val name = queryDisplayName(context.contentResolver, uri)
        val project = PhonkProject(
            name = name,
            videoUri = uri.toString(),
            videoDurationMs = durationMs,
        )
        store.save(project)
        refresh()
        Toast.makeText(context, R.string.home_video_imported, Toast.LENGTH_SHORT).show()
        onOpen(project)
    }

    fun createNewProject() {
        val project = PhonkProject(name = untitledName)
        store.save(project)
        refresh()
        Toast.makeText(context, R.string.home_project_created, Toast.LENGTH_SHORT).show()
        onOpen(project)
    }

    fun duplicateProject(project: PhonkProject) {
        val copy = project.copy(
            id = System.currentTimeMillis().toString(16),
            name = "${project.name} (copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        store.save(copy)
        refresh()
        Toast.makeText(context, R.string.duplicate_project, Toast.LENGTH_SHORT).show()
    }

    fun shareProject(project: PhonkProject) {
        val file = File(context.filesDir, "projects/${project.id}.json")
        if (!file.exists()) {
            Toast.makeText(context, R.string.home_project_file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, "Phonk Editor project: ${project.name}")
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_project)))
        }.onFailure {
            Toast.makeText(context, R.string.home_share_error, Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Media launchers ────────────────────────────────────────────────────
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importVideo(uri)
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { }
        val durationMs = runCatching {
            AudioExtractor.queryDuration(context.contentResolver, uri)
        }.getOrDefault(0L)
        val name = queryDisplayName(context.contentResolver, uri)
        val project = PhonkProject(
            name = name,
            audioUri = uri.toString(),
            audioDurationMs = durationMs,
        )
        store.save(project)
        refresh()
        Toast.makeText(context, R.string.home_video_imported, Toast.LENGTH_SHORT).show()
        onOpen(project)
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context.contentResolver, uri)
        val overlay = OverlayLayer(
            kind = "Image",
            label = name,
            uri = uri.toString(),
            startMs = 0L,
            endMs = 3000L,
            x = 0.5f,
            y = 0.5f,
            scaleX = 1f,
            scaleY = 1f,
        )
        val project = PhonkProject(name = name, overlays = listOf(overlay))
        store.save(project)
        refresh()
        Toast.makeText(context, R.string.home_photo_added, Toast.LENGTH_SHORT).show()
        onOpen(project)
    }

    val recordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        val uri = pendingRecordingUri
        pendingRecordingUri = null
        if (success == true && uri != null) {
            val durationMs = runCatching {
                AudioExtractor.queryDuration(context.contentResolver, uri)
            }.getOrDefault(0L)
            val project = PhonkProject(
                name = "Recording",
                videoUri = uri.toString(),
                videoDurationMs = durationMs,
            )
            store.save(project)
            refresh()
            Toast.makeText(context, R.string.home_recording_saved, Toast.LENGTH_SHORT).show()
            onOpen(project)
        }
    }

    fun startRecording() {
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "rec_$stamp.mp4")
        val uri = FileProvider.getUriForFile(context, "dev.phonk.editor.fileprovider", file)
        pendingRecordingUri = uri
        runCatching {
            recordLauncher.launch(uri)
        }.onFailure {
            pendingRecordingUri = null
            Toast.makeText(context, R.string.home_no_camera, Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Quick actions + AI tools (data-driven) ─────────────────────────────
    val homeProjects = remember(projects) { projects.map { HomeProject.from(it) } }

    val quickActions = listOf(
        QuickAction("trim", stringResource(R.string.home_trim_video), Icons.Filled.ContentCut) {
            videoPicker.launch(arrayOf("video/*", "application/mp4"))
        },
        QuickAction("templates", stringResource(R.string.home_templates), Icons.Filled.Layers) {
            onNavigate(NavTab.TEMPLATES)
        },
        QuickAction("effects", stringResource(R.string.home_effects), Icons.Filled.AutoAwesome) {
            videoPicker.launch(arrayOf("video/*", "application/mp4"))
        },
        QuickAction("music", stringResource(R.string.home_add_music), Icons.Filled.MusicNote) {
            audioPicker.launch(arrayOf("audio/*", "audio/mpeg", "audio/x-wav"))
        },
        QuickAction("text", stringResource(R.string.home_add_text), Icons.Filled.TextFields) {
            videoPicker.launch(arrayOf("video/*", "application/mp4"))
        },
    )

    val aiTools = listOf(
        AiTool("autoedit", stringResource(R.string.home_ai_auto_edit), stringResource(R.string.home_ai_auto_edit_desc), Icons.Filled.AutoAwesome) {
            videoPicker.launch(arrayOf("video/*", "application/mp4"))
        },
        AiTool("beatsync", stringResource(R.string.home_ai_beat_sync), stringResource(R.string.home_ai_beat_sync_desc), Icons.Filled.GraphicEq) {
            onOpenBeatAnalyzer()
        },
        AiTool("aieffects", stringResource(R.string.home_ai_effects), stringResource(R.string.home_ai_effects_desc), Icons.Filled.AutoFixHigh) {
            videoPicker.launch(arrayOf("video/*", "application/mp4"))
        },
        AiTool("reframe", stringResource(R.string.home_ai_reframe), stringResource(R.string.home_ai_reframe_desc), Icons.Filled.AspectRatio) {
            videoPicker.launch(arrayOf("video/*", "application/mp4"))
        },
    )

    // ─── Status bar: always light icons over the dark home background ──────
    val view = LocalView.current
    val darkTheme = when (SettingsManager.themeMode) {
        SettingsManager.THEME_LIGHT -> false
        SettingsManager.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    DisposableEffect(view, darkTheme) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = HomeTokens.ScreenHorizontal,
                end = HomeTokens.ScreenHorizontal,
                top = 12.dp,
                bottom = bottomInset + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(HomeTokens.SectionSpacing),
        ) {
            item {
                PhonkHeader(
                    onProClick = onOpenPro,
                    onSettingsClick = onOpenSettings,
                )
            }
            item {
                HeroBanner(onCreateProject = ::createNewProject)
            }
            item {
                QuickActionsSection(
                    actions = quickActions,
                    onSeeAll = { showCreateSheet = true },
                )
            }
            item {
                ProjectsSection(
                    projects = homeProjects,
                    onOpen = { openProject(it.id) },
                    onSeeAll = { onNavigate(NavTab.PROJECTS) },
                    onRename = { renameTarget = findBy(it.id) },
                    onDuplicate = { findBy(it.id)?.let(::duplicateProject) },
                    onShare = { findBy(it.id)?.let(::shareProject) },
                    onDelete = { p ->
                        store.delete(p.id)
                        refresh()
                    },
                    onEmptyAction = { showCreateSheet = true },
                )
            }
            item {
                AIToolsSection(tools = aiTools)
            }
            item {
                Spacer(Modifier.height(8.dp))
            }
        }

        // ─── Bottom navigation (floating, fixed) ───────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = bottomInset + 6.dp),
        ) {
            BottomNav(
                activeTab = NavTab.HOME,
                onTabSelected = { tab ->
                    when (tab) {
                        NavTab.CREATE -> showCreateSheet = true
                        NavTab.HOME -> Unit
                        else -> onNavigate(tab)
                    }
                },
            )
        }
    }

    // ─── Creation sheet ────────────────────────────────────────────────────
    if (showCreateSheet) {
        CreateProjectSheet(
            onNewProject = {
                showCreateSheet = false
                createNewProject()
            },
            onImportVideo = {
                showCreateSheet = false
                videoPicker.launch(arrayOf("video/*", "application/mp4"))
            },
            onImportPhotos = {
                showCreateSheet = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRecordVideo = {
                showCreateSheet = false
                startRecording()
            },
            onDismiss = { showCreateSheet = false },
        )
    }

    // ─── Rename dialog ────────────────────────────────────────────────────
    renameTarget?.let { project ->
        RenameProjectDialog(
            currentName = project.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                store.save(project.copy(name = newName, updatedAt = System.currentTimeMillis()))
                refresh()
                renameTarget = null
            },
        )
    }
}

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx) ?: "Untitled"
        }
    }
    return "Untitled"
}
