package com.farias.prismajam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farias.prismajam.BuildConfig
import com.farias.prismajam.R
import com.farias.prismajam.ads.AdsManager
import com.farias.prismajam.audio.Haptics
import com.farias.prismajam.audio.Sfx
import com.farias.prismajam.data.Progress
import com.farias.prismajam.game.GameViewModel
import com.farias.prismajam.game.Power
import com.farias.prismajam.game.Side
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private class PendingPay(val power: Power, val action: () -> Unit)

@Composable
fun GameScreen(vm: GameViewModel, ads: AdsManager, onGreatMoment: () -> Unit, onHome: () -> Unit) {
    val view = LocalView.current
    var pendingPay by remember { mutableStateOf<PendingPay?>(null) }

    if (BuildConfig.DEBUG && vm.debugShatterLoop) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(700)
                vm.debugShatter()
            }
        }
    }
    LaunchedEffect(view) { vm.haptic = { kind -> Haptics.play(view.context, kind) } }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos {
            vm.fx.step(it)
            vm.tick(it)
        }
    }
    LaunchedEffect(vm.showWin) { if (vm.showWin && vm.timeLeft >= vm.timeTotal * 0.5f) onGreatMoment() }
    BackHandler(enabled = vm.hammerMode || vm.scissorsMode) {
        vm.hammerMode = false
        vm.scissorsMode = false
    }
    BackHandler(enabled = !vm.hammerMode && !vm.scissorsMode && !vm.paused && !vm.showWin && !vm.showTimeUp) { vm.paused = true }

    fun pay(p: Power, action: () -> Unit) {
        if (Progress.spend(p.cost)) action() else {
            vm.playInvalid()
            pendingPay = PendingPay(p, action)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()

        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val context = LocalContext.current
            val widthPx = constraints.maxWidth
            val bannerH = bannerHeight(remember(widthPx) { bannerAdSize(context, widthPx) })
            val tablet = maxWidth >= 600.dp && maxHeight >= 900.dp
            val ui = if (tablet) 1.25f else 1f
            val compact = maxHeight < 660.dp
            val topH = (if (compact) 46.dp else 54.dp) * ui
            val timerH = (if (compact) 52.dp else 64.dp) * ui
            val powerH = (if (compact) 62.dp else 76.dp) * ui
            val boardH = maxHeight - topH - timerH - powerH - bannerH - 16.dp
            val boardW = maxWidth - 16.dp

            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = vm.fx.shake.floatValue },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Scaled(ui) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TopBar(vm, compact)
                        TimerBar(vm, compact)
                    }
                }
                Board(
                    vm, boardW, boardH,
                    onHammer = { b -> pay(Power.HAMMER) { vm.smash(b) } },
                    onScissors = { b -> pay(Power.SCISSORS) { vm.cut(b) } },
                )
                // Level 1 tutorial: the hint sits where the powers are, so hide them until the first block leaves.
                if (!Progress.tutorialDone && vm.level?.number == 1) {
                    Spacer(Modifier.height(powerH))
                } else {
                    Scaled(ui) {
                        PowerBar(vm, compact) { p ->
                            when (p) {
                                Power.HINT -> pay(p) { vm.showHint() }
                                Power.FREEZE -> pay(p) { vm.freeze() }
                                Power.HAMMER -> {
                                    vm.scissorsMode = false
                                    vm.hammerMode = !vm.hammerMode
                                }
                                Power.SCISSORS -> {
                                    vm.hammerMode = false
                                    vm.scissorsMode = !vm.scissorsMode
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                BannerAd(ads)
            }
        }

        ParticlesLayer(vm)
        FloatingTexts(vm.fx)
        TutorialLayer(vm)

        vm.intro?.let { m -> MechanicDialog(m) { vm.dismissIntro() } }

        AnimatedVisibility(vm.paused, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
            PauseOverlay(vm, ads, onHome)
        }
        AnimatedVisibility(
            vm.showWin,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.6f)),
            exit = fadeOut(tween(150)),
        ) { WinOverlay(vm, ads, onHome) }
        AnimatedVisibility(
            vm.showTimeUp,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(tween(150)),
        ) { TimeUpOverlay(vm, ads, onHome) }

        pendingPay?.let { pending ->
            PayWithVideoDialog(
                power = pending.power,
                ads = ads,
                onWatch = {
                    pendingPay = null
                    ads.showRewarded {
                        vm.sound.play(Sfx.COIN)
                        pending.action()
                    }
                },
                onCancel = { pendingPay = null },
            )
        }
    }
}

