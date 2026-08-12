package dev.phonk.editor.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonk.editor.R

// ============================================================
// HEADER
// ============================================================

/**
 * Settings header: rounded-square back button (charcoal, subtle border)
 * followed by the screen title. No second title and no right-side action.
 */
@Composable
fun SettingsHeader(
    onBack: () -> Unit,
    title: String,
    onTitleClick: (() -> Unit)? = null,
) {
    val palette = settingsPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(SettingsTokens.HeaderBackSize)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.card)
                .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                .clickable(onClick = onBack),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = palette.text,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = palette.text,
            modifier = if (onTitleClick != null) {
                Modifier.clickable(onClick = onTitleClick)
            } else Modifier,
        )
    }
}

// ============================================================
// SECTION HEADING
// ============================================================

/** Section heading with a small vertical purple accent bar on the left. */
@Composable
fun SettingsSectionHeader(title: String) {
    val palette = settingsPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(listOf(palette.primaryBright, palette.primary)),
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
        )
    }
}

// ============================================================
// GROUPED CARD + ROWS
// ============================================================

/** One rounded grouped settings card containing several [SettingsRow]s. */
@Composable
fun PhonkSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = settingsPalette()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsTokens.CardCorner))
            .background(palette.card)
            .border(1.dp, palette.border, RoundedCornerShape(SettingsTokens.CardCorner)),
    ) {
        content()
    }
}

/** Thin divider placed between grouped rows (aligned after the icon column). */
@Composable
fun SettingsDivider() {
    val palette = settingsPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 66.dp, end = 16.dp)
            .height(1.dp)
            .background(palette.border.copy(alpha = 0.5f)),
    )
}

/**
 * Base settings row: icon badge, title/subtitle, optional trailing control.
 * Used by every row type; grouped inside [PhonkSettingsCard].
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    iconTint: Color? = null,
    titleColor: Color? = null,
) {
    val palette = settingsPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = iconTint ?: palette.primary
    val titleTint = titleColor ?: palette.text

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SettingsTokens.RowHeight)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier,
            )
            .background(if (pressed) palette.primary.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = SettingsTokens.RowHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(SettingsTokens.IconBadge)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(SettingsTokens.IconSize),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = titleTint,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Navigation row: value + chevron, whole row clickable. */
@Composable
fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val palette = settingsPalette()
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = palette.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/** Toggle row with an always-dark switch (purple ON, dark gray OFF). */
@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val palette = settingsPalette()
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = palette.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = palette.textSecondary,
                    uncheckedTrackColor = palette.switchOffTrack,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

/** Row with a purple text action on the right (e.g. "Clear"). */
@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionTint: Color? = null,
) {
    val palette = settingsPalette()
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Text(
                text = actionLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = actionTint ?: palette.primaryBright,
            )
        },
    )
}

// ============================================================
// PROFILE CARD
// ============================================================

/** Full-width profile card: glowing avatar, name, account type, chevron. */
@Composable
fun ProfileCard(onClick: () -> Unit) {
    val palette = settingsPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) SettingsTokens.PressScale else 1f, label = "profileScale")
    val shape = RoundedCornerShape(SettingsTokens.CardCorner)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(SettingsTokens.AvatarSize)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = palette.primary.copy(alpha = 0.45f),
                    spotColor = palette.primary.copy(alpha = 0.45f),
                )
                .clip(CircleShape)
                .background(palette.primary.copy(alpha = 0.14f))
                .border(2.dp, palette.primary, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = palette.primaryBright,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_profile_title),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.settings_profile_subtitle),
                fontSize = 14.sp,
                color = palette.textSecondary,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ============================================================
// PRO UPGRADE CARD
// ============================================================

/** Premium "Unlock Phonk Pro" upgrade card with a single CTA button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProUpgradeCard(onUpgrade: () -> Unit) {
    val palette = settingsPalette()
    val shape = RoundedCornerShape(SettingsTokens.CardCorner)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = palette.primary.copy(alpha = 0.30f),
                spotColor = palette.primary.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.deep.copy(alpha = 0.75f),
                        palette.card,
                    ),
                ),
            )
            .border(1.dp, palette.primary.copy(alpha = 0.55f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(palette.deep)
                    .border(1.dp, palette.primary.copy(alpha = 0.6f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    tint = palette.primaryBright,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.settings_pro_title_prefix),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_pro_title_accent),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryBright,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_pro_subtitle),
                    fontSize = 14.sp,
                    color = palette.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProBadge(stringResource(R.string.settings_pro_badge_premium))
            ProBadge(stringResource(R.string.settings_pro_badge_watermark))
            ProBadge(stringResource(R.string.settings_pro_badge_4k))
            ProBadge(stringResource(R.string.settings_pro_badge_ai))
        }

        Spacer(Modifier.height(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(palette.primary, palette.primaryBright)))
                .clickable(onClick = onUpgrade),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_upgrade_pro),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProBadge(text: String) {
    val palette = settingsPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.cardSecondary)
            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = palette.primaryBright,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

// ============================================================
// STORAGE CARD
// ============================================================

/**
 * Compact storage card: purple ring chart, per-category sizes, a used-space
 * progress bar and a "Manage Storage" action.
 */
@Composable
fun StorageCard(
    usedLabel: String,
    usedPercent: Float,
    projectsSize: String,
    cacheSize: String,
    freeSize: String,
    onManage: () -> Unit,
) {
    val palette = settingsPalette()

    PhonkSettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StorageRing(usedPercent = usedPercent, usedLabel = usedLabel)
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StorageLine(label = stringResource(R.string.settings_storage_projects), value = projectsSize)
                    StorageLine(label = stringResource(R.string.settings_storage_cache), value = cacheSize)
                    StorageLine(label = stringResource(R.string.settings_storage_free), value = freeSize)
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(palette.switchOffTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usedPercent.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Brush.horizontalGradient(listOf(palette.primary, palette.primaryBright))),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onManage)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_manage_storage),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryBright,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = palette.primaryBright,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StorageRing(usedPercent: Float, usedLabel: String) {
    val palette = settingsPalette()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(84.dp),
    ) {
        Canvas(modifier = Modifier.size(84.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = palette.switchOffTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(palette.primary, palette.primaryBright, palette.primary),
                ),
                startAngle = -90f,
                sweepAngle = 360f * usedPercent.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = usedLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.settings_storage_used),
                fontSize = 9.sp,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun StorageLine(label: String, value: String) {
    val palette = settingsPalette()
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
        )
    }
}

// ============================================================
// FOOTER
// ============================================================

/** Centered PHONK EDITOR footer with the brand tagline. */
@Composable
fun SettingsFooter() {
    val palette = settingsPalette()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.linearGradient(listOf(palette.primary, palette.primaryBright))),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phonk_logo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = palette.text,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_footer_tagline),
            fontSize = 12.sp,
            color = palette.muted,
            textAlign = TextAlign.Center,
        )
    }
}
