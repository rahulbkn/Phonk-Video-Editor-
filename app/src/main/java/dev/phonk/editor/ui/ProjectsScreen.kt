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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.analysis.AudioExtractor
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.project.ProjectStore
import dev.phonk.editor.ui.components.NavTab
import dev.phonk.editor.ui.components.PhonkTabScaffold
import dev.phonk.editor.ui.components.TabScreenHeader
import dev.phonk.editor.ui.components.UiDimens
import dev.phonk.editor.ui.home.HomeTokens
import dev.phonk.editor.ui.home.homePalette

private fun queryName(resolver: android.content.ContentResolver, uri: Uri): String {
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return c.getString(idx) ?: "Untitled"
        }
    }
    return "Untitled"
}

/**
 * Projects screen — full list of the user's projects. Uses the same shared
 * dark design system, spacing, insets and floating bottom navigation as the
 * Home screen so the two screens feel like one application.
 */
@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpen: (PhonkProject) -> Unit,
    onNavigate: (NavTab) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember(context) { ProjectStore(context) }
    var projects by remember { mutableStateOf<List<PhonkProject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val palette = homePalette()

    fun refresh() {
        isLoading = true
        projects = store.listRecent()
        isLoading = false
    }

    LaunchedEffect(Unit) { refresh() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
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
            refresh()
            onOpen(project)
        }
    }

    PhonkTabScaffold(
        activeTab = NavTab.PROJECTS,
        onTabSelected = { tab ->
            if (tab != NavTab.PROJECTS) onNavigate(tab)
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabScreenHeader(
                title = stringResource(R.string.nav_projects),
                onBack = onBack,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.loading),
                            fontSize = UiDimens.textSizeMd,
                            color = palette.textSecondary,
                        )
                    }
                } else if (projects.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = UiDimens.screenPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.empty_no_projects),
                            fontSize = UiDimens.textSizeSectionTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                        )
                        Spacer(Modifier.height(UiDimens.spaceSm))
                        Text(
                            text = stringResource(R.string.empty_no_projects_subtitle),
                            fontSize = UiDimens.textSizeMd,
                            color = palette.textSecondary,
                        )
                        Spacer(Modifier.height(UiDimens.spaceXl))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(palette.primary, palette.primaryBright),
                                    )
                                )
                                .clickable { launcher.launch(arrayOf("video/*", "application/mp4")) },
                        ) {
                            Text(
                                text = stringResource(R.string.open_video),
                                fontSize = UiDimens.textSizeButton,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = HomeTokens.ScreenHorizontal,
                            end = HomeTokens.ScreenHorizontal,
                            top = UiDimens.spaceSm,
                            bottom = HomeTokens.ScreenHorizontal,
                        ),
                        verticalArrangement = Arrangement.spacedBy(UiDimens.itemSpacing),
                    ) {
                        items(projects) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { onOpen(project) },
                                onDelete = {
                                    store.delete(project.id)
                                    refresh()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}