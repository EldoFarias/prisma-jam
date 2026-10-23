package com.farias.prismajam.ui

import android.Manifest
import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farias.prismajam.R
import com.farias.prismajam.ads.AdsManager
import com.farias.prismajam.audio.Sfx
import com.farias.prismajam.audio.SoundManager
import com.farias.prismajam.data.Progress
import com.farias.prismajam.game.Block
import com.farias.prismajam.game.BlockShape
import com.farias.prismajam.notify.DailyReminder
import kotlin.math.sin

const val VIDEO_COINS = 40

/** Day the chest popped up automatically, so it shows only once per day. */
private var chestAutoShownDay = 0

fun skipChestToday() {
    chestAutoShownDay = Progress.today()
}

@Composable
fun HomeScreen(
    ads: AdsManager,
    sound: SoundManager,
    onPlay: () -> Unit,
    onThemes: () -> Unit,
    onStats: () -> Unit,
    onMap: () -> Unit,
    onDaily: () -> Unit,
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }
    var showMissions by remember { mutableStateOf(false) }
    var showChest by remember { mutableStateOf(false) }

    // Back closes an open dialog; on the menu itself it must be pressed twice to leave the game.
    var backPressedAt by remember { mutableLongStateOf(0L) }
    var showExitHint by remember { mutableStateOf(false) }
    BackHandler {
        when {
            showSettings || showHowTo || showMissions || showChest -> {
                showSettings = false
                showHowTo = false
                showMissions = false
                showChest = false
            }
            SystemClock.uptimeMillis() - backPressedAt < EXIT_WINDOW_MS -> (context as? Activity)?.finish()
            else -> {
                backPressedAt = SystemClock.uptimeMillis()
                showExitHint = true
            }
        }
    }
    LaunchedEffect(backPressedAt) {
        if (backPressedAt == 0L) return@LaunchedEffect
        delay(EXIT_WINDOW_MS)
        showExitHint = false
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Progress.setNotifications(granted)
        DailyReminder.sync(context)
    }

    fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !DailyReminder.canNotify(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Progress.setNotifications(true)
            DailyReminder.sync(context)
        }
    }

    LaunchedEffect(Unit) {
        if (Progress.levelsWon >= 1 && !Progress.notifAsked && Progress.notificationsOn) {
            Progress.markNotifAsked()
            enableNotifications()
        } else if (Progress.chestAvailable() && chestAutoShownDay != Progress.today() && Progress.levelsWon >= 1) {
            chestAutoShownDay = Progress.today()
            showChest = true
        }
    }

    fun watchForCoins() {
        if (ads.adsReady && ads.rewardedReady) {
            ads.showRewarded {
                Progress.addCoins(VIDEO_COINS)
                sound.play(Sfx.COIN)
                Toast.makeText(context, context.getString(R.string.coins_reward, VIDEO_COINS), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, R.string.no_video, Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        FloatingBlocks()

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinPill(Progress.coins, onPlus = { watchForCoins() })
                Spacer(Modifier.weight(1f))
                Box {
                    GlassCircleButton(onClick = { showChest = true }) {
                        ChestIcon(Modifier.fillMaxSize(), shaking = Progress.chestAvailable())
                    }
                    if (Progress.chestAvailable()) NotificationDot(Modifier.align(Alignment.TopEnd))
                }
                Spacer(Modifier.width(10.dp))
                GlassCircleButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Palette.TextPrimary)
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val sz = homeSizes(maxWidth, maxHeight)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Logo(sz)
                    Column(
                        Modifier
                            .padding(horizontal = 20.dp)
                            .widthIn(max = sz.contentWidth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (Progress.winStreak >= 2) {
                            Text(stringResource(R.string.win_streak, Progress.winStreak), style = Styles.glow(17, Color(0xFFFB923C), FontWeight.Black))
                            Spacer(Modifier.height(10.dp))
                        }
                        PlayButton(stringResource(R.string.play_level, Progress.level), sz.playHeight) {
                            sound.play(Sfx.WHOOSH, 0.7f)
                            onPlay()
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MapCard(Modifier.weight(1f), sz.cardHeight, onMap)
                            DailyCard(Modifier.weight(1f), sz.cardHeight) {
                                sound.play(Sfx.WHOOSH, 0.7f)
                                onDaily()
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HomeTile(stringResource(R.string.missions), Modifier.weight(1f), sz.tileHeight, Progress.claimableMissions, { showMissions = true }) {
                                MissionIcon(Modifier.size(24.dp))
                            }
                            HomeTile(stringResource(R.string.themes), Modifier.weight(1f), sz.tileHeight, 0, onThemes) {
                                PaletteIcon(Modifier.size(24.dp))
                            }
                            HomeTile(stringResource(R.string.tile_stats), Modifier.weight(1f), sz.tileHeight, 0, onStats) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
            BannerAd(ads)
        }

        if (showSettings) {
            SettingsDialog(
                ads = ads,
                onToggleReminder = {
                    if (Progress.notificationsOn) {
                        Progress.setNotifications(false)
                        DailyReminder.sync(context)
                    } else enableNotifications()
                },
                onHowTo = {
                    showSettings = false
                    showHowTo = true
                },
                onClose = { showSettings = false },
            )
        }
        if (showHowTo) HowToDialog { showHowTo = false }
        if (showMissions) MissionsDialog(sound) { showMissions = false }
        if (showChest) ChestDialog(ads, sound) { showChest = false }

        AnimatedVisibility(
            visible = showExitHint,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                stringResource(R.string.press_back_exit),
                style = Styles.plain(15, Color.White, FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xE6120B2E))
                    .border(1.dp, Palette.GlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** Time to press back a second time to leave the game from the menu. */
private const val EXIT_WINDOW_MS = 2000L

@Composable
private fun ModeCard(
    modifier: Modifier,
    height: Dp,
    title: String,
    subtitle: String,
    colors: List<Color>,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.28f) }))
            .border(1.5.dp, Brush.linearGradient(colors), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Column {
                FitText(title, Styles.plain(17, Palette.TextPrimary, FontWeight.Black), minSp = 11f)
                FitText(subtitle, Styles.plain(13, Palette.TextSecondary, FontWeight.SemiBold), minSp = 10f)
            }
        }
        if (badge != null) {
            Text(
                badge,
                style = Styles.glow(16, Color(0xFFFB923C), FontWeight.Black),
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/** Level map: shows the current level and the stars collected. */
@Composable
private fun MapCard(modifier: Modifier, height: Dp, onClick: () -> Unit) {
    ModeCard(
        modifier = modifier,
        height = height,
        title = stringResource(R.string.map_title),
        subtitle = stringResource(R.string.level_x, Progress.level),
        colors = listOf(Color(0xFFE879F9), Color(0xFF8B5CF6)),
        badge = if (Progress.totalStars > 0) "★ ${Progress.totalStars}" else null,
        onClick = onClick,
    ) {
        Canvas(Modifier.size(26.dp)) {
            // Tiny map trail: three dots joined by a line.
            val w = size.width
            val pts = listOf(Offset(w * 0.2f, w * 0.8f), Offset(w * 0.5f, w * 0.45f), Offset(w * 0.8f, w * 0.2f))
            drawLine(Color.White.copy(alpha = 0.6f), pts[0], pts[1], w * 0.08f)
            drawLine(Color.White.copy(alpha = 0.6f), pts[1], pts[2], w * 0.08f)
            drawCircle(Palette.Cyan, w * 0.13f, pts[0])
            drawCircle(Color(0xFFE879F9), w * 0.13f, pts[1])
            drawCircle(Palette.Gold, w * 0.16f, pts[2])
        }
    }
}

/** Today's challenge: one special level per day, bigger reward, streak of days. */
@Composable
private fun DailyCard(modifier: Modifier, height: Dp, onDaily: () -> Unit) {
    val done = Progress.dailyDoneToday()
    val streak = Progress.dailyStreak()
    ModeCard(
        modifier = modifier,
        height = height,
        title = stringResource(R.string.daily_title),
        subtitle = if (done) stringResource(R.string.daily_done) else stringResource(R.string.daily_reward, Progress.dailyReward()),
        colors = listOf(Palette.Cyan, Color(0xFF34D399)),
        enabled = !done,
        badge = if (streak > 0) "🔥 $streak" else null,
        onClick = onDaily,
    ) {
        Icon(
            if (done) Icons.Default.Check else Icons.Default.Star,
            contentDescription = null,
            tint = if (done) Color(0xFF34D399) else Palette.Gold,
            modifier = Modifier.size(26.dp),
        )
    }
}

private class HomeSizes(val logo: Dp, val title: Int, val subtitle: Int, val playHeight: Dp, val tileHeight: Dp, val contentWidth: Dp, val cardHeight: Dp)

private fun homeSizes(width: Dp, height: Dp): HomeSizes = when {
    width >= 600.dp && height >= 700.dp -> HomeSizes(170.dp, 64, 32, 84.dp, 92.dp, 560.dp, 128.dp)
    height < 560.dp -> HomeSizes(70.dp, 32, 18, 56.dp, 60.dp, 420.dp, 104.dp)
    height < 640.dp -> HomeSizes(90.dp, 38, 20, 60.dp, 64.dp, 420.dp, 108.dp)
    else -> HomeSizes(128.dp, 50, 26, 74.dp, 76.dp, 420.dp, 118.dp)
}

/** Logo: a little jam of jelly blocks heading for a glowing gate. */
@Composable
private fun Logo(sz: HomeSizes) {
    val transition = rememberInfiniteTransition(label = "logo")
    val t by transition.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "bob")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(sz.logo)
                .graphicsLayer { translationY = sin(t) * 5.dp.toPx() },
        ) {
            val cell = size.width / 3.4f
            val o = Offset((size.width - cell * 3) / 2f, (size.height - cell * 3) / 2f)
            val shapes = BlockShape.all
            val l = shapes.first { it.cells.size == 3 && it.rows == 2 && it.cols == 2 }
            val bar = shapes.first { it.rows == 1 && it.cols == 2 }
            val dot = shapes.first { it.size == 1 }
            drawJellyBlock(Block(1, l, 2, 0, 0), o, cell, Palette.block(2))
            drawJellyBlock(Block(2, bar, 1, 2, 0), o, cell, Palette.block(1))
            drawJellyBlock(Block(3, dot, 7, 0, 2), o, cell, Palette.block(7))
            drawJellyBlock(Block(4, dot, 3, 1, 2), Offset(o.x, o.y + sin(t * 2) * cell * 0.04f), cell, Palette.block(3))
            drawJellyBlock(Block(5, dot, 6, 2, 2), o, cell, Palette.block(6))
        }
        Spacer(Modifier.height(10.dp))
        Text("PRISMA", style = Styles.gradient(sz.title))
        Text("JAM", style = Styles.glow(sz.subtitle, Color.White, FontWeight.Black).copy(letterSpacing = (sz.subtitle * 0.5f).sp))
    }
}

@Composable
private fun PlayButton(text: String, height: Dp, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "play")
    val pulse by transition.animateFloat(1f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFDB2777))))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(22.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                }
                drawPath(path, Color.White)
            }
            Spacer(Modifier.width(12.dp))
            FitText(text, Styles.plain(24, Color.White, FontWeight.Black).copy(letterSpacing = 1.sp), minSp = 14f)
        }
    }
}

