package dev.phonk.editor.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings screen: theme + language override, styled to match Home/Editor cards. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDeveloper: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val themeMode = SettingsManager.themeMode
    val languageCode = SettingsManager.languageCode
    val devRevealed = SettingsManager.devRevealed

    // Hidden developer reveal: DEV_REVEAL_TAPS rapid taps on the Settings title
    // raise devRevealed and surface the Developer card. Taps expire after 2.5s.
    var devTaps by remember { mutableIntStateOf(0) }
    val tapScope = rememberCoroutineScope()

    fun applyLanguage(code: String) {
        if (languageCode == code) return
        SettingsManager.setLanguage(context, code)
        (context as? Activity)?.recreate()
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhonkIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            Text(
                stringResource(R.string.settings),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable {
                        devTaps += 1
                        tapScope.launch {
                            delay(2500)
                            devTaps = 0
                        }
                        if (devTaps >= SettingsManager.DEV_REVEAL_TAPS) {
                            if (!SettingsManager.devRevealed) {
                                Toast.makeText(context, context.getString(R.string.debug_revealed), Toast.LENGTH_SHORT).show()
                            }
                            SettingsManager.setDevRevealed(context, true)
                            devTaps = 0
                        }
                    },
            )
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                SettingsCard(titleRes = R.string.theme, icon = Icons.Filled.DarkMode) {
                    listOf(
                        Triple(SettingsManager.THEME_SYSTEM, R.string.theme_system, Icons.Filled.SettingsBrightness),
                        Triple(SettingsManager.THEME_LIGHT, R.string.theme_light, Icons.Filled.LightMode),
                        Triple(SettingsManager.THEME_DARK, R.string.theme_dark, Icons.Filled.DarkMode),
                    ).forEach { (mode, labelRes, icon) ->
                        SettingsOptionRow(
                            icon = icon,
                            label = stringResource(labelRes),
                            selected = themeMode == mode,
                            onClick = { SettingsManager.setThemeMode(context, mode) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
            item {
                SettingsCard(titleRes = R.string.language, icon = Icons.Filled.Translate) {
                    listOf(
                        Triple(SettingsManager.LANG_SYSTEM, R.string.language_system, Icons.Filled.Language),
                        Triple(SettingsManager.LANG_EN, R.string.language_english, Icons.Filled.Language),
                        Triple(SettingsManager.LANG_HI, R.string.language_hindi, Icons.Filled.Language),
                    ).forEach { (code, labelRes, icon) ->
                        SettingsOptionRow(
                            icon = icon,
                            label = stringResource(labelRes),
                            selected = languageCode == code,
                            onClick = { applyLanguage(code) },
                        )
                    }
                }
            }
            if (devRevealed) {
                item { Spacer(Modifier.height(14.dp)) }
                item {
                    SettingsCard(titleRes = R.string.developer, icon = Icons.Filled.BugReport) {
                        DeveloperEntryRow(onClick = onOpenDeveloper)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperEntryRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(scheme.surfaceVariant.copy(alpha = 0.6f)),
        ) {
            Icon(Icons.Filled.BugReport, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.debug_screen_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsCard(titleRes: Int, icon: ImageVector, content: @Composable () -> Unit) {
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
private fun SettingsOptionRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.6f)),
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
    }
}