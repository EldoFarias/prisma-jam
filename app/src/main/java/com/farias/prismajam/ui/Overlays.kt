package com.farias.prismajam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.farias.prismajam.R
import com.farias.prismajam.ads.AdsManager
import com.farias.prismajam.audio.Haptics
import com.farias.prismajam.data.Progress
import com.farias.prismajam.game.GameViewModel
import com.farias.prismajam.game.Haptic
import com.farias.prismajam.game.Power
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun SettingsToggles() {
    val ctx = LocalContext.current
    ToggleRow(stringResource(R.string.music), Progress.musicOn) {
        Progress.toggleMusic()
        com.farias.prismajam.audio.MusicPlayer.update()
    }
    ToggleRow(stringResource(R.string.sound), Progress.soundOn) { Progress.toggleSound() }
    ToggleRow(stringResource(R.string.vibration), Progress.vibrationOn) {
        Progress.toggleVibration()
        if (Progress.vibrationOn) Haptics.play(ctx, Haptic.CLEAR)
    }
}

@Composable
fun PauseOverlay(vm: GameViewModel, ads: AdsManager, onHome: () -> Unit) {
    DimOverlay {
        GlassPanel {
            Text(stringResource(R.string.paused), style = Styles.gradient(34))
            Spacer(Modifier.height(4.dp))
            SettingsToggles()
            Spacer(Modifier.height(8.dp))
            PrimaryButton(stringResource(R.string.resume)) { vm.paused = false }
            SecondaryButton(stringResource(R.string.restart)) {
                vm.paused = false
                ads.onNewGame { vm.restart() }
            }
            SecondaryButton(stringResource(R.string.home)) {
                vm.paused = false
                onHome()
            }
            if (ads.privacyOptionsRequired) PrivacyLink(ads)
        }
    }
}

@Composable
fun PrivacyLink(ads: AdsManager) {
    Text(
        text = stringResource(R.string.privacy_options),
        style = Styles.plain(14, Palette.TextSecondary),
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable { ads.showPrivacyOptions() }
            .padding(8.dp),
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Styles.plain(17), modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF8B5CF6),
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Color(0x33FFFFFF),
                uncheckedBorderColor = Color(0x55FFFFFF),
            ),
        )
    }
}

@Composable
private fun CoinsEarned(amount: Int) {
    if (amount <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoinIcon(Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.coins_earned, amount), style = Styles.glow(19, Palette.Gold, FontWeight.Black))
    }
}

@Composable
fun WinOverlay(vm: GameViewModel, ads: AdsManager, onHome: () -> Unit) {
    val lv = vm.level ?: return
    val used = (vm.timeTotal - vm.timeLeft).toInt().coerceAtLeast(0)
    DimOverlay {
        GlassPanel {
            Text(
                if (vm.isDaily) stringResource(R.string.daily_title) else stringResource(R.string.level_x, lv.number),
                style = Styles.plain(16, Palette.TextSecondary, FontWeight.Bold),
            )
            Text(
                stringResource(if (vm.isDaily) R.string.daily_complete else R.string.level_complete),
                style = Styles.glow(30, Palette.Gold, FontWeight.Black), textAlign = TextAlign.Center,
            )
            WinStars(vm.starsEarned)
            Text(stringResource(R.string.time_used, used / 60, used % 60), style = Styles.plain(16, Palette.TextPrimary, FontWeight.Bold))
            if (!vm.isDaily && Progress.winStreak >= 2) {
                Text(stringResource(R.string.win_streak, Progress.winStreak), style = Styles.glow(18, Color(0xFFFB923C), FontWeight.Black))
            }
            CoinsEarned(vm.coinsEarned)
            Spacer(Modifier.height(8.dp))
            if (vm.isDaily) {
                PrimaryButton(stringResource(R.string.home)) { onHome() }
            } else {
                PrimaryButton(stringResource(R.string.next_level)) {
                    ads.onNewGame { vm.startLevel(lv.number + 1) }
                }
                SecondaryButton(stringResource(R.string.home)) { onHome() }
            }
        }
    }
}

/** Three stars that pop in one after another. */
@Composable
private fun WinStars(earned: Int) {
    val anim = remember { List(3) { androidx.compose.animation.core.Animatable(0f) } }
    androidx.compose.runtime.LaunchedEffect(earned) {
        anim.forEachIndexed { i, a ->
            kotlinx.coroutines.delay(if (i == 0) 250L else 180L)
            a.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 380f))
        }
    }
    Row(verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 3) {
            val big = i == 1
            val sc = anim[i].value
            StarIcon(
                filled = i < earned,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (big) 52.dp else 42.dp)
                    .graphicsLayer {
                        scaleX = sc
                        scaleY = sc
                        rotationZ = (1f - sc) * -40f
                    },
            )
        }
    }
}

