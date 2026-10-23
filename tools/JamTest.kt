import com.farias.prismajam.game.*

fun check(c: Boolean, m: String) { if (!c) throw AssertionError(m) }

/** Plays a level like a player would: walks each free block step by step to its exit. */
fun playByStepping(level: JamLevel): Boolean {
    val b = level.board.copy()
    var guard = 0
    while (!b.isCleared && guard < 500) {
        guard++
        val blk = b.blocks.firstOrNull { b.findExit(it) != null } ?: return false
        val (target, side) = b.findExit(blk)!!
        // BFS path of single steps
        val prev = HashMap<Cell, Cell?>()
        val q = ArrayDeque<Cell>(); val start = Cell(blk.r, blk.c); q.add(start); prev[start] = null
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (q.isNotEmpty()) { val p = q.removeFirst(); if (p == target) break
            for ((dr, dc) in dirs) { val nx = Cell(p.r + dr, p.c + dc); if (nx in prev) continue
                val cand = blk.copy(r = p.r, c = p.c); if (!b.canMove(cand, dr, dc)) continue
                prev[nx] = p; q.add(nx) } }
        check(target in prev, "path exists")
        val path = ArrayList<Cell>(); var cur: Cell? = target; while (cur != null) { path.add(cur); cur = prev[cur] }; path.reverse()
        for (i in 1 until path.size) { val s = b.step(blk.id, path[i].r - path[i-1].r, path[i].c - path[i-1].c); check(s != null, "step ok") }
        check(b.exit(blk.id, side), "exit ok")
    }
    return b.isCleared
}

fun main() {
    // Rules
    val g = listOf(Gate(Side.TOP, 0, 2, 1), Gate(Side.RIGHT, 2, 3, 2))
    val sq = BlockShape.all.first { it.rows == 2 && it.cols == 2 }
    val bar3 = BlockShape.all.first { it.rows == 1 && it.cols == 3 }
    val board = JamBoard(5, 5, emptySet(), g, listOf(Block(1, sq, 1, 0, 0), Block(2, bar3, 1, 4, 0, ice = 1)))
    check(board.exitGate(board.block(1)!!, Side.TOP) != null, "2x2 fits 2-wide gate")
    check(board.findExit(board.block(2)!!) == null, "iced block frozen")
    check(board.exit(1, Side.TOP), "exit")
    check(board.block(2)!!.ice == 0, "thawed")
    check(board.findExit(board.block(2)!!) == null, "bar3 too wide for 2-gate")

    // Levels: determinism, solvability, speed, difficulty curve
    check(JamLevels.level(7).board.blocks.map { it.toString() } == JamLevels.level(7).board.blocks.map { it.toString() }, "deterministic")
    val t0 = System.currentTimeMillis()
    var maxMs = 0L
    for (n in 1..150) {
        val s = System.currentTimeMillis()
        val lv = JamLevels.level(n)
        val ms = System.currentTimeMillis() - s; if (ms > maxMs) maxMs = ms
        check(JamSolver.waves(lv.board) == lv.waves && lv.waves > 0, "solvable $n")
        check(playByStepping(lv), "playable by stepping $n")
        if (n in listOf(1,2,3,5,8,10,15,20,30,40,60,80,100,150)) {
            val b = lv.board
            println("L%3d %dx%d colors=%d blocks=%2d cells=%2d stones=%d ice=%d axis=%d gates=%d waves=%d free=%d time=%ds gen=%dms".format(
                n, b.rows, b.cols, b.gates.map { it.color }.toSet().size, b.blocks.size, b.blocks.sumOf { it.shape.size },
                b.stones.size, b.blocks.count { it.ice > 0 }, b.blocks.count { it.axis != Axis.BOTH }, b.gates.size,
                lv.waves, JamSolver.freeAtStart(b), lv.timeSec, ms))
        }
    }
    println("OK total=${System.currentTimeMillis() - t0}ms maxLevelMs=$maxMs")
}
