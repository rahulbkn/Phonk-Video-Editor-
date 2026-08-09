package dev.phonk.editor.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.phonk.editor.R
import dev.phonk.editor.settings.SettingsManager

/** Settings screen: theme override + language override. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val themeMode = SettingsManager.themeMode
    val languageCode = SettingsManager.languageCode

    fun applyLanguage(code: String) {
        if (languageCode == code) return
        SettingsManager.setLanguage(context, code)
        (context as? Activity)?.recreate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                stringResource(R.string.theme),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            listOf(SettingsManager.THEME_SYSTEM, SettingsManager.THEME_LIGHT, SettingsManager.THEME_DARK)
                .forEach { mode ->
                    OptionRow(
                        label = when (mode) {
                            SettingsManager.THEME_LIGHT -> stringResource(R.string.theme_light)
                            SettingsManager.THEME_DARK -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.theme_system)
                        },
                        selected = themeMode == mode,
                        onClick = { SettingsManager.setThemeMode(context, mode) },
                    )
                }

            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            listOf(SettingsManager.LANG_SYSTEM, SettingsManager.LANG_EN, SettingsManager.LANG_HI)
                .forEach { code ->
                    OptionRow(
                        label = when (code) {
                            SettingsManager.LANG_EN -> stringResource(R.string.language_english)
                            SettingsManager.LANG_HI -> stringResource(R.string.language_hindi)
                            else -> stringResource(R.string.language_system)
                        },
                        selected = languageCode == code,
                        onClick = { applyLanguage(code) },
                    )
                }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
