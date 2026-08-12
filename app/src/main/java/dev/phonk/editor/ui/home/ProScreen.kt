package dev.phonk.editor.ui.home

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R

/**
 * Pro subscription screen — feature list and an upgrade action. The upgrade
 * button opens a dialog explaining that billing arrives in a future release
 * (no fake purchases, no dead buttons).
 */
@Composable
fun ProScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = homePalette()
    var showBilling by remember { mutableStateOf(false) }

    val features = listOf(
        FeatureItem(
            title = stringResource(R.string.pro_feature_unlimited),
            description = stringResource(R.string.pro_feature_unlimited_desc),
        ),
        FeatureItem(
            title = stringResource(R.string.pro_feature_effects),
            description = stringResource(R.string.pro_feature_effects_desc),
        ),
        FeatureItem(
            title = stringResource(R.string.pro_feature_beat),
            description = stringResource(R.string.pro_feature_beat_desc),
        ),
        FeatureItem(
            title = stringResource(R.string.pro_feature_watermark),
            description = stringResource(R.string.pro_feature_watermark_desc),
        ),
        FeatureItem(
            title = stringResource(R.string.pro_feature_priority),
            description = stringResource(R.string.pro_feature_priority_desc),
        ),
        FeatureItem(
            title = stringResource(R.string.pro_feature_support),
            description = stringResource(R.string.pro_feature_support_desc),
        ),
    )

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(top = topInset),
    ) {
        // ─── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = palette.text,
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.pro_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.text,
                )
                Text(
                    text = stringResource(R.string.pro_subtitle),
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                )
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Hero banner ───────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(HomeTokens.CardCornerLarge),
                            ambientColor = palette.primary.copy(alpha = 0.3f),
                            spotColor = palette.primary.copy(alpha = 0.3f),
                        )
                        .clip(RoundedCornerShape(HomeTokens.CardCornerLarge))
                        .background(
                            Brush.linearGradient(
                                listOf(palette.primary, palette.primaryBright),
                            ),
                        )
                        .padding(22.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_crown),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.pro_upgrade),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.pro_current),
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // ─── Feature list ──────────────────────────────────────────────
            items(features.size) { i ->
                FeatureRow(feature = features[i])
            }

            // ─── Upgrade CTA ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(6.dp))
                UpgradeButton(onClick = { showBilling = true })
            }
        }
    }

    if (showBilling) {
        AlertDialog(
            onDismissRequest = { showBilling = false },
            title = { Text(stringResource(R.string.pro_billing_title)) },
            text = { Text(stringResource(R.string.pro_billing_message)) },
            confirmButton = {
                TextButton(onClick = { showBilling = false }) {
                    Text(stringResource(R.string.pro_billing_ok))
                }
            },
        )
    }
}

private data class FeatureItem(
    val title: String,
    val description: String,
)

@Composable
private fun FeatureRow(feature: FeatureItem) {
    val palette = homePalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.primary.copy(alpha = 0.18f)),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = palette.primaryBright,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = feature.description,
                fontSize = 12.sp,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun UpgradeButton(onClick: () -> Unit) {
    val palette = homePalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) HomeTokens.PressScale else 1f,
        label = "upgradeScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = if (pressed) 6.dp else 12.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = palette.primary.copy(alpha = 0.5f),
                spotColor = palette.primary.copy(alpha = 0.5f),
            )
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(listOf(palette.primary, palette.primaryBright)),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Text(
            text = stringResource(R.string.pro_upgrade),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
