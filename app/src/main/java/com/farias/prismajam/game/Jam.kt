package com.farias.prismajam.game

/** Grid coordinate. */
data class Cell(val r: Int, val c: Int)

enum class Side { TOP, BOTTOM, LEFT, RIGHT }

/** Which way a block may slide. */
enum class Axis { BOTH, HORIZONTAL, VERTICAL }

/**
 * A colored opening on the board frame. Blocks of the same color leave through it if they fit.
 * A [timed] gate opens and closes in a cycle (the UI drives it through [JamBoard.closedGates]).
 */
data class Gate(val side: Side, val start: Int, val length: Int, val color: Int, val timed: Boolean = false)

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

        /** A shape for any set of cells (normalized); reuses a known id when it matches one. */
        fun of(cells: List<Cell>): BlockShape {
            val r0 = cells.minOf { it.r }
            val c0 = cells.minOf { it.c }
            val norm = cells.map { Cell(it.r - r0, it.c - c0) }.sortedWith(compareBy({ it.r }, { it.c }))
            all.firstOrNull { s -> s.cells.sortedWith(compareBy({ it.r }, { it.c })) == norm }?.let { return it }
            // Stable id from the pattern (cut pieces): used for drawing caches only.
            var code = 17
            for (c in norm) code = code * 31 + c.r * 8 + c.c
            return BlockShape(1000 + (code and 0x3FFFF), norm)
        }
    }
}

/**
 * A colored block on the board.
 * [ice] = number of other blocks to clear before it can move.
 * [inner] = color under the outer shell (0 = single layer): leaving through a gate of [color] only peels the shell.
 * [lock] > 0 = padlocked until the block carrying [key] == lock leaves the board.
 */
data class Block(
    val id: Int,
    val shape: BlockShape,
    val color: Int,
    val r: Int,
    val c: Int,
    val axis: Axis = Axis.BOTH,
    val ice: Int = 0,
    val inner: Int = 0,
    val lock: Int = 0,
    val key: Int = 0,
) {
    fun cells(): List<Cell> = shape.cells.map { Cell(r + it.r, c + it.c) }

    /** Frozen or padlocked: cannot move or leave. */
    val stuck: Boolean get() = ice > 0 || lock > 0
}

/** Obstacles a level can contain; each is introduced with a short note the first time it appears. */
enum class Mechanic { STONE, ICE, ARROW, LAYER, LOCK, TIMED;
    companion object {
        fun inBoard(b: JamBoard): List<Mechanic> = buildList {
            if (b.stones.isNotEmpty()) add(STONE)
            if (b.blocks.any { it.ice > 0 }) add(ICE)
            if (b.blocks.any { it.axis != Axis.BOTH }) add(ARROW)
            if (b.blocks.any { it.inner != 0 }) add(LAYER)
            if (b.blocks.any { it.lock > 0 }) add(LOCK)
            if (b.gates.any { it.timed }) add(TIMED)
        }
    }
}

