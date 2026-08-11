package dev.phonk.editor.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.phonk.editor.R
import dev.phonk.editor.analysis.AudioExtractor
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.ProjectCodec
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.ui.components.BottomNav
import dev.phonk.editor.ui.components.NavTab
import dev.phonk.editor.util.ThumbnailLoader
import dev.phonk.editor.util.TimeUtils.formatClock
import java.io.File

private fun queryName(resolver: android.content.ContentResolver, uri: Uri): String {
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx) ?: "Untitled"
        }
    }
    return "Untitled"
}

private fun formatRelativeTime(updatedAt: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - updatedAt
    val minutes = diff / 60000
    val hours = diff / 3600000
    val days = diff / 86400000
    return when {
        minutes < 1 -> "Updated just now"
        hours < 1 -> "Updated $minutes min ago"
        days < 1 -> "Updated $hours hr ago"
        else -> "Updated $days days ago"
    }
}

@Composable
fun HomeScreen(
    onOpen: (PhonkProject) -> Unit,
    onOpenSettings: () -> Unit,
    onNavigate: (NavTab) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember(context) { ProjectStore(context) }
    var recent by remember { mutableStateOf<List<PhonkProject>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    // Dialog states
    var showHelpDialog by remember { mutableStateOf(false) }
    var renameProject by remember { mutableStateOf<PhonkProject?>(null) }

    fun refresh() {
        isLoading = true
        errorMessage = null
        try {
            recent = store.listRecent()
        } catch (e: Exception) {
            errorMessage = "Could not load projects"
            recent = emptyList()
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { refresh() }

    fun duplicateProject(project: PhonkProject) {
        val copy = project.copy(
            id = System.currentTimeMillis().toString(16),
            name = "${project.name} (copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        store.save(copy)
        refresh()
        Toast.makeText(context, "Project duplicated", Toast.LENGTH_SHORT).show()
    }

    fun shareProject(project: PhonkProject) {
        val file = File(context.filesDir, "projects/${project.id}.json")
        if (!file.exists()) {
            Toast.makeText(context, "Project file not found", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, "Phonk Drop Editor project: ${project.name}")
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Share project"))
        }.onFailure {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    fun renameProject(project: PhonkProject, newName: String) {
        val renamed = project.copy(name = newName, updatedAt = System.currentTimeMillis())
        store.save(renamed)
        refresh()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importing = true
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { }
            val durationMs = runCatching {
                AudioExtractor.queryDuration(context.contentResolver, uri)
            }.getOrDefault(0L)
            val name = queryName(context.contentResolver, uri)
            val project = PhonkProject(
                name = name,
                videoUri = uri.toString(),
                videoDurationMs = durationMs,
            )
            store.save(project)
            importing = false
            onOpen(project)
        }
    }

    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNav(
                activeTab = NavTab.HOME,
                onTabSelected = { tab ->
                    if (tab != NavTab.HOME) onNavigate(tab)
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.background,
                        1f to scheme.surfaceContainerLowest,
                    )
                ),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 16.dp,
                    bottom = 100.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Header(
                        onOpenSettings = onOpenSettings,
                    )
                }

                item {
                    OpenVideoCard(
                        importing = importing,
                        onClick = { launcher.launch(arrayOf("video/*", "application/mp4")) },
                    )
                }

                item {
                    QuickActionsCard(
                        onNewProject = { launcher.launch(arrayOf("video/*", "application/mp4")) },
                        onOpenProject = { launcher.launch(arrayOf("video/*", "application/mp4")) },
                        onExportVideo = {
                            recent?.firstOrNull()?.let { onOpen(it) }
                                ?: run { Toast.makeText(context, "Open a video first", Toast.LENGTH_SHORT).show() }
                        },
                        onBeatAnalyzer = { onNavigate(NavTab.BEATS) },
                        onHelp = { showHelpDialog = true },
                    )
                }

                item {
                    RecentProjectsHeader(
                        onViewAll = { onNavigate(NavTab.PROJECTS) },
                    )
                }

                when {
                    isLoading -> {
                        item {
                            LoadingState()
                        }
                    }
                    errorMessage != null -> {
                        item {
                            ErrorState(
                                message = errorMessage!!,
                                onRetry = { refresh() },
                            )
                        }
                    }
                    recent.isNullOrEmpty() -> {
                        item {
                            EmptyState()
                        }
                    }
                    else -> {
                        items(recent!!) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { onOpen(project) },
                                onDelete = {
                                    store.delete(project.id)
                                    refresh()
                                },
                                onRename = { renameProject = it },
                                onDuplicate = { duplicateProject(it) },
                                onShare = { shareProject(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    renameProject?.let { project ->
        RenameProjectDialog(
            currentName = project.name,
            onDismiss = { renameProject = null },
            onConfirm = { newName ->
                renameProject(project, newName)
                renameProject = null
            },
        )
    }
}

@Composable
private fun Header(
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(scheme.primary, scheme.tertiary),
                        ),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.clickable(onClick = onOpenSettings),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OpenVideoCard(
    importing: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !importing,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (pressed) Brush.linearGradient(
                        listOf(scheme.primary.copy(alpha = 0.85f), scheme.tertiary.copy(alpha = 0.85f))
                    ) else Brush.linearGradient(
                        listOf(scheme.primary, scheme.tertiary),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                ) {
                    Icon(
                        imageVector = if (importing) Icons.Filled.VideoLibrary else Icons.Filled.VideoFile,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (importing) stringResource(R.string.loading) else stringResource(R.string.open_video),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (importing) "" else stringResource(R.string.open_video_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onExportVideo: () -> Unit,
    onBeatAnalyzer: () -> Unit,
    onHelp: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.quick_actions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuickActionItem(
                    icon = Icons.Filled.OpenInNew,
                    label = stringResource(R.string.action_new_project),
                    onClick = onNewProject,
                )
                QuickActionItem(
                    icon = Icons.Filled.VideoLibrary,
                    label = stringResource(R.string.action_open_project),
                    onClick = onOpenProject,
                )
                QuickActionItem(
                    icon = Icons.Filled.SaveAlt,
                    label = stringResource(R.string.action_export_video),
                    onClick = onExportVideo,
                )
                QuickActionItem(
                    icon = Icons.Filled.Analytics,
                    label = stringResource(R.string.action_beat_analyzer),
                    onClick = onBeatAnalyzer,
                )
                QuickActionItem(
                    icon = Icons.Filled.HelpOutline,
                    label = stringResource(R.string.action_help),
                    onClick = onHelp,
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (pressed) scheme.primary.copy(alpha = 0.15f)
                    else scheme.surfaceContainerHigh,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = scheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentProjectsHeader(
    onViewAll: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.recent_projects),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
        }
        TextButton(onClick = onViewAll) {
            Text(
                text = stringResource(R.string.view_all),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.primary,
            )
        }
    }
}

@Composable
internal fun ProjectCard(
    project: PhonkProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: (PhonkProject) -> Unit = {},
    onDuplicate: (PhonkProject) -> Unit = {},
    onShare: (PhonkProject) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var thumb by remember(project.id) { mutableStateOf(ThumbnailLoader.peek(project.id)) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(project.id, project.videoUri) {
        if (thumb == null) thumb = ThumbnailLoader.load(context, project.id, project.videoUri)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(thumbGradient(scheme, project.id)),
            ) {
                thumb?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val hasBpm = project.bpm > 0.0
                val beats = project.beats.size
                val drops = project.drops.size
                val duration = if (project.videoDurationMs > 0) formatClock(project.videoDurationMs) else null

                if (hasBpm) {
                    Text(
                        text = stringResource(R.string.bpm_value, project.bpm),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = if (beats > 0 || drops > 0) {
                        stringResource(R.string.beats_drops_value, beats, drops)
                    } else {
                        stringResource(R.string.home_not_analyzed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )

                if (duration != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            text = "  •  ${formatRelativeTime(project.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.project_menu),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_project)) },
                        onClick = {
                            menuExpanded = false
                            onRename(project)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_project)) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate(project)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_project)) },
                        onClick = {
                            menuExpanded = false
                            onShare(project)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete_project),
                                color = scheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh),
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = scheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_no_projects),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.empty_no_projects_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingState() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.error,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.retry),
                color = scheme.primary,
            )
        }
    }
}

@Composable
private fun thumbGradient(scheme: androidx.compose.material3.ColorScheme, id: String): Brush =
    Brush.linearGradient(
        listOf(
            scheme.primary.copy(alpha = 0.55f + (id.hashCode().and(3)) * 0.1f),
            scheme.surfaceVariant,
        ),
    )
