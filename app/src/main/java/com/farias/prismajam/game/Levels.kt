package com.farias.prismajam.game

import kotlin.math.roundToInt
import kotlin.random.Random

/** A ready-to-play level: always solvable (checked by [JamSolver]). */
class JamLevel(
    val number: Int,
    val board: JamBoard,
    val timeSec: Int,
    val waves: Int,
)

/**
 * Endless, deterministic levels: the same number always gives the same board for every player.
 * Difficulty grows with board size, colors, density, ordering ("waves") and obstacles.
 */
object JamLevels {
    const val MAX_COLORS = 7

    /**
     * Today's challenge: a mid/hard level that is the same for everyone on [day] (yyyyMMdd)
     * but different from the regular levels.
     */
    fun daily(day: Int): JamLevel = level(30 + (day % 37) * 2, salt = day * 2_654_435_761L)

    /** Every 10th level is a "hard" one (crown on the map); the one after is a breather. */
    fun isHard(n: Int) = n >= 10 && n % 10 == 0

    fun level(number: Int, salt: Long = 0L): JamLevel {
        val n = number.coerceAtLeast(1)
        val d = ((n - 1) / 80f).coerceIn(0f, 1f)
        val hard = isHard(n)
        val breather = n > 10 && n % 10 == 1
        // Rounds of exits the player must find, in order (more = more planning).
        val minWaves = when {
            n <= 3 -> 1
            n <= 10 -> 2
            else -> 2 + (d * 2).roundToInt()
        } + (if (hard) 2 else 0) - (if (breather) 1 else 0)
        // How many blocks may leave right away: few = a narrow path, close to a single order.
        val maxFree = when {
            hard -> 1
            breather || n <= 5 -> 99
            n <= 15 -> 4
            d < 0.5f -> 3
            else -> 2
        }
        for (attempt in 0 until 120) {
            val rnd = Random(n * 7_919L + attempt * 104_729L + 3 + salt)
            val built = build(n, d, rnd, maxFree) ?: continue
            val waves = JamSolver.waves(built)
            if (waves < 0) continue
            // Relax the targets only if the generator keeps missing them.
            if (attempt < 90 && JamSolver.freeAtStart(built) > maxFree) continue
            if (attempt < 105 && waves < minWaves) continue
            val blocks = built.blocks.size
            // Tight but fair: ~3 s per block plus thinking time per wave, a bit less as levels get harder.
            val raw = (6 + blocks * 2.8f + waves * 4) * (1.15f - 0.3f * d) * (if (hard) 1.15f else 1f)
            val time = ((raw / 5f).roundToInt() * 5).coerceIn(25, 140)
            return JamLevel(n, built, time, waves)
        }
        // Fallback (practically never): a tiny trivial board.
        return JamLevel(n, fallback(), 40, 1)
    }

    private fun sizeFor(n: Int, rnd: Random): Pair<Int, Int> = when {
        // Portrait boards (more rows than columns) fill the tall phone screen.
        // At most 8 columns so a cell stays finger-sized on phones.
        n <= 3 -> 7 to 5
        n <= 10 -> 8 to 6
        n <= 25 -> 9 to 6
        n <= 50 -> if (rnd.nextBoolean()) 9 to 7 else 10 to 7
        n <= 80 -> 10 to 7
        else -> if (rnd.nextBoolean()) 10 to 8 else 11 to 8
    }

    private fun colorsFor(n: Int): Int = when {
        n <= 2 -> 2
        n <= 6 -> 3
        n <= 15 -> 4
        n <= 35 -> 5
        n <= 70 -> 6
        else -> MAX_COLORS
    }

