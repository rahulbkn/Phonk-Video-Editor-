package dev.phonk.editor.ui.editor

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.ui.PlayerView
import dev.phonk.editor.R
import dev.phonk.editor.model.BeatSyncEngine
import dev.phonk.editor.model.ColorGrade
import dev.phonk.editor.model.EffectKind
import dev.phonk.editor.model.OverlayFx
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.OverlayLayer
import dev.phonk.editor.model.PhonkProject
import dev.phonk.editor.model.TextLayer
import dev.phonk.editor.model.evaluateOverlayFx
import dev.phonk.editor.preview.PlayerController
import dev.phonk.editor.settings.SettingsManager
import dev.phonk.editor.ui.components.PhonkIconButton
import dev.phonk.editor.util.TimeUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = 2.0 * kotlin.math.PI

/** Small reusable noise texture for the film-grain overlay (allocated once). */
private val grainBitmap: Bitmap by lazy {
    val w = 128
    val h = 128
    val px = IntArray(w * h)
    val random = java.util.Random(42)
    for (i in px.indices) {
        val v = 96 + random.nextInt(128)
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** Per-frame visual state computed for the real-time effect preview. */
private class FxFrame {
    var renderEffect: RenderEffect? = null
    var transX = 0f
    var transY = 0f
    var scale = 1f
    var flashAlpha = 0f
    var scanY: Float? = null
    var vignetteAlpha = 0f
    var grainAlpha = 0f
    var grainFlicker = 0f
}

/**
 * Editor preview. Renders the source video through the player, then applies a
 * live effect overlay (color grade matrix + real-time FLASH/GLITCH/SHAKE/ZOOM)
 * so edits are visible immediately instead of only after export.
 */
@Composable
fun EditorPreview(
    playerController: PlayerController,
    project: PhonkProject?,
    isPlaying: Boolean,
    positionMs: Long,
    destPlayheadMs: Long,
    onPlayPause: () -> Unit,
    selectedOverlayId: String?,
    onOverlaySelect: (String?) -> Unit,
    onOverlayTransformBegin: () -> Unit,
    onOverlayTransformLive: (String, Float, Float, Float, Float, Float, Float) -> Unit,
    onOverlayTransformEnd: () -> Unit,
    onEditText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var fullscreen by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(9f / 16f) }
    var showControls by remember { mutableStateOf(true) }
    val imageCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val totalMs = project?.videoDurationMs ?: 0L

    val fxPhase by rememberInfiniteTransition(label = "fx").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "fxPhase",
    )
    val frame = computeFxFrame(project, destPlayheadMs, positionMs, fxPhase)

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(if (fullscreen) 0.dp else 14.dp))
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center,
    ) {
        val ld = LocalDensity.current
        val previewW = with(ld) { maxWidth.toPx() }
        val previewH = with(ld) { maxHeight.toPx() }
        Box(
            modifier = Modifier
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(aspect))
                .graphicsLayer {
                    translationX = frame.transX
                    translationY = frame.transY
                    scaleX = frame.scale
                    scaleY = frame.scale
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    val v = LayoutInflater.from(ctx)
                        .inflate(R.layout.player_view, null, false) as PlayerView
                    v.player = playerController.player
                    v
                },
                update = { view ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        view.setRenderEffect(frame.renderEffect)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (frame.flashAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .alpha(frame.flashAlpha),
                )
            }
            frame.scanY?.let { y ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .offset(y = (y * 500).dp)
                        .background(Color.White.copy(alpha = 0.55f)),
                )
            }
            val grain = remember { grainBitmap.asImageBitmap() }
            if (frame.grainAlpha > 0.004f) {
                Image(
                    bitmap = grain,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha((frame.grainAlpha * frame.grainFlicker).coerceIn(0f, 1f)),
                )
            }
            if (frame.vignetteAlpha > 0.004f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val r = size.maxDimension * 0.72f
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val shader = RadialGradient(
                                cx, cy, r,
                                intArrayOf(0x00000000.toInt(), 0xE6000000.toInt()),
                                floatArrayOf(0.5f, 1f),
                                Shader.TileMode.CLAMP,
                            )
                            val paint = Paint().apply { this.shader = shader }
                            drawContext.canvas.nativeCanvas.drawCircle(cx, cy, r, paint)
                        }
                        .graphicsLayer { alpha = frame.vignetteAlpha },
                )
            }

            // Editor overlay canvas: text / sticker / image layers drawn above the
            // video inside the SAME aspect box, so they always align with the video
            // content rectangle and are never hidden by the (TextureView) surface.
            ProjectOverlays(
                project = project,
                destMs = destPlayheadMs,
                sourceMs = positionMs,
                imageCache = imageCache,
                debug = SettingsManager.debugMode,
                selectedOverlayId = selectedOverlayId,
                onOverlaySelect = onOverlaySelect,
                onOverlayTransformBegin = onOverlayTransformBegin,
                onOverlayTransformLive = onOverlayTransformLive,
                onOverlayTransformEnd = onOverlayTransformEnd,
                onEditText = onEditText,
            )
        }

        // Dev diagnostics panel: drawn at the preview level (not inside the
        // aspect box) so it stays visible for any aspect ratio. The CANVAS line
        // reports the exact aspect-box rectangle (preview width x height/aspect).
        if (SettingsManager.debugMode && project != null) {
            DebugOverlayPanel(
                project = project,
                destMs = destPlayheadMs,
                sourceMs = positionMs,
                canvasW = previewW.toInt(),
                canvasH = if (fullscreen) previewH.toInt() else (previewW / aspect).toInt(),
                modifier = Modifier
                    .zIndex(5f)
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
        }

        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        project?.name ?: stringResource(R.string.untitled),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    AspectChip("9:16", aspect == 9f / 16f) { aspect = 9f / 16f }
                    AspectChip("1:1", aspect == 1f) { aspect = 1f }
                    AspectChip("16:9", aspect == 16f / 9f) { aspect = 16f / 9f }
                    Spacer(Modifier.width(6.dp))
                    PhonkIconButton(
                        icon = Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.fullscreen),
                        onClick = { fullscreen = !fullscreen },
                        tint = Color.White,
                        background = Color.White.copy(alpha = 0.15f),
                        size = 34.dp,
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhonkIconButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        onClick = onPlayPause,
                        tint = Color.White,
                        background = scheme.primary,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        TimeUtils.formatClock(positionMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    )
                    Text(
                        TimeUtils.formatClock(totalMs),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/** Composes the live visual frame from project grade + the active clip effect.
 *  [destMs] drives clip effects/keyframes; [sourceMs] drives the beat engine
 *  (beat markers are in media/source time). */
private fun computeFxFrame(
    project: PhonkProject?,
    destMs: Long,
    sourceMs: Long,
    phase: Float,
): FxFrame {
    val frame = FxFrame()
    if (project == null) return frame
    val clip = project.clips.firstOrNull { destMs in it.destStartMs until it.destEndMs }
    val effect = clip?.effect ?: EffectKind.NONE
    val strength = (clip?.effectStrength ?: 0.7f).coerceIn(0f, 1.5f)

    val grade = project.gradeAt(destMs)
    frame.renderEffect = buildRenderEffect(grade, effect, strength, project.beatSyncStrength)
    frame.vignetteAlpha = grade.vignette
    frame.grainAlpha = grade.grain
    frame.grainFlicker = (0.6f + 0.4f * abs(sin(phase * TWO_PI * 11f)).toFloat())

    when (effect) {
        EffectKind.FLASH -> {
            frame.flashAlpha = if ((destMs / 200L) % 2L == 0L) 0.85f else 0f
        }
        EffectKind.GLITCH -> {
            val glitching = phase < 0.12f
            if (glitching) {
                frame.transX = if (phase < 0.06f) -14f else 14f
                frame.transX *= strength
                frame.scanY = 0.2f + 0.6f * phase
            }
        }
        EffectKind.SHAKE -> {
            frame.transX = (sin(phase * TWO_PI) * (10f + 22f * strength)).toFloat()
            frame.transY = (cos(phase * TWO_PI * 1.3f) * 6f * strength).toFloat()
        }
        EffectKind.ZOOM -> {
            frame.scale = 1f + (0.06f * strength * abs(sin(phase * TWO_PI * 2f)).toFloat())
        }
        EffectKind.RGBSPLIT -> {
            frame.transX = (sin(phase * TWO_PI * 3f) * 6f * strength).toFloat()
        }
        else -> Unit
    }

    if (project.beatSync) {
        val beat = BeatSyncEngine.frame(project.beats, project.drops, sourceMs)
        val boost = project.beatSyncStrength
        if (beat.isBeat) {
            frame.scale *= 1f + 0.045f * beat.beatStrength * boost
            frame.flashAlpha = maxOf(frame.flashAlpha, 0.22f * beat.beatStrength * boost)
        }
        if (beat.isDrop) {
            frame.scale *= 1f + 0.14f * beat.dropStrength * boost
            frame.flashAlpha = maxOf(frame.flashAlpha, 0.5f * beat.dropStrength * boost)
            frame.transX += (sin(phase * TWO_PI * 6f) * 9f * beat.dropStrength * boost).toFloat()
        }
    }
    return frame
}

/**
 * Builds an Android color/render effect matching the export grade. One
 * combined color matrix (saturation -> contrast -> brightness -> temperature
 * -> tint) keeps it to a single GPU pass; a blur is layered in via a second
 * render effect when -blur is engaged.
 */
private fun buildRenderEffect(
    grade: ColorGrade,
    effect: EffectKind,
    strength: Float,
    beatSyncStrength: Float,
): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    var b = grade.brightness + grade.exposure * 0.5f
    var c = grade.contrast + grade.highlights * 0.15f
    var s = grade.saturation * (1f - grade.fade * 0.5f)
    b += grade.shadows * 0.3f + grade.fade * 0.35f
    when (effect) {
        EffectKind.BRIGHTNESS -> b += strength * 0.5f
        EffectKind.CONTRAST -> c += strength * 0.5f
        EffectKind.GLITCH -> s = -0.7f
        EffectKind.RGBSPLIT -> s = -0.5f
        else -> Unit
    }
    if (abs(b) < 0.001f && abs(c) < 0.001f && abs(s) < 0.001f &&
        abs(grade.temperature) < 0.001f && abs(grade.tint) < 0.001f &&
        grade.blur < 0.001f
    ) {
        return null
    }
    val cm = ColorMatrix()
    cm.setSaturation((1f + s).coerceIn(0f, 2.5f))

    // Temperature (R/B) and tint (G/M) as per-channel gains.
    val gR = (1f + grade.temperature * 0.18f - grade.tint * 0.1f).coerceIn(0.6f, 1.4f)
    val gG = (1f + grade.tint * 0.18f).coerceIn(0.6f, 1.4f)
    val gB = (1f - grade.temperature * 0.18f - grade.tint * 0.1f).coerceIn(0.6f, 1.4f)
    val channel = floatArrayOf(
        gR, 0f, 0f, 0f, 0f,
        0f, gG, 0f, 0f, 0f,
        0f, 0f, gB, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    cm.postConcat(ColorMatrix(channel))

    val contrastScale = (1f + c * 0.6f).coerceIn(0.4f, 1.9f)
    cm.postConcat(ColorMatrix().apply { setScale(contrastScale, contrastScale, contrastScale, 1f) })

    val bOff = (b * 60f).coerceIn(-90f, 110f)
    val shift = floatArrayOf(
        1f, 0f, 0f, 0f, bOff,
        0f, 1f, 0f, 0f, bOff,
        0f, 0f, 1f, 0f, bOff,
        0f, 0f, 0f, 1f, 0f,
    )
    cm.postConcat(ColorMatrix(shift))

    val colorFx = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
    val blurRadius = grade.blur * 16f
    return if (blurRadius >= 0.5f) {
        val blur = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm), blur)
    } else {
        colorFx
    }
}

