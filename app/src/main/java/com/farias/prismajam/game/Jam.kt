package com.farias.prismajam.game

/** Grid coordinate. */
data class Cell(val r: Int, val c: Int)

enum class Side { TOP, BOTTOM, LEFT, RIGHT }

/** Which way a block may slide. */
enum class Axis { BOTH, HORIZONTAL, VERTICAL }

/** A colored opening on the board frame. Blocks of the same color leave through it if they fit. */
data class Gate(val side: Side, val start: Int, val length: Int, val color: Int)

/** Block shape, normalized so its bounding box starts at (0, 0). */
class BlockShape(val id: Int, val cells: List<Cell>) {
    val rows: Int = cells.maxOf { it.r } + 1
    val cols: Int = cells.maxOf { it.c } + 1
    val size: Int get() = cells.size

    companion object {
        private val defs = listOf(
            arrayOf("#"),
            arrayOf("##"),
            arrayOf("#", "#"),
            arrayOf("###"),
            arrayOf("#", "#", "#"),
            arrayOf("##", "##"),
            arrayOf("##", "#."),
            arrayOf("##", ".#"),
            arrayOf("#.", "##"),
            arrayOf(".#", "##"),
            arrayOf("#.", "#.", "##"),
            arrayOf("##", "#.", "#."),
            arrayOf("###", "#.."),
            arrayOf("###", "..#"),
            arrayOf("###", ".#."),
            arrayOf("#.", "##", "#."),
            arrayOf("##", "##", "##"),
            arrayOf("###", "###"),
        )

        val all: List<BlockShape> = defs.mapIndexed { i, rows ->
            BlockShape(i, rows.flatMapIndexed { r, line -> line.mapIndexedNotNull { c, ch -> if (ch == '#') Cell(r, c) else null } })
        }

        fun byId(id: Int) = all.getOrNull(id)
    }
}

/** A colored block on the board. [ice] = number of other blocks to clear before it can move. */
data class Block(
    val id: Int,
    val shape: BlockShape,
    val color: Int,
    val r: Int,
    val c: Int,
    val axis: Axis = Axis.BOTH,
    val ice: Int = 0,
) {
    fun cells(): List<Cell> = shape.cells.map { Cell(r + it.r, c + it.c) }
}

/**
 * Pure rules of the color-jam puzzle (no Android). Board is rows x cols; [stones] are fixed cells.
 * Blocks slide one cell at a time and leave the board through a gate of their color they fit in.
 */
