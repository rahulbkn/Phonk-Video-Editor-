package dev.phonk.editor.ui.components

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.phonk.editor.R
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.home.BottomNav
import dev.phonk.editor.ui.home.homePalette

/**
 * Shared scaffold for the five bottom-navigation tab screens (Home, Templates,
 * Projects, Profile). Guarantees a single, consistent experience across every
 * tab screen:
 *
 *  - Always-dark background shared with the rest of the home design system.
 *  - Content starts below the status bar ([WindowInsets] driven, never a
 *    hardcoded pixel offset) with light system-bar icons.
 *  - A floating, rounded [BottomNav] pinned above the navigation bar inset.
 *  - The content area is automatically padded above the floating nav so no
 *    content can scroll underneath it.
 *
 * Every tab screen renders the exact same [BottomNav]; nothing is hardcoded
 * per-screen.
 */
@Composable
fun PhonkTabScaffold(
    activeTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = homePalette().background,
    content: @Composable BoxScope.() -> Unit,
) {
    HomeStatusBarAppearance()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiDimens.floatingNavPadding)
                    .padding(bottom = bottomInset + UiDimens.navBarBottomSpacing),
            ) {
                BottomNav(activeTab = activeTab, onTabSelected = onTabSelected)
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
        ) {
            content()
        }
    }
}

/**
 * Keeps the status/navigation bar icons light over the always-dark tab screen
 * background regardless of the system or pinned theme. Restores the correct
 * appearance when the screen leaves composition.
 */
@Composable
private fun HomeStatusBarAppearance() {
    val view = LocalView.current
    val darkTheme = when (SettingsManager.themeMode) {
        SettingsManager.THEME_LIGHT -> false
        SettingsManager.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    DisposableEffect(view, darkTheme) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !darkTheme
            controller?.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

/**
 * Shared secondary-screen header: back arrow + bold title with optional
 * muted subtitle. Used by Templates, Projects, Profile and Pro to keep the
 * same look, spacing and insets across the app.
 */
@Composable
fun TabScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val palette = homePalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = UiDimens.spaceSm, end = UiDimens.screenPadding, top = 4.dp, bottom = UiDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(UiDimens.touchTargetIcon)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = palette.text,
            )
        }
        Column {
            Text(
                text = title,
                fontSize = UiDimens.textSizeScreenTitle,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = UiDimens.textSizeSm,
                    color = palette.textSecondary,
                )
            }
        }
    }
}
