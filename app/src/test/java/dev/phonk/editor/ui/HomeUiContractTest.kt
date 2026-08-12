package dev.phonk.editor.ui

import androidx.compose.ui.unit.dp
import dev.phonk.editor.ui.components.UiDimens
import dev.phonk.editor.ui.home.HomeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression contract for the Home UI layout fix.
 *
 * Guards the "centralized dimensions" requirement: the Home screen components
 * must not define their own slightly-different values for visually identical
 * surfaces, and every shared value must stay in sync with [UiDimens]. It also
 * pins the floating-bottom-navigation geometry used by [PhonkTabScaffold] so a
 * future tweak cannot silently reintroduce content hidden behind the nav.
 */
class HomeUiContractTest {

    @Test
    fun homeTokensResolveToSharedUiDimens() {
        assertEquals("screen horizontal padding must be shared", UiDimens.screenPadding, HomeTokens.ScreenHorizontal)
        assertEquals("section spacing must be shared", UiDimens.sectionSpacing, HomeTokens.SectionSpacing)
        assertEquals("card corner radius must be shared", UiDimens.homeCardCorner, HomeTokens.CardCorner)
        assertEquals("large card corner radius must be shared", UiDimens.homeCardCornerLarge, HomeTokens.CardCornerLarge)
        assertEquals("icon badge must be shared", UiDimens.homeIconBadge, HomeTokens.IconBadge)
        assertEquals("icon badge corner must be shared", UiDimens.homeIconBadgeCorner, HomeTokens.IconBadgeCorner)
        assertEquals("quick action width must be shared", UiDimens.homeQuickActionWidth, HomeTokens.QuickActionWidth)
        assertEquals("quick action height must be shared", UiDimens.homeQuickActionHeight, HomeTokens.QuickActionHeight)
        assertEquals("project card width must be shared", UiDimens.homeProjectCardWidth, HomeTokens.ProjectCardWidth)
        assertEquals("ai card width must be shared", UiDimens.homeAiCardWidth, HomeTokens.AiCardWidth)
    }

    @Test
    fun floatingNavLeavesBreathingRoomForNavInset() {
        assertTrue(
            "nav must sit slightly above the system nav-bar inset",
            UiDimens.navBarBottomSpacing > 0.dp,
        )
        assertTrue(
            "content must reserve space for the floating nav height",
            UiDimens.bottomNavHeight > UiDimens.navItemIconContainer,
        )
        assertTrue(
            "nav padding must be defined and positive",
            UiDimens.floatingNavPadding > 0.dp,
        )
    }

    @Test
    fun navGeometryIsSharedNotPerScreen() {
        // All floating-nav geometry lives in UiDimens so Home, Templates,
        // Projects and Profile agree on the same measured bar instead of each
        // hardcoding a magic margin.
        assertEquals(70.0, UiDimens.bottomNavHeight.value.toDouble(), 0.0)
        assertEquals("floating nav bottom offset", UiDimens.navBarBottomSpacing, 6.dp)
        assertEquals("floating nav horizontal padding", UiDimens.floatingNavPadding, 14.dp)
    }
}