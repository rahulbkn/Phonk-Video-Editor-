package dev.phonk.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.phonk.editor.model.OverlayFx
import dev.phonk.editor.model.OverlayItem
import dev.phonk.editor.model.evaluateOverlayFx
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimum/maximum uniform scale applied by resize / pinch gestures. */
private const val MIN_SCALE = 0.05f
private const val MAX_SCALE = 20f

/** Touch slop for deciding a tap vs a drag (density independent via dp->px). */
private const val TAP_SLOP_DP = 6f

/** Radius (px) around a selection handle that counts as a grab. */
private const val HANDLE_GRAB_PX = 26f

/** Snap-to-guide distance (px). */
private const val SNAP_DIST_PX = 12f

internal enum class GestureMode { NONE, SELECT, MOVE, RESIZE, ROTATE }

internal class HitResult(val mode: GestureMode, val item: OverlayItem?)

private fun dist(a: Offset, b: Offset): Float =
    sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

private fun angleDeg(center: Offset, p: Offset): Float =
    Math.toDegrees(Math.atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat()

/** Rotates a local point (hx, hy) around the origin by [deg] (clockwise). */
private fun rotatePoint(hx: Float, hy: Float, deg: Float): Pair<Float, Float> {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r)
    val s = sin(r)
    return (hx * c - hy * s).toFloat() to (hx * s + hy * c).toFloat()
}

/** True when [px],[py] (screen px) lies inside the rotated centred rect. */
private fun rotatedContains(px: Float, py: Float, cx: Float, cy: Float, halfW: Float, halfH: Float, deg: Float): Boolean {
    val r = Math.toRadians(-deg.toDouble())
    val c = cos(r)
    val s = sin(r)
    val dx = px - cx
    val dy = py - cy
    val lx = (dx * c - dy * s).toFloat()
    val ly = (dx * s + dy * c).toFloat()
    return abs(lx) <= halfW && abs(ly) <= halfH
}

private class GestureLive(
    var x: Float,
    var y: Float,
    var sx: Float,
    var sy: Float,
    var rot: Float,
    var op: Float,
)

/**
 * Editor-only interaction layer drawn inside the video content rect. Owns hit
 * testing, selection, free drag, corner resize, rotation handle, two-finger
 * pinch/rotate, snap guides and double-tap-to-edit. Nothing here is exported.
 */
