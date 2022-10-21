package org.mcxa.softsound

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

class PlayerService : Service() {
    private val notificationID = 132
    private val tag = "Player"

    // called when sound is started or stopped
    var playerChangeListener: (() -> Unit)? = null

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService {
            return this@PlayerService
        }
    }

    private val playerBinder = PlayerBinder()

    override fun onCreate() {
        // load each player into the map
        Sound.values().forEach {
            exoPlayers[it] = initializeExoPlayer(it.file)
        }
    }

    enum class Sound(val file: String) {
        RAIN("rain.ogg"),
        STORM("storm.ogg"),
        WATER("water.ogg"),
        FIRE("fire.ogg"),
        WIND("wind.ogg"),
        NIGHT("night.ogg"),
        PURR("purr.ogg")
    }

    private val exoPlayers = mutableMapOf<Sound, ExoPlayer>()

    private fun initializeExoPlayer(soundFile: String): ExoPlayer {
        // create the player
        val trackSelector = DefaultTrackSelector(this)
        val exoPlayer = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()

        // load the media source
        val dataSource = DefaultDataSourceFactory(this,
                Util.getUserAgent(this, this.getString(R.string.app_name)))
        val mediaSource = ProgressiveMediaSource.Factory(dataSource)
                .createMediaSource(MediaItem.fromUri(Uri.parse("asset:///$soundFile")))

        // load the media
        Log.d("MAIN", "loading $soundFile")
        exoPlayer.prepare(mediaSource)
        // loop indefinitely
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL

        return exoPlayer
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // don't continue if we're not playing any sound and the main activity exits
        playerChangeListener = null
        if (!isPlaying()) {
            stopSelf()
            Log.d(tag, "stopping service")
        }
        return super.onUnbind(intent)
    }

    override fun onBind(intent: Intent?): IBinder {
        // return the binding interface
        return playerBinder
    }

    fun startForeground() {
        // move to the foreground if we are playing sound
        if (SDK_INT >= 24 && isPlaying()) {
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

            val notification = NotificationCompat.Builder(this, "softsound")
                    .setContentTitle(getText(R.string.app_name))
                    .setContentText(getText(R.string.notification_message))
                    .setSmallIcon(R.drawable.ic_volume)
                    .setContentIntent(pendingIntent)
                    .build()

            Log.d(tag, "starting foreground service...")
            startForeground(notificationID, notification)
        }
    }

    fun stopForeground() {
        // we don't need to be foreground anymore
        if (SDK_INT >= 24) {
            Log.d(tag, "stopping foreground service...")
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    fun stopPlaying() {
        exoPlayers.values.forEach { it.playWhenReady = false }
    }

    fun isPlaying(): Boolean {
        var playing = false
        exoPlayers.values.forEach { if (it.playWhenReady) playing = true }
        return playing
    }

    fun setVolume(sound: Sound, volume: Float) {
        exoPlayers[sound]?.volume = volume
    }

    fun toggleSound(sound: Sound) {
        exoPlayers[sound]?.playWhenReady = !(exoPlayers[sound]?.playWhenReady ?: false)
        // call the change listener
        playerChangeListener?.invoke()
    }
}
