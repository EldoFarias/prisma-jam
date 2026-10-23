package com.farias.prismajam.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.farias.prismajam.audio.Sfx
import com.farias.prismajam.audio.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Classic "ease out bounce": the block hits the floor, then bounces twice. */
private val BounceEasing = Easing { x ->
    val n1 = 7.5625f
    val d1 = 2.75f
    when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> { val t = x - 1.5f / d1; n1 * t * t + 0.75f }
        x < 2.5f / d1 -> { val t = x - 2.25f / d1; n1 * t * t + 0.9375f }
        else -> { val t = x - 2.625f / d1; n1 * t * t + 0.984375f }
    }
}

/**
 * Opening animation: four gems fall and bounce into the logo, burst into sparks,
 * then the title pops in with a light sweep. Tap anywhere to skip.
 */
@Composable
fun SplashScreen(sound: SoundManager, onFinish: () -> Unit) {
    val drops = remember { List(4) { Animatable(0f) } }
    val title = remember { Animatable(0f) }
    val shine = remember { Animatable(-1f) }
    val fx = remember { Fx() }
    var done by remember { mutableStateOf(false) }
    // Geometry of the gem cluster (px), filled in while drawing.
    val cluster = remember { floatArrayOf(0f, 0f, 0f) } // centerX, centerY, cell

    fun finish() {
        if (done) return
        done = true
        onFinish()
    }

    LaunchedEffect(Unit) {
        while (true) withFrameNanos { fx.step(it) }
    }
    LaunchedEffect(Unit) {
        delay(250)
        drops.forEachIndexed { i, anim ->
            launch {
                delay(i * 140L)
                launch {
                    delay(200) // first contact of the bounce
                    sound.play(Sfx.PLACE, 0.8f, 0.9f + i * 0.08f)
                }
                anim.animateTo(1f, tween(550, easing = BounceEasing))
            }
        }
        delay(3 * 140L + 600L)
        // Sparks from every gem
        val (cx, cy, cell) = cluster.toList()
        if (cell > 0f) {
            val colors = listOf(0, 1, 5, 6)
            for (i in 0 until 4) {
                val col = i % 2
                val row = i / 2
                val center = Offset(cx + (col - 0.5f) * cell * 1.08f, cy + (row - 0.5f) * cell * 1.08f)
                fx.burst(center, Palette.block(colors[i] + 1), cell * 0.25f, 14)
            }
        }
        sound.play(Sfx.CLEAR)
        launch { title.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
        delay(380)
        shine.animateTo(1f, tween(650))
        delay(550)
        finish()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
    ) {
        AuroraBackground()

        val unitDp = min(maxWidth, maxHeight) * 0.17f
        Canvas(Modifier.fillMaxSize()) {
            fx.frame.longValue
            val unit = unitDp.toPx()
            val gap = unit * 0.08f
            val step = unit + gap
            val cx = size.width / 2f
            val cy = size.height * 0.40f
            cluster[0] = cx
            cluster[1] = cy
            cluster[2] = unit
            val colors = listOf(1, 2, 6, 7)
            for (i in 0 until 4) {
                val v = drops[i].value
                if (v <= 0f) continue
                val col = i % 2
                val row = i / 2
                val tx = cx - step + col * step + gap / 2f
                val ty = cy - step + row * step + gap / 2f
                val startY = -unit * 2f
                val y = startY + (ty - startY) * v
                drawGem(Offset(tx, y), unit, Palette.block(colors[i]))
            }
            for (p in fx.particles) {
                val a = (p.life / p.maxLife).coerceIn(0f, 1f)
                val r = p.size * (0.5f + 0.5f * a)
                if (r > 0.1f) drawCircle(p.color.copy(alpha = a), r, Offset(p.x, p.y))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = maxHeight * 0.40f + unitDp * 1.15f + 28.dp)
                .graphicsLayer {
                    val t = title.value
                    alpha = t.coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * t
                    scaleY = 0.6f + 0.4f * t
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val s = shine.value
                    if (s > -1f && s < 1f) {
                        val x = size.width * (0.5f + 0.8f * s)
                        val band = size.width * 0.18f
                        drawRect(
                            brush = Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                                start = Offset(x - band, 0f),
                                end = Offset(x + band, size.height),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PRISMA", style = Styles.gradient(56))
            Text(
                "JAM",
                style = Styles.glow(28, Color.White, FontWeight.Black).copy(letterSpacing = 10.sp),
            )
        }
    }
}
