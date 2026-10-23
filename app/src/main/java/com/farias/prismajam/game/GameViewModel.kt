package com.farias.prismajam.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.farias.prismajam.R
import com.farias.prismajam.audio.Sfx
import com.farias.prismajam.audio.SoundManager
import com.farias.prismajam.data.MissionType
import com.farias.prismajam.data.Progress
import com.farias.prismajam.ui.Fx
import com.farias.prismajam.ui.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

enum class Haptic { TICK, PLACE, CLEAR, COMBO, HEAVY }

enum class Power(val cost: Int) { FREEZE(40), HAMMER(50) }

/** A block sliding out through a gate (drawn for a short moment after it left the board). */
class ExitAnim(val block: Block, val side: Side, val start: Long)

/** A flying fragment of a shattered block. Positions are in board cells; [pts] are vertices around (x, y). */
class Shard(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rot: Float,
    val vrot: Float,
    val pts: FloatArray,
    val color: Color,
    val shade: Float,
    var life: Float,
    val maxLife: Float,
    val gravity: Float,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    init {
        Progress.init(app)
    }

    val sound = SoundManager.get(app)
    val fx = Fx()
    var haptic: (Haptic) -> Unit = {}

    // ------------------------------------------------------------- level state
    var level by mutableStateOf<JamLevel?>(null)
        private set
    private var board: JamBoard = JamBoard(5, 5, emptySet(), emptyList(), emptyList())
    /** Snapshot of the blocks for drawing. */
    var blocks by mutableStateOf<List<Block>>(emptyList())
        private set
    var gates by mutableStateOf<List<Gate>>(emptyList())
        private set
    var totalBlocks by mutableIntStateOf(0)
        private set

    var timeLeft by mutableFloatStateOf(0f)
        private set
    var timeTotal by mutableFloatStateOf(1f)
        private set
    /** The clock starts with the first touch, so loading or an ad never eats the player's time. */
    var started by mutableStateOf(false)
        private set
    var won by mutableStateOf(false)
        private set
    var timeUp by mutableStateOf(false)
        private set
    var showWin by mutableStateOf(false)
        private set
    var showTimeUp by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
    var hammerMode by mutableStateOf(false)
    var frozenLeft by mutableFloatStateOf(0f)
        private set
    var extraTimeUsed by mutableStateOf(false)
        private set
    var coinsEarned by mutableIntStateOf(0)
        private set

    /** Id of the block under the finger, or -1. */
    var dragging by mutableIntStateOf(-1)
        private set

    val finished: Boolean get() = won || timeUp

    // ------------------------------------------------------------- geometry, set by the UI
    /** Top-left of cell (0,0) and cell size, in the board's own (untilted) px. Touches arrive in the same space. */
    var boardOrigin = Offset.Zero
    var cellPx = 1f
    /** Maps a point in the board's px to screen (root) px, through the 3D tilt. */
    var toRoot: (Offset) -> Offset = { it }

    /** Screen position of a point given in cells (e.g. 2.5, 3.5 = centre of cell r3 c2). */
    fun screenAt(cells: Offset): Offset = toRoot(boardOrigin + cells * cellPx)

    /** Approximate size of one cell on screen. */
    val screenCell: Float
        get() = (screenAt(Offset(1f, 0f)) - screenAt(Offset.Zero)).getDistance().coerceAtLeast(1f)

    val shards = ArrayList<Shard>()
    private val rng = java.util.Random()

    /** Smoothly animated top-left (in cells) per block id, updated every frame. */
    val visual = HashMap<Int, Offset>()
    val exits = ArrayList<ExitAnim>()
    private var grab = Offset.Zero
    private var pull = Offset.Zero
    private var panelJob: Job? = null
    private var lastTick = 0L

    // ------------------------------------------------------------- starting

    fun startLevel(number: Int = Progress.level) {
        panelJob?.cancel()
        val lv = JamLevels.level(number)
        level = lv
        board = lv.board.copy()
        gates = board.gates
        totalBlocks = board.blocks.size
        timeTotal = lv.timeSec.toFloat()
        timeLeft = timeTotal
        started = false
        won = false
        timeUp = false
        showWin = false
        showTimeUp = false
        paused = false
        hammerMode = false
        frozenLeft = 0f
        extraTimeUsed = false
        coinsEarned = 0
        dragging = -1
        visual.clear()
        exits.clear()
        shards.clear()
        fx.particles.clear()
        sync()
        for (b in blocks) visual[b.id] = Offset(b.c.toFloat(), b.r.toFloat())
    }

    fun restart() {
        val n = level?.number ?: Progress.level
        startLevel(n)
        sound.play(Sfx.WHOOSH, 0.6f)
    }

    // ------------------------------------------------------------- per-frame update

    /** Called every frame by the UI: clock, smooth movement, exit animations. */
    fun tick(frameNanos: Long) {
        val dt = if (lastTick == 0L) 0f else ((frameNanos - lastTick) / 1e9f).coerceIn(0f, 0.05f)
        lastTick = frameNanos
        // Clock
        if (started && !finished && !paused) {
            if (frozenLeft > 0f) {
                frozenLeft = (frozenLeft - dt).coerceAtLeast(0f)
            } else {
                timeLeft = (timeLeft - dt).coerceAtLeast(0f)
                if (timeLeft <= 0f) onTimeUp()
            }
        }
        // Blocks glide towards their cell; the dragged one leans towards the finger.
        val k = (dt * 22f).coerceAtMost(1f)
        for (b in blocks) {
            val target = Offset(b.c.toFloat(), b.r.toFloat()) + if (b.id == dragging) pull else Offset.Zero
            val cur = visual[b.id] ?: target
            visual[b.id] = cur + (target - cur) * k
        }
        if (exits.isNotEmpty()) exits.removeAll { frameNanos - it.start > EXIT_NANOS }
        if (shards.isNotEmpty()) {
            val drag = 1f - (dt * 1.6f).coerceAtMost(0.5f)
            val it = shards.iterator()
            while (it.hasNext()) {
                val s = it.next()
                s.life -= dt
                if (s.life <= 0f) { it.remove(); continue }
                s.vy += s.gravity * dt
                s.vx *= drag
                s.vy *= drag
                s.x += s.vx * dt
                s.y += s.vy * dt
                s.rot += s.vrot * dt
            }
        }
    }

    /**
     * Breaks a block into triangular shards. [dir] is the main flight direction in cells/s (towards the gate),
     * or zero for an explosion in all directions.
     */
    private fun shatter(b: Block, dir: Offset, gravity: Float) {
        val color = Palette.block(b.color)
        val cells = b.cells()
        val cx = cells.map { it.c + 0.5f }.average().toFloat()
        val cy = cells.map { it.r + 0.5f }.average().toFloat()
        fun j() = (rng.nextFloat() - 0.5f) * 0.22f
        for (cell in cells) {
            val m = 0.06f
            val x0 = cell.c + m
            val y0 = cell.r + m
            val x1 = cell.c + 1 - m
            val y1 = cell.r + 1 - m
            val mid = Offset(cell.c + 0.5f + j(), cell.r + 0.5f + j())
            // Ring of points around the cell: corners and jittered edge midpoints.
            val ring = listOf(
                Offset(x0, y0), Offset((x0 + x1) / 2 + j(), y0), Offset(x1, y0), Offset(x1, (y0 + y1) / 2 + j()),
                Offset(x1, y1), Offset((x0 + x1) / 2 + j(), y1), Offset(x0, y1), Offset(x0, (y0 + y1) / 2 + j()),
            )
            for (i in ring.indices) {
                val a = ring[i]
                val bb = ring[(i + 1) % ring.size]
                val g = Offset((mid.x + a.x + bb.x) / 3f, (mid.y + a.y + bb.y) / 3f)
                val pts = floatArrayOf(mid.x - g.x, mid.y - g.y, a.x - g.x, a.y - g.y, bb.x - g.x, bb.y - g.y)
                val out = Offset(g.x - cx, g.y - cy)
                val speed = 0.6f + rng.nextFloat() * 0.8f
                val vx: Float
                val vy: Float
                if (dir == Offset.Zero) {
                    val len = out.getDistance().coerceAtLeast(0.2f)
                    val sp = 4f + rng.nextFloat() * 6f
                    vx = out.x / len * sp + (rng.nextFloat() - 0.5f) * 3f
                    vy = out.y / len * sp - 3f + (rng.nextFloat() - 0.5f) * 3f
                } else {
                    vx = dir.x * speed + out.x * 2.5f + (rng.nextFloat() - 0.5f) * 3.5f
                    vy = dir.y * speed + out.y * 2.5f + (rng.nextFloat() - 0.5f) * 3.5f
                }
                val life = 0.55f + rng.nextFloat() * 0.4f
                shards.add(
                    Shard(
                        g.x, g.y, vx, vy, 0f, (rng.nextFloat() - 0.5f) * 14f, pts, color,
                        shade = rng.nextFloat(), life = life, maxLife = life, gravity = gravity,
                    )
                )
            }
        }
        if (shards.size > 900) shards.subList(0, shards.size - 900).clear()
    }

    // ------------------------------------------------------------- input

    private fun cellUnder(root: Offset): Pair<Float, Float> =
        ((root.y - boardOrigin.y) / cellPx) to ((root.x - boardOrigin.x) / cellPx)

    fun touchDown(root: Offset) {
        if (finished || paused) return
        val (fr, fc) = cellUnder(root)
        val r = fr.toInt()
        val c = fc.toInt()
        if (fr < 0 || fc < 0) return
        val b = board.blockAt(r, c) ?: return
        if (hammerMode) return // handled by hammerAt
        if (b.ice > 0) {
            sound.play(Sfx.INVALID, 0.6f)
            vibrate(Haptic.TICK)
            fx.text("❄ ${b.ice}", root, Color(0xFFBAE6FD))
            return
        }
        if (!started) started = true
        dragging = b.id
        grab = Offset(fc - b.c, fr - b.r)
        pull = Offset.Zero
        sound.play(Sfx.PICK, 0.5f)
        vibrate(Haptic.TICK)
    }

    fun touchMove(root: Offset) {
        val id = dragging
        if (id < 0 || finished || paused) return
        var b = board.block(id) ?: return
        val (fr, fc) = cellUnder(root)
        val wantR = fr - grab.y
        val wantC = fc - grab.x
        val targetR = wantR.roundToInt()
        val targetC = wantC.roundToInt()
        var moved = false
        // Walk cell by cell towards the finger; blocks and stones stop the slide.
        for (i in 0 until 24) {
            val dr = (targetR - b.r).sign
            val dc = (targetC - b.c).sign
            if (dr == 0 && dc == 0) break
            val preferRows = abs(wantR - b.r) >= abs(wantC - b.c)
            val first = if (preferRows) dr to 0 else 0 to dc
            val second = if (preferRows) 0 to dc else dr to 0
            val next = (if (first != (0 to 0)) board.step(id, first.first, first.second) else null)
                ?: (if (second != (0 to 0)) board.step(id, second.first, second.second) else null)
                ?: break
            b = next
            moved = true
        }
        if (moved) {
            vibrate(Haptic.TICK)
            sync()
        }
        // Leaving: pushed past the edge where a matching gate is.
        val side = when {
            wantR < b.r - EXIT_PUSH && b.r == 0 -> Side.TOP
            wantR > b.r + EXIT_PUSH && b.r + b.shape.rows == board.rows -> Side.BOTTOM
            wantC < b.c - EXIT_PUSH && b.c == 0 -> Side.LEFT
            wantC > b.c + EXIT_PUSH && b.c + b.shape.cols == board.cols -> Side.RIGHT
            else -> null
        }
        if (side != null && board.exitGate(b, side) != null) {
            exitBlock(b, side)
            return
        }
        // Lean a little towards the finger for a responsive feel.
        pull = Offset(
            (wantC - b.c).coerceIn(-LEAN, LEAN),
            (wantR - b.r).coerceIn(-LEAN, LEAN),
        )
    }

    fun touchUp() {
        dragging = -1
        pull = Offset.Zero
    }

    private fun exitBlock(b: Block, side: Side) {
        if (!board.exit(b.id, side)) return
        dragging = -1
        pull = Offset.Zero
        exits.add(ExitAnim(b, side, fx.now))
        visual.remove(b.id)
        val color = Palette.block(b.color)
        // The block bursts into shards that fly out through the gate, plus sparks.
        val dir = when (side) {
            Side.TOP -> Offset(0f, -9f)
            Side.BOTTOM -> Offset(0f, 9f)
            Side.LEFT -> Offset(-9f, 0f)
            Side.RIGHT -> Offset(9f, 0f)
        }
        shatter(b, dir, gravity = 6f)
        val sc = screenCell
        for (cell in b.cells()) {
            fx.burst(screenAt(Offset(cell.c + 0.5f, cell.r + 0.5f)), color, sc * 0.5f, 3)
        }
        fx.shake(sc * 0.08f)
        val big = b.shape.size >= 4
        sound.play(Sfx.CLEAR, 0.9f, 0.9f + 0.05f * (totalBlocks - board.blocks.size).coerceAtMost(8))
        vibrate(if (big) Haptic.COMBO else Haptic.CLEAR)
        Progress.onBlockCleared()
        missionEvent(MissionType.BLOCKS, 1)
        if (big) missionEvent(MissionType.BIG_BLOCKS, 1)
        if (!Progress.tutorialDone) Progress.markTutorial(true)
        sync()
        if (board.isCleared) onWin()
    }

    // ------------------------------------------------------------- power-ups

    fun powerAvailable(p: Power): Boolean {
        if (finished || paused) return false
        return when (p) {
            Power.FREEZE -> frozenLeft <= 0f
            Power.HAMMER -> blocks.isNotEmpty()
        }
    }

    fun freeze() {
        frozenLeft = FREEZE_SECONDS
        if (!started) started = true
        sound.play(Sfx.REVIVE, 0.8f, 1.2f)
        vibrate(Haptic.CLEAR)
    }

    /** Block id under a tap while the hammer is armed, or null. */
    fun hammerTarget(root: Offset): Block? {
        val (fr, fc) = cellUnder(root)
        if (fr < 0 || fc < 0) return null
        return board.blockAt(fr.toInt(), fc.toInt())
    }

    fun smash(b: Block) {
        if (!board.smash(b.id)) return
        hammerMode = false
        visual.remove(b.id)
        val color = Palette.block(b.color)
        shatter(b, Offset.Zero, gravity = 16f)
        val sc = screenCell
        for (cell in b.cells()) {
            fx.burst(screenAt(Offset(cell.c + 0.5f, cell.r + 0.5f)), color, sc, 6)
        }
        fx.shake(sc * 0.25f)
        sound.play(Sfx.BOMB)
        vibrate(Haptic.HEAVY)
        Progress.onBlockCleared()
        missionEvent(MissionType.BLOCKS, 1)
        sync()
        if (board.isCleared) onWin()
    }

    fun playInvalid() = sound.play(Sfx.INVALID, 0.5f)

    // ------------------------------------------------------------- end of level

    private fun onWin() {
        if (won) return
        won = true
        dragging = -1
        hammerMode = false
        val lv = level ?: return
        val used = (timeTotal - timeLeft).roundToInt()
        Progress.onLevelWon(lv.number, used)
        coinsEarned = (10 + lv.number / 4).coerceAtMost(35) + if (timeLeft >= timeTotal * 0.5f) 10 else 0
        Progress.addCoins(coinsEarned)
        missionEvent(MissionType.LEVELS, 1)
        if (timeLeft >= timeTotal * 0.4f) missionEvent(MissionType.FAST_WINS, 1)
        missionEvent(MissionType.STREAK, Progress.winStreak, asMax = true)
        // Confetti
        val center = screenAt(Offset(board.cols / 2f, board.rows / 2f))
        for (i in 1..7) fx.burst(center, Palette.block(i), screenCell * 1.4f, 10)
        fx.text(getApplication<Application>().getString(R.string.level_complete), center, Palette.Gold, big = true, delayMs = 100)
        sound.play(Sfx.RECORD)
        vibrate(Haptic.HEAVY)
        panelJob?.cancel()
        panelJob = viewModelScope.launch {
            delay(1100)
            showWin = true
        }
    }

    private fun onTimeUp() {
        if (timeUp || won) return
        timeUp = true
        dragging = -1
        hammerMode = false
        sound.play(Sfx.GAME_OVER, 0.9f)
        vibrate(Haptic.HEAVY)
        panelJob?.cancel()
        panelJob = viewModelScope.launch {
            delay(700)
            showTimeUp = true
        }
    }

    /** "+30 seconds" after a rewarded video. Allowed once per level. */
    fun addTime() {
        if (!timeUp || extraTimeUsed) return
        extraTimeUsed = true
        timeUp = false
        showTimeUp = false
        timeLeft = EXTRA_SECONDS
        sound.play(Sfx.REVIVE)
        vibrate(Haptic.CLEAR)
    }

    /** The player gives up after time ran out (retry or home). */
    fun acceptLoss() {
        if (timeUp) Progress.onLevelLost()
    }

    /** For the tutorial: a block that can leave now, and the center of its gate (in cells). */
    fun tutorialTarget(): Pair<Block, Offset>? {
        for (b in blocks) {
            val (_, side) = board.findExit(b) ?: continue
            val gate = board.gates.firstOrNull { it.side == side && it.color == b.color } ?: continue
            val mid = gate.start + gate.length / 2f
            val center = when (side) {
                Side.TOP -> Offset(mid, -0.4f)
                Side.BOTTOM -> Offset(mid, board.rows + 0.4f)
                Side.LEFT -> Offset(-0.4f, mid)
                Side.RIGHT -> Offset(board.cols + 0.4f, mid)
            }
            return b to center
        }
        return null
    }

    // ------------------------------------------------------------- helpers

    private fun missionEvent(type: MissionType, value: Int, asMax: Boolean = false) {
        val done = Progress.missionEvent(type, value, asMax)
        if (done.isEmpty()) return
        fx.text(
            getApplication<Application>().getString(R.string.mission_done),
            screenAt(Offset(board.cols / 2f, -0.7f)),
            Color(0xFF34D399), big = false, delayMs = 400,
        )
        sound.play(Sfx.COIN, 0.8f, 0.9f)
    }

    private fun sync() {
        blocks = board.blocks.sortedBy { it.id }
    }

    /** Rows/cols and stones of the current board, for drawing. */
    val rows: Int get() = board.rows
    val cols: Int get() = board.cols
    val stones: Set<Cell> get() = board.stones

    private fun vibrate(kind: Haptic) {
        if (Progress.vibrationOn) haptic(kind)
    }

    companion object {
        const val EXIT_NANOS = 260_000_000L
        const val EXIT_PUSH = 0.35f
        const val LEAN = 0.3f
        const val FREEZE_SECONDS = 10f
        const val EXTRA_SECONDS = 30f
    }
}
