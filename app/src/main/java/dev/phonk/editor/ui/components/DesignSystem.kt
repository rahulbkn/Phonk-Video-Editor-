package dev.phonk.editor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

val ToolbarHeight = 52.dp
val TrackHeight = 48.dp
val PanelHeight = 150.dp
val BottomToolHeight = 56.dp

val CornerCard = 16.dp
val CornerChip = 20.dp
val CornerPill = 28.dp

private val ActiveGlow = 0.85f
private val IdleAlpha = 0.18f

val LabelFont = 11.sp
val TitleFont = 13.sp

@Composable
fun EditorColor(
    active: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Color = if (active) activeColor else inactiveColor

@Composable
fun PhonkToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateFloatAsState(if (active) ActiveGlow else 0f, label = "tbg")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(CornerChip))
            .background(
                if (active) activeColor.copy(alpha = bg)
                else if (pressed) scheme.surfaceVariant.copy(alpha = 0.5f)
                else Color.Transparent,
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (active) activeColor else scheme.surfaceVariant.copy(alpha = 0.6f),
                ),
        ) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = label,
                tint = if (active) scheme.onPrimary else scheme.onSurface,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = LabelFont,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) activeColor else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PhonkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    size: Dp = 40.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) background.copy(alpha = 1.1f) else background)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun PhonkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        !enabled -> scheme.surfaceVariant.copy(alpha = 0.4f)
        primary -> scheme.primary
        pressed -> scheme.surfaceVariant
        else -> scheme.surface
    }
    val fg = when {
        !enabled -> scheme.onSurfaceVariant
        primary -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(CornerPill))
            .background(
                if (primary && enabled) Brush.horizontalGradient(
                    listOf(scheme.primary, scheme.tertiary),
                ) else SolidColor(bg),
            )
            .border(
                width = if (primary) 0.dp else 1.dp,
                color = if (primary) Color.Transparent else scheme.outline,
                shape = RoundedCornerShape(CornerPill),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(label, color = fg, fontSize = TitleFont, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PhonkPanel(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(CornerCard))
            .clip(RoundedCornerShape(CornerCard))
            .background(scheme.surface),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
fun PhonkSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.foundation.layout.Column(modifier) {
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun RowScope.TrackSpacer(weight: Float = 1f) {
    Spacer(Modifier.weight(weight))
}