@Composable
fun BoxScope.OverlayEditorLayer(
    activeItems: List<OverlayItem>,
    selectedId: String?,
    sizes: Map<String, IntSize>,
    boxWpx: Float,
    boxHpx: Float,
    fxFor: (OverlayItem) -> OverlayFx,
    onSelect: (String?) -> Unit,
    onTransformBegin: () -> Unit,
    onTransformLive: (id: String, x: Float, y: Float, sx: Float, sy: Float, rot: Float, op: Float) -> Unit,
    onTransformEnd: () -> Unit,
    onEditText: (String) -> Unit,
) {
    val activeItemsState = rememberUpdatedState(activeItems)
    val sizesState = rememberUpdatedState(sizes)
    val selectedIdState = rememberUpdatedState(selectedId)
    val boxWState = rememberUpdatedState(boxWpx)
    val boxHState = rememberUpdatedState(boxHpx)
    val fxForState = rememberUpdatedState(fxFor)
    val onSelectState = rememberUpdatedState(onSelect)
    val onBeginState = rememberUpdatedState(onTransformBegin)
    val onLiveState = rememberUpdatedState(onTransformLive)
    val onEndState = rememberUpdatedState(onTransformEnd)
    val onEditState = rememberUpdatedState(onEditText)

    var snapV by remember { mutableStateOf<Float?>(null) }
    var snapH by remember { mutableStateOf<Float?>(null) }
    var lastTapId by remember { mutableStateOf<String?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val items = activeItemsState.value
                    val selectedNow = items.firstOrNull { it.id == selectedIdState.value }
                    val boxW = boxWState.value
                    val boxH = boxHState.value
                    val sizesNow = sizesState.value
                    val fxNow = fxForState.value

                    // ---- hit test: handles first, then topmost item body ----
                    var hitMode = GestureMode.NONE
                    var hitItem: OverlayItem? = null
                    if (selectedNow != null) {
                        val sz = sizesNow[selectedNow.id]
                        if (sz != null) {
                            val fx = fxNow(selectedNow)
                            val halfW = sz.width / 2f * fx.scaleX
                            val halfH = sz.height / 2f * fx.scaleY
                            val cx = fx.x * boxW
                            val cy = fx.y * boxH
                            // rotation handle sits above the top-centre
                            val (rx, ry) = rotatePoint(0f, -halfH - 22f, fx.rotation)
                            if (dist(downPos, Offset(cx + rx, cy + ry)) <= HANDLE_GRAB_PX) {
                                hitMode = GestureMode.ROTATE
                                hitItem = selectedNow
                            } else {
                                val corners = listOf(
                                    rotatePoint(halfW, halfH, fx.rotation),
                                    rotatePoint(-halfW, halfH, fx.rotation),
                                    rotatePoint(halfW, -halfH, fx.rotation),
                                    rotatePoint(-halfW, -halfH, fx.rotation),
                                )
                                for ((hx, hy) in corners) {
                                    if (dist(downPos, Offset(cx + hx, cy + hy)) <= HANDLE_GRAB_PX) {
                                        hitMode = GestureMode.RESIZE
                                        hitItem = selectedNow
                                        break
                                    }
                                }
                            }
                        }
                    }
                    if (hitMode == GestureMode.NONE) {
                        val ordered = items.sortedByDescending { it.zIndex }
                        for (item in ordered) {
                            val sz = sizesNow[item.id] ?: continue
                            val fx = fxNow(item)
                            val halfW = sz.width / 2f * fx.scaleX
                            val halfH = sz.height / 2f * fx.scaleY
                            val cx = fx.x * boxW
                            val cy = fx.y * boxH
                            if (rotatedContains(downPos.x, downPos.y, cx, cy, halfW, halfH, fx.rotation)) {
                                hitItem = item
                                hitMode = if (item.locked) GestureMode.SELECT else GestureMode.MOVE
                                break
                            }
                        }
                    }

                    if (hitItem == null) {
                        onSelectState.value(null)
                        snapV = null
                        snapH = null
                        // Leave the tap unconsumed so the parent preview can toggle controls.
                        return@awaitEachGesture
                    }
                    if (hitItem.id != selectedNow?.id) onSelectState.value(hitItem.id)

                    // lock: selecting is allowed, transforming is not
                    if (hitItem.locked && hitMode != GestureMode.SELECT) {
                        onSelectState.value(hitItem.id)
                        do {
                            val e = awaitPointerEvent()
                            if (e.changes.none { it.pressed }) break
                            e.changes.forEach { it.consume() }
                        } while (true)
                        return@awaitEachGesture
                    }

                    val live = GestureLive(hitItem.x, hitItem.y, hitItem.scaleX, hitItem.scaleY, hitItem.rotation, hitItem.opacity)
                    val sz = sizesNow[hitItem.id]
                    val halfW = if (sz != null) sz.width / 2f * live.sx else 40f
                    val halfH = if (sz != null) sz.height / 2f * live.sy else 40f
                    val center0 = Offset(live.x * boxW, live.y * boxH)
                    var downDelta = Offset.Zero
                    var distToCenter0 = if (sz != null) dist(downPos, center0) else 0f
                    var handleAngle0 = if (sz != null) angleDeg(center0, downPos) else 0f

                    // two-finger rebaseline bookkeeping
                    var id0 = down.id
                    var id1: PointerId? = null
                    var p0 = downPos
                    var p1 = downPos
                    var twoFinger = false
                    var base = GestureLive(live.x, live.y, live.sx, live.sy, live.rot, live.op)
                    var focal0 = downPos
                    var dist0 = 0f
                    var angle0 = 0f

                    var moved = false
                    var totalMove = 0f
                    // Coalesce the whole gesture into ONE undo entry: capture the
                    // pre-gesture project only when the first live update fires.
                    var began = false
                    val emit: () -> Unit = {
                        if (!began) {
                            began = true
                            onBeginState.value()
                        }
                        onLiveState.value(hitItem.id, live.x, live.y, live.sx, live.sy, live.rot, live.op)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        val curFirst = pressed.firstOrNull { it.id == id0 } ?: pressed.first()
                        val prev0 = p0
                        p0 = curFirst.position
                        totalMove += dist(p0, prev0)

                        // a second finger appeared -> enter pinch mode (rebaseline)
                        if (!twoFinger && pressed.size >= 2) {
                            val second = pressed.first { it.id != id0 }
                            id1 = second.id
                            p1 = second.position
                            base = GestureLive(live.x, live.y, live.sx, live.sy, live.rot, live.op)
                            focal0 = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                            dist0 = dist(p0, p1)
                            angle0 = angleDeg(focal0, p1)
                            twoFinger = true
                        }
                        if (twoFinger) {
                            val stillTwo = pressed.firstOrNull { it.id == id1 }
                            if (stillTwo != null) {
                                p1 = stillTwo.position
                                val f = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                                val d = dist(p0, p1)
                                val a = angleDeg(f, p1)
                                val zoom = if (dist0 > 1f) d / dist0 else 1f
                                val rotDelta = a - angle0
                                live.x = (base.x + (f.x - focal0.x) / boxW).coerceIn(-2f, 3f)
                                live.y = (base.y + (f.y - focal0.y) / boxH).coerceIn(-2f, 3f)
                                live.sx = (base.sx * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                live.sy = (base.sy * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                live.rot = base.rot + rotDelta
                                emit()
                                moved = true
                                curFirst.consume()
                                stillTwo.consume()
                            } else {
                                // fell back to one finger; keep dragging as a move
                                twoFinger = false
                                downDelta = Offset(downPos.x - p0.x, downPos.y - p0.y)
                                distToCenter0 = dist(p0, center0)
                            }
                        } else {
                            when (hitMode) {
                                GestureMode.MOVE -> {
                                    val dx = p0.x - downPos.x
                                    val dy = p0.y - downPos.y
                                    var nx = live.x + (dx - downDelta.x) / boxW
                                    var ny = live.y + (dy - downDelta.y) / boxH
                                    downDelta = Offset(dx, dy)
                                    // safe bounds: keep at least ~20% of the item visible
                                    nx = nx.coerceIn(-(halfW * 0.8f) / boxW, 1f + (halfW * 0.8f) / boxW)
                                    ny = ny.coerceIn(-(halfH * 0.8f) / boxH, 1f + (halfH * 0.8f) / boxH)
                                    // snap guides
                                    val cxp = nx * boxW
                                    val cyp = ny * boxH
                                    snapV = null
                                    snapH = null
                                    if (abs(cxp - boxW / 2f) < SNAP_DIST_PX) { nx = 0.5f; snapV = boxW / 2f }
                                    else if (abs(cxp - halfW) < SNAP_DIST_PX) { nx = halfW / boxW; snapV = halfW }
                                    else if (abs((boxW - cxp) - halfW) < SNAP_DIST_PX) { nx = 1f - halfW / boxW; snapV = boxW - halfW }
                                    if (abs(cyp - boxH / 2f) < SNAP_DIST_PX) { ny = 0.5f; snapH = boxH / 2f }
                                    else if (abs(cyp - halfH) < SNAP_DIST_PX) { ny = halfH / boxH; snapH = halfH }
                                    else if (abs((boxH - cyp) - halfH) < SNAP_DIST_PX) { ny = 1f - halfH / boxH; snapH = boxH - halfH }
                                    live.x = nx
                                    live.y = ny
                                    emit()
                                    moved = true
                                }
                                GestureMode.RESIZE -> {
                                    val d = dist(p0, Offset(live.x * boxW, live.y * boxH))
                                    val factor = if (distToCenter0 > 1f) d / distToCenter0 else 1f
                                    val s = (live.sx * factor).coerceIn(MIN_SCALE, MAX_SCALE)
                                    live.sx = s
                                    live.sy = s
                                    distToCenter0 = d
                                    emit()
                                    moved = true
                                }
                                GestureMode.ROTATE -> {
                                    val a = angleDeg(Offset(live.x * boxW, live.y * boxH), p0)
                                    var newRot = live.rot + (a - handleAngle0)
                                    // snap to useful angles
                                    val snapAngles = listOf(-180f, -90f, 0f, 90f, 180f, 270f)
                                    val snapped = snapAngles.firstOrNull { abs(it - newRot) < 8f }
                                    if (snapped != null) newRot = snapped
                                    live.rot = newRot
                                    handleAngle0 = a
                                    emit()
                                    moved = true
                                }
                                else -> Unit
                            }
                            curFirst.consume()
                        }
                    }

                    snapV = null
                    snapH = null
                    onEndState.value()

                    if (!moved && totalMove < with(density) { TAP_SLOP_DP.dp.toPx() }) {
                        // tap: track double-tap on the same text overlay
                        val now = System.currentTimeMillis()
                        val dbl = hitItem is dev.phonk.editor.model.TextLayer &&
                            hitItem.id == lastTapId &&
                            now - lastTapTime < 400L &&
                            dist(downPos, lastTapPos) < with(density) { 60.dp.toPx() }
                        lastTapId = hitItem.id
                        lastTapTime = now
                        lastTapPos = downPos
                        if (dbl) onEditState.value(hitItem.id)
                    }
                }
            },
    ) {
        // ---- selection box (editor-only) ----
        val sel = activeItems.firstOrNull { it.id == selectedId }
        if (sel != null) {
            val size = sizes[sel.id]
            if (size != null) {
                val fx = fxFor(sel)
                val halfW = size.width / 2f * fx.scaleX
                val halfH = size.height / 2f * fx.scaleY
                val cx = fx.x * boxWpx
                val cy = fx.y * boxHpx
                val wPx = (halfW * 2f).coerceAtLeast(2f)
                val hPx = (halfH * 2f).coerceAtLeast(2f)
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .offset { IntOffset((cx - halfW).toInt(), (cy - halfH).toInt()) }
                        .size(with(density) { wPx.dp }, with(density) { hPx.dp })
                        .graphicsLayer { rotationZ = fx.rotation }
                        .border(1.dp, Color.White.copy(alpha = 0.95f)),
                ) {
                    HandleDot(Alignment.TopStart)
                    HandleDot(Alignment.TopEnd)
                    HandleDot(Alignment.BottomStart)
                    HandleDot(Alignment.BottomEnd)
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = -26.dp)
                            .size(16.dp)
                            .graphicsLayer { rotationZ = -fx.rotation }
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
                    )
                    if (sel is dev.phonk.editor.model.TextLayer) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 18.dp, y = -18.dp)
                                .size(18.dp)
                                .graphicsLayer { rotationZ = -fx.rotation }
                                .background(Color(0xFFE53935), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                        )
                    }
                }
            }
        }
        // snap guides
        snapV?.let { x ->
            Box(
                Modifier
                    .offset { IntOffset(x.toInt(), 0) }
                    .size(1.dp, with(density) { boxHpx.dp })
                    .background(Color(0xFF00E5FF).copy(alpha = 0.7f)),
            )
        }
        snapH?.let { y ->
            Box(
                Modifier
                    .offset { IntOffset(0, y.toInt()) }
                    .size(with(density) { boxWpx.dp }, 1.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.HandleDot(align: Alignment) {
    Box(
        Modifier
            .align(align)
            .offset(x = -6.dp, y = -6.dp)
            .size(12.dp)
            .background(Color.White, CircleShape)
            .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
    )
}