/** What happened when a block went through a gate. */
enum class ExitResult { NONE, GONE, PEELED }

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

    /** Indexes (in [gates]) of timed gates that are closed right now. Empty for the solver: it can always wait. */
    var closedGates: Set<Int> = emptySet()

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
        if (block.stuck) return false
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
        if (block.stuck) return null
        if ((side == Side.TOP || side == Side.BOTTOM) && block.axis == Axis.HORIZONTAL) return null
        if ((side == Side.LEFT || side == Side.RIGHT) && block.axis == Axis.VERTICAL) return null
        val touching = when (side) {
            Side.TOP -> block.r == 0
            Side.BOTTOM -> block.r + block.shape.rows == rows
            Side.LEFT -> block.c == 0
            Side.RIGHT -> block.c + block.shape.cols == cols
        }
        if (!touching) return null
        // The whole piece slides out: every square needs a clear lane from where it is to the edge
        // (an arm of an L or T must not pass through another block or a stone).
        val (dr, dc) = when (side) {
            Side.TOP -> -1 to 0
            Side.BOTTOM -> 1 to 0
            Side.LEFT -> 0 to -1
            Side.RIGHT -> 0 to 1
        }
        for (cell in block.cells()) {
            var r = cell.r + dr
            var c = cell.c + dc
            while (r in 0 until rows && c in 0 until cols) {
                if (Cell(r, c) in stones) return null
                val other = blockAt(r, c)
                if (other != null && other.id != block.id) return null
                r += dr
                c += dc
            }
        }
        val (from, to) = when (side) {
            Side.TOP, Side.BOTTOM -> block.c to block.c + block.shape.cols - 1
            Side.LEFT, Side.RIGHT -> block.r to block.r + block.shape.rows - 1
        }
        return gates.withIndex().firstOrNull { (i, g) ->
            i !in closedGates && g.side == side && g.color == block.color && g.start <= from && to <= g.start + g.length - 1
        }?.value
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
        val b = _blocks.remove(id) ?: return false
        thaw()
        unlock(b.key)
        return true
    }

    private fun thaw() {
        for ((k, other) in _blocks.toMap()) {
            if (other.ice > 0) _blocks[k] = other.copy(ice = other.ice - 1)
        }
    }

    private fun unlock(key: Int) {
        if (key == 0) return
        for ((k, other) in _blocks.toMap()) {
            if (other.lock == key) _blocks[k] = other.copy(lock = 0)
        }
    }

    /**
     * Scissors power-up: cuts a block into two connected pieces of the same color, along the row or
     * column that splits it most evenly. Returns the two new blocks, or null if it can't be cut.
     */
    fun cut(id: Int, newId: Int): Pair<Block, Block>? {
        val b = _blocks[id] ?: return null
        if (b.shape.size < 2 || b.stuck) return null
        val cells = b.cells()
        var best: Pair<List<Cell>, List<Cell>>? = null
        var bestScore = Int.MAX_VALUE
        fun connected(set: List<Cell>): Boolean {
            if (set.isEmpty()) return false
            val all = set.toHashSet()
            val seen = HashSet<Cell>()
            val stack = ArrayDeque<Cell>().apply { add(set[0]) }
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                if (!seen.add(p)) continue
                for (n in listOf(Cell(p.r + 1, p.c), Cell(p.r - 1, p.c), Cell(p.r, p.c + 1), Cell(p.r, p.c - 1))) {
                    if (n in all && n !in seen) stack.add(n)
                }
            }
            return seen.size == all.size
        }
        for (k in 1 until b.shape.rows) {
            val a = cells.filter { it.r < b.r + k }
            val z = cells.filter { it.r >= b.r + k }
            val score = kotlin.math.abs(a.size - z.size)
            if (connected(a) && connected(z) && score < bestScore) { best = a to z; bestScore = score }
        }
        for (k in 1 until b.shape.cols) {
            val a = cells.filter { it.c < b.c + k }
            val z = cells.filter { it.c >= b.c + k }
            val score = kotlin.math.abs(a.size - z.size)
            if (connected(a) && connected(z) && score < bestScore) { best = a to z; bestScore = score }
        }
        val (pa, pz) = best ?: return null
        fun piece(part: List<Cell>, pid: Int) = Block(
            pid, BlockShape.of(part), b.color, part.minOf { it.r }, part.minOf { it.c },
            axis = b.axis, inner = b.inner, key = b.key,
        )
        val one = piece(pa, newId)
        val two = piece(pz, newId + 1).copy(key = 0)
        _blocks.remove(id)
        _blocks[one.id] = one
        _blocks[two.id] = two
        return one to two
    }

    /** Block covering cell (r, c), if any. */
    fun blockAt(r: Int, c: Int): Block? = _blocks.values.firstOrNull { b ->
        val lr = r - b.r
        val lc = c - b.c
        lr >= 0 && lc >= 0 && lr < b.shape.rows && lc < b.shape.cols && b.shape.cells.any { it.r == lr && it.c == lc }
    }

    /**
     * A block goes through a gate (if allowed). A two-layer block only loses its shell and stays,
     * now in its inner color. Either way iced blocks thaw a step; a key opens its padlock.
     */
    fun exit(id: Int, side: Side): ExitResult {
        val b = _blocks[id] ?: return ExitResult.NONE
        exitGate(b, side) ?: return ExitResult.NONE
        thaw()
        if (b.inner != 0) {
            _blocks[id] = (_blocks[id] ?: b).copy(color = b.inner, inner = 0)
            return ExitResult.PEELED
        }
        _blocks.remove(id)
        unlock(b.key)
        return ExitResult.GONE
    }

    /** All top-left positions [block] can reach by sliding, from its current position. */
    fun reachable(block: Block): Set<Cell> {
        val start = Cell(block.r, block.c)
        if (block.stuck) return setOf(start)
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

    /** A block that can leave (or peel) right now, with where to slide it and which side: used by the hint. */
    fun hint(board: JamBoard): Triple<Block, Cell, Side>? {
        // Prefer blocks whose exit unlocks or thaws others, then the biggest (more space freed).
        val free = board.blocks.mapNotNull { b -> board.findExit(b)?.let { Triple(b, it.first, it.second) } }
        return free.maxByOrNull { (b, _, _) -> (if (b.key > 0) 100 else 0) + (if (b.inner == 0) 10 else 0) + b.shape.size }
    }

    /** Number of blocks that can leave right now (fewer = harder start). */
    fun freeAtStart(board: JamBoard): Int = board.blocks.count { board.findExit(it) != null }
}
