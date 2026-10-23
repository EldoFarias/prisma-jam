package com.farias.prismajam.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Draws one glossy, glowing "gem" block inside the square at [topLeft] with side [cell]. */
fun DrawScope.drawGem(
    topLeft: Offset,
    cell: Float,
    color: Color,
    alpha: Float = 1f,
    glow: Boolean = true,
    scale: Float = 1f,
) {
    if (alpha <= 0.01f || scale <= 0.01f) return
    val s = cell * scale
    // Gradients crash with a zero radius/height (e.g. first frame or start of an animation).
    if (s < 1f) return
    val o = Offset(topLeft.x + (cell - s) / 2f, topLeft.y + (cell - s) / 2f)
    val inset = s * 0.05f
    val d = s - inset * 2f
    val x = o.x + inset
    val y = o.y + inset
    val corner = CornerRadius(d * 0.22f)
    val center = Offset(o.x + s / 2f, o.y + s / 2f)

    if (glow) {
        val radius = s * 0.95f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.42f * alpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    // Body: vertical gradient for depth
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Palette.lighter(color, 0.28f), color, Palette.darker(color, 0.30f)),
            startY = y,
            endY = y + d,
        ),
        topLeft = Offset(x, y),
        size = Size(d, d),
        cornerRadius = corner,
        alpha = alpha,
    )
    // Inner bevel
    drawRoundRect(
        color = Color.White.copy(alpha = 0.20f),
        topLeft = Offset(x + d * 0.09f, y + d * 0.09f),
        size = Size(d * 0.82f, d * 0.82f),
        cornerRadius = CornerRadius(d * 0.16f),
        style = Stroke(width = d * 0.045f),
        alpha = alpha,
    )
    // Glossy top highlight
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
            startY = y + d * 0.08f,
            endY = y + d * 0.48f,
        ),
        topLeft = Offset(x + d * 0.12f, y + d * 0.08f),
        size = Size(d * 0.76f, d * 0.38f),
        cornerRadius = CornerRadius(d * 0.14f),
        alpha = alpha,
    )
    // Sparkle
    drawCircle(
        color = Color.White.copy(alpha = 0.75f),
        radius = d * 0.055f,
        center = Offset(x + d * 0.25f, y + d * 0.23f),
        alpha = alpha,
    )
    // Crisp edge
    drawRoundRect(
        color = Palette.darker(color, 0.55f).copy(alpha = 0.55f),
        topLeft = Offset(x, y),
        size = Size(d, d),
        cornerRadius = corner,
        style = Stroke(width = d * 0.03f),
        alpha = alpha,
    )
}

/** Empty board slot: a soft, recessed glass square. */
fun DrawScope.drawEmptyCell(topLeft: Offset, cell: Float) {
    if (cell < 1f) return
    val inset = cell * 0.05f
    val d = cell - inset * 2f
    val corner = CornerRadius(d * 0.2f)
    drawRoundRect(
        color = Palette.EmptyCell,
        topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
        size = Size(d, d),
        cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Palette.EmptyCellInner, Color.Transparent),
            startY = topLeft.y + inset,
            endY = topLeft.y + inset + d * 0.6f,
        ),
        topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
        size = Size(d, d),
        cornerRadius = corner,
    )
}

/** A faceted diamond drawn on top of a block that holds a collectible gem (adventure mode). */
fun DrawScope.drawGemIcon(center: Offset, size: Float, twinkle: Float = 1f, alpha: Float = 1f) {
    if (size < 2f || alpha <= 0.01f) return
    val w = size * 0.62f
    val h = size * 0.56f
    val top = center.y - h * 0.48f
    val girdle = center.y - h * 0.12f
    val bottom = center.y + h * 0.52f
    val left = center.x - w / 2f
    val right = center.x + w / 2f
    // glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF67E8F9).copy(alpha = 0.55f * alpha * twinkle), Color.Transparent),
            center = center, radius = size * 0.6f,
        ),
        radius = size * 0.6f, center = center,
    )
    val crown = androidx.compose.ui.graphics.Path().apply {
        moveTo(left + w * 0.22f, top)
        lineTo(right - w * 0.22f, top)
        lineTo(right, girdle)
        lineTo(left, girdle)
        close()
    }
    val pavilion = androidx.compose.ui.graphics.Path().apply {
        moveTo(left, girdle)
        lineTo(right, girdle)
        lineTo(center.x, bottom)
        close()
    }
    drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFA5F3FC)), top, girdle), alpha = alpha)
    drawPath(pavilion, Brush.verticalGradient(listOf(Color(0xFF67E8F9), Color(0xFF0E7490)), girdle, bottom), alpha = alpha)
    val edge = Stroke(width = size * 0.035f)
    drawPath(crown, Color(0xFF0E7490).copy(alpha = 0.8f), alpha = alpha, style = edge)
    drawPath(pavilion, Color(0xFF0E7490).copy(alpha = 0.8f), alpha = alpha, style = edge)
    drawLine(Color(0xFF0E7490).copy(alpha = 0.5f * alpha), Offset(center.x, girdle), Offset(center.x, bottom), size * 0.025f)
    // sparkle
    val s = size * 0.12f * twinkle
    val sp = Offset(left + w * 0.3f, top + (girdle - top) * 0.45f)
    drawLine(Color.White.copy(alpha = alpha), Offset(sp.x - s, sp.y), Offset(sp.x + s, sp.y), size * 0.03f)
    drawLine(Color.White.copy(alpha = alpha), Offset(sp.x, sp.y - s), Offset(sp.x, sp.y + s), size * 0.03f)
}