@Composable
private fun AspectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary else Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = if (selected) scheme.onPrimary else Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Motion state for a text/sticker layer at a destination-timeline instant. */
private class LayerMotion(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val rotationZ: Float = 0f,
    val transX: Float = 0f,
    val transY: Float = 0f,
)

private fun easeInOut(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Runs the layer's named animation against the elapsed/remaining time. */
private fun layerMotion(layer: TextLayer, destMs: Long): LayerMotion {
    val elapsed = destMs - layer.startMs
    val remaining = layer.endMs - destMs
    var alpha = 1f
    if (elapsed < 400) alpha *= (elapsed / 400f)
    if (remaining < 600) alpha *= (remaining / 600f)
    val motion = when (layer.animation.lowercase()) {
        "slide" -> LayerMotion(
            transX = -240f * (1f - easeInOut(elapsed / 700f)),
            alpha = alpha,
        )
        "pop" -> LayerMotion(
            scale = 0.6f + 0.4f * easeInOut(elapsed / 450f),
            rotationZ = (1f - easeInOut(elapsed / 450f)) * -12f,
            alpha = alpha,
        )
        "zoom" -> LayerMotion(
            scale = 0.6f + 0.4f * easeInOut(elapsed / 900f),
            alpha = alpha,
        )
        "glitch" -> LayerMotion(
            transX = if (sin(elapsed * 0.06) > 0.72f) 5f else 0f,
            rotationZ = if (sin(elapsed * 0.05) > 0.8f) 2.5f else 0f,
            alpha = alpha,
        )
        else -> LayerMotion(alpha = alpha) // "fade"
    }
    return motion
}

/**
 * The real-time editor overlay canvas. Draws every active text layer and
 * image/sticker layer ABOVE the video preview, time-gated by the destination
 * playhead so scrubbing/playback reveal overlays at the correct moments. Drawn
 * inside the aspect box, so project-space positions map to the video content
 * rectangle directly (no letterbox drift). The interactive selection/transform
 * layer is composed on top here (editor-only, never exported).
 */
@Composable
private fun BoxScope.ProjectOverlays(
    project: PhonkProject?,
    destMs: Long,
    sourceMs: Long,
    imageCache: Map<String, ImageBitmap>,
    debug: Boolean,
    selectedOverlayId: String?,
    onOverlaySelect: (String?) -> Unit,
    onOverlayTransformBegin: () -> Unit,
    onOverlayTransformLive: (String, Float, Float, Float, Float, Float, Float) -> Unit,
    onOverlayTransformEnd: () -> Unit,
    onEditText: (String) -> Unit,
) {
    if (project == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ld = LocalDensity.current
        val wPx = with(ld) { maxWidth.toPx() }
        val hPx = with(ld) { maxHeight.toPx() }
        val refW = 1080f
        val activeTexts = project.textLayers.filter { it.isActiveAt(destMs) }
        val activeOverlays = project.overlays.filter { it.isActiveAt(destMs) }

        // Live beat pulse scales overlays on top of their own transform so the
        // preview reacts to the beat exactly like the export beat-sync.
        var beatPulse = 1f
        if (project.beatSync) {
            val beat = BeatSyncEngine.frame(project.beats, project.drops, sourceMs)
            if (beat.isBeat) beatPulse = 1f + 0.03f * beat.beatStrength * project.beatSyncStrength
            if (beat.isDrop) beatPulse = 1f + 0.09f * beat.dropStrength * project.beatSyncStrength
        }
        val fxFor: (OverlayItem) -> OverlayFx = { item -> evaluateOverlayFx(item, destMs, beatPulse) }

        // ---- Base render size (scale = 1.0) for every overlay item ----
        // Used both to draw and to hit-test/box the selection, so what you touch
        // is exactly what you see. Text is measured once per (text,size) and
        // cached; images/shapes use the same relative sizing as export.
        val measurer = rememberTextMeasurer()
        val textSizeCache = remember { mutableStateMapOf<String, IntSize>() }
        val sizes: Map<String, IntSize> = buildMap {
            for (item in (project.textLayers as List<OverlayItem>) + project.overlays) {
                when (item) {
                    is TextLayer -> {
                        val fontPx = (item.fontSize * (wPx / refW)).coerceAtLeast(8f)
                        val key = "${item.id}|${item.text}|${fontPx.toInt()}"
                        val s = textSizeCache[key] ?: measurer.measure(
                            AnnotatedString(item.text),
                            style = TextStyle(fontSize = fontPx.sp, fontWeight = FontWeight.Bold),
                            constraints = Constraints(maxWidth = wPx.toInt()),
                        ).size.let {
                            IntSize(it.width.coerceAtLeast(1), it.height.coerceAtLeast(1))
                        }
                        textSizeCache[key] = s
                        put(item.id, s)
                    }
                    is OverlayLayer -> {
                        val side = if (item.uri == null) {
                            (wPx * 0.22f).coerceAtMost(hPx * 0.22f)
                        } else {
                            (wPx * 0.4f).coerceAtMost(hPx * 0.4f)
                        }
                        put(item.id, IntSize(side.toInt().coerceAtLeast(2), side.toInt().coerceAtLeast(2)))
                    }
                }
            }
        }

        // ---- Text layers (positioned by normalized centre, scaled/rotated) ----
        activeTexts.forEach { layer ->
            val fx = fxFor(layer)
            val motion = layerMotion(layer, destMs)
            val drawAlpha = (fx.opacity * motion.alpha).coerceIn(0f, 1f)
            if (drawAlpha <= 0.004f) return@forEach
            val sz = sizes[layer.id] ?: return@forEach
            val cx = fx.x * wPx
            val cy = fx.y * hPx
            val fontPx = (layer.fontSize * (wPx / refW)).coerceAtLeast(8f)
            Text(
                text = layer.text,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = fontPx.sp,
                color = Color(layer.colorArgb.toInt()),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.55f),
                        offset = Offset(0f, 2f),
                        blurRadius = 3f,
                    ),
                ),
                modifier = Modifier
                    .offset { IntOffset((cx - sz.width / 2f).toInt(), (cy - sz.height / 2f).toInt()) }
                    .graphicsLayer {
                        scaleX = fx.scaleX * motion.scale
                        scaleY = fx.scaleY * motion.scale
                        rotationZ = fx.rotation + motion.rotationZ
                        alpha = drawAlpha
                    },
            )
        }

        // ---- Image / sticker layers ----
        activeOverlays.forEach { ov ->
            val fx = fxFor(ov)
            val imgAlpha = fx.opacity.coerceIn(0f, 1f)
            if (imgAlpha <= 0.004f) return@forEach
            val sz = sizes[ov.id] ?: return@forEach
            val cx = fx.x * wPx
            val cy = fx.y * hPx
            val placed = Modifier
                .offset { IntOffset((cx - sz.width / 2f).toInt(), (cy - sz.height / 2f).toInt()) }
                .width(with(ld) { sz.width.toDp() })
                .height(with(ld) { sz.height.toDp() })
                .graphicsLayer {
                    scaleX = fx.scaleX
                    scaleY = fx.scaleY
                    rotationZ = fx.rotation
                    alpha = imgAlpha
                }
            if (ov.uri == null) {
                drawShapeFallback(ov, wPx, hPx, placed)
            } else {
                val bmp = rememberBitmapFromUri(ov.uri, imageCache)
                if (bmp == null) return@forEach
                Image(
                    bitmap = bmp,
                    contentDescription = ov.label,
                    contentScale = ContentScale.Fit,
                    modifier = placed,
                )
            }
        }

        // ---- Interactive transform layer (editor-only, never exported) ----
        OverlayEditorLayer(
            activeItems = (activeTexts as List<OverlayItem>) + activeOverlays,
            selectedId = selectedOverlayId,
            sizes = sizes,
            boxWpx = wPx,
            boxHpx = hPx,
            fxFor = fxFor,
            onSelect = onOverlaySelect,
            onTransformBegin = onOverlayTransformBegin,
            onTransformLive = onOverlayTransformLive,
            onTransformEnd = onOverlayTransformEnd,
            onEditText = onEditText,
        )

        // ---- Debug diagnostics (dev-only, never exported, app gated) ----
        // The on-screen readout panel is drawn at the preview level (outside this
        // aspect box) so it is never clipped by aspect-ratio overflow; this log
        // stays here because it knows the true canvas rectangle.
        if (debug) {
            android.util.Log.d("EDPRV", "canvas ${wPx.toInt()}x${hPx.toInt()} d=$destMs texts=${activeTexts.size} imgs=${activeOverlays.size} totalTexts=${project.textLayers.size} totalOvs=${project.overlays.size}")
        }
    }
}