@Composable
private fun HomeTile(label: String, modifier: Modifier, height: Dp, badge: Int, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(Palette.Glass)
                .border(1.dp, if (badge > 0) Color(0xFF34D399) else Palette.GlassBorder, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.height(6.dp))
            FitText(label, Styles.plain(13, Palette.TextPrimary, FontWeight.Bold), minSp = 9f)
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Palette.Danger),
                contentAlignment = Alignment.Center,
            ) { Text(badge.toString(), style = Styles.plain(12, Color.White, FontWeight.Black)) }
        }
    }
}

@Composable
private fun NotificationDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Palette.Danger)
            .border(1.5.dp, Color.White, CircleShape),
    )
}

@Composable
private fun MissionIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF34D399), Offset(w * 0.15f, h * 0.08f), androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.86f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f))
        val line = Color.White.copy(alpha = 0.9f)
        for (i in 0 until 3) {
            val y = h * (0.3f + i * 0.22f)
            drawCircle(line, w * 0.05f, Offset(w * 0.32f, y))
            drawLine(line, Offset(w * 0.42f, y), Offset(w * 0.72f, y), w * 0.08f)
        }
    }
}

@Composable
fun PaletteIcon(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.24f
        val colors = Palette.theme.blocks
        drawCircle(colors[0], r, Offset(size.width * 0.28f, size.height * 0.30f))
        drawCircle(colors[1], r, Offset(size.width * 0.72f, size.height * 0.30f))
        drawCircle(colors[2], r, Offset(size.width * 0.28f, size.height * 0.72f))
        drawCircle(colors[6], r, Offset(size.width * 0.72f, size.height * 0.72f))
    }
}

