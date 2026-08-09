package dev.phonk.editor.ui

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import dev.phonk.editor.R
import dev.phonk.editor.preview.PlayerController

/** Compose host for the ExoPlayer surface. */
@Composable
fun PreviewPlayer(
    playerController: PlayerController,
    totalMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).also { v ->
                    v.useController = true
                    v.player = playerController.player
                    if (v.layoutParams == null) {
                        v.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                }
            },
            update = {},
            modifier = Modifier.fillMaxSize(),
        )
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
            Text(stringResource(R.string.preview))
        }
    }
}