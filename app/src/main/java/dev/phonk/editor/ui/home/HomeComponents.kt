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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared section heading with an optional right-aligned "See All" action. */
@Composable
fun SectionHeaderRow(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String = "See All",
) {
    val palette = homePalette()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            if (badge != null) {
                BadgePill(text = badge)
            }
        }
        if (onSeeAll != null) {
            Text(
                text = seeAllLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryBright,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/** Small purple "NEW"-style pill badge. */
@Composable
fun BadgePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(start = 10.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(palette.primary.copy(alpha = 0.16f))
            .border(1.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = palette.primaryBright,
        )
    }
}

/**
 * Shared dark card surface with thin border, optional glow and a pressed-scale
 * animation used by all home cards (quick actions, projects, AI tools).
 */
@Composable
fun PhonkHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = homePalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) HomeTokens.PressScale else 1f, label = "cardScale")
    val shape = RoundedCornerShape(HomeTokens.CardCorner)

    val base = modifier
        .scale(scale)
        .shadow(
            elevation = if (glow) 14.dp else 2.dp,
            shape = shape,
            ambientColor = if (glow) palette.primary.copy(alpha = 0.25f) else palette.primary.copy(alpha = 0f),
            spotColor = if (glow) palette.primary.copy(alpha = 0.25f) else palette.primary.copy(alpha = 0f),
        )
        .clip(shape)
        .background(palette.card)
        .border(1.dp, if (pressed) palette.primary.copy(alpha = 0.55f) else palette.border, shape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)

    Box(modifier = base, content = { content() })
}

/** Small internal wrapper so cards can opt out of the default card chrome. */
@Composable
fun HomeIconBadge(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = homePalette()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(HomeTokens.IconBadgeCorner))
            .background(
                Brush.linearGradient(listOf(palette.primary, palette.primaryBright)),
            ),
    ) {
        content()
    }
}
