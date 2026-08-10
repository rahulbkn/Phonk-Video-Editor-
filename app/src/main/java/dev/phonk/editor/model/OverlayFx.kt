package dev.phonk.editor.model

/** Evaluated overlay transform at a destination-timeline instant. */
data class OverlayFx(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val opacity: Float,
)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Interpolates an overlay's transform at [destMs]. Keyframes are honoured when
 * two or more exist (smooth-stepped between the bracketing pair); a beat pulse
 * multiplier scales the overlay on top. This is the single source of truth for
 * overlay motion — the preview and the ffmpeg export both evaluate through here.
 */
fun evaluateOverlayFx(item: OverlayItem, destMs: Long, beatPulse: Float = 1f): OverlayFx {
    var x = item.x
    var y = item.y
    var sx = item.scaleX
    var sy = item.scaleY
    var rot = item.rotation
    var op = item.opacity
    val kf = item.keyframes.sortedBy { it.atMs }
    if (kf.size >= 2) {
        if (destMs <= kf.first().atMs) {
            val k = kf.first(); x = k.x; y = k.y; sx = k.scaleX; sy = k.scaleY; rot = k.rotation; op = k.opacity
        } else if (destMs >= kf.last().atMs) {
            val k = kf.last(); x = k.x; y = k.y; sx = k.scaleX; sy = k.scaleY; rot = k.rotation; op = k.opacity
        } else {
            for (i in 0 until kf.size - 1) {
                val a = kf[i]
                val b = kf[i + 1]
                if (destMs in a.atMs..b.atMs) {
                    val t = (destMs - a.atMs).toFloat() / (b.atMs - a.atMs).coerceAtLeast(1L)
                    val e = smoothStep(t)
                    x = lerp(a.x, b.x, e)
                    y = lerp(a.y, b.y, e)
                    sx = lerp(a.scaleX, b.scaleX, e)
                    sy = lerp(a.scaleY, b.scaleY, e)
                    rot = lerp(a.rotation, b.rotation, e)
                    op = lerp(a.opacity, b.opacity, e)
                    break
                }
            }
        }
    }
    if (beatPulse != 1f) {
        sx *= beatPulse
        sy *= beatPulse
    }
    return OverlayFx(x, y, sx, sy, rot, op)
}
