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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
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
    val m = cell * BLOCK_GAP
    val rad = cell * 0.26f
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
        var piece = Path().apply {
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
        // Inner (concave) corners: cut the little square that would stick out past the neighbours' edges.
        val cuts = ArrayList<Rect>()
        if (right && down && Cell(c.r + 1, c.c + 1) !in set) cuts.add(Rect(r - m, b - m, r, b))
        if (left && down && Cell(c.r + 1, c.c - 1) !in set) cuts.add(Rect(l, b - m, l + m, b))
        if (right && up && Cell(c.r - 1, c.c + 1) !in set) cuts.add(Rect(r - m, t, r, t + m))
        if (left && up && Cell(c.r - 1, c.c - 1) !in set) cuts.add(Rect(l, t, l + m, t + m))
        for (cut in cuts) {
            piece = Path().apply { op(piece, Path().apply { addRect(cut) }, PathOperation.Difference) }
        }
        merged = Path().apply { op(merged, piece, PathOperation.Union) }
    }
    if (pathCache.size > 400) pathCache.clear()
    pathCache[key] = merged
    return merged
}

/** Thickness of a block (its side wall), as a fraction of the cell. */
const val BLOCK_DEPTH = 0.14f

/** Gap between a block and the cell edge (so neighbouring blocks read as separate pieces). */
const val BLOCK_GAP = 0.11f

/**
 * A glossy 3D block: soft contact shadow, a darker side wall (thickness), a top face with a diagonal
 * gradient, an inner bevel, a raised "pillow" on every cell, a specular gloss and sparkles.
 * [origin] is the block's top-left in px (the top face when resting on the board).
 */
fun DrawScope.drawJellyBlock(
    block: Block,
    origin: Offset,
    cell: Float,
    color: Color,
    alpha: Float = 1f,
    lifted: Boolean = false,
    whiten: Float = 0f,
    scale: Float = 1f,
) {
    if (cell < 2f || alpha <= 0.01f) return
    translate(origin.x, origin.y) {
        if (scale != 1f) {
            val cx = block.shape.cols * cell / 2f
            val cy = block.shape.rows * cell / 2f
            withTransform({ scale(scale, scale, Offset(cx, cy)) }) { jelly(block, cell, color, alpha, lifted, whiten) }
        } else {
            jelly(block, cell, color, alpha, lifted, whiten)
        }
    }
}