    private fun build(n: Int, d: Float, rnd: Random, maxFree: Int): JamBoard? {
        val (rows, cols) = sizeFor(n, rnd)
        val k = colorsFor(n)
        val colors = (1..MAX_COLORS).shuffled(rnd).take(k)

        // Stones
        val stones = HashSet<Cell>()
        if (n >= 8) {
            val count = rnd.nextInt(0, 2 + (d * 4).roundToInt())
            repeat(count) { stones.add(Cell(rnd.nextInt(1, rows - 1), rnd.nextInt(1, cols - 1))) }
        }

        // Gates: every color gets 1-2 gates, never overlapping on the same side.
        val gates = ArrayList<Gate>()
        val usedSlots = HashMap<Side, BooleanArray>()
        fun sideLen(s: Side) = if (s == Side.TOP || s == Side.BOTTOM) cols else rows
        for (color in colors) {
            val wanted = if (rnd.nextFloat() < 0.35f + 0.3f * (1 - d)) 2 else 1
            var placed = 0
            var guard = 0
            while (placed < wanted && guard < 40) {
                guard++
                val side = Side.entries[rnd.nextInt(4)]
                val len = sideLen(side)
                val gl = rnd.nextInt(2, minOf(3, len - 1) + 1)
                val start = rnd.nextInt(0, len - gl + 1)
                val slots = usedSlots.getOrPut(side) { BooleanArray(len) }
                // keep one empty slot between gates so they read clearly
                val lo = (start - 1).coerceAtLeast(0)
                val hi = (start + gl).coerceAtMost(len - 1)
                if ((lo..hi).any { slots[it] }) continue
                for (i in start until start + gl) slots[i] = true
                gates.add(Gate(side, start, gl, color))
                placed++
            }
            if (placed == 0) return null
        }

        // Blocks, built "backwards": a block is only added if it could still leave the board with
        // everything already placed. Removing them in reverse order then always works, so the
        // level is solvable by construction, and the board can be packed tight.
        val board = JamBoard(rows, cols, stones, gates, emptyList())
        val freeCells = rows * cols - stones.size
        val targetFill = ((0.48f + 0.24f * d) * freeCells).roundToInt()
        val wantIce = ArrayList<Pair<Int, Int>>() // block id -> desired ice
        var filled = 0
        var nextId = 1
        var guard = 0
        val cellsOf = HashMap<Int, Int>()
        while (filled < targetFill && guard < 900) {
            guard++
            // Keep the colors balanced: usually pick the color with the fewest cells so far.
            val color = if (rnd.nextFloat() < 0.9f) {
                val least = colors.minOf { cellsOf[it] ?: 0 }
                colors.filter { (cellsOf[it] ?: 0) == least }.let { it[rnd.nextInt(it.size)] }
            } else colors[rnd.nextInt(colors.size)]
            val colorGates = gates.filter { it.color == color }
            val shapes = BlockShape.all.filter { s ->
                colorGates.any { g ->
                    if (g.side == Side.TOP || g.side == Side.BOTTOM) s.cols <= g.length else s.rows <= g.length
                }
            }
            if (shapes.isEmpty()) continue
            val shape = shapes[rnd.nextInt(shapes.size)]
            if (shape.size > 3 && n <= 4 && rnd.nextFloat() < 0.7f) continue
            // Late in filling, prefer small pieces to plug the gaps.
            if (filled > targetFill * 0.8f && shape.size > 3 && rnd.nextFloat() < 0.6f) continue
            val r = rnd.nextInt(0, rows - shape.rows + 1)
            val c = rnd.nextInt(0, cols - shape.cols + 1)
            val axis = if (n >= 18 && rnd.nextFloat() < 0.08f + 0.12f * d) {
                if (rnd.nextBoolean()) Axis.HORIZONTAL else Axis.VERTICAL
            } else Axis.BOTH
            val b = Block(nextId, shape, color, r, c, axis)
            if (!board.fits(b, r, c)) continue
            if (board.findExit(b) == null) continue
            board.relocate(b)
            if (n >= 12 && rnd.nextFloat() < 0.07f + 0.10f * d) wantIce.add(nextId to rnd.nextInt(2, 4 + (d * 3).roundToInt()))
            nextId++
            filled += shape.size
            cellsOf[color] = (cellsOf[color] ?: 0) + shape.size
        }
        if (board.blocks.size < 2) return null
        // Ice: block #i is removed after (count - i) others in the reverse order, so cap it there.
        val count = board.blocks.size
        for ((id, ice) in wantIce) {
            val b = board.block(id) ?: continue
            val cap = count - id
            if (cap >= 1) board.relocate(b.copy(ice = minOf(ice, cap)))
        }
        if (JamSolver.waves(board) < 0) return null

        // Tighten: add blocks that cannot leave yet and that shut the way out of a block that could,
        // so only a few moves are possible at any time (a narrow path, close to one order).
        // Every addition is kept only if the level is still solvable.
        var free = JamSolver.freeAtStart(board)
        val maxCells = (0.86f * freeCells).roundToInt()
        var tries = 0
        while (free > maxFree && filled < maxCells && tries < 320) {
            tries++
            val color = colors[rnd.nextInt(colors.size)]
            val colorGates = gates.filter { it.color == color }
            val shapes = BlockShape.all.filter { s ->
                s.size <= 3 && colorGates.any { g ->
                    if (g.side == Side.TOP || g.side == Side.BOTTOM) s.cols <= g.length else s.rows <= g.length
                }
            }
            if (shapes.isEmpty()) continue
            val shape = shapes[rnd.nextInt(shapes.size)]
            val r = rnd.nextInt(0, rows - shape.rows + 1)
            val c = rnd.nextInt(0, cols - shape.cols + 1)
            val b = Block(nextId, shape, color, r, c, Axis.BOTH)
            if (!board.fits(b, r, c)) continue
            if (board.findExit(b) != null) continue
            board.relocate(b)
            val f = JamSolver.freeAtStart(board)
            if (f >= free || JamSolver.waves(board) < 0) {
                board.remove(b.id)
                continue
            }
            free = f
            nextId++
            filled += shape.size
        }
        return board
    }

    /** Blocks left over when the greedy solver gets stuck. */
    private fun stuckBlocks(board: JamBoard): List<Block> {
        val b = board.copy()
        while (!b.isCleared) {
            val free = b.blocks.filter { b.findExit(it) != null }
            if (free.isEmpty()) return b.blocks.toList()
            for (blk in free) {
                val cur = b.block(blk.id) ?: continue
                val (pos, side) = b.findExit(cur) ?: continue
                b.relocate(cur.copy(r = pos.r, c = pos.c))
                b.exit(cur.id, side)
            }
        }
        return emptyList()
    }

    private fun fallback(): JamBoard {
        val gates = listOf(Gate(Side.TOP, 1, 3, 1), Gate(Side.BOTTOM, 1, 3, 2))
        val blocks = listOf(
            Block(1, BlockShape.all[0], 1, 2, 2),
            Block(2, BlockShape.all[0], 2, 3, 3),
        )
        return JamBoard(5, 5, emptySet(), gates, blocks)
    }
}
