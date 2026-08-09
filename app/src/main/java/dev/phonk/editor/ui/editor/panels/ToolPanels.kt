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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.editor.CutPattern
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkSlider
import dev.phonk.editor.ui.components.SectionHeader

private data class NamedEffect(val kind: EffectKind, val labelRes: Int)

enum class SpeedPreset {
    NORMAL,
    SLOW,
    FAST,
    BEAT_DROP,
    HYPER,
}

@Composable
fun EffectsPanel(onAdd: (EffectKind) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_effects))
        EffectCategory(stringResource(R.string.fx_category_beat)) {
            listOf(
                NamedEffect(EffectKind.FLASH, R.string.fx_beat_flash),
                NamedEffect(EffectKind.SHAKE, R.string.fx_beat_shake),
                NamedEffect(EffectKind.ZOOM, R.string.fx_beat_zoom),
                NamedEffect(EffectKind.BLUR, R.string.fx_beat_blur),
                NamedEffect(EffectKind.GLITCH, R.string.fx_beat_glitch),
                NamedEffect(EffectKind.RGBSPLIT, R.string.fx_beat_rgb),
            ).forEach { e -> EffectChip(e.labelRes, e.kind, onAdd) }
        }
        Spacer(Modifier.height(6.dp))
        EffectCategory(stringResource(R.string.fx_category_visual)) {
            listOf(
                NamedEffect(EffectKind.BLUR, R.string.fx_blur),
                NamedEffect(EffectKind.GLITCH, R.string.fx_glitch),
                NamedEffect(EffectKind.FADE, R.string.fx_film),
                NamedEffect(EffectKind.BRIGHTNESS, R.string.adj_brightness),
                NamedEffect(EffectKind.CONTRAST, R.string.adj_contrast),
            ).forEach { e -> EffectChip(e.labelRes, e.kind, onAdd) }
        }
        Spacer(Modifier.height(6.dp))
        EffectCategory(stringResource(R.string.fx_category_anime)) {
            listOf(
                NamedEffect(EffectKind.FLASH, R.string.fx_impact),
                NamedEffect(EffectKind.SHAKE, R.string.fx_screen_shake),
                NamedEffect(EffectKind.ZOOM, R.string.fx_speed_lines),
            ).forEach { e -> EffectChip(e.labelRes, e.kind, onAdd) }
        }
    }
}

@Composable
private fun EffectCategory(title: String, content: @Composable () -> Unit) {
    Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
private fun EffectChip(labelRes: Int, kind: EffectKind, onAdd: (EffectKind) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, scheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onAdd(kind) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(stringResource(labelRes), fontSize = 11.sp, color = scheme.onSurface)
    }
}

@Composable
fun TransitionsPanel(
    durationMs: Long,
    onDuration: (Long) -> Unit,
    onSelect: (String?) -> Unit,
    current: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_transition))
        listOf(
            stringResource(R.string.tr_category_basic),
            stringResource(R.string.tr_category_smooth),
            stringResource(R.string.tr_category_zoom),
            stringResource(R.string.tr_category_glitch),
            stringResource(R.string.tr_category_flash),
            stringResource(R.string.tr_category_beat),
        ).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    val selected = current == label
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) scheme.primary.copy(alpha = 0.25f) else scheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { onSelect(if (selected) null else label) },
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(4.dp))
        SectionHeader(stringResource(R.string.tr_duration))
        PhonkSlider(
            value = durationMs.toFloat(),
            onValueChange = { onDuration(it.toLong()) },
            valueRange = 100f..1500f,
        )
        Text(stringResource(R.string.tr_duration) + ": ${durationMs}ms", fontSize = 10.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
fun FiltersPanel(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_filters))
        SliderRow(stringResource(R.string.adj_brightness), brightness) { onBrightness(it) }
        SliderRow(stringResource(R.string.adj_contrast), contrast) { onContrast(it) }
        SliderRow(stringResource(R.string.adj_saturation), saturation) { onSaturation(it) }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.filters_note), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.filters_presets))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PresetDef(R.string.preset_dark_phonk, -0.2f, 0.3f, 0.2f),
                PresetDef(R.string.preset_purple_night, -0.1f, 0.25f, 0.6f),
                PresetDef(R.string.preset_cinematic, -0.05f, 0.15f, -0.25f),
                PresetDef(R.string.preset_vhs, 0.05f, 0.1f, -0.4f),
                PresetDef(R.string.preset_cyber, 0f, 0.2f, 0.7f),
                PresetDef(R.string.preset_mono, 0f, 0.1f, -1f),
            ).forEach { p ->
                PresetChip(p.labelRes, p.brightness, p.contrast, p.saturation,
                    onBrightness = onBrightness, onContrast = onContrast, onSaturation = onSaturation)
            }
        }
    }
}

private data class PresetDef(val labelRes: Int, val brightness: Float, val contrast: Float, val saturation: Float)