/**
 * Dev-only diagnostics readout. Sits at the preview level (outside the aspect
 * box) so it is visible for any aspect ratio; the CANVAS line still reports
 * the aspect-box rectangle the overlays render into.
 */
@Composable
private fun DebugOverlayPanel(
    project: PhonkProject,
    destMs: Long,
    sourceMs: Long,
    canvasW: Int,
    canvasH: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val activeTexts = project.textLayers.filter { destMs in it.startMs until it.endMs }
        val activeOverlays = project.overlays.filter { destMs in it.startMs until it.endMs }

        var beatPulse = 1f
        if (project.beatSync) {
            val beat = BeatSyncEngine.frame(project.beats, project.drops, sourceMs)
            if (beat.isBeat) beatPulse = 1f + 0.03f * beat.beatStrength * project.beatSyncStrength
            if (beat.isDrop) beatPulse = 1f + 0.09f * beat.dropStrength * project.beatSyncStrength
        }

        Column(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(8.dp),
        ) {
            Text("CANVAS ${canvasW}x${canvasH} @${TimeUtils.formatClock(destMs)}", color = Color(0xFF8BC34A), fontSize = 10.sp)
            Text("PLAYHEAD d=$destMs src=$sourceMs", color = Color(0xFF8BC34A), fontSize = 10.sp)
            Text("LAYERS text=${activeTexts.size}/${project.textLayers.size} img=${activeOverlays.size}/${project.overlays.size} beat=${beatPulse.toString().take(4)}", color = Color(0xFF8BC34A), fontSize = 10.sp)
            Text("ALIGN center · FONT default (bold)", color = Color(0xFF8BC34A), fontSize = 10.sp)
            activeTexts.forEach { t ->
                val m = layerMotion(t, destMs)
                Text(
                    "  TEXT '${t.text.take(16)}' f=${t.fontSize.toInt().toString().take(3)} a=${(t.opacity * m.alpha).toString().take(4)} anim=${t.animation.take(8)} s=${t.startMs}..${t.endMs}",
                    color = Color(0xFFCDDC39), fontSize = 10.sp,
                )
            }
            activeOverlays.forEach { o ->
                Text("  IMG '${o.label.take(18)}' ${o.kind} ${o.startMs}..${o.endMs}", color = Color(0xFFCDDC39), fontSize = 10.sp)
            }
        }
    }
}

