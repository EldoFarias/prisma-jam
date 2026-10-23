package com.farias.prismajam.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.farias.prismajam.R
import com.farias.prismajam.data.Progress
import kotlinx.coroutines.flow.first
import kotlin.math.sin

private val ROW_HEIGHT = 108.dp
private const val LOCKED_AHEAD = 10

/** Horizontal position (0..1) of a level node along the winding path. */
private fun pathX(level: Int): Float = 0.5f + 0.28f * sin(level * 0.85f)

/** Every 10th level is a milestone (crown, gold node). */
private fun isMilestone(n: Int) = n % 10 == 0

/** Level map: a winding trail of levels with the stars earned on each; the current one pulses. */
@Composable
fun MapScreen(onBack: () -> Unit, onPlay: (Int) -> Unit) {
    BackHandler(onBack = onBack)
    val current = Progress.level
    val shown = current + LOCKED_AHEAD
    val list = rememberLazyListState()
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Palette.TextPrimary)
                }
                Spacer(Modifier.width(12.dp))
                FitText(stringResource(R.string.map_title), Styles.gradient(24), modifier = Modifier.weight(1f), minSp = 14f)
                Spacer(Modifier.width(8.dp))
                StarPill(Progress.totalStars)
                Spacer(Modifier.width(10.dp))
                CoinPill(Progress.coins)
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewportPx = with(density) { maxHeight.toPx() }
                val mapWidth = maxWidth
                // Scroll so the current level sits in the middle of the screen.
                LaunchedEffect(current) {
                    snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
                    val rowPx = with(density) { ROW_HEIGHT.toPx() }
                    val target = ((shown - current) * rowPx - (viewportPx - rowPx) / 2f).coerceAtLeast(0f)
                    list.scrollToItem((target / rowPx).toInt(), (target % rowPx).toInt())
                }
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = ROW_HEIGHT / 2),
                ) {
                    items(shown, key = { shown - it }) { i ->
                        val n = shown - i
                        MapRow(n, shown, current, mapWidth) { onPlay(n) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarPill(stars: Int) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Palette.Glass)
            .border(1.dp, Palette.GlassBorder, shape)
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StarIcon(true, Modifier.size(20.dp))
        Spacer(Modifier.width(5.dp))
        Text(stars.toString(), style = Styles.plain(15, Palette.Gold, FontWeight.Black))
    }
}

@Composable
private fun MapRow(n: Int, shown: Int, current: Int, width: Dp, onLevel: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val row = size.height
            val c = Offset(size.width * pathX(n), row / 2f)
            if (n < shown) pathHalf(c, Offset(size.width * (pathX(n) + pathX(n + 1)) / 2f, 0f), unlocked = n + 1 <= current)
            if (n > 1) pathHalf(c, Offset(size.width * (pathX(n) + pathX(n - 1)) / 2f, row), unlocked = n <= current)
        }
        LevelNode(n, current, width, onLevel)
    }
}

private fun DrawScope.pathHalf(from: Offset, to: Offset, unlocked: Boolean) {
    drawLine(
        color = if (unlocked) Palette.Cyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.15f),
        start = from, end = to,
        strokeWidth = if (unlocked) 6.dp.toPx() else 4.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = if (unlocked) null else PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 10.dp.toPx())),
    )
}

@Composable
private fun LevelNode(n: Int, current: Int, width: Dp, onClick: () -> Unit) {
    val locked = n > current
    val isCurrent = n == current
    val milestone = isMilestone(n)
    val stars = Progress.starsFor(n)
    val nodeSize = (if (isCurrent) 76.dp else 62.dp) + (if (milestone) 8.dp else 0.dp)
    val pulse = if (isCurrent) pulseValue() else 1f
    val tint = Palette.block(1 + n % 7)
    Box(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
    ) {
        Column(
            modifier = Modifier
                .offset(x = width * pathX(n) - 50.dp)
                .width(100.dp)
                .align(Alignment.CenterStart),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(nodeSize)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .clip(RoundedCornerShape(nodeSize * 0.3f))
                        .background(
                            when {
                                locked -> Brush.verticalGradient(listOf(Color(0xFF2A2547), Color(0xFF17132E)))
                                isCurrent -> Brush.verticalGradient(listOf(Color(0xFFF0ABFC), Color(0xFF8B5CF6)))
                                milestone -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFD97706)))
                                else -> Brush.verticalGradient(listOf(Palette.lighter(tint, 0.2f), Palette.darker(tint, 0.35f)))
                            },
                        )
                        .border(
                            if (isCurrent || milestone) 3.dp else 2.dp,
                            when {
                                milestone -> Palette.Gold.copy(alpha = if (locked) 0.45f else 1f)
                                locked -> Color(0x33FFFFFF)
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            RoundedCornerShape(nodeSize * 0.3f),
                        )
                        .clickable(enabled = !locked, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (locked) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0x88FFFFFF), modifier = Modifier.size(24.dp))
                    } else {
                        Text(n.toString(), style = Styles.plain(if (isCurrent) 26 else 22, Color.White, FontWeight.Black))
                    }
                }
                if (milestone) {
                    CrownIcon(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-16).dp)
                            .size(26.dp)
                            .alpha(if (locked) 0.5f else 1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (!locked && !isCurrent) {
                Row {
                    for (i in 0 until 3) StarIcon(i < stars, Modifier.padding(horizontal = 1.dp).size(16.dp))
                }
            } else if (isCurrent) {
                Text(stringResource(R.string.play), style = Styles.glow(14, Palette.Gold, FontWeight.Black))
            }
        }
    }
}

@Composable
private fun pulseValue(): Float {
    val transition = rememberInfiniteTransition(label = "current")
    val pulse by transition.animateFloat(1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    return pulse
}