// --------------------------------------------------------------------------- header

@Composable
private fun TopBar(vm: GameViewModel, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 46.dp else 54.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitText(
            if (vm.isDaily) stringResource(R.string.daily_title) else stringResource(R.string.level_x, vm.level?.number ?: 1),
            Styles.gradient(if (compact) 22 else 26),
            modifier = Modifier.weight(1f),
            minSp = 14f,
        )
        Spacer(Modifier.width(8.dp))
        CoinPill(Progress.coins)
        Spacer(Modifier.width(10.dp))
        GlassCircleButton(onClick = { vm.paused = true }) { PauseIcon(Modifier.size(16.dp)) }
    }
}

@Composable
private fun TimerBar(vm: GameViewModel, compact: Boolean) {
    val frozen = vm.frozenLeft > 0f
    val frac = (vm.timeLeft / vm.timeTotal).coerceIn(0f, 1f)
    val urgent = vm.timeLeft <= 10f && vm.started && !frozen && !vm.finished
    val transition = rememberInfiniteTransition(label = "timer")
    val pulse by transition.animateFloat(1f, 1.06f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "p")
    val seconds = vm.timeLeft.toInt().coerceAtLeast(0)
    val label = "%d:%02d".format(seconds / 60, seconds % 60)
    val barColors = when {
        frozen -> listOf(Color(0xFF7DD3FC), Color(0xFF38BDF8))
        frac > 0.5f -> listOf(Color(0xFF34D399), Color(0xFF22D3EE))
        frac > 0.25f -> listOf(Color(0xFFFACC15), Color(0xFFFB923C))
        else -> listOf(Color(0xFFFB7185), Color(0xFFF43F5E))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 64.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .graphicsLayer {
                val s = if (urgent) pulse else 1f
                scaleX = s
                scaleY = s
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF))
                .border(1.dp, Palette.GlassBorder, RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(barColors)),
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (frozen) "❄" else "⏱", style = Styles.plain(if (compact) 16 else 18, Color.White, FontWeight.Black))
                Spacer(Modifier.weight(1f))
                Text(label, style = Styles.glow(if (compact) 18 else 22, Color.White, FontWeight.Black))
            }
        }
    }
}

// --------------------------------------------------------------------------- board

