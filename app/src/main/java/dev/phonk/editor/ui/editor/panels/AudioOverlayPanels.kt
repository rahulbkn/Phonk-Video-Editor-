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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.ui.components.PhonkButton
import dev.phonk.editor.ui.components.PhonkSlider
import dev.phonk.editor.ui.components.SectionHeader

@Composable
fun AudioTrackPanel(
    volume: Float,
    muted: Boolean,
    fadeInMs: Long,
    fadeOutMs: Long,
    pitch: Float,
    onVolume: (Float) -> Unit,
    onMuted: (Boolean) -> Unit,
    onFadeIn: (Long) -> Unit,
    onFadeOut: (Long) -> Unit,
    onPitch: (Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_audio))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.text_opacity), fontSize = 11.sp, color = scheme.onSurface, modifier = Modifier.width(70.dp))
            PhonkSlider(value = volume, onValueChange = onVolume, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            Text("%d".format((volume * 100).toInt()), fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.audio_fade), fontSize = 10.sp, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FadeChip("0ms", fadeInMs == 0L, { onFadeIn(0L) })
            FadeChip("250ms", fadeInMs == 250L, { onFadeIn(250L) })
            FadeChip("500ms", fadeInMs == 500L, { onFadeIn(500L) })
            FadeChip("1000ms", fadeInMs == 1000L, { onFadeIn(1000L) })
        }
        Text(stringResource(R.string.audio_fade_out), fontSize = 10.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FadeChip("0ms", fadeOutMs == 0L, { onFadeOut(0L) })
            FadeChip("250ms", fadeOutMs == 250L, { onFadeOut(250L) })
            FadeChip("500ms", fadeOutMs == 500L, { onFadeOut(500L) })
            FadeChip("1000ms", fadeOutMs == 1000L, { onFadeOut(1000L) })
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionChip(stringResource(R.string.mute), muted) { onMuted(!muted) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pitch", fontSize = 11.sp, color = scheme.onSurface, modifier = Modifier.width(70.dp))
            PhonkSlider(value = pitch, onValueChange = onPitch, valueRange = 0.5f..2f, modifier = Modifier.weight(1f))
            Text("%.2f".format(pitch), fontSize = 11.sp, color = scheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
        }
    }
}

@Composable
private fun FadeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.25f) else scheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) scheme.primary else scheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ActionChip(label: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) scheme.error.copy(alpha = 0.25f) else scheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, if (active) scheme.error else scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 11.sp, color = if (active) scheme.error else scheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OverlayPanel(
    items: List<OverlayItem>,
    selectedId: String?,
    onAddImage: () -> Unit,
    onAddSymbol: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLock: (String, Boolean) -> Unit,
    onHide: (String, Boolean) -> Unit,
    onFront: (String) -> Unit,
    onBack: (String) -> Unit,
    onEditText: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        SectionHeader(stringResource(R.string.tool_overlay))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(stringResource(R.string.import_photos), "Image", "Image"),
                Triple("Sticker", "Sticker", "★"),
                Triple("Emoji", "Emoji", "♥"),
                Triple("Shape", "Shape", "●"),
            ).forEach { (label, kind, symbol) ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(72.dp)
                        .height(52.dp)
                        .background(scheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .clickable {
                            if (kind == "Image") onAddImage() else onAddSymbol(symbol, label)
                        },
                ) {
                    Text(label, fontSize = 10.sp, color = scheme.onSurface, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(stringResource(R.string.empty_no_effects), fontSize = 11.sp, color = scheme.onSurfaceVariant)
        } else {
            items.sortedBy { it.zIndex }.forEach { item ->
                OverlayRow(
                    item = item,
                    selected = item.id == selectedId,
                    onSelect = { onSelect(item.id) },
                    onDuplicate = { onDuplicate(item.id) },
                    onRemove = { onRemove(item.id) },
                    onLock = { onLock(item.id, !item.locked) },
                    onHide = { onHide(item.id, !item.visible) },
                    onFront = { onFront(item.id) },
                    onBack = { onBack(item.id) },
                    onEditText = if (item is TextLayer) { { onEditText(item.id) } } else null,
                )
            }
        }
    }
}

@Composable
private fun OverlayRow(
    item: OverlayItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onLock: () -> Unit,
    onHide: () -> Unit,
    onFront: () -> Unit,
    onBack: () -> Unit,
    onEditText: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "[${item.type}] ${item.label.ifBlank { item.type }}",
                fontSize = 11.sp,
                color = if (item.visible) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                "${item.startMs / 1000}.${(item.startMs % 1000) / 100}s-${item.endMs / 1000}.${(item.endMs % 1000) / 100}s" +
                    if (item.locked) "  🔒" else "",
                fontSize = 9.sp,
                color = scheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            OverlayActionChip("⧉", stringResource(R.string.overlay_duplicate), onClick = onDuplicate)
            OverlayActionChip(if (item.locked) "🔒" else "🔓", stringResource(if (item.locked) R.string.overlay_unlock else R.string.overlay_lock), onClick = onLock)
            OverlayActionChip(if (item.visible) "👁" else "🚫", stringResource(if (item.visible) R.string.overlay_hide else R.string.overlay_show), onClick = onHide)
            OverlayActionChip("▲", stringResource(R.string.overlay_front), onClick = onFront)
            OverlayActionChip("▼", stringResource(R.string.overlay_back), onClick = onBack)
            if (onEditText != null) {
                OverlayActionChip("✎", stringResource(R.string.overlay_edit), onClick = onEditText)
            }
            OverlayActionChip("✕", stringResource(R.string.tool_delete), danger = true, onClick = onRemove)
        }
    }
}

@Composable
private fun OverlayActionChip(glyph: String, desc: String, onClick: () -> Unit, danger: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = desc }
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Text(
            glyph,
            fontSize = 12.sp,
            color = if (danger) scheme.error else scheme.onSurface,
        )
    }
}