@Composable
fun TimeUpOverlay(vm: GameViewModel, ads: AdsManager, onHome: () -> Unit) {
    val lv = vm.level ?: return
    val left = vm.blocks.size
    DimOverlay {
        GlassPanel {
            Text("⏰", style = Styles.plain(48))
            Text(stringResource(R.string.time_up), style = Styles.plain(26, Palette.TextPrimary, FontWeight.Black))
            Text(
                stringResource(R.string.blocks_left, left),
                style = Styles.plain(16, Palette.TextSecondary, FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            if (!vm.extraTimeUsed && ads.adsReady) {
                PrimaryButton(
                    text = stringResource(if (ads.rewardedReady) R.string.extra_time_video else R.string.loading_video),
                    enabled = ads.rewardedReady,
                ) { ads.showRewarded { vm.addTime() } }
            }
            SecondaryButton(stringResource(R.string.try_again)) {
                vm.acceptLoss()
                ads.onNewGame { if (vm.isDaily) vm.restart() else vm.startLevel(lv.number) }
            }
            SecondaryButton(stringResource(R.string.home)) {
                vm.acceptLoss()
                onHome()
            }
        }
    }
}

@Composable
fun PayWithVideoDialog(power: Power, ads: AdsManager, onWatch: () -> Unit, onCancel: () -> Unit) {
    DimOverlay {
        GlassPanel {
            Text(stringResource(R.string.not_enough_title), style = Styles.plain(22, Palette.TextPrimary, FontWeight.Black))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.need_coins, (power.cost - Progress.coins).coerceAtLeast(1)),
                    style = Styles.plain(16, Palette.Gold, FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(4.dp))
            if (ads.adsReady && ads.rewardedReady) {
                PrimaryButton(stringResource(R.string.watch_free)) { onWatch() }
            } else {
                Text(stringResource(R.string.no_video), style = Styles.plain(14, Palette.TextSecondary), textAlign = TextAlign.Center)
            }
            SecondaryButton(stringResource(R.string.cancel)) { onCancel() }
        }
    }
}

@Composable
fun HowToDialog(onClose: () -> Unit) {
    DimOverlay {
        GlassPanel {
            Text(stringResource(R.string.how_to), style = Styles.gradient(30))
            val steps = listOf(R.string.how_to_1, R.string.how_to_2, R.string.how_to_3, R.string.how_to_4)
            steps.forEachIndexed { i, res ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                            drawGem(androidx.compose.ui.geometry.Offset.Zero, size.width, Palette.block(i + 1), glow = false)
                        }
                        Text("${i + 1}", style = Styles.plain(13, Color.White, FontWeight.Black))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(res), style = Styles.plain(16, Palette.TextPrimary, FontWeight.Medium))
                }
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton(stringResource(R.string.got_it)) { onClose() }
        }
    }
}

/**
 * Anchored adaptive banner across the full width (the large variant looked too tall). Measured in real pixels
 * because on tablets the app runs under [Scaled] (bigger density), and the AdView itself uses the real density.
 */
@Suppress("DEPRECATION")
fun bannerAdSize(context: Context, widthPx: Int): AdSize =
    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, (widthPx / context.resources.displayMetrics.density).toInt())

/** Banner height in the current (possibly scaled) dp, to reserve the space before the ad loads. */
@Composable
fun bannerHeight(adSize: AdSize): Dp {
    val context = LocalContext.current
    return with(LocalDensity.current) { adSize.getHeightInPixels(context).toDp() }
}

/** Adaptive banner at the bottom. Space is reserved up front so the layout doesn't jump. */
@Composable
fun BannerAd(ads: AdsManager) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val adSize = remember(widthPx) { bannerAdSize(context, widthPx) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight(adSize)),
            contentAlignment = Alignment.Center,
        ) {
            if (ads.adsReady) {
                // New AdView when the size changes (rotation): an AdView's size can't be changed after loading.
                key(adSize) {
                    AndroidView(
                        factory = { ctx ->
                            AdView(ctx).apply {
                                setAdSize(adSize)
                                adUnitId = ads.bannerId
                                loadAd(AdRequest.Builder().build())
                            }
                        },
                        onRelease = { it.destroy() },
                    )
                }
            }
        }
    }
}


