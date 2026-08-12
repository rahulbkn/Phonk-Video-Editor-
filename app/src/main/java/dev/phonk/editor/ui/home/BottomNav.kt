package dev.phonk.editor.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.phonk.editor.ui.components.NavTab
import dev.phonk.editor.ui.components.UiDimens

/**
 * Single bottom navigation used by every tab screen (Home, Templates,
 * Projects, Profile). Five destinations — Home, Templates, Create (raised
 * purple action), Projects, Profile. The centre Create button opens the
 * creation sheet instead of navigating.
 */
@Composable
fun BottomNav(
    activeTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(UiDimens.cornerRadiusNav))
            .clip(RoundedCornerShape(UiDimens.cornerRadiusNav))
            .background(palette.cardSecondary)
            .border(1.dp, palette.border, RoundedCornerShape(UiDimens.cornerRadiusNav))
            .padding(horizontal = UiDimens.spaceSm, vertical = UiDimens.spaceSm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(
            tab = NavTab.HOME,
            activeTab = activeTab,
            icon = Icons.Filled.Home,
            activeColor = palette.primaryBright,
            inactiveColor = palette.textSecondary,
            onClick = { onTabSelected(NavTab.HOME) },
        )
        NavItem(
            tab = NavTab.TEMPLATES,
            activeTab = activeTab,
            icon = Icons.Filled.AutoAwesome,
            activeColor = palette.primaryBright,
            inactiveColor = palette.textSecondary,
            onClick = { onTabSelected(NavTab.TEMPLATES) },
        )
        CenterCreateButton(onClick = { onTabSelected(NavTab.CREATE) })
        NavItem(
            tab = NavTab.PROJECTS,
            activeTab = activeTab,
            icon = Icons.Filled.Folder,
            activeColor = palette.primaryBright,
            inactiveColor = palette.textSecondary,
            onClick = { onTabSelected(NavTab.PROJECTS) },
        )
        NavItem(
            tab = NavTab.PROFILE,
            activeTab = activeTab,
            icon = Icons.Filled.Person,
            activeColor = palette.primaryBright,
            inactiveColor = palette.textSecondary,
            onClick = { onTabSelected(NavTab.PROFILE) },
        )
    }
}

@Composable
private fun RowScope.NavItem(
    tab: NavTab,
    activeTab: NavTab,
    icon: ImageVector,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
) {
    val isActive = tab == activeTab
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "navItemScale")
    val label = stringResource(tab.labelRes)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(UiDimens.cornerRadiusNavItem))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp)
            .scale(scale),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(UiDimens.navItemIconContainer)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.14f)
                    else Color.Transparent,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else inactiveColor,
                modifier = Modifier.size(UiDimens.iconSizeNav),
            )
        }
        Text(
            text = label,
            fontSize = UiDimens.textSizeNavLabel,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) activeColor else inactiveColor,
        )
    }
}

/** Raised purple centre action that opens the create sheet. */
@Composable
private fun CenterCreateButton(onClick: () -> Unit) {
    val palette = homePalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "createBtnScale")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(UiDimens.navCreateButton)
            .padding(top = 0.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = palette.primary.copy(alpha = 0.6f),
                spotColor = palette.primary.copy(alpha = 0.6f),
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(palette.primary, palette.primaryBright)),
            )
            .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(NavTab.CREATE.labelRes),
            tint = Color.White,
            modifier = Modifier.size(UiDimens.iconSizeNavCreate),
        )
    }
}
