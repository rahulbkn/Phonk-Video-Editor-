package dev.phonk.editor.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R

/**
 * Large hero card: headline, description and a full-width purple
 * "New Project" CTA. The layout is fully responsive — the headline wraps
 * naturally on narrow screens instead of being squeezed by fixed artwork.
 */
@Composable
fun HeroBanner(
    onCreateProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()
    var entered by remember { mutableStateOf(false) }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(420),
        label = "heroEntrance",
    )
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(HomeTokens.CardCornerLarge),
                ambientColor = palette.primary.copy(alpha = 0.22f),
                spotColor = palette.primary.copy(alpha = 0.22f),
            )
            .clip(RoundedCornerShape(HomeTokens.CardCornerLarge))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(HomeTokens.CardCornerLarge))
            .graphicsLayer {
                alpha = entrance
                scaleX = 0.96f + 0.04f * entrance
                scaleY = 0.96f + 0.04f * entrance
            }
            .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.home_tagline_1),
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.home_tagline_2),
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryBright,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_hero_description),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = palette.textSecondary,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ─── New Project CTA ────────────────────────────────────────────────
        NewProjectButton(onClick = onCreateProject)
    }
}

@Composable
private fun NewProjectButton(onClick: () -> Unit) {
    val palette = homePalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) HomeTokens.PressScale else 1f, label = "npScale")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = if (pressed) 6.dp else 12.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = palette.primary.copy(alpha = if (pressed) 0.35f else 0.55f),
                spotColor = palette.primary.copy(alpha = if (pressed) 0.35f else 0.55f),
            )
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(listOf(palette.primary, palette.primaryBright)),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = palette.primarySelected,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.home_new_project),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
