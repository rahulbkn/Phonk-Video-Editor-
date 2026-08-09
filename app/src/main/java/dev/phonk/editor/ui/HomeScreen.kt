package dev.phonk.editor.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.phonk.editor.analysis.AudioExtractor
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.util.TimeUtils.formatClock
import dev.phonk.editor.R

private fun queryName(resolver: android.content.ContentResolver, uri: Uri): String {
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx) ?: "Untitled"
        }
    }
    return "Untitled"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (PhonkProject) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { ProjectStore(context) }
    var recent by remember { mutableStateOf<List<PhonkProject>>(emptyList()) }

    fun refresh() {
        recent = store.listRecent()
    }
    LaunchedEffect(Unit) { refresh() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.onFailure { }
            val durationMs = runCatching { AudioExtractor.queryDuration(context.contentResolver, uri) }.getOrDefault(0L)
            val name = queryName(context.contentResolver, uri)
            val store = ProjectStore(context)
            val project = PhonkProject(
                name = name,
                videoUri = uri.toString(),
                videoDurationMs = durationMs,
            )
            store.save(project)
            onOpen(project)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                )
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_title)) },
                    actions = {
                        TextButton(onClick = onOpenSettings) {
                            Text(stringResource(R.string.settings))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Button(
                    onClick = { launcher.launch(arrayOf("video/*", "application/mp4")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.open_video))
                }
                Text(
                    stringResource(R.string.recent_projects),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recent) { project ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(project) },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(project.name, style = MaterialTheme.typography.titleSmall)
                                val d = project.videoDurationMs
                                val hasBpm = project.bpm > 0.0
                                val duration = if (d > 0) formatClock(d) else null
                                val info = when {
                                    duration != null && hasBpm -> stringResource(
                                        R.string.home_duration_bpm, duration, project.bpm
                                    )
                                    duration != null -> duration
                                    hasBpm -> stringResource(R.string.home_bpm, project.bpm)
                                    else -> stringResource(R.string.home_not_analyzed)
                                }
                                Text(info, style = MaterialTheme.typography.bodySmall)
                                val drops = project.drops.size
                                val beats = project.beats.size
                                Text(
                                    stringResource(R.string.home_beats_drops, beats, drops),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}