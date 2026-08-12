package dev.phonk.editor.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import dev.phonk.editor.R

/**
 * Always-dark premium settings design system for PHONK EDITOR.
 *
 * Matches the home screen identity: near-black surfaces, neon purple accents
 * and red destructive actions. The colors are resolved from resources so
 * values + values-night carry identical hex values on purpose — the settings
 * screen stays dark in both system themes.
 */
data class PhonkSettingsPalette(
    val background: Color,
    val card: Color,
    val cardSecondary: Color,
    val border: Color,
    val primary: Color,
    val primaryBright: Color,
    val primarySelected: Color,
    val deep: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val success: Color,
    val danger: Color,
    val switchOffTrack: Color,
)

@Composable
fun settingsPalette(): PhonkSettingsPalette {
    val background = colorResource(R.color.settings_bg)
    val card = colorResource(R.color.settings_card)
    val cardSecondary = colorResource(R.color.settings_card_secondary)
    val border = colorResource(R.color.settings_border)
    val primary = colorResource(R.color.settings_primary)
    val primaryBright = colorResource(R.color.settings_primary_bright)
    val primarySelected = colorResource(R.color.settings_primary_selected)
    val deep = colorResource(R.color.settings_deep)
    val text = colorResource(R.color.settings_text)
    val textSecondary = colorResource(R.color.settings_text_secondary)
    val muted = colorResource(R.color.settings_muted)
    val success = colorResource(R.color.settings_success)
    val danger = colorResource(R.color.settings_danger)
    val switchOffTrack = colorResource(R.color.settings_switch_off)
    return remember(
        background, card, cardSecondary, border, primary, primaryBright, primarySelected,
        deep, text, textSecondary, muted, success, danger, switchOffTrack,
    ) {
        PhonkSettingsPalette(
            background = background,
            card = card,
            cardSecondary = cardSecondary,
            border = border,
            primary = primary,
            primaryBright = primaryBright,
            primarySelected = primarySelected,
            deep = deep,
            text = text,
            textSecondary = textSecondary,
            muted = muted,
            success = success,
            danger = danger,
            switchOffTrack = switchOffTrack,
        )
    }
}

/** Shared spacing / sizing tokens for the settings screen. */
object SettingsTokens {
    val ScreenHorizontal = 20.dp
    val SectionSpacing = 20.dp
    val CardCorner = 16.dp
    val PressScale = 0.97f
    val RowHeight = 68.dp
    val RowHorizontal = 16.dp
    val IconBadge = 36.dp
    val IconSize = 22.dp
    val HeaderBackSize = 52.dp
    val AvatarSize = 62.dp
}
