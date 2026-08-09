package dev.phonk.editor.ui.editor

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import dev.phonk.editor.R
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.preview.PlayerController
import dev.phonk.editor.ui.components.PhonkIconButton
import dev.phonk.editor.util.TimeUtils
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow

@Composable
fun EditorPreview(
    playerController: PlayerController,
    project: PhonkProject?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var fullscreen by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(9f / 16f) }
    var showControls by remember { mutableStateOf(true) }
    var positionMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    val totalMs = project?.videoDurationMs ?: 0L

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = playerController.pollPosition()
            delay(100)
        }
    }

    LaunchedEffect(Unit) {
        playerController.onEnded = { isPlaying = false }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (fullscreen) 0.dp else 14.dp))
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(aspect)),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).also { v ->
                        v.useController = false
                        v.player = playerController.player
                        v.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        project?.name ?: stringResource(R.string.untitled),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    AspectChip("9:16", aspect == 9f / 16f) { aspect = 9f / 16f }
                    AspectChip("1:1", aspect == 1f) { aspect = 1f }
                    AspectChip("16:9", aspect == 16f / 9f) { aspect = 16f / 9f }
                    Spacer(Modifier.width(6.dp))
                    PhonkIconButton(
                        icon = Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.fullscreen),
                        onClick = { fullscreen = !fullscreen },
                        tint = Color.White,
                        background = Color.White.copy(alpha = 0.15f),
                        size = 34.dp,
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhonkIconButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        onClick = {
                            isPlaying = !isPlaying
                            if (isPlaying) playerController.play() else playerController.pause()
                        },
                        tint = Color.White,
                        background = scheme.primary,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        TimeUtils.formatClock(positionMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    )
                    Text(
                        TimeUtils.formatClock(totalMs),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AspectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary else Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = if (selected) scheme.onPrimary else Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
