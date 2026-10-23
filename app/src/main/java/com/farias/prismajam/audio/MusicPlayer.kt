package com.farias.prismajam.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.farias.prismajam.R
import com.farias.prismajam.data.Progress

/** Background music loop. Follows the music setting and the activity lifecycle. */
object MusicPlayer {
    private const val VOLUME = 0.35f
    private var player: MediaPlayer? = null
    private var appContext: Context? = null
    private var foreground = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onResume() {
        foreground = true
        update()
    }

    fun onPause() {
        foreground = false
        player?.takeIf { it.isPlaying }?.pause()
    }

    /** Starts or stops the music to match the setting. */
    fun update() {
        if (!foreground) return
        if (!Progress.musicOn) {
            player?.takeIf { it.isPlaying }?.pause()
            return
        }
        val p = player ?: create() ?: return
        if (!p.isPlaying) p.start()
    }

    private fun create(): MediaPlayer? {
        val ctx = appContext ?: return null
        return try {
            MediaPlayer.create(
                ctx,
                R.raw.music_chill,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                0,
            )?.apply {
                isLooping = true
                setVolume(VOLUME, VOLUME)
            }.also { player = it }
        } catch (e: Exception) {
            null
        }
    }
}
