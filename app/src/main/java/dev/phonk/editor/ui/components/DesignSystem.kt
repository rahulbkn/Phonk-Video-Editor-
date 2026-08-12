package dev.phonk.editor.ui.components

import dev.phonk.editor.R

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

val ToolbarHeight = 52.dp
val TrackHeight = 48.dp
val PanelHeight = 150.dp
val BottomToolHeight = 50.dp

val CornerCard = 16.dp
val CornerChip = 20.dp
val CornerPill = 28.dp

// ─── Shared Editor Design Tokens ─────────────────────────────────────────────
// Central source of truth for control sizes, touch targets, corners, spacing
// and typography so editor controls are consistent across screens.

object EditorTokens {
    // Touch targets (accessibility minimums)
    val IconTarget = 44.dp          // icon-only buttons
    val ToolTarget = 44.dp          // tool / toolbar buttons
    val CompactTarget = 40.dp       // secondary compact controls
    val PrimaryHeight = 48.dp       // primary action buttons
    val StatusBarHeight = 24.dp     // bottom status bar

    // Bar heights (editor chrome)
    val TopBarHeight = 52.dp        // editor top bar
    val AspectBarHeight = 40.dp     // aspect / canvas bar
    val PlayerBarHeight = 48.dp     // playback controls bar
    val TimelineBarHeight = 44.dp   // timeline tool bar
    val MainToolbarHeight = 56.dp   // bottom main tool bar

    // Preview
    val PreviewHeightFraction = 0.30f  // preview max height as fraction of screen height

    // Visual icon sizes (visual can be smaller than the touch target)
    val Icon = 18.dp
    val IconCompact = 16.dp

    // Corner radii
    val CornerControl = 8.dp
    val CornerButton = 10.dp
    val CornerSheet = 16.dp

    // Spacing scale
    val Space4 = 4.dp
    val Space6 = 6.dp
    val Space8 = 8.dp
    val Space10 = 10.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp

    // Typography
    val FontPrimary = 14.sp
    val FontTool = 12.sp
    val FontCompact = 11.sp
    val FontLabel = 10.sp
    val FontRuler = 12.sp
    val FontTrack = 10.sp
    val FontDialogTitle = 18.sp
    val FontDialogBody = 14.sp
    val FontDialogAction = 13.sp
}

object AppDimens {
    val screenPadding = 16.dp
    val screenPaddingLarge = 24.dp
    val cardPadding = 16.dp
    val spacingSmall = 8.dp
    val spacingMedium = 12.dp
    val spacingLarge = 16.dp
    val spacingXLarge = 20.dp
    val cornerRadiusSmall = 8.dp
    val cornerRadiusMedium = 12.dp
    val cornerRadiusLarge = 16.dp
    val cornerRadiusXLarge = 24.dp
    val buttonHeight = 48.dp
    val iconSizeSmall = 16.dp
    val iconSizeMedium = 20.dp
    val iconSizeLarge = 24.dp
    val fabSize = 56.dp
    val bottomNavHeight = 80.dp
    val topBarHeight = 52.dp
    val previewMinHeight = 180.dp
}

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
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) activeColor.copy(alpha = 0.9f)
                else if (pressed) scheme.surfaceVariant.copy(alpha = 0.5f)
                else Color.Transparent,
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = label,
            tint = if (active) Color.White else scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Color.White else scheme.onSurfaceVariant,
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

/** Shared icon-only button with a consistent 44dp touch target. */
@Composable
fun EditorIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    target: Dp = EditorTokens.IconTarget,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(target)
            .clip(RoundedCornerShape(EditorTokens.CornerButton))
            .background(if (pressed) background else background.copy(alpha = 0.85f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(EditorTokens.Icon),
        )
    }
}

/** Shared tool button (icon + label) used in editor toolbars. */
@Composable
fun EditorToolButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = EditorTokens.ToolTarget,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(EditorTokens.CornerButton))
            .background(
                when {
                    active -> activeColor.copy(alpha = 0.9f)
                    pressed -> scheme.surfaceVariant.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = if (active) Color.White else scheme.onSurfaceVariant,
                modifier = Modifier.size(EditorTokens.IconCompact),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            fontSize = EditorTokens.FontLabel,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Shared pill selector chip for preset items (speed, fade, beat, colour). */
@Composable
fun EditorChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    height: Dp = EditorTokens.CompactTarget,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(EditorTokens.CornerButton))
            .background(
                when {
                    !enabled -> scheme.surfaceVariant.copy(alpha = 0.35f)
                    selected -> scheme.primary.copy(alpha = 0.25f)
                    pressed -> scheme.surfaceVariant
                    else -> scheme.surfaceVariant.copy(alpha = 0.7f)
                },
            )
            .border(
                1.dp,
                if (selected) scheme.primary else scheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(EditorTokens.CornerButton),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp),
    ) {
        Text(
            label,
            fontSize = EditorTokens.FontCompact,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                !enabled -> scheme.onSurfaceVariant
                selected -> scheme.primary
                else -> scheme.onSurface
            },
            maxLines = 1,
        )
    }
}

/** Bottom-sheet header row with title + close, for consistent sheets/dialogs. */
@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = EditorTokens.Space16, vertical = EditorTokens.Space8),
    ) {
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        EditorIconButton(
            icon = androidx.compose.material.icons.Icons.Filled.Close,
            contentDescription = "Close",
            onClick = onClose,
            background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
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
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colorResource(R.color.surface))
            .border(0.5.dp, colorResource(R.color.border_panel)),
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
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    PhonkSeekBar(
        progress = fraction,
        onSeek = { f -> onValueChange(valueRange.start + f * (valueRange.endInclusive - valueRange.start)) },
        activeColor = activeColor,
        modifier = modifier,
    )
}

@Composable
fun PhonkSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = colorResource(R.color.border_default),
    thumbSize: Dp = 12.dp,
    trackHeight: Dp = 4.dp,
) {
    var dragging by remember { mutableStateOf(false) }
    val p = progress.coerceIn(0f, 1f)
    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { off -> onSeek((off.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val trackW = maxWidth
        val thumbOff = (trackW * p - thumbSize / 2).coerceIn(0.dp, (trackW - thumbSize).coerceAtLeast(0.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(99.dp))
                .background(inactiveColor),
        )
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(trackHeight)
                .clip(RoundedCornerShape(99.dp))
                .background(activeColor),
        )
        Box(
            Modifier
                .offset(x = thumbOff)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (dragging) Color.White else activeColor),
        )
    }
}

@Composable
fun PhonkProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = colorResource(R.color.surface_control),
    height: Dp = 5.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor),
    ) {
        val f = progress.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(activeColor),
            )
        }
    }
}

@Composable
fun RowScope.TrackSpacer(weight: Float = 1f) {
    Spacer(Modifier.weight(weight))
}
