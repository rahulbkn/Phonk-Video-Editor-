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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.editor.CutPattern
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.ui.components.EditorChip
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkSlider
import dev.phonk.editor.ui.components.SectionHeader

/** Sticker panel — add emoji/symbol stickers as text overlays. */
@Composable
fun StickerPanel(
    onAddSticker: (String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val stickers = listOf(
        "★" to "Star",
        "♥" to "Heart",
        "🔥" to "Fire",
        "💎" to "Diamond",
        "🎵" to "Music",
        "🎶" to "Notes",
        "👏" to "Clap",
        "💯" to "100",
        "⚡" to "Lightning",
        "✨" to "Sparkle",
        "🎤" to "Mic",
        "🎧" to "Headphones",
        "😎" to "Cool",
        "🤯" to "Mind Blown",
        "😱" to "Scream",
        "🔥" to "Fire2",
    )
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_sticker))
        // Grid of sticker emojis
        stickers.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (symbol, label) ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { onAddSticker(symbol, label) },
                    ) {
                        Text(symbol, fontSize = 24.sp)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** More panel — less frequently used tools: Speed, Beat, Transition. */
@Composable
fun MorePanel(
    speed: Float,
    onSpeed: (Float) -> Unit,
    onPreset: (SpeedPreset) -> Unit,
    bpm: Double,
    onDetect: () -> Unit,
    onSubdivision: (Double) -> Unit,
    onAddDrop: () -> Unit,
    onRemoveDrop: () -> Unit,
    dropCount: Int,
    onPattern: (CutPattern) -> Unit,
    transitionDurationMs: Long,
    onTransitionDuration: (Long) -> Unit,
    onTransitionSelect: (String?) -> Unit,
    currentTransition: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        // Speed section
        SectionHeader(stringResource(R.string.tool_speed))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val presets = listOf(
                Pair(stringResource(R.string.speed_normal), SpeedPreset.NORMAL),
                Pair(stringResource(R.string.speed_slow_fast), SpeedPreset.SLOW),
                Pair(stringResource(R.string.speed_fast_slow), SpeedPreset.FAST),
                Pair(stringResource(R.string.speed_beat_drop), SpeedPreset.BEAT_DROP),
                Pair(stringResource(R.string.speed_hyper), SpeedPreset.HYPER),
            )
            presets.forEach { (label, preset) ->
                EditorChip(label, onClick = { onPreset(preset) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.speed_value, "%.2f".format(speed)),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
        PhonkSlider(value = speed, onValueChange = onSpeed, valueRange = 0.25f..4f)

        Spacer(Modifier.height(12.dp))

        // Beat section
        SectionHeader(stringResource(R.string.tool_beat))
        if (bpm > 0) {
            Text(
                stringResource(R.string.beat_bpm, bpm),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
        } else {
            PhonkButton(
                stringResource(R.string.beat_detect),
                onClick = onDetect,
                primary = true,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                stringResource(R.string.beat_detect_first),
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.beat_auto_cut), fontSize = 10.sp, color = scheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                Pair(stringResource(R.string.beat_preset_1_4), 0.25),
                Pair(stringResource(R.string.beat_preset_1_2), 0.5),
                Pair(stringResource(R.string.beat_preset_1), 1.0),
                Pair(stringResource(R.string.beat_preset_2), 2.0),
                Pair(stringResource(R.string.beat_preset_4), 4.0),
                Pair(stringResource(R.string.beat_preset_8), 8.0),
            ).forEach { (label, sub) ->
                EditorChip(label, onClick = { onSubdivision(sub) }, enabled = bpm > 0)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

        Spacer(Modifier.height(12.dp))

        // Transition section
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
                    val selected = currentTransition == label
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) scheme.primary.copy(alpha = 0.25f)
                                else scheme.surfaceVariant.copy(alpha = 0.7f),
                            )
                            .border(
                                1.dp,
                                if (selected) scheme.primary else scheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onTransitionSelect(if (selected) null else label) },
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
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
            value = transitionDurationMs.toFloat(),
            onValueChange = { onTransitionDuration(it.toLong()) },
            valueRange = 0f..3000f,
        )
        Text(
            stringResource(R.string.tr_duration) + ": ${transitionDurationMs}ms",
            fontSize = 10.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}
