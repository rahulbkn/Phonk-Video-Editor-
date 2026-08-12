package dev.phonk.editor.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.random.Random

/**
 * Runtime-generated neon cyberpunk artwork (phonk night-city scene).
 *
 * This is real vector art drawn with Canvas — a purple gradient sky, a glowing
 * moon, a dark city skyline with lit windows, a hooded figure, a low muscle
 * car with underglow and purple fog. It is used by the hero banner and as a
 * stylized thumbnail fallback for project cards (seeded per project).
 */
@Composable
fun PhonkArtwork(
    modifier: Modifier,
    seed: Int = 1,
    accent: Color = Color(0xFFA83FFF),
    accentBright: Color = Color(0xFFC45CFF),
) {
    val rnd = Random(seed)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // ─── Sky gradient ──────────────────────────────────────────────────
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF241040),
                0.55f to Color(0xFF140A20),
                1f to Color(0xFF08050D),
            ),
        )

        // ─── Glowing moon ───────────────────────────────────────────────────
        val moon = Offset(w * 0.72f, h * 0.30f)
        val moonR = w * 0.16f
        for (i in 4 downTo 1) {
            drawCircle(
                color = accent.copy(alpha = 0.06f * i),
                radius = moonR * (1f + i * 0.28f),
                center = moon,
            )
        }
        drawCircle(color = Color(0xFFEFD9FF), radius = moonR, center = moon)
        drawCircle(color = accentBright.copy(alpha = 0.55f), radius = moonR, center = moon)

        // ─── Skyline ────────────────────────────────────────────────────────
        val groundY = h * 0.72f
        val buildings = listOf(
            0.02f to 0.34f, 0.10f to 0.22f, 0.19f to 0.40f, 0.27f to 0.18f,
            0.36f to 0.30f, 0.45f to 0.14f, 0.54f to 0.36f, 0.62f to 0.20f,
            0.70f to 0.33f, 0.79f to 0.16f, 0.88f to 0.28f, 0.96f to 0.38f,
        )
        buildings.forEachIndexed { i, (x, bw) ->
            val bh = h * (0.20f + rnd.nextFloat() * 0.20f)
            val left = w * x
            val width = w * bw
            drawRect(
                color = Color(0xFF0B0711),
                topLeft = Offset(left, groundY - bh),
                size = Size(width, bh),
            )
            // antenna on some buildings
            if (i % 3 == 0) {
                drawLine(
                    color = Color(0xFF171022),
                    start = Offset(left + width / 2, groundY - bh),
                    end = Offset(left + width / 2, groundY - bh - h * 0.05f),
                    strokeWidth = w * 0.008f,
                    cap = StrokeCap.Round,
                )
            }
            // lit windows
            repeat(5) { wy ->
                repeat(4) { wx ->
                    if (rnd.nextFloat() > 0.55f) {
                        drawRect(
                            color = if (rnd.nextFloat() > 0.5f) accent.copy(alpha = 0.75f) else Color(0xFF3A2A55),
                            topLeft = Offset(
                                left + width * (0.15f + wx * 0.22f),
                                groundY - bh + h * 0.03f + wy * h * 0.025f,
                            ),
                            size = Size(w * 0.014f, h * 0.014f),
                        )
                    }
                }
            }
        }

        // ─── Purple fog bands ───────────────────────────────────────────────
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.35f to accent.copy(alpha = 0.10f),
                1f to accentBright.copy(alpha = 0.05f),
            ),
            topLeft = Offset(0f, groundY - h * 0.05f),
            size = Size(w, h * 0.10f),
        )

        // ─── Road ───────────────────────────────────────────────────────────
        drawRect(
            color = Color(0xFF060409),
            topLeft = Offset(0f, h * 0.86f),
            size = Size(w, h * 0.14f),
        )
        // road centre dashes
        val dashGap = w * 0.07f
        var dashX = w * 0.03f
        while (dashX < w) {
            drawRect(
                color = accent.copy(alpha = 0.7f),
                topLeft = Offset(dashX, h * 0.93f),
                size = Size(w * 0.035f, h * 0.008f),
            )
            dashX += dashGap
        }

        // ─── Muscle car silhouette with purple underglow ────────────────────
        drawCar(this, w, h, groundY, accentBright)

        // ─── Hooded figure ──────────────────────────────────────────────────
        drawFigure(this, w, h, groundY, accent)

        // ─── Neon streaks ───────────────────────────────────────────────────
        val streaks = listOf(0.10f to 0.9f, 0.55f to 0.7f, 0.85f to 0.85f, 0.35f to 0.5f)
        streaks.forEach { (sx, len) ->
            val startY = h * (0.10f + rnd.nextFloat() * 0.15f)
            drawLine(
                color = accentBright.copy(alpha = 0.35f),
                start = Offset(w * sx, startY),
                end = Offset(w * sx, startY + h * len * 0.22f),
                strokeWidth = w * 0.006f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawCar(ds: DrawScope, w: Float, h: Float, groundY: Float, accent: Color) {
    val carTop = groundY + h * 0.03f
    val carBottom = groundY + h * 0.12f
    val body = Path().apply {
        moveTo(w * 0.16f, carBottom)
        lineTo(w * 0.22f, carTop + h * 0.02f)
        lineTo(w * 0.40f, carTop)
        lineTo(w * 0.60f, carTop)
        lineTo(w * 0.78f, carTop + h * 0.02f)
        lineTo(w * 0.84f, carBottom)
        close()
    }
    // underglow
    drawOval(
        color = accent.copy(alpha = 0.22f),
        topLeft = Offset(w * 0.20f, carBottom - h * 0.012f),
        size = Size(w * 0.62f, h * 0.05f),
    )
    drawPath(body, color = Color(0xFF030208))
    // wheels
    drawCircle(color = Color(0xFF0E0B16), radius = w * 0.035f, center = Offset(w * 0.34f, carBottom))
    drawCircle(color = Color(0xFF0E0B16), radius = w * 0.035f, center = Offset(w * 0.68f, carBottom))
    drawCircle(color = accent.copy(alpha = 0.8f), radius = w * 0.012f, center = Offset(w * 0.34f, carBottom))
    drawCircle(color = accent.copy(alpha = 0.8f), radius = w * 0.012f, center = Offset(w * 0.68f, carBottom))
    // headlight / tail glow
    drawLine(
        color = accent.copy(alpha = 0.85f),
        start = Offset(w * 0.79f, carTop + h * 0.025f),
        end = Offset(w * 0.92f, carTop + h * 0.025f),
        strokeWidth = w * 0.012f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawFigure(ds: DrawScope, w: Float, h: Float, groundY: Float, accent: Color) {
    val headY = groundY - h * 0.24f
    val bodyY = groundY + h * 0.015f
    val cx = w * 0.30f
    // figure body (hooded silhouette)
    val figure = Path().apply {
        moveTo(cx - w * 0.035f, headY)
        quadraticBezierTo(
            cx - w * 0.045f, headY + h * 0.07f,
            cx - w * 0.05f, bodyY - h * 0.06f,
        )
        lineTo(cx - w * 0.02f, bodyY)
        lineTo(cx + w * 0.02f, bodyY)
        lineTo(cx + w * 0.05f, bodyY - h * 0.06f)
        quadraticBezierTo(
            cx + w * 0.045f, headY + h * 0.07f,
            cx + w * 0.035f, headY,
        )
        close()
    }
    drawPath(figure, color = Color(0xFF050308))
    // rim light on the hood side
    drawLine(
        color = accent.copy(alpha = 0.5f),
        start = Offset(cx - w * 0.05f, headY + h * 0.03f),
        end = Offset(cx - w * 0.05f, bodyY - h * 0.06f),
        strokeWidth = w * 0.008f,
        cap = StrokeCap.Round,
    )
    // head
    drawCircle(
        color = Color(0xFF050308),
        radius = w * 0.02f,
        center = Offset(cx, headY + h * 0.005f),
    )
}
