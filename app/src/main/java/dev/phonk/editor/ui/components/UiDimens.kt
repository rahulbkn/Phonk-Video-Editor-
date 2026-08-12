package dev.phonk.editor.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared design dimensions for the phonk home/settings UI (always-dark design
 * system). Mirrors text_size_* entries in dimens.xml for typography.
 */
object UiDimens {
    // Spacing scale
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 24.dp
    val spaceXxl = 32.dp

    // Layout
    val screenPadding = 20.dp
    val sectionSpacing = 28.dp
    val itemSpacing = 12.dp
    val gridSpacing = 12.dp
    val floatingNavPadding = 14.dp

    // Bar heights
    val toolbarHeight = 52.dp
    val subToolbarHeight = 44.dp
    val bottomNavHeight = 70.dp
    val timelineTrackHeight = 48.dp
    val toolPanelHeight = 150.dp

    // Buttons & touch targets
    val buttonHeight = 48.dp
    val buttonMinWidth = 120.dp
    val buttonHorizontalPadding = 18.dp
    val touchTargetIcon = 44.dp
    val touchTargetCompact = 40.dp
    val navItemIconContainer = 36.dp
    val navCreateButton = 54.dp

    // Icon sizes
    val iconSizeSm = 16.dp
    val iconSizeMd = 18.dp
    val iconSizeLg = 24.dp
    val iconSizeNav = 22.dp
    val iconSizeNavCreate = 26.dp

    // Corner radii
    val cornerRadiusSm = 8.dp
    val cornerRadiusMd = 12.dp
    val cornerRadiusLg = 16.dp
    val cornerRadiusButton = 10.dp
    val cornerRadiusChip = 20.dp
    val cornerRadiusPill = 28.dp
    val cornerRadiusNav = 22.dp
    val cornerRadiusNavItem = 16.dp

    // Dialogs / sheets
    val dialogWidth = 320.dp
    val dialogPadding = 24.dp
    val sheetCorner = 16.dp

    // Home cards
    val homeCardCorner = 18.dp
    val homeCardCornerLarge = 20.dp
    val homeIconBadge = 52.dp
    val homeIconBadgeCorner = 16.dp
    val homeQuickActionWidth = 140.dp
    val homeQuickActionHeight = 150.dp
    val homeProjectCardWidth = 232.dp
    val homeAiCardWidth = 248.dp

    // Typography (mirrors text_size_* in dimens.xml)
    val textSizeXs = 10.sp
    val textSizeSm = 12.sp
    val textSizeMd = 14.sp
    val textSizeLg = 16.sp
    val textSizeXl = 20.sp
    val textSizeScreenTitle = 22.sp
    val textSizeSectionTitle = 18.sp
    val textSizeDialogTitle = 18.sp
    val textSizeDialogBody = 14.sp
    val textSizeDialogAction = 13.sp
    val textSizeButton = 13.sp
    val textSizeToolbarLabel = 12.sp
    val textSizeNavLabel = 10.sp
}