/** A few small jelly blocks drifting up behind the menu. */
@Composable
private fun FloatingBlocks() {
    val transition = rememberInfiniteTransition(label = "float")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(Modifier.fillMaxSize()) {
        val xs = listOf(0.08f, 0.24f, 0.4f, 0.58f, 0.74f, 0.9f, 0.16f, 0.66f)
        xs.forEachIndexed { i, x ->
            val speed = 0.6f + (i % 3) * 0.25f
            val y = 1.1f - ((t * speed + i * 0.13f) % 1.2f)
            val s = size.minDimension * (0.035f + (i % 4) * 0.01f)
            val shape = BlockShape.all[i % 6]
            drawJellyBlock(Block(i, shape, i % 7 + 1, 0, 0), Offset(size.width * x, size.height * y), s, Palette.block(i % 7 + 1), alpha = 0.25f)
        }
    }
}

@Composable
private fun SettingsDialog(ads: AdsManager, onToggleReminder: () -> Unit, onHowTo: () -> Unit, onClose: () -> Unit) {
    DimOverlay {
        GlassPanel {
            Text(stringResource(R.string.settings), style = Styles.gradient(30))
            Spacer(Modifier.height(4.dp))
            SettingsToggles()
            ToggleRow(stringResource(R.string.notifications), Progress.notificationsOn) { onToggleReminder() }
            if (ads.privacyOptionsRequired) PrivacyLink(ads)
            Spacer(Modifier.height(4.dp))
            SecondaryButton(stringResource(R.string.how_to)) { onHowTo() }
            PrimaryButton(stringResource(R.string.got_it)) { onClose() }
        }
    }
}
