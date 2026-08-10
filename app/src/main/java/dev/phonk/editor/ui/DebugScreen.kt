package dev.phonk.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.components.CornerCard
import dev.phonk.editor.ui.components.PhonkIconButton

/**
 * Developer-only diagnostics screen. Hidden from normal users:
 *  - not reachable from a launcher/home entry point;
 *  - only opened from Settings after the hidden tap-gesture has revealed the
 *    Developer card and raised [SettingsManager.devRevealed];
 *  - turning [SettingsManager.devRevealed] back off removes every dev entry.
 * The master switch toggles [SettingsManager.debugMode], which drives the
 * editor-only overlay diagnostics (font, alignment, layers, playhead).
 */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val debugMode = SettingsManager.debugMode
    val devRevealed = SettingsManager.devRevealed

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhonkIconButton(icon = Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back), onClick = onBack)
            Text(
                stringResource(R.string.debug_screen_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            DebugCard(titleRes = R.string.debug_overlays, icon = Icons.Filled.BugReport) {
                DebugSwitchRow(
                    icon = Icons.Filled.Visibility,
                    label = stringResource(R.string.debug_overlays_enabled),
                    checked = debugMode,
                    onClick = { SettingsManager.setDebugMode(context, !debugMode) },
                )
                Text(
                    stringResource(R.string.debug_overlays_hint),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            DebugCard(titleRes = R.string.debug_dev_entries, icon = Icons.Filled.Lock) {
                DebugSwitchRow(
                    icon = Icons.Filled.Lock,
                    label = stringResource(R.string.debug_show_dev_entries),
                    checked = devRevealed,
                    onClick = { SettingsManager.setDevRevealed(context, !devRevealed) },
                )
                Text(
                    stringResource(R.string.debug_dev_entries_hint),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DebugCard(titleRes: Int, icon: ImageVector, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CornerCard)).background(scheme.surface).padding(vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(titleRes).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = scheme.primary,
            )
        }
        content()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DebugSwitchRow(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(if (checked) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.6f)),
        ) {
            Icon(icon, contentDescription = null, tint = if (checked) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.primary,
                checkedTrackColor = scheme.primary.copy(alpha = 0.35f),
            ),
        )
    }
}