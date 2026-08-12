package dev.phonk.editor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.settings.SettingsManager

// ============================================================
// GENERIC OPTION SHEET
// ============================================================

data class SettingsOption(val label: String, val value: String)

/**
 * Always-dark single-selection bottom sheet. Rows show a check mark for the
 * currently selected value. Shared by language, appearance, export quality,
 * frame rate, aspect ratio and preview quality pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOptionSheet(
    title: String,
    options: List<SettingsOption>,
    selected: String,
    onSelect: (SettingsOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = settingsPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.card,
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
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            Spacer(Modifier.height(12.dp))
            options.forEach { option ->
                val isSelected = option.value == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = option.label,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) palette.primaryBright else palette.text,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = palette.primaryBright,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// SPECIFIC PICKERS
// ============================================================

@Composable
fun LanguageBottomSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        SettingsOption(stringResource(R.string.language_system), SettingsManager.LANG_SYSTEM),
        SettingsOption(stringResource(R.string.language_english), SettingsManager.LANG_EN),
        SettingsOption(stringResource(R.string.language_hindi), SettingsManager.LANG_HI),
    )
    SettingsOptionSheet(
        title = stringResource(R.string.settings_select_language),
        options = options,
        selected = selected,
        onSelect = { onSelect(it.value) },
        onDismiss = onDismiss,
    )
}

@Composable
fun AppearanceBottomSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        SettingsOption(stringResource(R.string.theme_system), SettingsManager.THEME_SYSTEM),
        SettingsOption(stringResource(R.string.theme_light), SettingsManager.THEME_LIGHT),
        SettingsOption(stringResource(R.string.theme_dark), SettingsManager.THEME_DARK),
    )
    SettingsOptionSheet(
        title = stringResource(R.string.settings_select_appearance),
        options = options,
        selected = selected,
        onSelect = { onSelect(it.value) },
        onDismiss = onDismiss,
    )
}

@Composable
fun ExportQualityBottomSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf("720p", "1080p", "1440p", "4K").map { SettingsOption(it, it) }
    SettingsOptionSheet(
        title = stringResource(R.string.settings_select_export_quality),
        options = options,
        selected = selected,
        onSelect = { onSelect(it.value) },
        onDismiss = onDismiss,
    )
}

@Composable
fun FrameRateBottomSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(24, 30, 60).map { SettingsOption("$it FPS", "$it") }
    SettingsOptionSheet(
        title = stringResource(R.string.settings_select_frame_rate),
        options = options,
        selected = selected.toString(),
        onSelect = { onSelect(it.value.toIntOrNull() ?: 30) },
        onDismiss = onDismiss,
    )
}

@Composable
fun AspectRatioBottomSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf("1:1", "4:5", "9:16", "16:9", "2.35:1").map { SettingsOption(it, it) }
    SettingsOptionSheet(
        title = stringResource(R.string.settings_select_aspect_ratio),
        options = options,
        selected = selected,
        onSelect = { onSelect(it.value) },
        onDismiss = onDismiss,
    )
}

@Composable
fun PreviewQualityBottomSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        SettingsOption(stringResource(R.string.settings_preview_quality_auto_label), SettingsManager.PREVIEW_QUALITY_AUTO),
        SettingsOption(stringResource(R.string.settings_preview_quality_low_label), SettingsManager.PREVIEW_QUALITY_LOW),
        SettingsOption(stringResource(R.string.settings_preview_quality_medium_label), SettingsManager.PREVIEW_QUALITY_MEDIUM),
        SettingsOption(stringResource(R.string.settings_preview_quality_high_label), SettingsManager.PREVIEW_QUALITY_HIGH),
    )
    SettingsOptionSheet(
        title = stringResource(R.string.settings_preview_quality),
        options = options,
        selected = selected,
        onSelect = { onSelect(it.value) },
        onDismiss = onDismiss,
    )
}

// ============================================================
// DIALOGS
// ============================================================

/** Always-dark confirmation dialog used for destructive/clear actions. */
@Composable
fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val palette = settingsPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = palette.textSecondary,
            )
        },
        confirmButton = {
            Text(
                text = confirmLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (destructive) palette.danger else palette.primaryBright,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
        dismissButton = {
            Text(
                text = stringResource(R.string.cancel),
                fontSize = 14.sp,
                color = palette.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
    )
}

@Composable
fun ClearCacheDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SettingsConfirmDialog(
        title = stringResource(R.string.dialog_clear_cache_title),
        message = stringResource(R.string.dialog_clear_cache_message),
        confirmLabel = stringResource(R.string.clear),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = false,
    )
}

@Composable
fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SettingsConfirmDialog(
        title = stringResource(R.string.dialog_delete_account_title),
        message = stringResource(R.string.dialog_delete_account_message),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}

/** Always-dark informational dialog with a single "Got it" button. */
@Composable
fun SettingsInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    val palette = settingsPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = palette.textSecondary,
            )
        },
        confirmButton = {
            Text(
                text = stringResource(R.string.pro_billing_ok),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryBright,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
    )
}