private fun DrawScope.jelly(block: Block, cell: Float, baseColor: Color, alpha: Float, lifted: Boolean, whiten: Float) {
    val shape = block.shape
    val color = if (whiten > 0f) lerp(baseColor, Color.White, whiten) else baseColor
    val path = blockPath(shape.id, shape.cells, cell)
    val w = shape.cols * cell
    val h = shape.rows * cell
    val depth = cell * BLOCK_DEPTH
    val lift = if (lifted) cell * 0.12f else 0f

    // Contact shadow (bigger and softer when lifted)
    translate(left = cell * 0.04f, top = depth + cell * (if (lifted) 0.2f else 0.06f)) {
        drawPath(path, Color.Black.copy(alpha = (if (lifted) 0.22f else 0.32f) * alpha))
        drawPath(path, Color.Black.copy(alpha = 0.10f * alpha), style = Stroke(cell * 0.12f))
    }
    // Glow when lifted
    if (lifted) {
        translate(top = -lift) { drawPath(path, color.copy(alpha = 0.4f * alpha), style = Stroke(cell * 0.2f)) }
    }
    // Side wall: the same outline shifted down, darker, with a lighter rim at the bottom.
    translate(top = depth - lift) {
        drawPath(
            path,
            Brush.verticalGradient(listOf(Palette.darker(color, 0.38f), Palette.darker(color, 0.58f)), 0f, h),
            alpha = alpha,
        )
        drawPath(path, Palette.darker(color, 0.7f).copy(alpha = 0.6f * alpha), style = Stroke(cell * 0.03f))
    }
    translate(top = -lift) {
        // Top face
        drawPath(
            path,
            Brush.linearGradient(
                listOf(Palette.lighter(color, 0.32f), color, Palette.darker(color, 0.12f)),
                start = Offset.Zero, end = Offset(w, h),
            ),
            alpha = alpha,
        )
        clipPath(path) {
            // Soft glossy band across the upper part of the face (smooth candy, no studs).
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.32f),
                    0.35f to Color.White.copy(alpha = 0.12f),
                    1f to Color.White.copy(alpha = 0f),
                    startY = 0f, endY = (cell * 0.75f).coerceAtMost(h),
                ),
                size = Size(w, h), alpha = alpha,
            )
            // Faint seams between the cells give a sense of scale on big blocks.
            for (c in shape.cells) {
                val cx = (c.c + 0.5f) * cell
                val cy = (c.r + 0.5f) * cell
                val rr = (cell * 0.42f).coerceAtLeast(1f)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0f)), Offset(cx, cy - cell * 0.08f), rr),
                    rr, Offset(cx, cy - cell * 0.08f), alpha = alpha,
                )
            }
            // Inner bevel: light on the upper rim, dark on the lower rim.
            drawPath(
                path,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.6f),
                    0.3f to Color.White.copy(alpha = 0.06f),
                    0.7f to Color.Black.copy(alpha = 0.04f),
                    1f to Color.Black.copy(alpha = 0.28f),
                    startY = 0f, endY = h,
                ),
                style = Stroke(cell * 0.16f),
                alpha = alpha,
            )
            // Specular gloss on the upper-left
            val gr = (cell * 1.1f).coerceAtLeast(1f)
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0f)),
                    center = Offset(cell * 0.35f, cell * 0.25f), radius = gr,
                ),
                size = Size(w, h), alpha = alpha,
            )
        }
        // Outline
        drawPath(path, Palette.darker(color, 0.55f).copy(alpha = 0.55f * alpha), style = Stroke(cell * 0.03f))
        // Sparkles
        val first = shape.cells.minWith(compareBy({ it.r }, { it.c }))
        val sx = first.c * cell
        val sy = first.r * cell
        drawCircle(Color.White.copy(alpha = 0.9f * alpha), cell * 0.05f, Offset(sx + cell * 0.22f, sy + cell * 0.2f))
        drawCircle(Color.White.copy(alpha = 0.6f * alpha), cell * 0.025f, Offset(sx + cell * 0.34f, sy + cell * 0.15f))
        if (whiten > 0f) drawPath(path, Color.White.copy(alpha = whiten * 0.6f * alpha))
        arrows(block, cell, alpha)
    }
}

private fun DrawScope.arrows(block: Block, cell: Float, alpha: Float) {
    val shape = block.shape
    val origin = Offset.Zero
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
    translate(top = cell * BLOCK_DEPTH) {
        drawPath(path, Brush.verticalGradient(listOf(Color(0xE67DD3FC), Color(0xE60EA5E9)), 0f, block.shape.rows * cell))
    }
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
    val m = size * 0.05f
    val tl = Offset(origin.x + cell.c * size + m, origin.y + cell.r * size + m)
    val s = Size(size - 2 * m, size - 2 * m)
    val cr = CornerRadius(size * 0.18f)
    val depth = size * BLOCK_DEPTH
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(tl.x + size * 0.04f, tl.y + depth + size * 0.06f), s, cr)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF374151), Color(0xFF1F2937)), tl.y, tl.y + s.height + depth), Offset(tl.x, tl.y + depth), s, cr)
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFF9CA3AF), Color(0xFF6B7280), Color(0xFF4B5563)), tl, tl + Offset(s.width, s.height)), tl, s, cr)
    // Chiselled facets
    val i = size * 0.16f
    drawRoundRect(Color.White.copy(alpha = 0.10f), tl + Offset(i, i), Size(s.width - 2 * i, s.height - 2 * i), CornerRadius(size * 0.1f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0f), Color.Black.copy(alpha = 0.3f)), tl.y, tl.y + s.height),
        tl, s, cr, style = Stroke(size * 0.05f),
    )
    drawCircle(Color.Black.copy(alpha = 0.22f), size * 0.06f, Offset(tl.x + s.width * 0.3f, tl.y + s.height * 0.35f))
    drawCircle(Color.Black.copy(alpha = 0.16f), size * 0.045f, Offset(tl.x + s.width * 0.68f, tl.y + s.height * 0.64f))
}

