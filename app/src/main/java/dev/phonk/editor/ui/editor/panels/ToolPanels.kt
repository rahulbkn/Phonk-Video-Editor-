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
import androidx.compose.material3.Switch
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
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.GradeParam
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
fun EffectsPanel(
    onAdd: (EffectKind) -> Unit,
    hasClipEffect: Boolean,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_effects))
        if (hasClipEffect) {
            PhonkButton(
                label = stringResource(R.string.fx_remove_effect),
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
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
    grade: ColorGrade,
    keyframesEnabled: Boolean,
    keyframeCount: Int,
    beatSync: Boolean,
    beatSyncStrength: Float,
    onGrade: (GradeParam, Float) -> Unit,
    onResetAll: () -> Unit,
    onAddKeyframe: () -> Unit,
    onClearKeyframes: () -> Unit,
    onKeyframesEnabled: (Boolean) -> Unit,
    onBeatSync: (Boolean) -> Unit,
    onBeatSyncStrength: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val labelRes = mapOf(
        GradeParam.BRIGHTNESS to R.string.adj_brightness,
        GradeParam.CONTRAST to R.string.adj_contrast,
        GradeParam.SATURATION to R.string.adj_saturation,
        GradeParam.EXPOSURE to R.string.adj_exposure,
        GradeParam.TEMPERATURE to R.string.adj_temperature,
        GradeParam.TINT to R.string.adj_tint,
        GradeParam.HIGHLIGHTS to R.string.adj_highlights,
        GradeParam.SHADOWS to R.string.adj_shadows,
        GradeParam.FADE to R.string.adj_fade,
        GradeParam.SHARPNESS to R.string.adj_sharpness,
        GradeParam.BLUR to R.string.adj_blur,
        GradeParam.VIGNETTE to R.string.adj_vignette,
        GradeParam.GRAIN to R.string.adj_grain,
    )
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_filters))
        GradeParam.entries.forEach { param ->
            GradeSliderRow(
                label = stringResource(labelRes.getValue(param)),
                value = grade.get(param),
                range = param.range,
                onValueChange = { onGrade(param, it) },
                onReset = { onGrade(param, 0f) },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton(stringResource(R.string.filters_reset_all), onClick = onResetAll, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.filters_note), fontSize = 10.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.filters_presets))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { p ->
                PresetChip(p.labelRes, p.grade) { g ->
                    PRESETS_APPLY.forEach { param -> onGrade(param, g.get(param)) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionHeader(stringResource(R.string.keyframes))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.keyframes_animate), modifier = Modifier.weight(1f), fontSize = 12.sp, color = scheme.onSurface)
            if (keyframeCount > 0) {
                Text("$keyframeCount", fontSize = 11.sp, color = scheme.primary, modifier = Modifier.padding(end = 6.dp))
            }
            Switch(checked = keyframesEnabled, onCheckedChange = onKeyframesEnabled)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton(stringResource(R.string.keyframes_add), onClick = onAddKeyframe, modifier = Modifier.weight(1f))
            PhonkButton(stringResource(R.string.keyframes_clear), onClick = onClearKeyframes, modifier = Modifier.weight(1f), enabled = keyframeCount > 0)
        }
        Spacer(Modifier.height(10.dp))
        SectionHeader(stringResource(R.string.beat_sync))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.beat_sync_live),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = scheme.onSurface,
            )
            Switch(checked = beatSync, onCheckedChange = onBeatSync)
        }
        if (beatSync) {
            Spacer(Modifier.height(4.dp))
            GradeSliderRow(
                label = stringResource(R.string.beat_sync_strength),
                value = beatSyncStrength,
                range = 0f..1f,
                onValueChange = onBeatSyncStrength,
                onReset = { onBeatSyncStrength(0.5f) },
            )
        }
    }
}

private data class PresetDef(val labelRes: Int, val grade: ColorGrade)

private val PRESETS = listOf(
    PresetDef(R.string.preset_dark_phonk, ColorGrade(contrast = 0.3f, saturation = 0.2f, brightness = -0.15f)),
    PresetDef(R.string.preset_purple_night, ColorGrade(contrast = 0.25f, saturation = 0.6f, temperature = 0.35f, brightness = -0.05f)),
    PresetDef(R.string.preset_cinematic, ColorGrade(contrast = 0.15f, saturation = -0.25f, fade = 0.08f)),
    PresetDef(R.string.preset_vhs, ColorGrade(brightness = 0.05f, contrast = 0.1f, saturation = -0.4f, grain = 0.5f, vignette = 0.35f)),
    PresetDef(R.string.preset_cyber, ColorGrade(contrast = 0.2f, saturation = 0.7f, temperature = -0.3f, tint = 0.4f)),
    PresetDef(R.string.preset_mono, ColorGrade(saturation = -1f, contrast = 0.1f)),
    PresetDef(R.string.preset_high_contrast, ColorGrade(contrast = 0.55f, brightness = 0.05f, shadows = -0.2f)),
    PresetDef(R.string.preset_deep_shadow, ColorGrade(contrast = 0.4f, brightness = -0.25f, shadows = -0.3f, vignette = 0.6f)),
)

/** All grade parameters a preset chip should overwrite when applied. */
private val PRESETS_APPLY = GradeParam.entries

@Composable
private fun GradeSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        PhonkSlider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        Text(
            String.format("%+.2f", value.toDouble()),
            fontSize = 10.sp,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(46.dp),
        )
        Text(
            "↺",
            fontSize = 13.sp,
            color = if (kotlin.math.abs(value) < 0.001f) scheme.outline.copy(alpha = 0.4f) else scheme.primary,
            modifier = Modifier
                .padding(start = 6.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = kotlin.math.abs(value) >= 0.001f, onClick = onReset)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PresetChip(labelRes: Int, grade: ColorGrade, onApply: (ColorGrade) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(82.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.8f), scheme.surface)))
            .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { onApply(grade) },
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
        GradeSliderRow(stringResource(R.string.adj_brightness), brightness, -1f..1f, onBrightness) { onBrightness(0f) }
        GradeSliderRow(stringResource(R.string.adj_contrast), contrast, -1f..1f, onContrast) { onContrast(0f) }
        GradeSliderRow(stringResource(R.string.adj_saturation), saturation, -1f..1f, onSaturation) { onSaturation(0f) }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhonkButton(stringResource(R.string.tool_split), onClick = onSplit, modifier = Modifier.weight(1f))
            PhonkButton(stringResource(R.string.tool_trim), onClick = onTrim, modifier = Modifier.weight(1f))
            PhonkButton(stringResource(R.string.tool_delete), onClick = onDelete, modifier = Modifier.weight(1f))
            PhonkButton(stringResource(R.string.tool_duplicate), onClick = onDuplicate, modifier = Modifier.weight(1f))
        }
    }
}