@Composable
private fun Board(
    vm: GameViewModel,
    maxW: Dp,
    maxH: Dp,
    onHammer: (com.farias.prismajam.game.Block) -> Unit,
    onScissors: (com.farias.prismajam.game.Block) -> Unit,
) {
    val rows = vm.rows
    val cols = vm.cols
    // Frame thickness is ~0.42 cell on each side (room for the gates).
    // Leave a margin around the board and keep cells moderate so pieces don't look oversized.
    // Board as wide as the screen minus 32 dp on each side (height permitting).
    val cell = minOf((maxW + 16.dp - 64.dp) / (cols + 0.84f), maxH / (rows + 1.07f))
    val frame = cell * 0.42f
    val w = cell * cols + frame * 2
    val h = cell * rows + frame * 2
    val measurer = rememberTextMeasurer()
    val transition = rememberInfiniteTransition(label = "gates")
    val pulseT by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "g")

    val slab = frame * 0.55f
    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.height(maxH), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(w, h + slab)
                // A gentle 3D tilt: the board lies on a table, the far edge leaning away.
                .graphicsLayer {
                    rotationX = BOARD_TILT
                }
                .onGloballyPositioned { coords ->
                    val f = with(localDensity) { frame.toPx() }
                    vm.cellPx = with(localDensity) { cell.toPx() }
                    vm.boardOrigin = Offset(f, f)
                    vm.toRoot = { p -> if (coords.isAttached) coords.localToRoot(p) else p }
                }
                .pointerInput(vm.hammerMode, vm.scissorsMode, vm.level?.number) {
                    // Pointer positions arrive in the board's own (untilted) space.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (vm.hammerMode) {
                            vm.hammerTarget(down.position)?.let { onHammer(it) } ?: vm.playInvalid()
                            return@awaitEachGesture
                        }
                        if (vm.scissorsMode) {
                            vm.hammerTarget(down.position)?.takeIf { it.shape.size >= 2 && !it.stuck }
                                ?.let { onScissors(it) } ?: vm.playInvalid()
                            return@awaitEachGesture
                        }
                        vm.touchDown(down.position)
                        if (vm.dragging < 0) return@awaitEachGesture
                        down.consume()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                vm.touchMove(change.position)
                                change.consume()
                                if (vm.dragging < 0) break
                            }
                        } catch (e: CancellationException) {
                            vm.touchUp()
                            throw e
                        }
                        vm.touchUp()
                    }
                },
        ) {
            vm.fx.frame.longValue // redraw every frame
            val c = cell.toPx()
            val f = frame.toPx()
            val o = Offset(f, f)

            drawBoardBase(
                o, c, rows, cols, f, slab.toPx(),
                when {
                    vm.hammerMode -> Palette.Danger.copy(alpha = 0.5f + 0.4f * pulseT)
                    vm.scissorsMode -> Palette.Cyan.copy(alpha = 0.5f + 0.4f * pulseT)
                    else -> null
                },
            )

            // Gates (flash when a block goes through)
            for (g in vm.gates) {
                var flash = 0f
                for (e in vm.exits) {
                    if (e.side == g.side && e.block.color == g.color) {
                        flash = maxOf(flash, 1f - ((vm.fx.now - e.start) / GameViewModel.EXIT_NANOS.toFloat()).coerceIn(0f, 1f))
                    }
                }
                val gi = vm.gates.indexOf(g)
                drawGate(
                    g, o, c, rows, cols, f, Palette.block(g.color), pulseT, flash,
                    bar = g.side != Side.BOTTOM, closed = vm.gateClosedness(gi), timed = g.timed,
                )
            }
            for (s in vm.stones) drawStone(s, o, c)

            // Block that just left: a quick white flash and swell where it was, while its shards fly.
            for (e in vm.exits) {
                val t = ((vm.fx.now - e.start) / GameViewModel.EXIT_NANOS.toFloat()).coerceIn(0f, 1f)
                drawJellyBlock(
                    e.block,
                    Offset(o.x + e.block.c * c, o.y + e.block.r * c),
                    c, Palette.block(e.block.color),
                    alpha = (1f - t) * (1f - t), whiten = 0.8f, scale = 1f + 0.18f * t,
                )
            }

            // Blocks (the dragged one last, on top)
            // Back to front so each block covers the side wall of the one behind it; the dragged one on top.
            val list = vm.blocks.sortedWith(compareBy({ if (it.id == vm.dragging) 1 else 0 }, { it.r + it.shape.rows }))
            val overT = if (vm.timeUp) 1f else 0f
            for (b in list) {
                val v = vm.visual[b.id] ?: Offset(b.c.toFloat(), b.r.toFloat())
                val pos = Offset(o.x + v.x * c, o.y + v.y * c)
                var color = Palette.block(b.color)
                if (overT > 0f) color = lerp(color, Palette.gray(color), 0.7f)
                drawJellyBlock(b, pos, c, color, lifted = b.id == vm.dragging)
                if (b.inner != 0) {
                    var inner = Palette.block(b.inner)
                    if (overT > 0f) inner = lerp(inner, Palette.gray(inner), 0.7f)
                    drawLayerCore(b, pos, c, inner)
                }
                if (b.lock > 0) drawLock(b, pos, c)
                if (b.key > 0) drawKey(b, pos, c)
                if (b.ice > 0) {
                    drawIce(b, pos, c)
                    val text = measurer.measure(
                        b.ice.toString(),
                        TextStyle(color = Color(0xFF0369A1), fontSize = (c * 0.42f / density / fontScale).sp, fontWeight = FontWeight.Black),
                    )
                    drawText(
                        text,
                        topLeft = Offset(
                            pos.x + b.shape.cols * c / 2f - text.size.width / 2f,
                            pos.y + b.shape.rows * c / 2f - text.size.height / 2f,
                        ),
                    )
                }
            }
            // Hint: glow on the block, ghost where to slide it, arrow out through the gate.
            vm.hint?.let { h ->
                val hb = vm.blocks.firstOrNull { it.id == h.blockId }
                if (hb != null) {
                    val v = vm.visual[hb.id] ?: Offset(hb.c.toFloat(), hb.r.toFloat())
                    drawHint(
                        hb, Offset(o.x + v.x * c, o.y + v.y * c), Offset(o.x + h.target.c * c, o.y + h.target.r * c),
                        h.side, c, pulseT,
                    )
                }
            }
            // The front gates sit in front of the blocks (nearer the player).
            for (g in vm.gates) {
                if (g.side != Side.BOTTOM) continue
                var flash = 0f
                for (e in vm.exits) {
                    if (e.side == g.side && e.block.color == g.color) {
                        flash = maxOf(flash, 1f - ((vm.fx.now - e.start) / GameViewModel.EXIT_NANOS.toFloat()).coerceIn(0f, 1f))
                    }
                }
                drawGate(
                    g, o, c, rows, cols, f, Palette.block(g.color), pulseT, flash,
                    spill = false, closed = vm.gateClosedness(vm.gates.indexOf(g)), timed = g.timed,
                )
            }
            // Exit burst: shockwave at the gate, glass shards and glitter.
            for (e in vm.exits) {
                val t = ((vm.fx.now - e.start) / GameViewModel.EXIT_NANOS.toFloat()).coerceIn(0f, 1f)
                val b = e.block
                val midR = b.r + b.shape.rows / 2f
                val midC = b.c + b.shape.cols / 2f
                val at = when (e.side) {
                    Side.TOP -> Offset(midC, 0f)
                    Side.BOTTOM -> Offset(midC, rows.toFloat())
                    Side.LEFT -> Offset(0f, midR)
                    Side.RIGHT -> Offset(cols.toFloat(), midR)
                }
                drawShockwave(Offset(o.x + at.x * c, o.y + at.y * c), c, Palette.block(b.color), t)
            }
            drawShards(vm.shards, o, c)
            drawSparkles(vm.sparkles, o, c, vm.fx.now / 1e9f)
        }
    }
}