/**
 * The board as a thick glass slab: front edge (thickness), frame, a recessed well and recessed cells.
 * [o] is the top-left of cell (0,0); [frame] the frame width; [slab] the visible thickness below.
 */
fun DrawScope.drawBoardBase(o: Offset, cell: Float, rows: Int, cols: Int, frame: Float, slab: Float, alert: Color?) {
    val w = cols * cell + frame * 2
    val h = rows * cell + frame * 2
    val cr = CornerRadius(frame * 1.4f)
    // Shadow on the floor
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(frame * 0.2f, slab + frame * 0.5f), Size(w - frame * 0.4f, h), cr)
    // Slab thickness
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2B1D63), Color(0xFF120A30)), h - frame, h + slab), Offset(0f, slab), Size(w, h), cr)
    drawRoundRect(Color(0x33C4B5FD), Offset(0f, slab), Size(w, h), cr, style = Stroke(frame * 0.05f))
    // Frame top
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFF3B2A7A), Color(0xFF241858), Color(0xFF1B1245)), Offset.Zero, Offset(w, h)), Offset.Zero, Size(w, h), cr)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f)), 0f, h),
        Offset.Zero, Size(w, h), cr, style = Stroke(frame * 0.07f),
    )
    if (alert != null) drawRoundRect(alert, Offset.Zero, Size(w, h), cr, style = Stroke(frame * 0.14f))
    // Recessed well
    val wellTl = Offset(o.x - cell * 0.06f, o.y - cell * 0.06f)
    val wellSize = Size(cols * cell + cell * 0.12f, rows * cell + cell * 0.12f)
    val wcr = CornerRadius(cell * 0.24f)
    drawRoundRect(Color(0xFF0B0620), wellTl, wellSize, wcr)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0f)), wellTl.y, wellTl.y + cell * 0.5f),
        wellTl, wellSize, wcr,
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.White.copy(alpha = 0.18f)), wellTl.y, wellTl.y + wellSize.height),
        wellTl, wellSize, wcr, style = Stroke(cell * 0.03f),
    )
    // Cells: shallow tiles
    val m = cell * 0.07f
    for (r in 0 until rows) for (c in 0 until cols) {
        val tl = Offset(o.x + c * cell + m, o.y + r * cell + m)
        val s = Size(cell - 2 * m, cell - 2 * m)
        val tcr = CornerRadius(cell * 0.18f)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF241A4D), Color(0xFF1A1340)), tl.y, tl.y + s.height),
            tl, s, tcr,
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f)), tl.y, tl.y + s.height),
            tl, s, tcr, style = Stroke(cell * 0.025f),
        )
    }
}

/** Flying fragments of shattered blocks, drawn in the board's space. */
fun DrawScope.drawShards(shards: List<com.farias.prismajam.game.Shard>, o: Offset, cell: Float) {
    if (cell < 2f) return
    val path = Path()
    val wall = Path()
    for (s in shards) {
        val t = s.life / s.maxLife
        val alpha = (t / 0.45f).coerceAtMost(1f)
        val k = cell * (0.55f + 0.45f * t)
        val cos = kotlin.math.cos(s.rot)
        val sin = kotlin.math.sin(s.rot)
        val cx = o.x + s.x * cell
        val cy = o.y + s.y * cell
        path.reset()
        wall.reset()
        var i = 0
        while (i < s.pts.size) {
            val px = s.pts[i] * k
            val py = s.pts[i + 1] * k
            val x = cx + px * cos - py * sin
            val y = cy + px * sin + py * cos
            if (i == 0) { path.moveTo(x, y); wall.moveTo(x, y + cell * 0.1f) } else { path.lineTo(x, y); wall.lineTo(x, y + cell * 0.1f) }
            i += 2
        }
        path.close()
        wall.close()
        drawPath(wall, Palette.darker(s.color, 0.5f).copy(alpha = alpha))
        val face = if (s.shade > 0.5f) Palette.lighter(s.color, 0.1f + 0.35f * (s.shade - 0.5f)) else Palette.darker(s.color, 0.2f * (0.5f - s.shade))
        drawPath(path, face.copy(alpha = alpha))
        drawPath(path, Color.White.copy(alpha = 0.45f * alpha), style = Stroke(cell * 0.02f))
    }
}

