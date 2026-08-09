package dev.phonk.editor.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.phonk.editor.R
import dev.phonk.editor.settings.SettingsManager

/**
 * App-wide Compose theme. Every color is resolved from resource files
 * (values/colors.xml for light, values-night/colors.xml for dark). Because
 * the plain [androidx.compose.ui.res.colorResource] resolves against the
 * *system* night mode (not the user's pinned theme), colors are loaded from
 * an explicit configuration context so a forced light/dark theme works even
 * when it differs from the system setting. Uses the existing Material version
 * and customizes its appearance to match the pink / deep-blue-purple design
 * system. A user override (Settings > Theme) can pin light or dark.
 */
@Composable
fun PhonkTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = when (SettingsManager.themeMode) {
        SettingsManager.THEME_DARK -> true
        SettingsManager.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = remember(darkTheme) { buildScheme(context, darkTheme) }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun buildScheme(context: Context, darkTheme: Boolean): ColorScheme {
    fun c(id: Int): Color {
        val conf = Configuration(context.resources.configuration).apply {
            uiMode = if (darkTheme) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
        }
        @Suppress("DEPRECATION")
        return Color(context.createConfigurationContext(conf).getColor(id))
    }

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c(R.color.primary),
            onPrimary = c(R.color.on_primary),
            primaryContainer = c(R.color.surface_variant),
            onPrimaryContainer = c(R.color.text_primary),
            inversePrimary = c(R.color.accent),
            secondary = c(R.color.secondary),
            onSecondary = c(R.color.on_accent),
            secondaryContainer = c(R.color.surface_variant),
            onSecondaryContainer = c(R.color.text_primary),
            tertiary = c(R.color.accent),
            onTertiary = c(R.color.on_accent),
            tertiaryContainer = c(R.color.surface_variant),
            onTertiaryContainer = c(R.color.text_primary),
            background = c(R.color.background),
            onBackground = c(R.color.text_primary),
            surface = c(R.color.surface),
            onSurface = c(R.color.text_primary),
            surfaceVariant = c(R.color.surface_variant),
            onSurfaceVariant = c(R.color.text_secondary),
            surfaceTint = c(R.color.primary),
            inverseSurface = c(R.color.surface_variant),
            inverseOnSurface = c(R.color.text_primary),
            surfaceDim = c(R.color.background),
            surfaceBright = c(R.color.surface),
            surfaceContainerLowest = c(R.color.background),
            surfaceContainerLow = c(R.color.surface),
            surfaceContainer = c(R.color.surface),
            surfaceContainerHigh = c(R.color.surface_variant),
            surfaceContainerHighest = c(R.color.surface_variant),
            outline = c(R.color.divider),
            outlineVariant = c(R.color.divider),
            scrim = Color.Black.copy(alpha = 0.6f),
            error = c(R.color.error),
            onError = c(R.color.on_error),
            errorContainer = c(R.color.surface_variant),
            onErrorContainer = c(R.color.error),
        )
    } else {
        lightColorScheme(
            primary = c(R.color.primary),
            onPrimary = c(R.color.on_primary),
            primaryContainer = c(R.color.surface_variant),
            onPrimaryContainer = c(R.color.text_primary),
            inversePrimary = c(R.color.accent),
            secondary = c(R.color.secondary),
            onSecondary = c(R.color.on_accent),
            secondaryContainer = c(R.color.surface_variant),
            onSecondaryContainer = c(R.color.text_primary),
            tertiary = c(R.color.accent),
            onTertiary = c(R.color.on_accent),
            tertiaryContainer = c(R.color.surface_variant),
            onTertiaryContainer = c(R.color.text_primary),
            background = c(R.color.background),
            onBackground = c(R.color.text_primary),
            surface = c(R.color.surface),
            onSurface = c(R.color.text_primary),
            surfaceVariant = c(R.color.surface_variant),
            onSurfaceVariant = c(R.color.text_secondary),
            surfaceTint = c(R.color.primary),
            inverseSurface = c(R.color.surface_variant),
            inverseOnSurface = c(R.color.text_primary),
            surfaceDim = c(R.color.surface_variant),
            surfaceBright = c(R.color.background),
            surfaceContainerLowest = c(R.color.background),
            surfaceContainerLow = c(R.color.surface),
            surfaceContainer = c(R.color.surface),
            surfaceContainerHigh = c(R.color.surface_variant),
            surfaceContainerHighest = c(R.color.surface_variant),
            outline = c(R.color.divider),
            outlineVariant = c(R.color.divider),
            scrim = Color.Black.copy(alpha = 0.6f),
            error = c(R.color.error),
            onError = c(R.color.on_error),
            errorContainer = c(R.color.surface_variant),
            onErrorContainer = c(R.color.error),
        )
    }
    return scheme
}