// --------------------------------------------------------------------------- power-ups

@Composable
private fun PowerBar(vm: GameViewModel, compact: Boolean, onUse: (Power) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 62.dp else 76.dp)
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        PowerButton(vm, Power.HINT, stringResource(R.string.power_hint), compact, onUse) { HintIcon(it) }
        PowerButton(vm, Power.FREEZE, stringResource(R.string.power_freeze), compact, onUse) { FreezeIcon(it) }
        PowerButton(vm, Power.HAMMER, stringResource(R.string.power_hammer), compact, onUse) { HammerIcon(it) }
        PowerButton(vm, Power.SCISSORS, stringResource(R.string.power_scissors), compact, onUse) { ScissorsIcon(it) }
    }
}

@Composable
private fun RowScope.PowerButton(
    vm: GameViewModel,
    power: Power,
    label: String,
    compact: Boolean,
    onUse: (Power) -> Unit,
    icon: @Composable (Modifier) -> Unit,
) {
    val active = (power == Power.HAMMER && vm.hammerMode) || (power == Power.SCISSORS && vm.scissorsMode)
    val accent = if (power == Power.SCISSORS) Palette.Cyan else Palette.Danger
    val enabled = active || vm.powerAvailable(power)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .widthIn(max = 96.dp)
            .fillMaxHeight()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.25f) else Palette.Glass)
            .border(if (active) 2.dp else 1.dp, if (active) accent else Palette.GlassBorder, shape)
            .clickable(enabled = enabled) { onUse(power) }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon(Modifier.size(if (compact) 20.dp else 24.dp))
            Spacer(Modifier.width(5.dp))
            CoinIcon(Modifier.size(11.dp))
            Spacer(Modifier.width(2.dp))
            Text(power.cost.toString(), style = Styles.plain(12, Palette.Gold, FontWeight.Bold))
        }
        if (!compact) Spacer(Modifier.height(2.dp))
        FitText(label, Styles.plain(12, Palette.TextPrimary, FontWeight.Bold), minSp = 8f)
    }
}

