package dev.phonk.editor.export

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.phonk.editor.R
import dev.phonk.editor.model.AudioBitrate
import dev.phonk.editor.model.ExportConfig
import dev.phonk.editor.model.FrameRate
import dev.phonk.editor.model.Resolution
import dev.phonk.editor.model.VideoCodec

/** Inline export options picker used inside the export dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    config: ExportConfig,
    onChange: (ExportConfig) -> Unit,
) {
    Text(stringResource(R.string.resolution))
    LazyRow(Modifier.fillMaxWidth()) {
        items(Resolution.entries) { opt ->
            FilterChip(
                selected = opt == config.resolution,
                onClick = { onChange(config.copy(resolution = opt)) },
                label = { Text(opt.label) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    Spacer(Modifier.padding(top = 8.dp))

    Text(stringResource(R.string.frame_rate))
    LazyRow(Modifier.fillMaxWidth()) {
        items(FrameRate.entries) { opt ->
            FilterChip(
                selected = opt == config.fps,
                onClick = { onChange(config.copy(fps = opt)) },
                label = { Text(opt.label) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    Spacer(Modifier.padding(top = 8.dp))

    Text(stringResource(R.string.video_codec))
    LazyRow(Modifier.fillMaxWidth()) {
        items(VideoCodec.entries) { opt ->
            FilterChip(
                selected = opt == config.videoCodec,
                onClick = { onChange(config.copy(videoCodec = opt)) },
                label = { Text(opt.label) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    Spacer(Modifier.padding(top = 8.dp))

    Text(stringResource(R.string.audio_bitrate))
    LazyRow(Modifier.fillMaxWidth()) {
        items(AudioBitrate.entries) { opt ->
            FilterChip(
                selected = opt == config.audioBitrate,
                onClick = { onChange(config.copy(audioBitrate = opt)) },
                label = { Text(opt.label) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
    Spacer(Modifier.padding(top = 8.dp))
}