package dev.phonk.editor.ui.editor.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.model.BackgroundType
import dev.phonk.editor.model.CanvasBackground
import dev.phonk.editor.model.CropConfig
import dev.phonk.editor.model.MaskConfig
import dev.phonk.editor.model.MaskShape
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.SubtitleTrack
import dev.phonk.editor.ui.components.EditorChip
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkSlider
import dev.phonk.editor.ui.components.SectionHeader

/** Small labelled toggle row used by several LibreCuts panels. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

/** Canvas background panel: none / solid colour / image / blurred fill. */
@Composable
fun BackgroundPanel(
    background: CanvasBackground,
    onType: (BackgroundType) -> Unit,
    onColor: (Long) -> Unit,
    onPickImage: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader("Canvas Background")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                BackgroundType.NONE to "None",
                BackgroundType.COLOR to "Color",
                BackgroundType.IMAGE to "Image",
                BackgroundType.BLUR to "Blur",
            ).forEach { (type, label) ->
                EditorChip(label, onClick = { onType(type) }, selected = background.type == type)
            }
        }
        Spacer(Modifier.height(8.dp))
        when (background.type) {
            BackgroundType.COLOR -> {
                val swatches = listOf(
                    0xFF000000L, 0xFFFFFFFFL, 0xFF1020F0L, 0xFFF01020L,
                    0xFF10F020L, 0xFFF0A010L, 0xFFA010F0L, 0xFF101010L,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (argb in swatches) {
                        val sel = background.colorArgb == argb
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(argb.toInt()))
                                .border(
                                    2.dp,
                                    if (sel) scheme.primary else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { onColor(argb) },
                        ) {}
                    }
                }
            }
            BackgroundType.IMAGE -> {
                PhonkButton("Pick background image", onClick = onPickImage, primary = true)
                Text(
                    background.imageUri ?: "No image selected",
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            BackgroundType.BLUR -> {
                Text("Blurred video fill", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            else -> Unit
        }
    }
}

/** Custom crop panel: enable + region sliders over the content rect. */
@Composable
fun CropPanel(
    crop: CropConfig,
    onCrop: (Boolean, Float, Float, Float, Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader("Custom Crop")
        ToggleRow("Enable crop", crop.enabled, onToggle = { enabled ->
            onCrop(enabled, crop.xFraction, crop.yFraction, crop.wFraction, crop.hFraction)
        })
        if (crop.enabled) {
            Text("X: ${"%.0f".format(crop.xFraction * 100)}%", fontSize = 10.sp, color = scheme.onSurfaceVariant)
            PhonkSlider(value = crop.xFraction, onValueChange = { onCrop(true, it, crop.yFraction, crop.wFraction, crop.hFraction) }, valueRange = 0f..0.5f)
            Text("Y: ${"%.0f".format(crop.yFraction * 100)}%", fontSize = 10.sp, color = scheme.onSurfaceVariant)
            PhonkSlider(value = crop.yFraction, onValueChange = { onCrop(true, crop.xFraction, it, crop.wFraction, crop.hFraction) }, valueRange = 0f..0.5f)
            Text("Width: ${"%.0f".format(crop.wFraction * 100)}%", fontSize = 10.sp, color = scheme.onSurfaceVariant)
            PhonkSlider(value = crop.wFraction, onValueChange = { onCrop(true, crop.xFraction, crop.yFraction, it, crop.hFraction) }, valueRange = 0.1f..1f)
            Text("Height: ${"%.0f".format(crop.hFraction * 100)}%", fontSize = 10.sp, color = scheme.onSurfaceVariant)
            PhonkSlider(value = crop.hFraction, onValueChange = { onCrop(true, crop.xFraction, crop.yFraction, crop.wFraction, it) }, valueRange = 0.1f..1f)
        }
    }
}

/** Audio panel: ducking toggle + voice-over track picker. */
@Composable
fun AudioMixPanel(
    ducking: Boolean,
    onDucking: (Boolean) -> Unit,
    hasVoiceOver: Boolean,
    onPickVoiceOver: () -> Unit,
    onClearVoiceOver: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader("Audio Mix")
        ToggleRow("Duck music under voice-over", ducking, onDucking, enabled = hasVoiceOver)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton(
                if (hasVoiceOver) "Replace voice-over" else "Pick voice-over",
                onClick = onPickVoiceOver,
                primary = !hasVoiceOver,
                modifier = Modifier.weight(1f),
            )
            if (hasVoiceOver) {
                PhonkButton("Clear", onClick = onClearVoiceOver, modifier = Modifier.weight(1f))
            }
        }
        Text(
            if (hasVoiceOver) "Voice-over active" else "No voice-over track",
            fontSize = 10.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Per-clip reverse toggle. */
@Composable
fun ReversePanel(
    reversed: Boolean,
    onReversed: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader("Playback")
        ToggleRow("Reverse clip", reversed, onReversed)
    }
}

/** Chroma key + shape mask for the selected image overlay. */
@Composable
fun OverlayKeyPanel(
    layer: OverlayLayer?,
    onChroma: (Int?, Float) -> Unit,
    onMask: (MaskConfig) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val chroma = layer?.chromaKeyColor
    val mask = layer?.mask ?: MaskConfig()
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader("Chroma Key & Mask")
        if (layer == null) {
            Text("Select an image overlay first", fontSize = 12.sp, color = scheme.onSurfaceVariant)
            return@Column
        }
        ToggleRow("Chroma key (green screen)", chroma != null, onToggle = { enabled ->
            onChroma(if (enabled) 0x00FF00 else null, layer.chromaKeySimilarity)
        })
        if (chroma != null) {
            Text("Key colour: green (tune in preview)", fontSize = 10.sp, color = scheme.onSurfaceVariant)
            PhonkSlider(
                value = layer.chromaKeySimilarity,
                onValueChange = { onChroma(chroma.toInt(), it) },
                valueRange = 0f..1f,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Shape mask", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaskShape.entries.forEach { shape ->
                EditorChip(
                    if (shape == MaskShape.NONE) "Off" else shape.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = { onMask(mask.copy(shape = shape)) },
                    selected = mask.shape == shape,
                )
            }
        }
        if (mask.isActive) {
            ToggleRow("Invert mask", mask.inverted, onToggle = { onMask(mask.copy(inverted = it)) })
            Text("Feather: ${"%.0f".format(mask.feather * 100)}%", fontSize = 10.sp, color = scheme.onSurfaceVariant)
            PhonkSlider(value = mask.feather, onValueChange = { onMask(mask.copy(feather = it)) }, valueRange = 0f..1f)
        }
    }
}

