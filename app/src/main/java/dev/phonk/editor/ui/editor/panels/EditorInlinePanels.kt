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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.GradeParam
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.ui.components.EditorChip
import dev.phonk.editor.ui.components.EditorTokens
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkSlider
import dev.phonk.editor.ui.components.SectionHeader

@Composable
fun VolumePanel(
    volume: Float,
    muted: Boolean,
    onVolume: (Float) -> Unit,
    onMuted: (Boolean) -> Unit,
) {
    Column {
        SectionHeader("Volume")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PhonkSlider(value = volume, onValueChange = onVolume, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            Text("%d%%".format((volume * 100).toInt()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            PhonkButton(if (muted) "Unmute" else "Mute", onClick = { onMuted(!muted) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun FadeInPanel(fadeInMs: Long, onFadeIn: (Long) -> Unit) {
    Column {
        SectionHeader("Fade In")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            listOf(0L to "0ms", 250L to "250ms", 500L to "500ms", 1000L to "1000ms").forEach { (ms, label) ->
                EditorChip(label, onClick = { onFadeIn(ms) }, selected = fadeInMs == ms)
            }
        }
    }
}

@Composable
fun FadeOutPanel(fadeOutMs: Long, onFadeOut: (Long) -> Unit) {
    Column {
        SectionHeader("Fade Out")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
            listOf(0L to "0ms", 250L to "250ms", 500L to "500ms", 1000L to "1000ms").forEach { (ms, label) ->
                EditorChip(label, onClick = { onFadeOut(ms) }, selected = fadeOutMs == ms)
            }
        }
    }
}

@Composable
fun PitchPanel(pitch: Float, onPitch: (Float) -> Unit) {
    Column {
        SectionHeader("Pitch")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PhonkSlider(value = pitch, onValueChange = onPitch, valueRange = 0.5f..2f, modifier = Modifier.weight(1f))
            Text("%.2fx".format(pitch), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
fun FontPanel(layer: TextLayer?, onSize: (String, Float) -> Unit) {
    val id = layer?.id
    Column {
        SectionHeader("Font Size")
        if (id != null && layer != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PhonkSlider(value = layer.fontSize, onValueChange = { onSize(id, it) }, valueRange = 8f..120f, modifier = Modifier.weight(1f))
                Text("${layer.fontSize.toInt()}pt", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
            }
        } else {
            Text("Select a text layer first", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ColorPanel(layer: TextLayer?, onColor: (String, Long) -> Unit) {
    val id = layer?.id
    val currentColor = layer?.colorArgb ?: 0xFFFFFFFFL
    Column {
        SectionHeader("Text Color")
        if (id != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
                listOf(
                    0xFFFFFFFFL to "White", 0xFF000000L to "Black", 0xFFA855F7L to "Purple",
                    0xFFFF6B6BL to "Red", 0xFF39D7B1L to "Teal", 0xFFFB923CL to "Orange",
                    0xFF3B82F6L to "Blue", 0xFF22C55EL to "Green",
                ).forEach { (color, label) ->
                    val sel = currentColor == color
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                        .clip(RoundedCornerShape(EditorTokens.CornerButton))
                        .clickable { onColor(id, color) }
                        .padding(EditorTokens.Space4)) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(color.toInt())).border(1.5.dp, if (sel) MaterialTheme.colorScheme.primary else colorResource(R.color.border_default), CircleShape))
                        Text(label, fontSize = EditorTokens.FontLabel, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text("Select a text layer first", fontSize = EditorTokens.FontLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TextAnimationPanel(layer: TextLayer?, onAnimation: (String, String) -> Unit) {
    val id = layer?.id
    Column {
        SectionHeader("Text Animation")
        if (id != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EditorTokens.Space8)) {
                listOf("Fade", "Slide", "Zoom", "Typewriter", "Bounce", "Glitch").forEach { anim ->
                    EditorChip(anim, onClick = { onAnimation(id, anim) }, selected = layer?.animation == anim)
                }
            }
        } else {
            Text("Select a text layer first", fontSize = EditorTokens.FontLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OpacityPanel(item: dev.phonk.editor.model.OverlayItem?, onOpacity: (String, Float) -> Unit) {
    val id = item?.id
    Column {
        SectionHeader("Opacity")
        if (id != null && item != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PhonkSlider(value = item.opacity, onValueChange = { onOpacity(id, it) }, valueRange = 0.05f..1f, modifier = Modifier.weight(1f))
                Text("%d%%".format((item.opacity * 100).toInt()), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
            }
        } else {
            Text("Select an overlay first", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GradeSlidersPanel(grade: ColorGrade, onGrade: (GradeParam, Float) -> Unit, onResetAll: () -> Unit) {
    Column {
        SectionHeader("Adjust")
        listOf(
            GradeParam.BRIGHTNESS to "Brightness",
            GradeParam.CONTRAST to "Contrast",
            GradeParam.SATURATION to "Saturation",
            GradeParam.EXPOSURE to "Exposure",
            GradeParam.TEMPERATURE to "Temperature",
            GradeParam.SHARPNESS to "Sharpness",
        ).forEach { (param, label) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                PhonkSlider(value = grade.get(param), onValueChange = { onGrade(param, it) }, valueRange = param.range, modifier = Modifier.weight(1f))
                Text("%+.2f".format(grade.get(param).toDouble()), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
            }
        }
        PhonkButton("Reset All", onClick = onResetAll, modifier = Modifier.padding(top = 4.dp))
    }
}