@Composable
fun HintIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(Palette.Gold.copy(alpha = 0.3f), w * 0.42f, Offset(w * 0.5f, h * 0.4f))
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFEF9C3), Palette.Gold), 0f, h * 0.7f), w * 0.28f, Offset(w * 0.5f, h * 0.4f))
        drawRoundRect(Color(0xFFD1D5DB), Offset(w * 0.36f, h * 0.66f), Size(w * 0.28f, h * 0.2f), CornerRadius(w * 0.05f))
        drawLine(Color(0xFF6B7280), Offset(w * 0.38f, h * 0.76f), Offset(w * 0.62f, h * 0.76f), w * 0.04f)
    }
}

@Composable
fun ScissorsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val steel = Brush.linearGradient(listOf(Color(0xFFF3F4F6), Color(0xFF9CA3AF)))
        drawLine(steel, Offset(w * 0.25f, h * 0.2f), Offset(w * 0.78f, h * 0.68f), w * 0.1f, StrokeCap.Round)
        drawLine(steel, Offset(w * 0.75f, h * 0.2f), Offset(w * 0.22f, h * 0.68f), w * 0.1f, StrokeCap.Round)
        drawCircle(Palette.Cyan, w * 0.13f, Offset(w * 0.22f, h * 0.78f), style = Stroke(w * 0.07f))
        drawCircle(Palette.Cyan, w * 0.13f, Offset(w * 0.78f, h * 0.78f), style = Stroke(w * 0.07f))
        drawCircle(Color(0xFF4B5563), w * 0.05f, Offset(w * 0.5f, h * 0.44f))
    }
}

@Composable
fun FreezeIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.45f
        val col = Color(0xFF7DD3FC)
        val sw = size.width * 0.09f
        for (i in 0 until 3) {
            val a = Math.toRadians(i * 60.0)
            val dx = (kotlin.math.cos(a) * r).toFloat()
            val dy = (kotlin.math.sin(a) * r).toFloat()
            drawLine(col, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        drawCircle(Color.White, size.width * 0.08f, c)
    }
}

@Composable
fun HammerIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawLine(Color(0xFFD97706), Offset(w * 0.3f, h * 0.9f), Offset(w * 0.62f, h * 0.38f), w * 0.12f, androidx.compose.ui.graphics.StrokeCap.Round)
        val head = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.42f, h * 0.18f)
            lineTo(w * 0.88f, h * 0.46f)
            lineTo(w * 0.74f, h * 0.68f)
            lineTo(w * 0.28f, h * 0.40f)
            close()
        }
        drawPath(head, Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF6B7280))))
    }
}

// --------------------------------------------------------------------------- layers