/** Subtitle panel: shows cues and lets the user toggle visibility. */
@Composable
fun SubtitlePanel(
    tracks: List<SubtitleTrack>,
    onImport: () -> Unit,
    onToggleTrack: (String, Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader("Subtitles")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton("Import SRT/VTT", onClick = onImport, primary = tracks.isEmpty(), modifier = Modifier.weight(1f))
            if (tracks.isNotEmpty()) {
                PhonkButton("Clear all", onClick = onClear, modifier = Modifier.weight(1f))
            }
        }
        if (tracks.isEmpty()) {
            Text("Import an .srt or .vtt file to add captions.", fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
        }
        tracks.forEach { track ->
            ToggleRow(track.fileName.ifBlank { track.id }, track.visible, onToggle = { onToggleTrack(track.id, it) })
            Text("${track.cues.size} cues", fontSize = 10.sp, color = scheme.onSurfaceVariant)
            track.cues.take(3).forEach { cue ->
                Text(
                    "${cue.startMs / 1000}.${(cue.startMs % 1000) / 100}-${cue.endMs / 1000}.${(cue.endMs % 1000) / 100}  ${cue.text}",
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (track.cues.size > 3) {
                Text("+${track.cues.size - 3} more", fontSize = 10.sp, color = scheme.primary)
            }
        }
    }
}