/** First time an obstacle appears: what it is and how to beat it, with a small drawing. */
@Composable
fun MechanicDialog(m: com.farias.prismajam.game.Mechanic, onClose: () -> Unit) {
    val (title, text) = when (m) {
        com.farias.prismajam.game.Mechanic.STONE -> R.string.mech_stone_t to R.string.mech_stone
        com.farias.prismajam.game.Mechanic.ICE -> R.string.mech_ice_t to R.string.mech_ice
        com.farias.prismajam.game.Mechanic.ARROW -> R.string.mech_arrow_t to R.string.mech_arrow
        com.farias.prismajam.game.Mechanic.LAYER -> R.string.mech_layer_t to R.string.mech_layer
        com.farias.prismajam.game.Mechanic.LOCK -> R.string.mech_lock_t to R.string.mech_lock
        com.farias.prismajam.game.Mechanic.TIMED -> R.string.mech_timed_t to R.string.mech_timed
    }
    DimOverlay {
        GlassPanel {
            Text(stringResource(R.string.new_mechanic), style = Styles.glow(16, Palette.Gold, FontWeight.Black))
            Text(stringResource(title), style = Styles.gradient(28), textAlign = TextAlign.Center)
            androidx.compose.foundation.Canvas(Modifier.size(150.dp, 100.dp)) { mechanicPicture(m) }
            Text(
                stringResource(text),
                style = Styles.plain(16, Palette.TextPrimary, FontWeight.Medium),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            PrimaryButton(stringResource(R.string.got_it)) { onClose() }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.mechanicPicture(m: com.farias.prismajam.game.Mechanic) {
    val cell = size.height / 2.4f
    val shapes = com.farias.prismajam.game.BlockShape.all
    fun block(shapeId: Int, color: Int, ice: Int = 0, inner: Int = 0, lock: Int = 0, key: Int = 0, axis: com.farias.prismajam.game.Axis = com.farias.prismajam.game.Axis.BOTH) =
        com.farias.prismajam.game.Block(1, shapes[shapeId], color, 0, 0, axis = axis, ice = ice, inner = inner, lock = lock, key = key)
    val y = (size.height - cell * 2) / 2f
    when (m) {
        com.farias.prismajam.game.Mechanic.STONE -> {
            drawStone(com.farias.prismajam.game.Cell(0, 0), androidx.compose.ui.geometry.Offset(size.width / 2f - cell, y + cell / 2f), cell * 2)
        }
        com.farias.prismajam.game.Mechanic.ICE -> {
            val b = block(5, 1, ice = 3)
            val o = androidx.compose.ui.geometry.Offset(size.width / 2f - cell, y)
            drawJellyBlock(b, o, cell, Palette.block(1))
            drawIce(b, o, cell)
        }
        com.farias.prismajam.game.Mechanic.ARROW -> {
            val b = block(3, 4, axis = com.farias.prismajam.game.Axis.HORIZONTAL)
            drawJellyBlock(b, androidx.compose.ui.geometry.Offset(size.width / 2f - cell * 1.5f, y + cell / 2f), cell, Palette.block(4))
        }
        com.farias.prismajam.game.Mechanic.LAYER -> {
            val b = block(5, 2, inner = 3)
            val o = androidx.compose.ui.geometry.Offset(size.width / 2f - cell, y)
            drawJellyBlock(b, o, cell, Palette.block(2))
            drawLayerCore(b, o, cell, Palette.block(3))
        }
        com.farias.prismajam.game.Mechanic.LOCK -> {
            val k = block(0, 6, key = 1)
            val l = block(5, 5, lock = 1)
            val ok = androidx.compose.ui.geometry.Offset(size.width / 2f - cell * 2.2f, y + cell / 2f)
            val ol = androidx.compose.ui.geometry.Offset(size.width / 2f + cell * 0.2f, y)
            drawJellyBlock(k, ok, cell, Palette.block(6))
            drawKey(k, ok, cell)
            drawJellyBlock(l, ol, cell, Palette.block(5))
            drawLock(l, ol, cell)
        }
        com.farias.prismajam.game.Mechanic.TIMED -> {
            val g = com.farias.prismajam.game.Gate(com.farias.prismajam.game.Side.TOP, 0, 2, 1, timed = true)
            val frame = cell * 0.42f
            val o = androidx.compose.ui.geometry.Offset(size.width / 2f - cell, y + cell)
            drawGate(g, o, cell, 1, 2, frame, Palette.block(1), 0.5f, closed = 0.55f, timed = true)
        }
    }
}
