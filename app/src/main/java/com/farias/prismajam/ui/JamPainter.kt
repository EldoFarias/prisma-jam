package com.farias.prismajam.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import com.farias.prismajam.game.Axis
import com.farias.prismajam.game.Block
import com.farias.prismajam.game.Cell
import com.farias.prismajam.game.Gate
import com.farias.prismajam.game.Side

private val pathCache = HashMap<Long, Path>()

/**
 * Outline of a polyomino as ONE merged path at (0, 0): outer corners rounded, shared edges seamless
 * (so outlines don't show the cell seams). Cached per shape and cell size.
 */
fun blockPath(shapeId: Int, cells: List<Cell>, cell: Float): Path {
    val key = shapeId.toLong() * 100_000L + (cell * 10f).toLong()
    pathCache[key]?.let { return it }
    val set = cells.toHashSet()
    val m = cell * 0.06f
    val rad = cell * 0.24f
    var merged = Path()
    for (c in cells) {
        val up = Cell(c.r - 1, c.c) in set
        val down = Cell(c.r + 1, c.c) in set
        val left = Cell(c.r, c.c - 1) in set
        val right = Cell(c.r, c.c + 1) in set
        val l = c.c * cell + if (left) 0f else m
        val t = c.r * cell + if (up) 0f else m
        val r = (c.c + 1) * cell - if (right) 0f else m
        val b = (c.r + 1) * cell - if (down) 0f else m
        fun corner(round: Boolean) = if (round) CornerRadius(rad) else CornerRadius.Zero
        val piece = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(l, t, r, b),
                    topLeft = corner(!up && !left),
                    topRight = corner(!up && !right),
                    bottomRight = corner(!down && !right),
                    bottomLeft = corner(!down && !left),
                )
            )
        }
        merged = Path().apply { op(merged, piece, PathOperation.Union) }
    }
    if (pathCache.size > 400) pathCache.clear()
    pathCache[key] = merged
    return merged
}

/**
 * A glossy "jelly" block: soft shadow, vertical gradient body, top shine, a soft dimple on every
 * cell and a crisp edge. [origin] is the block's top-left in px.
 */
fun DrawScope.drawJellyBlock(
    block: Block,
    origin: Offset,
    cell: Float,
    color: Color,
    alpha: Float = 1f,
    lifted: Boolean = false,
) {
    if (cell < 2f || alpha <= 0.01f) return
    val shape = block.shape
    translate(origin.x, origin.y) { jelly(block, cell, color, alpha, lifted) }
}

