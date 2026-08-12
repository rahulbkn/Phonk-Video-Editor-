package dev.phonk.editor.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.util.ThumbnailLoader

/**
 * "Projects" section with a horizontally scrolling carousel of project cards.
 * Renders real project data (backed by the project store) with real video
 * thumbnails when available and a stylized neon placeholder otherwise.
 */
@Composable
fun ProjectsSection(
    projects: List<HomeProject>,
    onOpen: (HomeProject) -> Unit,
    onSeeAll: () -> Unit,
    onRename: (HomeProject) -> Unit,
    onDuplicate: (HomeProject) -> Unit,
    onShare: (HomeProject) -> Unit,
    onDelete: (HomeProject) -> Unit,
    onEmptyAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderRow(
            title = stringResource(R.string.home_projects),
            onSeeAll = if (projects.isNotEmpty()) onSeeAll else null,
            seeAllLabel = stringResource(R.string.home_see_all),
        )
        Spacer(Modifier.height(14.dp))
        if (projects.isEmpty()) {
            EmptyProjectsCard(onAction = onEmptyAction)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpen(project) },
                        onRename = { onRename(project) },
                        onDuplicate = { onDuplicate(project) },
                        onShare = { onShare(project) },
                        onDelete = { onDelete(project) },
                    )
                }
            }
        }
    }
}

/** Real, interactive project card used in the home carousel. */
@Composable
fun ProjectCard(
    project: HomeProject,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = homePalette()
    val context = LocalContext.current
    var thumb by remember(project.id) { mutableStateOf(ThumbnailLoader.peek(project.id)) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(project.id, project.videoUri) {
        if (thumb == null) thumb = ThumbnailLoader.load(context, project.id, project.videoUri)
    }

    PhonkHomeCard(
        onClick = onOpen,
        modifier = Modifier.width(HomeTokens.ProjectCardWidth),
        glow = false,
    ) {
        Column {
            // ─── Thumbnail (16:9) ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = project.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                } else {
                    PhonkArtwork(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        seed = project.artSeed,
                        accent = palette.primary,
                        accentBright = palette.primaryBright,
                    )
                }
                // subtle bottom scrim for badge legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            )
                        ),
                )
                // centre play affordance
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // duration badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = project.durationLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
                // three-dot menu (dark translucent circle)
                androidx.compose.material3.IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(38.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.project_menu),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // ─── Meta ───────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = project.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_project_date_size, project.dateLabel, project.sizeLabel),
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }

    // ─── Three-dot menu ──────────────────────────────────────────────────────
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.rename_project)) },
            onClick = { menuExpanded = false; onRename() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.duplicate_project)) },
            onClick = { menuExpanded = false; onDuplicate() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.share_project)) },
            onClick = { menuExpanded = false; onShare() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete_project), color = MaterialTheme.colorScheme.error) },
            onClick = { menuExpanded = false; onDelete() },
        )
    }
}

@Composable
private fun EmptyProjectsCard(onAction: () -> Unit) {
    val palette = homePalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HomeTokens.CardCorner))
            .background(palette.cardSecondary)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_projects),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_projects_empty_hint),
            fontSize = 13.sp,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(palette.primary, palette.primaryBright),
                    )
                )
                .clickable(onClick = onAction),
        ) {
            Text(
                text = stringResource(R.string.home_empty_cta),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}
