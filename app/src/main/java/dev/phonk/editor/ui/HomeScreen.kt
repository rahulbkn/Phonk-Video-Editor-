package dev.phonk.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.phonk.editor.R
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.ui.components.UiDimens
import dev.phonk.editor.ui.home.HomeTokens
import dev.phonk.editor.ui.home.homePalette
import dev.phonk.editor.util.ThumbnailLoader
import dev.phonk.editor.util.TimeUtils.formatClock

/**
 * Shared project list card used by the full Projects screen.
 * (The home screen uses its own always-dark card in ui/home.)
 */
@Composable
internal fun ProjectCard(
    project: PhonkProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: (PhonkProject) -> Unit = {},
    onDuplicate: (PhonkProject) -> Unit = {},
    onShare: (PhonkProject) -> Unit = {},
) {
    val palette = homePalette()
    val context = LocalContext.current
    var thumb by remember(project.id) { mutableStateOf(ThumbnailLoader.peek(project.id)) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(project.id, project.videoUri) {
        if (thumb == null) thumb = ThumbnailLoader.load(context, project.id, project.videoUri)
    }

    Card(
        shape = RoundedCornerShape(HomeTokens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = palette.card),
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
                    .clip(RoundedCornerShape(UiDimens.cornerRadiusMd))
                    .background(thumbGradient(palette, project.id)),
            ) {
                thumb?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(UiDimens.cornerRadiusMd)),
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
                    color = palette.text,
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
                        color = palette.primaryBright,
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
                    color = palette.textSecondary,
                )

                if (duration != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                        Text(
                            text = "  •  ${formatRelativeTime(project.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary.copy(alpha = 0.7f),
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
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_project), color = palette.text) },
                        onClick = {
                            menuExpanded = false
                            onRename(project)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_project), color = palette.text) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate(project)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_project), color = palette.text) },
                        onClick = {
                            menuExpanded = false
                            onShare(project)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.delete_project),
                                color = MaterialTheme.colorScheme.error,
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
private fun thumbGradient(palette: dev.phonk.editor.ui.home.PhonkHomePalette, id: String): Brush =
    Brush.linearGradient(
        listOf(
            palette.primary.copy(alpha = 0.55f + (id.hashCode().and(3)) * 0.1f),
            palette.card,
        ),
    )