/** Renders a non-image overlay (shape/sticker) as its label glyph. */
@Composable
private fun BoxScope.drawShapeFallback(ov: OverlayLayer, wPx: Float, hPx: Float, modifier: Modifier) {
    val label = ov.label.ifBlank { "⬤" }
    val glyph = if (ov.kind.equals("Emoji", ignoreCase = true) || label.length <= 4) label else "⬤"
    val side = (wPx * 0.22f).coerceAtMost(hPx * 0.22f)
    Text(
        text = glyph,
        fontSize = side.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** Loads + downsamples a content-URI image once and caches it for the session. */
@Composable
private fun rememberBitmapFromUri(uri: String?, cache: Map<String, ImageBitmap>): ImageBitmap? {
    if (uri == null) return null
    cache[uri]?.let { return it }
    val ctx = LocalContext.current
    val loaded by produceState<ImageBitmap?>(initialValue = null, key1 = uri, key2 = ctx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = ctx.contentResolver
                val u = android.net.Uri.parse(uri)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxSide > 0 && maxSide / (sample * 2) >= 768) sample *= 2
                val full = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = resolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, full) }
                bmp?.asImageBitmap()
            }.getOrNull()
        }
        if (value != null && uri != null) (cache as MutableMap)[uri] = value!!
    }
    return loaded
}