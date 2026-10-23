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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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

/** Adaptive banner at the bottom. Space is reserved up front so the layout doesn't jump. */
@Composable
fun BannerAd(ads: AdsManager) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        @Suppress("DEPRECATION")
        val adSize = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(adSize.height.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (ads.adsReady) {
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