private fun DrawScope.jelly(block: Block, cell: Float, color: Color, alpha: Float, lifted: Boolean) {
    val shape = block.shape
    val origin = Offset.Zero
    val path = blockPath(shape.id, shape.cells, cell)
    val top = 0f
    val bottom = shape.rows * cell
    val shadow = if (lifted) cell * 0.16f else cell * 0.07f

    // Shadow
    translate(top = shadow, left = shadow * 0.3f) {
        drawPath(path, Color.Black.copy(alpha = (if (lifted) 0.35f else 0.25f) * alpha))
    }
    // Glow when lifted
    if (lifted) {
        drawPath(path, color.copy(alpha = 0.35f * alpha), style = Stroke(cell * 0.18f))
    }
    // Body
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Palette.lighter(color, 0.30f), color, Palette.darker(color, 0.28f)),
            startY = top, endY = bottom,
        ),
        alpha = alpha,
    )
    // Dimples + shine, clipped to the block
    clipPath(path) {
        for (c in shape.cells) {
            val cx = origin.x + (c.c + 0.5f) * cell
            val cy = origin.y + (c.r + 0.5f) * cell
            val s = cell * 0.52f
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.02f)),
                    startY = cy - s / 2f, endY = cy + s / 2f,
                ),
                topLeft = Offset(cx - s / 2f, cy - s / 2f),
                size = Size(s, s),
                cornerRadius = CornerRadius(s * 0.35f),
                alpha = alpha,
            )
        }
        drawRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0f)),
                startY = top, endY = top + cell * 0.55f,
            ),
            topLeft = Offset(origin.x, top),
            size = Size(shape.cols * cell, cell * 0.55f),
            alpha = alpha,
        )
    }
    // Edge
    drawPath(path, Palette.darker(color, 0.5f).copy(alpha = 0.55f * alpha), style = Stroke(cell * 0.035f))
    drawPath(path, Color.White.copy(alpha = 0.18f * alpha), style = Stroke(cell * 0.02f))

    // Direction-locked blocks show their track.
    if (block.axis != Axis.BOTH) {
        val cx = origin.x + shape.cols * cell / 2f
        val cy = origin.y + shape.rows * cell / 2f
        val len = cell * 0.32f
        val w = Color.White.copy(alpha = 0.85f * alpha)
        val sw = cell * 0.07f
        if (block.axis == Axis.HORIZONTAL) {
            drawLine(w, Offset(cx - len, cy), Offset(cx + len, cy), sw, StrokeCap.Round)
            drawLine(w, Offset(cx - len, cy), Offset(cx - len * 0.6f, cy - len * 0.4f), sw, StrokeCap.Round)
            drawLine(w, Offset(cx - len, cy), Offset(cx - len * 0.6f, cy + len * 0.4f), sw, StrokeCap.Round)
            drawLine(w, Offset(cx + len, cy), Offset(cx + len * 0.6f, cy - len * 0.4f), sw, StrokeCap.Round)
            drawLine(w, Offset(cx + len, cy), Offset(cx + len * 0.6f, cy + len * 0.4f), sw, StrokeCap.Round)
        } else {
            drawLine(w, Offset(cx, cy - len), Offset(cx, cy + len), sw, StrokeCap.Round)
            drawLine(w, Offset(cx, cy - len), Offset(cx - len * 0.4f, cy - len * 0.6f), sw, StrokeCap.Round)
            drawLine(w, Offset(cx, cy - len), Offset(cx + len * 0.4f, cy - len * 0.6f), sw, StrokeCap.Round)
            drawLine(w, Offset(cx, cy + len), Offset(cx - len * 0.4f, cy + len * 0.6f), sw, StrokeCap.Round)
            drawLine(w, Offset(cx, cy + len), Offset(cx + len * 0.4f, cy + len * 0.6f), sw, StrokeCap.Round)
        }
    }
}

/** Frost over an iced block. The number of blocks still to clear is drawn by the caller. */
fun DrawScope.drawIce(block: Block, origin: Offset, cell: Float) {
    if (cell < 2f) return
    translate(origin.x, origin.y) { ice(block, cell) }
}

private fun DrawScope.ice(block: Block, cell: Float) {
    val origin = Offset.Zero
    val path = blockPath(block.shape.id, block.shape.cells, cell)
    drawPath(
        path,
        Brush.linearGradient(
            listOf(Color(0xCCE0F2FE), Color(0x99BAE6FD), Color(0xCCF0F9FF)),
            start = origin,
            end = Offset(origin.x + block.shape.cols * cell, origin.y + block.shape.rows * cell),
        ),
    )
    drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(cell * 0.04f))
    clipPath(path) {
        val crack = Color.White.copy(alpha = 0.7f)
        val sw = cell * 0.025f
        for (c in block.shape.cells) {
            val x = origin.x + c.c * cell
            val y = origin.y + c.r * cell
            drawLine(crack, Offset(x + cell * 0.2f, y + cell * 0.25f), Offset(x + cell * 0.45f, y + cell * 0.45f), sw)
            drawLine(crack, Offset(x + cell * 0.45f, y + cell * 0.45f), Offset(x + cell * 0.4f, y + cell * 0.75f), sw)
            drawLine(crack, Offset(x + cell * 0.45f, y + cell * 0.45f), Offset(x + cell * 0.78f, y + cell * 0.55f), sw)
        }
    }
}

fun DrawScope.drawStone(cell: Cell, origin: Offset, size: Float) {
    if (size < 2f) return
    val m = size * 0.06f
    val tl = Offset(origin.x + cell.c * size + m, origin.y + cell.r * size + m)
    val s = Size(size - 2 * m, size - 2 * m)
    drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(tl.x, tl.y + size * 0.06f), s, CornerRadius(size * 0.2f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF6B7280), Color(0xFF374151)), tl.y, tl.y + s.height),
        tl, s, CornerRadius(size * 0.2f),
    )
    drawRoundRect(Color.White.copy(alpha = 0.12f), tl, s, CornerRadius(size * 0.2f), style = Stroke(size * 0.03f))
    drawCircle(Color.Black.copy(alpha = 0.18f), size * 0.07f, Offset(tl.x + s.width * 0.3f, tl.y + s.height * 0.35f))
    drawCircle(Color.Black.copy(alpha = 0.14f), size * 0.05f, Offset(tl.x + s.width * 0.68f, tl.y + s.height * 0.62f))
}

