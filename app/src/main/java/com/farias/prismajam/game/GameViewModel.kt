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

enum class Power(val cost: Int) { HINT(30), FREEZE(40), HAMMER(50), SCISSORS(35) }

/** The hint on screen: slide [blockId] to [target] (top-left, in cells) and out through [side]. */
class Hint(val blockId: Int, val target: Cell, val side: Side, val start: Long)

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
    /** 0 = splinter (tiny), 1 = shard, 2 = chunk (big): bigger pieces get a thicker side. */
    val kind: Int = 1,
)

/** A twinkling glitter star left by an explosion (board cells). */
class Sparkle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val color: Color,
    var life: Float,
    val maxLife: Float,
    val phase: Float,
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
    var scissorsMode by mutableStateOf(false)
    var hint by mutableStateOf<Hint?>(null)
        private set
    /** Obstacle shown for the first time in this level: explained before play starts. */
    var intro by mutableStateOf<Mechanic?>(null)
        private set
    /** Seconds the level has been running (drives the timed gates). */
    var gateClock = 0f
        private set
    var frozenLeft by mutableFloatStateOf(0f)
        private set
    var extraTimeUsed by mutableStateOf(false)
        private set
    var coinsEarned by mutableIntStateOf(0)
        private set
    /** Stars for the level just won (1-3, by time left). */
    var starsEarned by mutableIntStateOf(0)
        private set
    /** Playing today's daily challenge instead of a regular level. */
    var isDaily by mutableStateOf(false)
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
    val sparkles = ArrayList<Sparkle>()
    /** Debug only (screenshots): keep shattering blocks in place so the effect can be captured. */
    var debugShatterLoop = false

    fun debugShatter() {
        val b = blocks.randomOrNull() ?: return
        val side = board.findExit(b)?.second ?: Side.RIGHT
        val dir = when (side) {
            Side.TOP -> Offset(0f, -9f)
            Side.BOTTOM -> Offset(0f, 9f)
            Side.LEFT -> Offset(-9f, 0f)
            Side.RIGHT -> Offset(9f, 0f)
        }
        exits.add(ExitAnim(b, side, fx.now))
        shatter(b, dir, gravity = 6f)
    }
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
        isDaily = false
        begin(JamLevels.level(number))
    }

    fun startDaily() {
        isDaily = true
        begin(JamLevels.daily(Progress.today()))
    }

    private fun begin(lv: JamLevel) {
        panelJob?.cancel()
        level = lv
        starsEarned = 0
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
        scissorsMode = false
        hint = null
        gateClock = 0f
        board.closedGates = emptySet()
        intro = Mechanic.inBoard(board).firstOrNull { it.name !in Progress.seenMechanics }
        frozenLeft = 0f
        extraTimeUsed = false
        coinsEarned = 0
        dragging = -1
        visual.clear()
        exits.clear()
        shards.clear()
        sparkles.clear()
        fx.particles.clear()
        sync()
        for (b in blocks) visual[b.id] = Offset(b.c.toFloat(), b.r.toFloat())
    }

    fun restart() {
        if (isDaily) {
            startDaily()
            sound.play(Sfx.WHOOSH, 0.6f)
            return
        }
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
        // Timed gates open and close in a cycle (each with its own phase).
        if (started && !paused && !finished) gateClock += dt
        if (gates.any { it.timed }) {
            val closed = HashSet<Int>()
            gates.forEachIndexed { i, g -> if (g.timed && gateClosedness(i) > 0.5f) closed.add(i) }
            if (closed != board.closedGates) board.closedGates = closed
        }
        hint?.let { if (frameNanos - it.start > HINT_NANOS) hint = null }
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
        if (sparkles.isNotEmpty()) {
            val it = sparkles.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.life -= dt
                if (p.life <= 0f) { it.remove(); continue }
                p.vx *= 1f - (dt * 2.5f).coerceAtMost(0.6f)
                p.vy *= 1f - (dt * 2.5f).coerceAtMost(0.6f)
                p.vy += 0.8f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
        }
    }

    /**
     * Breaks a block into triangular shards. [dir] is the main flight direction in cells/s (towards the gate),
     * or zero for an explosion in all directions.
     */
    private fun shatter(b: Block, dir: Offset, gravity: Float, light: Boolean = false) {
        val color = Palette.block(b.color)
        val cells = b.cells()
        val cx = cells.map { it.c + 0.5f }.average().toFloat()
        val cy = cells.map { it.r + 0.5f }.average().toFloat()
        val explode = dir == Offset.Zero

        /** Random convex-ish polygon of radius [r] with 3..5 corners, around (0,0). */
        fun poly(r: Float): FloatArray {
            val n = 3 + rng.nextInt(3)
            val start = rng.nextFloat() * 6.283f
            val out = FloatArray(n * 2)
            for (i in 0 until n) {
                val a = start + i * 6.283f / n + (rng.nextFloat() - 0.5f) * (3.0f / n)
                val rr = r * (0.65f + rng.nextFloat() * 0.5f)
                out[i * 2] = kotlin.math.cos(a) * rr
                out[i * 2 + 1] = kotlin.math.sin(a) * rr
            }
            return out
        }

        fun piece(kind: Int, radius: Float, x: Float, y: Float, speedK: Float, life: Float) {
            val out = Offset(x - cx, y - cy)
            val vx: Float
            val vy: Float
            if (explode) {
                val len = out.getDistance().coerceAtLeast(0.25f)
                val sp = (3.5f + rng.nextFloat() * 6f) * speedK
                vx = out.x / len * sp + (rng.nextFloat() - 0.5f) * 4f
                vy = out.y / len * sp - 3f + (rng.nextFloat() - 0.5f) * 4f
            } else {
                // Mostly through the gate, fanning out like a burst of glass.
                val sp = (0.55f + rng.nextFloat() * 0.75f) * speedK
                vx = dir.x * sp + out.x * 2.2f + (rng.nextFloat() - 0.5f) * 5f * speedK
                vy = dir.y * sp + out.y * 2.2f + (rng.nextFloat() - 0.5f) * 5f * speedK
            }
            shards.add(
                Shard(
                    x, y, vx, vy, rng.nextFloat() * 6.283f, (rng.nextFloat() - 0.5f) * (8f + 14f * speedK), poly(radius), color,
                    shade = rng.nextFloat(), life = life, maxLife = life, gravity = gravity, kind = kind,
                )
            )
        }

        for (cell in cells) {
            fun inCell() = Offset(cell.c + 0.15f + rng.nextFloat() * 0.7f, cell.r + 0.15f + rng.nextFloat() * 0.7f)
            // Big chunks: slow, heavy, last longest.
            repeat(if (light) 0 else 2) {
                val p = inCell()
                piece(2, 0.2f + rng.nextFloat() * 0.08f, p.x, p.y, 0.75f, 0.75f + rng.nextFloat() * 0.3f)
            }
            // Medium shards.
            repeat(if (light) 3 else 5) {
                val p = inCell()
                piece(1, 0.1f + rng.nextFloat() * 0.07f, p.x, p.y, 1f, 0.6f + rng.nextFloat() * 0.35f)
            }
            // Tiny splinters: fast and short-lived.
            repeat(9) {
                val p = inCell()
                piece(0, 0.035f + rng.nextFloat() * 0.04f, p.x, p.y, 1.45f, 0.4f + rng.nextFloat() * 0.35f)
            }
            // Glitter
            repeat(3) {
                val p = inCell()
                val life = 0.6f + rng.nextFloat() * 0.6f
                val sp = if (explode) 3f else 4.5f
                sparkles.add(
                    Sparkle(
                        p.x, p.y,
                        (if (explode) 0f else dir.x * 0.35f) + (rng.nextFloat() - 0.5f) * sp,
                        (if (explode) 0f else dir.y * 0.35f) + (rng.nextFloat() - 0.5f) * sp,
                        size = 0.08f + rng.nextFloat() * 0.1f,
                        color = if (rng.nextFloat() < 0.5f) Color.White else Palette.lighter(color, 0.5f),
                        life = life, maxLife = life, phase = rng.nextFloat() * 6.283f,
                    )
                )
            }
        }
        if (shards.size > 1600) shards.subList(0, shards.size - 1600).clear()
        if (sparkles.size > 300) sparkles.subList(0, sparkles.size - 300).clear()
    }

    /** 0 = fully open, 1 = fully closed (with a short slide between), for timed gate [index]. */
    fun gateClosedness(index: Int): Float {
        val g = gates.getOrNull(index) ?: return 0f
        if (!g.timed) return 0f
        val t = (gateClock + index * 1.7f) % GATE_CYCLE
        val slide = 0.25f
        return when {
            t < GATE_OPEN - slide -> 0f
            t < GATE_OPEN -> (t - (GATE_OPEN - slide)) / slide
            t < GATE_CYCLE - slide -> 1f
            else -> 1f - (t - (GATE_CYCLE - slide)) / slide
        }
    }

    // ------------------------------------------------------------- input

    private fun cellUnder(root: Offset): Pair<Float, Float> =
        ((root.y - boardOrigin.y) / cellPx) to ((root.x - boardOrigin.x) / cellPx)

    fun touchDown(root: Offset) {
        if (finished || paused || intro != null) return
        val (fr, fc) = cellUnder(root)
        val r = fr.toInt()
        val c = fc.toInt()
        if (fr < 0 || fc < 0) return
        val b = board.blockAt(r, c) ?: return
        if (hammerMode || scissorsMode) return // handled by the tool tap
        if (b.lock > 0) {
            sound.play(Sfx.INVALID, 0.6f)
            vibrate(Haptic.TICK)
            fx.text("🔒", toRoot(root), Palette.Gold)
            return
        }
        if (b.ice > 0) {
            sound.play(Sfx.INVALID, 0.6f)
            vibrate(Haptic.TICK)
            fx.text("❄ ${b.ice}", toRoot(root), Color(0xFFBAE6FD))
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
        val lockedBefore = board.blocks.filter { it.lock > 0 }.map { it.id }.toSet()
        val result = board.exit(b.id, side)
        if (result == ExitResult.NONE) return
        dragging = -1
        pull = Offset.Zero
        if (hint?.blockId == b.id) hint = null
        exits.add(ExitAnim(b, side, fx.now))
        announceUnlocks(lockedBefore)
        val color = Palette.block(b.color)
        if (result == ExitResult.PEELED) {
            // Only the outer shell bursts; the block stays, now in its inner color.
            val dir = when (side) {
                Side.TOP -> Offset(0f, -6f)
                Side.BOTTOM -> Offset(0f, 6f)
                Side.LEFT -> Offset(-6f, 0f)
                Side.RIGHT -> Offset(6f, 0f)
            }
            shatter(b, dir, gravity = 6f, light = true)
            sound.play(Sfx.GLASS, 0.8f, 1.15f)
            vibrate(Haptic.CLEAR)
            sync()
            return
        }
        visual.remove(b.id)
        // The block bursts into shards that fly out through the gate, plus sparks.
        val dir = when (side) {
            Side.TOP -> Offset(0f, -9f)
            Side.BOTTOM -> Offset(0f, 9f)
            Side.LEFT -> Offset(-9f, 0f)
            Side.RIGHT -> Offset(9f, 0f)
        }
        shatter(b, dir, gravity = 6f)
        val sc = screenCell
        fx.shake(sc * 0.08f)
        val big = b.shape.size >= 4
        sound.play(Sfx.GLASS, 0.95f, 0.92f + 0.04f * (totalBlocks - board.blocks.size).coerceAtMost(8))
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
            Power.HINT -> hint == null && blocks.isNotEmpty()
            Power.FREEZE -> frozenLeft <= 0f
            Power.HAMMER -> blocks.isNotEmpty()
            Power.SCISSORS -> blocks.any { it.shape.size >= 2 && !it.stuck }
        }
    }

    fun dismissIntro() {
        intro?.let { Progress.markMechanicSeen(it.name) }
        // Several new obstacles at once: explain the next one.
        intro = Mechanic.inBoard(board).firstOrNull { it.name !in Progress.seenMechanics }
    }

    /** Shows which block can leave now and where to slide it. */
    fun showHint() {
        val (b, target, side) = JamSolver.hint(board) ?: run {
            playInvalid()
            return
        }
        if (!started) started = true
        hint = Hint(b.id, target, side, fx.now)
        sound.play(Sfx.REVIVE, 0.7f, 1.4f)
        vibrate(Haptic.TICK)
    }

    /** Cuts [b] in two (scissors power-up). */
    fun cut(b: Block) {
        val nextId = (board.blocks.maxOfOrNull { it.id } ?: 0) + 1
        val (one, two) = board.cut(b.id, nextId) ?: run {
            playInvalid()
            return
        }
        scissorsMode = false
        if (hint?.blockId == b.id) hint = null
        val old = visual.remove(b.id) ?: Offset(b.c.toFloat(), b.r.toFloat())
        // The halves spring apart a little from where the block was.
        visual[one.id] = Offset(one.c.toFloat(), one.r.toFloat()) + (Offset(one.c.toFloat(), one.r.toFloat()) - Offset(two.c.toFloat(), two.r.toFloat())) * 0.12f + (old - Offset(b.c.toFloat(), b.r.toFloat()))
        visual[two.id] = Offset(two.c.toFloat(), two.r.toFloat()) + (Offset(two.c.toFloat(), two.r.toFloat()) - Offset(one.c.toFloat(), one.r.toFloat())) * 0.12f
        totalBlocks += 1
        // A few sparks along the cut.
        val color = Palette.block(b.color)
        val sc = screenCell
        for (cell in one.cells()) fx.burst(screenAt(Offset(cell.c + 0.5f, cell.r + 0.5f)), color, sc * 0.4f, 2)
        sound.play(Sfx.WHOOSH, 0.9f, 1.4f)
        vibrate(Haptic.PLACE)
        if (!started) started = true
        sync()
    }

    /** Sparkles and a sound when a key opened padlocks. */
    private fun announceUnlocks(lockedBefore: Set<Int>) {
        val opened = lockedBefore.filter { id -> board.block(id)?.lock == 0 }
        if (opened.isEmpty()) return
        for (id in opened) {
            val ob = board.block(id) ?: continue
            val center = screenAt(Offset(ob.c + ob.shape.cols / 2f, ob.r + ob.shape.rows / 2f))
            fx.burst(center, Palette.Gold, screenCell * 0.8f, 10)
            fx.text("🔓", center, Palette.Gold)
        }
        sound.play(Sfx.COIN, 0.9f, 1.3f)
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
        val lockedBefore = board.blocks.filter { it.lock > 0 }.map { it.id }.toSet()
        if (!board.smash(b.id)) return
        announceUnlocks(lockedBefore)
        if (hint?.blockId == b.id) hint = null
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
        scissorsMode = false
        hint = null
        val lv = level ?: return
        val used = (timeTotal - timeLeft).roundToInt()
        val left = timeLeft / timeTotal.coerceAtLeast(1f)
        starsEarned = when {
            left >= 0.5f -> 3
            left >= 0.25f -> 2
            else -> 1
        }
        if (isDaily) {
            coinsEarned = Progress.onDailyWon()
        } else {
            Progress.onLevelWon(lv.number, used)
            Progress.setStars(lv.number, starsEarned)
            coinsEarned = (10 + lv.number / 4).coerceAtMost(35) + 5 * (starsEarned - 1)
            Progress.addCoins(coinsEarned)
        }
        missionEvent(MissionType.LEVELS, 1)
        if (timeLeft >= timeTotal * 0.4f) missionEvent(MissionType.FAST_WINS, 1)
        missionEvent(MissionType.STREAK, Progress.winStreak, asMax = true)
        // Confetti
        val center = screenAt(Offset(board.cols / 2f, board.rows / 2f))
        for (i in 1..7) fx.burst(center, Palette.block(i), screenCell * 1.4f, 10)
        fx.text(
            getApplication<Application>().getString(if (isDaily) R.string.daily_complete else R.string.level_complete),
            center, Palette.Gold, big = true, delayMs = 100,
        )
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
        const val EXIT_NANOS = 450_000_000L
        const val EXIT_PUSH = 0.35f
        const val HINT_NANOS = 7_000_000_000L
        const val GATE_CYCLE = 5.5f
        const val GATE_OPEN = 3.2f
        const val LEAN = 0.3f
        const val FREEZE_SECONDS = 10f
        const val EXTRA_SECONDS = 30f
    }
}
