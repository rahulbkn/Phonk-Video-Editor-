package dev.phonk.editor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NavTab(val label: String) {
    HOME("Home"),
    PROJECTS("Projects"),
    ADD("Add"),
    BEATS("Beats"),
    PROFILE("Profile"),
}

@Composable
fun BottomNav(
    activeTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = scheme.surface
    val inactiveColor = scheme.onSurfaceVariant
    val activeColor = scheme.primary

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(containerColor)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                tab = NavTab.HOME,
                activeTab = activeTab,
                activeIcon = Icons.Filled.Home,
                inactiveIcon = Icons.Outlined.Home,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(NavTab.HOME) },
            )
            NavItem(
                tab = NavTab.PROJECTS,
                activeTab = activeTab,
                activeIcon = Icons.Filled.Folder,
                inactiveIcon = Icons.Outlined.Folder,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(NavTab.PROJECTS) },
            )
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                CenterAddButton(onClick = { onTabSelected(NavTab.ADD) })
            }
            NavItem(
                tab = NavTab.BEATS,
                activeTab = activeTab,
                activeIcon = Icons.Filled.MusicNote,
                inactiveIcon = Icons.Outlined.MusicNote,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(NavTab.BEATS) },
            )
            NavItem(
                tab = NavTab.PROFILE,
                activeTab = activeTab,
                activeIcon = Icons.Filled.Person,
                inactiveIcon = Icons.Outlined.Person,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(NavTab.PROFILE) },
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
    tab: NavTab,
    activeTab: NavTab,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
) {
    val isActive = tab == activeTab
    val color by animateColorAsState(if (isActive) activeColor else inactiveColor, label = "navColor")
    val scale by animateFloatAsState(if (isActive) 1.05f else 1f, label = "navScale")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.12f) else Color.Transparent),
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = tab.label,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun CenterAddButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .offset(y = (-12).dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (pressed) scheme.primary.copy(alpha = 0.85f)
                else scheme.primary,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = NavTab.ADD.label,
            tint = scheme.onPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}
