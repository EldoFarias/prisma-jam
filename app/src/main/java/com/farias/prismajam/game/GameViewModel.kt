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

    // ------------------------------------------------------------- geometry (root px), set by the UI
    var boardOrigin = Offset.Zero
    var cellPx = 1f

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
        // Sparks along the gate.
        val cells = b.cells()
        for (cell in cells) {
            val center = Offset(boardOrigin.x + (cell.c + 0.5f) * cellPx, boardOrigin.y + (cell.r + 0.5f) * cellPx)
            fx.burst(center, color, cellPx * 0.6f, 5)
        }
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
        for (cell in b.cells()) {
            val center = Offset(boardOrigin.x + (cell.c + 0.5f) * cellPx, boardOrigin.y + (cell.r + 0.5f) * cellPx)
            fx.burst(center, color, cellPx, 9)
        }
        fx.shake(cellPx * 0.25f)
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
        if (timeLeft >= FAST_WIN_SECONDS) missionEvent(MissionType.FAST_WINS, 1)
        missionEvent(MissionType.STREAK, Progress.winStreak, asMax = true)
        // Confetti
        val center = Offset(boardOrigin.x + cellPx * board.cols / 2f, boardOrigin.y + cellPx * board.rows / 2f)
        for (i in 1..7) fx.burst(center, Palette.block(i), cellPx * 1.4f, 10)
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
            Offset(boardOrigin.x + cellPx * board.cols / 2f, boardOrigin.y - cellPx * 0.7f),
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
        const val EXIT_NANOS = 420_000_000L
        const val EXIT_PUSH = 0.35f
        const val LEAN = 0.3f
        const val FREEZE_SECONDS = 10f
        const val EXTRA_SECONDS = 30f
        const val FAST_WIN_SECONDS = 30f
    }
}
