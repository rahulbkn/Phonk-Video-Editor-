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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R

/**
 * PHONK EDITOR header — logo mark on the left, Pro + Settings on the right.
 */
@Composable
fun PhonkHeader(
    onProClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ─── Logo ────────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .shadow(10.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(palette.primary, palette.primaryBright),
                        )
                    ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phonk_logo),
                    contentDescription = stringResource(R.string.app_name),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.home_brand_title),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.sp,
                    color = palette.text,
                    lineHeight = 20.sp,
                )
                Text(
                    text = stringResource(R.string.home_brand_subtitle),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 5.sp,
                    color = palette.primaryBright,
                )
            }
        }

        // ─── Pro + Settings ─────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProButton(onClick = onProClick)
            SettingsButton(onClick = onSettingsClick)
        }
    }
}

/** Dark rounded Pro button with crown icon and subtle purple border/glow. */
@Composable
private fun ProButton(onClick: () -> Unit) {
    val palette = homePalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(if (pressed) 0.9f else 0.35f, label = "proGlow")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(44.dp)
            .shadow(
                elevation = if (pressed) 14.dp else 8.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = palette.primary.copy(alpha = glowAlpha),
                spotColor = palette.primary.copy(alpha = glowAlpha),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(palette.cardSecondary)
            .border(1.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_crown),
                contentDescription = null,
                tint = palette.primaryBright,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_pro),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (pressed) palette.primaryBright else palette.text,
            )
        }
    }
}

/** Square rounded settings button with a white gear vector icon. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val palette = homePalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) palette.cardSecondary else palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.settings),
            tint = if (pressed) palette.primaryBright else palette.text,
            modifier = Modifier.size(21.dp),
        )
    }
}
