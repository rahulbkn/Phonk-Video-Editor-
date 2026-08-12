package dev.phonk.editor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R

/**
 * Bottom sheet with the four creation options: New Project, Import Video,
 * Import Photos and Record Video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectSheet(
    onNewProject: () -> Unit,
    onImportVideo: () -> Unit,
    onImportPhotos: () -> Unit,
    onRecordVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = homePalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSecondary,
        dragHandle = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(palette.border),
                )
            }
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.create_sheet_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.create_sheet_subtitle),
                fontSize = 13.sp,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CreateOption(
                    icon = Icons.Filled.AddCircleOutline,
                    label = stringResource(R.string.create_project),
                    description = stringResource(R.string.create_project_desc),
                    onClick = onNewProject,
                    modifier = Modifier.weight(1f),
                )
                CreateOption(
                    icon = Icons.Filled.VideoFile,
                    label = stringResource(R.string.create_import_video),
                    description = stringResource(R.string.create_import_video_desc),
                    onClick = onImportVideo,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CreateOption(
                    icon = Icons.Filled.Image,
                    label = stringResource(R.string.create_import_photos),
                    description = stringResource(R.string.create_import_photos_desc),
                    onClick = onImportPhotos,
                    modifier = Modifier.weight(1f),
                )
                CreateOption(
                    icon = Icons.Filled.Videocam,
                    label = stringResource(R.string.create_record_video),
                    description = stringResource(R.string.create_record_video_desc),
                    onClick = onRecordVideo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CreateOption(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) palette.card else palette.cardSecondary)
            .border(1.dp, if (pressed) palette.primary.copy(alpha = 0.6f) else palette.border, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(listOf(palette.primary, palette.primaryBright)),
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            fontSize = 11.sp,
            color = palette.textSecondary,
        )
    }
}
