package com.farias.prismajam.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val color: Color, val size: Float,
    /** Vertical acceleration in px/s² (negative floats up, for embers). */
    val g: Float = 1800f,
)

/** A cleared cell that is bursting out of the board. */
class FlashCell(val index: Int, val color: Color, val start: Long)

class FloatText(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val color: Color,
    val big: Boolean,
    val start: Long,
    val duration: Long = 1_100_000_000L,
)

/**
 * Lightweight effects system driven by the frame clock.
 * Plain lists are mutated every frame; readers observe [frame] to redraw.
 */
class Fx {
    val frame = mutableLongStateOf(0L)
    val shake = mutableFloatStateOf(0f)
    val particles = ArrayList<Particle>()
    val flashes = ArrayList<FlashCell>()
    val texts = mutableStateListOf<FloatText>()
    /** Placement time per board cell (nanos), for the "pop in" animation. */
    val pops = LongArray(64)

    var now: Long = 0L
        private set
    private var last = 0L
    private var nextId = 1L
    private var shakeAmount = 0f
    private val rng = Random.Default

    fun step(frameNanos: Long) {
        now = frameNanos
        val dt = if (last == 0L) 0.016f else ((frameNanos - last) / 1e9f).coerceIn(0f, 0.05f)
        last = frameNanos

        if (particles.isNotEmpty()) {
            val it = particles.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.life -= dt
                if (p.life <= 0f) { it.remove(); continue }
                p.vy += p.g * dt
                p.vx *= 0.985f
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
        }
        if (flashes.isNotEmpty()) flashes.removeAll { now - it.start > FLASH_NANOS }
        if (texts.isNotEmpty()) {
            val dead = texts.filter { now - it.start > it.duration }
            if (dead.isNotEmpty()) texts.removeAll(dead)
        }
        if (shakeAmount > 0.1f) {
            shakeAmount *= 0.88f
            shake.floatValue = sin(now / 18_000_000.0).toFloat() * shakeAmount
        } else if (shake.floatValue != 0f) {
            shakeAmount = 0f
            shake.floatValue = 0f
        }
        frame.longValue = frameNanos
    }

    fun burst(center: Offset, color: Color, cellPx: Float, count: Int = 7) {
        repeat(count) {
            val angle = rng.nextFloat() * (Math.PI * 2).toFloat()
            val speed = cellPx * (4f + rng.nextFloat() * 9f)
            val life = 0.45f + rng.nextFloat() * 0.45f
            particles.add(
                Particle(
                    x = center.x, y = center.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - cellPx * 6f,
                    life = life, maxLife = life,
                    color = if (rng.nextFloat() < 0.25f) Color.White else color,
                    size = cellPx * (0.08f + rng.nextFloat() * 0.12f),
                )
            )
        }
        if (particles.size > 600) particles.subList(0, particles.size - 600).clear()
    }

    /** A small glowing ember that floats up (combo streak fire). */
    fun ember(x: Float, y: Float, cellPx: Float) {
        val life = 0.5f + rng.nextFloat() * 0.6f
        val color = EMBER_COLORS[rng.nextInt(EMBER_COLORS.size)]
        particles.add(
            Particle(
                x = x, y = y,
                vx = (rng.nextFloat() - 0.5f) * cellPx * 1.2f,
                vy = -cellPx * (1.5f + rng.nextFloat() * 2.5f),
                life = life, maxLife = life,
                color = color,
                size = cellPx * (0.05f + rng.nextFloat() * 0.07f),
                g = -cellPx * 0.5f,
            )
        )
    }

    fun flash(index: Int, color: Color) {
        flashes.add(FlashCell(index, color, now))
    }

    fun text(text: String, at: Offset, color: Color, big: Boolean = false, delayMs: Long = 0) {
        texts.add(FloatText(nextId++, text, at.x, at.y, color, big, now + delayMs * 1_000_000L))
    }

    fun shake(amountPx: Float) {
        shakeAmount = maxOf(shakeAmount, amountPx)
    }

    fun pop(index: Int) {
        pops[index] = now
    }

    companion object {
        const val FLASH_NANOS = 380_000_000L
        const val POP_NANOS = 200_000_000L
        val EMBER_COLORS = listOf(Color(0xFFFFD166), Color(0xFFFF9F1C), Color(0xFFFF5E3A), Color(0xFFFFF3B0))
    }
}
