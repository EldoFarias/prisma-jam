package com.farias.prismajam

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.farias.prismajam.ads.AdsManager
import com.farias.prismajam.audio.MusicPlayer
import com.farias.prismajam.audio.SoundManager
import com.farias.prismajam.data.Progress
import com.farias.prismajam.game.GameViewModel
import com.farias.prismajam.notify.DailyReminder
import com.farias.prismajam.ui.GameScreen
import com.farias.prismajam.ui.HomeScreen
import com.farias.prismajam.ui.SplashScreen
import com.farias.prismajam.ui.StatsScreen
import com.farias.prismajam.ui.ThemesScreen
import com.farias.prismajam.ui.skipChestToday

enum class Screen { SPLASH, HOME, GAME, THEMES, STATS, MAP }

class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()
    private lateinit var ads: AdsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Progress.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        DailyReminder.createChannel(this)
        DailyReminder.sync(this)
        ads = AdsManager(this)
        ads.start()
        val sound = SoundManager.get(this)
        MusicPlayer.init(this)

        val initial = debugStartScreen()

        setContent {
            var screen by remember { mutableStateOf(initial) }
            // Tablets: draw the whole app bigger so it fills the screen like on a phone.
            val sw = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp
            val appScale = if (sw >= 600) (sw / 420f).coerceIn(1f, 1.6f) else 1f
            com.farias.prismajam.ui.Scaled(appScale) {
            Crossfade(targetState = screen, animationSpec = tween(350), label = "screen") { current ->
                when (current) {
                    Screen.SPLASH -> SplashScreen(sound) { screen = Screen.HOME }
                    Screen.HOME -> HomeScreen(
                        ads = ads,
                        sound = sound,
                        onPlay = {
                            ads.onNewGame {
                                vm.startLevel(Progress.level)
                                screen = Screen.GAME
                            }
                        },
                        onThemes = { screen = Screen.THEMES },
                        onStats = { screen = Screen.STATS },
                        onMap = { screen = Screen.MAP },
                        onDaily = {
                            ads.onNewGame {
                                vm.startDaily()
                                screen = Screen.GAME
                            }
                        },
                    )
                    Screen.GAME -> GameScreen(
                        vm = vm,
                        ads = ads,
                        onGreatMoment = { ReviewPrompt.maybeAsk(this@MainActivity) },
                        onHome = { screen = Screen.HOME },
                    )
                    Screen.THEMES -> ThemesScreen(sound) { screen = Screen.HOME }
                    Screen.STATS -> StatsScreen { screen = Screen.HOME }
                    Screen.MAP -> com.farias.prismajam.ui.MapScreen(
                        onBack = { screen = Screen.HOME },
                        onPlay = { n ->
                            ads.onNewGame {
                                vm.startLevel(n)
                                screen = Screen.GAME
                            }
                        },
                    )
                }
            }
            }
        }
    }

    /**
     * Debug builds only: lets the automated screenshot test open a screen directly, e.g.
     * `adb shell am start -n <pkg>/com.farias.prismajam.MainActivity --es screen game --ei level 12`.
     */
    private fun debugStartScreen(): Screen {
        if (!BuildConfig.DEBUG) return Screen.SPLASH
        val target = intent.getStringExtra("screen") ?: return Screen.SPLASH
        if (intent.getBooleanExtra("landscape", false)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (target != "chest") skipChestToday()
        return when (target) {
            "game" -> {
                vm.startLevel(intent.getIntExtra("level", 1))
                vm.debugShatterLoop = intent.getBooleanExtra("shatter", false)
                Screen.GAME
            }
            "daily" -> {
                vm.startDaily()
                Screen.GAME
            }
            "map" -> Screen.MAP
            "themes" -> Screen.THEMES
            "stats" -> Screen.STATS
            else -> Screen.HOME
        }
    }

    override fun onResume() {
        super.onResume()
        Progress.ensureMissionDay()
        MusicPlayer.onResume()
    }

    override fun onPause() {
        super.onPause()
        MusicPlayer.onPause()
        vm.touchUp()
        if (!vm.finished && vm.started) vm.paused = true
    }
}
