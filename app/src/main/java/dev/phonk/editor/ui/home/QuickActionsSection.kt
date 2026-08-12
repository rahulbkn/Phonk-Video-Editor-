package dev.phonk.editor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R

/**
 * Horizontally scrolling "Quick Actions" row — Trim Video, Templates,
 * Effects, Add Music, Add Text.
 */
@Composable
fun QuickActionsSection(
    actions: List<QuickAction>,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeaderRow(
            title = stringResource(R.string.home_quick_actions),
            onSeeAll = onSeeAll,
            seeAllLabel = stringResource(R.string.home_see_all),
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(actions, key = { it.id }) { action ->
                QuickActionCard(action = action)
            }
        }
    }
}

@Composable
private fun QuickActionCard(action: QuickAction) {
    val palette = homePalette()
    PhonkHomeCard(
        onClick = action.onClick,
        modifier = Modifier.width(HomeTokens.QuickActionWidth),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeTokens.QuickActionHeight)
                .padding(vertical = 18.dp),
        ) {
            HomeIconBadge(modifier = Modifier.size(HomeTokens.IconBadge)) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = action.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