/**
 * A gate: a dark slot cut into the frame with a lit coloured bar inside and chevrons pointing out.
 * Its light spills softly onto the board floor (no blurry halo).
 */
fun DrawScope.drawGate(
    gate: Gate, boardOrigin: Offset, cell: Float, rows: Int, cols: Int, frame: Float, color: Color, pulse: Float,
    flash: Float = 0f, spill: Boolean = true, bar: Boolean = true,
) {
    val pad = cell * 0.1f
    val th = frame * 0.72f            // bar thickness
    val inset = (frame - th) * 0.5f   // centred in the frame
    val x0 = boardOrigin.x + gate.start * cell + pad
    val x1 = boardOrigin.x + (gate.start + gate.length) * cell - pad
    val y0 = boardOrigin.y + gate.start * cell + pad
    val y1 = boardOrigin.y + (gate.start + gate.length) * cell - pad
    val left = boardOrigin.x
    val top = boardOrigin.y
    val right = boardOrigin.x + cols * cell
    val bottom = boardOrigin.y + rows * cell
    val rect = when (gate.side) {
        Side.TOP -> Rect(x0, top - frame + inset, x1, top - inset)
        Side.BOTTOM -> Rect(x0, bottom + inset, x1, bottom + frame - inset)
        Side.LEFT -> Rect(left - frame + inset, y0, left - inset, y1)
        Side.RIGHT -> Rect(right + inset, y0, right + frame - inset, y1)
    }
    val horizontal = gate.side == Side.TOP || gate.side == Side.BOTTOM
    val light = 0.16f + 0.10f * pulse + 0.35f * flash
    // Light spilling onto the floor of the board, fading inwards.
    val spillLen = cell * 0.55f
    val (spillRect, spillBrush) = when (gate.side) {
        Side.TOP -> Rect(x0, top, x1, top + spillLen) to Brush.verticalGradient(listOf(color.copy(alpha = light), Color.Transparent), top, top + spillLen)
        Side.BOTTOM -> Rect(x0, bottom - spillLen, x1, bottom) to Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = light)), bottom - spillLen, bottom)
        Side.LEFT -> Rect(left, y0, left + spillLen, y1) to Brush.horizontalGradient(listOf(color.copy(alpha = light), Color.Transparent), left, left + spillLen)
        Side.RIGHT -> Rect(right - spillLen, y0, right, y1) to Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = light)), right - spillLen, right)
    }
    if (spill && false) drawRect(spillBrush, spillRect.topLeft, spillRect.size) // light spill disabled: looked like stray bands
    if (!bar) return
    // Slot cut into the frame
    val cr = CornerRadius(th * 0.5f)
    val slotPad = frame * 0.07f
    drawRoundRect(
        Color(0xFF0B0620),
        Offset(rect.left - slotPad, rect.top - slotPad),
        Size(rect.width + slotPad * 2, rect.height + slotPad * 2),
        CornerRadius(th * 0.5f + slotPad),
    )
    // Lit bar
    drawRoundRect(
        if (horizontal) Brush.verticalGradient(listOf(Palette.lighter(color, 0.35f), color, Palette.darker(color, 0.2f)), rect.top, rect.bottom)
        else Brush.horizontalGradient(listOf(Palette.lighter(color, 0.35f), color, Palette.darker(color, 0.2f)), rect.left, rect.right),
        rect.topLeft, rect.size, cr,
    )
    // Glass shine on the bar
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0f)), rect.top, rect.top + rect.height * 0.6f),
        rect.topLeft, rect.size, cr,
    )
    if (flash > 0f) drawRoundRect(Color.White.copy(alpha = 0.7f * flash), rect.topLeft, rect.size, cr)
    // Chevrons
    val w = Color.White.copy(alpha = 0.9f)
    val sw = th * 0.16f
    val a = th * 0.26f
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
