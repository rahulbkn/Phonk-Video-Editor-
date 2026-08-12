package dev.phonk.editor.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import dev.phonk.editor.R
import dev.phonk.editor.ui.components.UiDimens

/**
 * Always-dark home design system for PHONK EDITOR.
 *
 * Every color is resolved from the resource palette (values + values-night
 * carry identical hex values on purpose) so the home screen keeps the exact
 * premium dark/cyberpunk look in both system themes. The home screen does not
 * follow the legacy light "soft pink" Material theme — it is a self-contained
 * design system that other editor screens can adopt later.
 */
data class PhonkHomePalette(
    val background: Color,
    val card: Color,
    val cardSecondary: Color,
    val border: Color,
    val primary: Color,
    val primaryBright: Color,
    val primarySelected: Color,
    val text: Color,
    val textSecondary: Color,
    val success: Color,
)

@Composable
fun homePalette(): PhonkHomePalette {
    val background = colorResource(R.color.home_bg)
    val card = colorResource(R.color.home_card)
    val cardSecondary = colorResource(R.color.home_card_secondary)
    val border = colorResource(R.color.home_border)
    val primary = colorResource(R.color.home_primary)
    val primaryBright = colorResource(R.color.home_primary_bright)
    val primarySelected = colorResource(R.color.home_primary_selected)
    val text = colorResource(R.color.home_text)
    val textSecondary = colorResource(R.color.home_text_secondary)
    val success = colorResource(R.color.home_success)
    return remember(background, card, cardSecondary, border, primary, primaryBright, primarySelected, text, textSecondary, success) {
        PhonkHomePalette(
            background = background,
            card = card,
            cardSecondary = cardSecondary,
            border = border,
            primary = primary,
            primaryBright = primaryBright,
            primarySelected = primarySelected,
            text = text,
            textSecondary = textSecondary,
            success = success,
        )
    }
}

/** Shared spacing / sizing tokens for the home screen components. */
object HomeTokens {
    val ScreenHorizontal = UiDimens.screenPadding
    val SectionSpacing = UiDimens.sectionSpacing
    val CardCorner = UiDimens.homeCardCorner
    val CardCornerLarge = UiDimens.homeCardCornerLarge
    val PressScale = 0.96f
    val IconBadge = UiDimens.homeIconBadge
    val IconBadgeCorner = UiDimens.homeIconBadgeCorner
    val QuickActionWidth = UiDimens.homeQuickActionWidth
    val QuickActionHeight = UiDimens.homeQuickActionHeight
    val ProjectCardWidth = UiDimens.homeProjectCardWidth
    val AiCardWidth = UiDimens.homeAiCardWidth
}