@Composable
private fun SliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        PhonkSlider(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PresetChip(
    labelRes: Int,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(80.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.8f), scheme.surface)))
            .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable {
                onBrightness(brightness)
                onContrast(contrast)
                onSaturation(saturation)
            },
    ) {
        Text(
            stringResource(labelRes),
            fontSize = 9.sp,
            color = scheme.onPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp),
        )
    }
}

@Composable
fun SpeedPanel(
    speed: Float,
    onSpeed: (Float) -> Unit,
    onPreset: (SpeedPreset) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_speed))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf(
                Pair(stringResource(R.string.speed_normal), SpeedPreset.NORMAL),
                Pair(stringResource(R.string.speed_slow_fast), SpeedPreset.SLOW),
                Pair(stringResource(R.string.speed_fast_slow), SpeedPreset.FAST),
                Pair(stringResource(R.string.speed_beat_drop), SpeedPreset.BEAT_DROP),
                Pair(stringResource(R.string.speed_hyper), SpeedPreset.HYPER),
            )
            presets.forEach { (label, preset) ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable { onPreset(preset) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(label, fontSize = 11.sp, color = scheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.speed_value, "%.2f".format(speed)), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
        PhonkSlider(value = speed, onValueChange = onSpeed, valueRange = 0.25f..4f)
    }
}

@Composable
fun BeatPanel(
    bpm: Double,
    onDetect: () -> Unit,
    onSubdivision: (Double) -> Unit,
    onAddDrop: () -> Unit,
    onRemoveDrop: () -> Unit,
    dropCount: Int,
    onPattern: (CutPattern) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_beat))
        if (bpm > 0) {
            Text(stringResource(R.string.beat_bpm, bpm), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
        } else {
            PhonkButton(stringResource(R.string.beat_detect), onClick = onDetect, primary = true, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.beat_auto_cut), fontSize = 10.sp, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Pair(stringResource(R.string.beat_preset_1_4), 0.25),
                Pair(stringResource(R.string.beat_preset_1_2), 0.5),
                Pair(stringResource(R.string.beat_preset_1), 1.0),
                Pair(stringResource(R.string.beat_preset_2), 2.0),
                Pair(stringResource(R.string.beat_preset_4), 4.0),
                Pair(stringResource(R.string.beat_preset_8), 8.0),
            ).forEach { (label, sub) ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable(enabled = bpm > 0) { onSubdivision(sub) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(label, fontSize = 11.sp, color = if (bpm > 0) scheme.onSurface else scheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.beat_cut_patterns), fontSize = 10.sp, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CutPattern.entries.forEach { cp ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable(enabled = bpm > 0) { onPattern(cp) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(cp.labelRes), fontSize = 11.sp, color = if (bpm > 0) scheme.onSurface else scheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton(
                label = stringResource(R.string.beat_add_drop) + " ($dropCount)",
                onClick = onAddDrop,
                modifier = Modifier.weight(1f),
            )
            PhonkButton(
                label = stringResource(R.string.beat_remove_drop),
                onClick = onRemoveDrop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun TextPanel(
    layers: List<TextLayer>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_text))
        PhonkButton(stringResource(R.string.text_add), onClick = onAdd, primary = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        if (layers.isEmpty()) {
            Text(
                stringResource(R.string.empty_no_text),
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            layers.forEach { layer ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "\"${layer.text}\"  ${layer.startMs / 1000}s",
                        fontSize = 11.sp,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.tool_delete),
                        fontSize = 11.sp,
                        color = scheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRemove(layer.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MediaPanel(
    onImportVideo: () -> Unit,
    onImportAudio: () -> Unit,
    onImportPhoto: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_media))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaTile(stringResource(R.string.import_videos), scheme.primary, Modifier.weight(1f), onImportVideo)
            MediaTile(stringResource(R.string.import_audio), scheme.tertiary, Modifier.weight(1f), onImportAudio)
            MediaTile(stringResource(R.string.import_photos), scheme.secondary, Modifier.weight(1f), onImportPhoto)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.media_note), fontSize = 10.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun MediaTile(label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
    }
}

@Composable
fun AdjustPanel(
    onSplit: () -> Unit,
    onTrim: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_adjust))
        SliderRow(stringResource(R.string.adj_brightness), brightness) { onBrightness(it) }
        SliderRow(stringResource(R.string.adj_contrast), contrast) { onContrast(it) }
        SliderRow(stringResource(R.string.adj_saturation), saturation) { onSaturation(it) }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton(stringResource(R.string.tool_split), onClick = onSplit, modifier = Modifier.weight(1f))
            PhonkButton(stringResource(R.string.tool_trim), onClick = onTrim, modifier = Modifier.weight(1f))
            PhonkButton(stringResource(R.string.tool_delete), onClick = onDelete, modifier = Modifier.weight(1f))
            PhonkButton(stringResource(R.string.tool_duplicate), onClick = onDuplicate, modifier = Modifier.weight(1f))
        }
    }
}