@Composable
private fun ParticlesLayer(vm: GameViewModel) {
    Canvas(Modifier.fillMaxSize()) {
        vm.fx.frame.longValue
        for (p in vm.fx.particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            val r = p.size * (0.5f + 0.5f * a)
            if (r > 0.1f) drawCircle(p.color.copy(alpha = a), r, Offset(p.x, p.y))
        }
    }
}

/** Level 1, first time: a finger shows how to drag a block to its gate. */
@Composable
private fun TutorialLayer(vm: GameViewModel) {
    val show = !Progress.tutorialDone && vm.level?.number == 1 && vm.dragging < 0 && !vm.finished && !vm.paused
    if (!show) return
    HintPill(stringResource(R.string.tutorial_drag), vm)
    val transition = rememberInfiniteTransition(label = "tutorial")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        if (vm.cellPx <= 1f) return@Canvas
        // Pick a block that can leave right away and show the way to its gate.
        val target = vm.tutorialTarget() ?: return@Canvas
        val (block, gateCenterCells) = target
        val from = vm.screenAt(Offset(block.c + block.shape.cols / 2f, block.r + block.shape.rows / 2f))
        val to = vm.screenAt(gateCenterCells)
        val move = ((t - 0.15f) / 0.6f).coerceIn(0f, 1f)
        val e = move * move * (3f - 2f * move)
        val pos = Offset(from.x + (to.x - from.x) * e, from.y + (to.y - from.y) * e)
        val alpha = if (t > 0.8f) 1f - (t - 0.8f) / 0.2f else (t / 0.1f).coerceAtMost(1f)
        val r = 16.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.25f * alpha), r * 1.5f, pos)
        drawCircle(Color.White.copy(alpha = 0.9f * alpha), r * 0.7f, pos)
    }
}

@Composable
private fun HintPill(text: String, vm: GameViewModel) {
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth - 48.dp.roundToPx()))
            layout(constraints.maxWidth, constraints.maxHeight) {
                vm.fx.frame.longValue
                val x = (constraints.maxWidth - placeable.width) / 2
                val y = (vm.screenAt(Offset(0f, vm.rows + 0.85f)).y + 14.dp.toPx()).roundToInt()
                placeable.place(x, y)
            }
        },
    ) {
        Text(
            text = text,
            style = Styles.plain(15, Color.White, FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xD9120B2E))
                .border(1.dp, Palette.GlassBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun FloatingTexts(fx: Fx) {
    Box(Modifier.fillMaxSize()) {
        for (t in fx.texts) key(t.id) { FloatingTextItem(t, fx) }
    }
}

@Composable
private fun FloatingTextItem(t: FloatText, fx: Fx) {
    val style = if (t.big) Styles.glow(32, t.color, FontWeight.Black) else Styles.glow(22, t.color, FontWeight.ExtraBold)
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            layout(0, 0) {
                val margin = 8.dp.roundToPx()
                val maxX = (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin)
                val x = (t.x - placeable.width / 2f).roundToInt().coerceIn(margin, maxX)
                val y = (t.y - placeable.height / 2f).roundToInt()
                placeable.place(x, y)
            }
        },
    ) {
        Text(
            text = t.text,
            style = style,
            modifier = Modifier.graphicsLayer {
                val prog = (fx.frame.longValue - t.start) / t.duration.toFloat()
                if (prog < 0f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val appear = (prog / 0.15f).coerceAtMost(1f)
                val s = 0.4f + 0.6f * easeOutBack(appear)
                scaleX = s
                scaleY = s
                translationY = -prog * 56.dp.toPx()
                alpha = if (prog > 0.7f) (1f - (prog - 0.7f) / 0.3f).coerceIn(0f, 1f) else 1f
            },
        )
    }
}

/** Degrees the board leans back (3D look). */
private const val BOARD_TILT = 0f // flat, seen from above (blocks keep their 3D thickness)
