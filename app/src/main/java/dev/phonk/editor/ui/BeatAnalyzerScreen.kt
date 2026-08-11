package dev.phonk.editor.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.core.net.toUri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.phonk.editor.analysis.AnalysisManager
import dev.phonk.editor.analysis.AnalysisState
import dev.phonk.editor.analysis.Phase
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkProgressBar
import dev.phonk.editor.util.ThumbnailLoader

@Composable
fun BeatAnalyzerScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProjectStore(context) }
    val scope = rememberCoroutineScope()
    val projects = remember { store.listRecent() }

    var selectedProject by remember { mutableStateOf<PhonkProject?>(null) }
    var analysisManager by remember { mutableStateOf<AnalysisManager?>(null) }
    val analysisState by analysisManager?.state?.collectAsStateWithLifecycle() ?: remember {
        mutableStateOf<AnalysisState>(AnalysisState.Idle)
    }

    val scheme = MaterialTheme.colorScheme

    fun startAnalysis(project: PhonkProject) {
        val uriStr = project.videoUri ?: return
        val uri = Uri.parse(uriStr)
        selectedProject = project
        val manager = AnalysisManager(context.contentResolver, scope)
        analysisManager = manager
        manager.analyze(uri)
    }

    fun cancelAnalysis() {
        analysisManager?.cancel()
    }

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
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = scheme.onBackground,
                    )
                }
                Text(
                    "Beat Analyzer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            when (val state = analysisState) {
                is AnalysisState.Running -> {
                    AnalysisProgressContent(state = state, onCancel = ::cancelAnalysis)
                }
                is AnalysisState.Done -> {
                    AnalysisResultContent(
                        result = state.result,
                        projectName = selectedProject?.name ?: "",
                        onAnalyzeAnother = {
                            selectedProject = null
                            analysisManager = null
                        },
                    )
                }
                is AnalysisState.Failed -> {
                    AnalysisErrorContent(
                        message = state.message,
                        onRetry = { selectedProject?.let(::startAnalysis) },
                        onDismiss = {
                            selectedProject = null
                            analysisManager = null
                        },
                    )
                }
                AnalysisState.Idle -> {
                    if (projects.isEmpty()) {
                        EmptyProjectsContent()
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                        ) {
                            Text(
                                "Select a video to analyze",
                                fontSize = 14.sp,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                            projects.forEach { project ->
                                AnalysisProjectCard(
                                    project = project,
                                    onClick = { startAnalysis(project) },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisProjectCard(
    project: PhonkProject,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                project.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
            )
            if (project.bpm > 0.0) {
                Text(
                    "${project.bpm.toInt()} BPM • ${project.beats.size} beats",
                    fontSize = 12.sp,
                    color = scheme.primary,
                )
            } else {
                Text(
                    "Not analyzed yet",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AnalysisProgressContent(
    state: AnalysisState.Running,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            if (state.phase == Phase.DECODING) "Decoding audio..." else "Analyzing beats...",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        PhonkProgressBar(
            progress = state.progress,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${(state.progress * 100).toInt()}%",
            fontSize = 14.sp,
            color = scheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun AnalysisResultContent(
    result: dev.phonk.editor.model.AnalysisResult,
    projectName: String,
    onAnalyzeAnother: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            "Analysis Complete",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            projectName,
            fontSize = 14.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ResultStat("BPM", String.format("%.1f", result.bpm))
            ResultStat("Beats", "${result.beats.size}")
            ResultStat("Drops", "${result.drops.size}")
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ResultStat("Duration", "${result.durationMs / 1000}s")
            ResultStat("Confidence", "${(result.beatConfidence * 100).toInt()}%")
        }
        Spacer(Modifier.height(32.dp))
        PhonkButton(
            label = "Analyze Another",
            onClick = onAnalyzeAnother,
            primary = true,
        )
    }
}

@Composable
private fun ResultStat(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        Text(
            label,
            fontSize = 12.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnalysisErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "Analysis Failed",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            fontSize = 14.sp,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
            PhonkButton(label = "Retry", onClick = onRetry, primary = true)
        }
    }
}

@Composable
private fun EmptyProjectsContent() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = scheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No projects to analyze",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Open a video first to analyze its beats",
            fontSize = 13.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}