/** A glowing gate on the board frame, with chevrons pointing out. */
fun DrawScope.drawGate(gate: Gate, boardOrigin: Offset, cell: Float, rows: Int, cols: Int, frame: Float, color: Color, pulse: Float) {
    val pad = cell * 0.08f
    val rect = when (gate.side) {
        Side.TOP -> Rect(boardOrigin.x + gate.start * cell + pad, boardOrigin.y - frame, boardOrigin.x + (gate.start + gate.length) * cell - pad, boardOrigin.y)
        Side.BOTTOM -> Rect(boardOrigin.x + gate.start * cell + pad, boardOrigin.y + rows * cell, boardOrigin.x + (gate.start + gate.length) * cell - pad, boardOrigin.y + rows * cell + frame)
        Side.LEFT -> Rect(boardOrigin.x - frame, boardOrigin.y + gate.start * cell + pad, boardOrigin.x, boardOrigin.y + (gate.start + gate.length) * cell - pad)
        Side.RIGHT -> Rect(boardOrigin.x + cols * cell, boardOrigin.y + gate.start * cell + pad, boardOrigin.x + cols * cell + frame, boardOrigin.y + (gate.start + gate.length) * cell - pad)
    }
    // Glow
    drawRoundRect(
        color.copy(alpha = 0.28f + 0.17f * pulse),
        topLeft = Offset(rect.left - frame * 0.35f, rect.top - frame * 0.35f),
        size = Size(rect.width + frame * 0.7f, rect.height + frame * 0.7f),
        cornerRadius = CornerRadius(frame),
    )
    // Bar
    val horizontal = gate.side == Side.TOP || gate.side == Side.BOTTOM
    drawRoundRect(
        if (horizontal) Brush.verticalGradient(listOf(Palette.lighter(color, 0.25f), Palette.darker(color, 0.15f)), rect.top, rect.bottom)
        else Brush.horizontalGradient(listOf(Palette.lighter(color, 0.25f), Palette.darker(color, 0.15f)), rect.left, rect.right),
        topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(frame * 0.45f),
    )
    drawRoundRect(Color.White.copy(alpha = 0.35f), rect.topLeft, rect.size, CornerRadius(frame * 0.45f), style = Stroke(frame * 0.08f))
    // Chevrons
    val w = Color.White.copy(alpha = 0.85f)
    val sw = frame * 0.12f
    val a = frame * 0.22f
    val count = gate.length
    for (i in 0 until count) {
        val t = (i + 0.5f) / count
        val center = if (horizontal) Offset(rect.left + rect.width * t, rect.center.y) else Offset(rect.center.x, rect.top + rect.height * t)
        when (gate.side) {
            Side.TOP -> { drawLine(w, Offset(center.x - a, center.y + a * 0.5f), Offset(center.x, center.y - a * 0.5f), sw, StrokeCap.Round); drawLine(w, Offset(center.x + a, center.y + a * 0.5f), Offset(center.x, center.y - a * 0.5f), sw, StrokeCap.Round) }
            Side.BOTTOM -> { drawLine(w, Offset(center.x - a, center.y - a * 0.5f), Offset(center.x, center.y + a * 0.5f), sw, StrokeCap.Round); drawLine(w, Offset(center.x + a, center.y - a * 0.5f), Offset(center.x, center.y + a * 0.5f), sw, StrokeCap.Round) }
            Side.LEFT -> { drawLine(w, Offset(center.x + a * 0.5f, center.y - a), Offset(center.x - a * 0.5f, center.y), sw, StrokeCap.Round); drawLine(w, Offset(center.x + a * 0.5f, center.y + a), Offset(center.x - a * 0.5f, center.y), sw, StrokeCap.Round) }
            Side.RIGHT -> { drawLine(w, Offset(center.x - a * 0.5f, center.y - a), Offset(center.x + a * 0.5f, center.y), sw, StrokeCap.Round); drawLine(w, Offset(center.x - a * 0.5f, center.y + a), Offset(center.x + a * 0.5f, center.y), sw, StrokeCap.Round) }
        }
    }
}