class JamBoard(
    val rows: Int,
    val cols: Int,
    val stones: Set<Cell>,
    val gates: List<Gate>,
    blocks: List<Block>,
) {
    private val _blocks = blocks.associateBy { it.id }.toMutableMap()
    val blocks: Collection<Block> get() = _blocks.values

    fun block(id: Int): Block? = _blocks[id]

    val isCleared: Boolean get() = _blocks.isEmpty()

    fun copy(): JamBoard = JamBoard(rows, cols, stones, gates, _blocks.values.toList())

    private fun occupant(r: Int, c: Int, ignore: Int): Int {
        if (Cell(r, c) in stones) return -2
        for (b in _blocks.values) {
            if (b.id == ignore) continue
            val lr = r - b.r
            val lc = c - b.c
            if (lr < 0 || lc < 0 || lr >= b.shape.rows || lc >= b.shape.cols) continue
            if (b.shape.cells.any { it.r == lr && it.c == lc }) return b.id
        }
        return -1
    }

    /** True if [block] could stand with its top-left at (r, c). */
    fun fits(block: Block, r: Int, c: Int): Boolean {
        if (r < 0 || c < 0 || r + block.shape.rows > rows || c + block.shape.cols > cols) return false
        return block.shape.cells.all { occupant(r + it.r, c + it.c, block.id) == -1 }
    }

    fun canMove(block: Block, dr: Int, dc: Int): Boolean {
        if (block.ice > 0) return false
        if (dr != 0 && block.axis == Axis.HORIZONTAL) return false
        if (dc != 0 && block.axis == Axis.VERTICAL) return false
        return fits(block, block.r + dr, block.c + dc)
    }

    /** Moves a block one cell. Returns the moved block or null if blocked. */
    fun step(id: Int, dr: Int, dc: Int): Block? {
        val b = _blocks[id] ?: return null
        if (!canMove(b, dr, dc)) return null
        val moved = b.copy(r = b.r + dr, c = b.c + dc)
        _blocks[id] = moved
        return moved
    }

    /** The gate this block may leave through from its current position on [side], or null. */
    fun exitGate(block: Block, side: Side): Gate? {
        if (block.ice > 0) return null
        if ((side == Side.TOP || side == Side.BOTTOM) && block.axis == Axis.HORIZONTAL) return null
        if ((side == Side.LEFT || side == Side.RIGHT) && block.axis == Axis.VERTICAL) return null
        val touching = when (side) {
            Side.TOP -> block.r == 0
            Side.BOTTOM -> block.r + block.shape.rows == rows
            Side.LEFT -> block.c == 0
            Side.RIGHT -> block.c + block.shape.cols == cols
        }
        if (!touching) return null
        val (from, to) = when (side) {
            Side.TOP, Side.BOTTOM -> block.c to block.c + block.shape.cols - 1
            Side.LEFT, Side.RIGHT -> block.r to block.r + block.shape.rows - 1
        }
        return gates.firstOrNull { g ->
            g.side == side && g.color == block.color && g.start <= from && to <= g.start + g.length - 1
        }
    }

    /** Puts a block at a position already known to be reachable (solver / restore only). */
    fun relocate(block: Block) {
        _blocks[block.id] = block
    }

    /** Takes a block off the board without the exit rules (level generation only). */
    fun remove(id: Int) {
        _blocks.remove(id)
    }

    /** Destroys a block (hammer power-up) and thaws iced blocks like a normal exit. */
    fun smash(id: Int): Boolean {
        if (_blocks.remove(id) == null) return false
        thaw()
        return true
    }

    private fun thaw() {
        for ((k, other) in _blocks.toMap()) {
            if (other.ice > 0) _blocks[k] = other.copy(ice = other.ice - 1)
        }
    }

    /** Block covering cell (r, c), if any. */
    fun blockAt(r: Int, c: Int): Block? = _blocks.values.firstOrNull { b ->
        val lr = r - b.r
        val lc = c - b.c
        lr >= 0 && lc >= 0 && lr < b.shape.rows && lc < b.shape.cols && b.shape.cells.any { it.r == lr && it.c == lc }
    }

    /** Removes a block through a gate (if allowed) and thaws iced blocks. */
    fun exit(id: Int, side: Side): Boolean {
        val b = _blocks[id] ?: return false
        exitGate(b, side) ?: return false
        _blocks.remove(id)
        thaw()
        return true
    }

    /** All top-left positions [block] can reach by sliding, from its current position. */
    fun reachable(block: Block): Set<Cell> {
        val start = Cell(block.r, block.c)
        if (block.ice > 0) return setOf(start)
        val seen = HashSet<Cell>()
        val queue = ArrayDeque<Cell>()
        seen.add(start)
        queue.add(start)
        val dirs = when (block.axis) {
            Axis.BOTH -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
            Axis.HORIZONTAL -> listOf(0 to -1, 0 to 1)
            Axis.VERTICAL -> listOf(-1 to 0, 1 to 0)
        }
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            for ((dr, dc) in dirs) {
                val n = Cell(p.r + dr, p.c + dc)
                if (n in seen) continue
                if (!fits(block, n.r, n.c)) continue
                seen.add(n)
                queue.add(n)
            }
        }
        return seen
    }

    /** A (position, side) from which [block] can currently get out, or null. */
    fun findExit(block: Block): Pair<Cell, Side>? {
        for (p in reachable(block)) {
            val at = block.copy(r = p.r, c = p.c)
            for (side in Side.entries) if (exitGate(at, side) != null) return p to side
        }
        return null
    }
}

object JamSolver {
    /**
     * Clearing a block only frees space (and thaws ice), so greedily removing any block that can
     * leave never hurts: the board is solvable iff this empties it. Returns the number of "waves"
     * (rounds of exits) needed, or -1 if unsolvable. More waves = more ordering to figure out.
     */
    fun waves(board: JamBoard): Int {
        val b = board.copy()
        var rounds = 0
        while (!b.isCleared) {
            val free = b.blocks.filter { b.findExit(it) != null }.map { it.id }
            if (free.isEmpty()) return -1
            rounds++
            for (id in free) {
                val cur = b.block(id) ?: continue
                val (pos, side) = b.findExit(cur) ?: continue
                b.relocate(cur.copy(r = pos.r, c = pos.c))
                b.exit(id, side)
            }
        }
        return rounds
    }

    /** Number of blocks that can leave right now (fewer = harder start). */
    fun freeAtStart(board: JamBoard): Int = board.blocks.count { board.findExit(it) != null }
}
