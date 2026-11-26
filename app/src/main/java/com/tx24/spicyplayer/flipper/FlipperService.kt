package com.tx24.spicyplayer.flipper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.playback.LiveLyrics

/**
 * Lyrics on a Flipper Zero, sent over Bluetooth to the Spicy Lyrics app on it ([FlipperBleLink]).
 * It follows the playing song and its lyrics as the overlay does ([LiveLyrics]), so the Flipper
 * keeps up with the phone in a pocket and the lyrics screen closed, and the Flipper's buttons play,
 * pause and skip.
 *
 * Settings → Device → Flipper Zero. A foreground service while on, with a quiet notification
 * saying whether the Flipper is connected.
 */
class FlipperService : Service() {
    private var live: LiveLyrics? = null
    private var relay: FlipperRelay? = null
    private var connected = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enabled(this) || !permitted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (relay == null) begin()
        return START_STICKY
    }

    private fun begin() {
        val follower = LiveLyrics(applicationContext)
        val created = FlipperRelay(
            link = FlipperBleLink(this),
            snapshot = {
                val track = follower.track
                FlipperFeed.Snapshot(
                    title = track?.title,
                    artist = track?.artist.orEmpty(),
                    durationMs = track?.durationMs ?: 0L,
                    playing = follower.playing,
                    positionMs = follower.positionMs(),
                    lyrics = follower.lyrics,
                    settled = follower.settled,
                )
            },
            controls = { command ->
                when (command) {
                    FlipperProtocol.Command.PlayPause -> follower.playPause()
                    FlipperProtocol.Command.Next -> follower.skipNext()
                    FlipperProtocol.Command.Previous -> follower.skipPrevious()
                }
            },
        )
        created.onConnectedChanged = { now ->
            connected = now
            runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification()) }
        }
        follower.onChange = { created.nudge() }
        follower.start()
        created.start()
        live = follower
        relay = created
    }

    override fun onDestroy() {
        relay?.stop()
        relay = null
        live?.stop()
        live = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type) }
            .onFailure { stopSelf() }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.app_glyph)
        .setContentTitle("Lyrics on your Flipper Zero")
        .setContentText(if (connected) "Connected" else "Open Spicy Lyrics on the Flipper (Apps > Media)")
        .addAction(
            R.drawable.app_glyph, getString(R.string.background_haptics_stop),
            PendingIntent.getService(
                this, 1, Intent(this, FlipperService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Flipper Zero", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
        )
    }

    companion object {
        /** The `ui` setting behind Settings → Device → Flipper Zero. */
        const val PREF_ENABLED = "flipperZero"
        private const val CHANNEL_ID = "flipper"
        private const val NOTIFICATION_ID = 4210
        private const val ACTION_STOP = "com.tx24.spicyplayer.flipper.STOP"

        /** How this device reaches the Flipper, for the setting. */
        const val HOW = "Show the lyrics over Bluetooth on a Flipper Zero running Spicy Lyrics (Apps > Media), with playback on its buttons."

        /** Whether this device reaches a Flipper this way (it has Bluetooth LE). */
        fun supported(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        /** What the link needs the person to allow first. */
        val permissions: Array<String>
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun permitted(context: Context): Boolean = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

        /** Starts it while it's on (and allowed), stops it otherwise. */
        fun sync(context: Context) {
            val intent = Intent(context, FlipperService::class.java)
            if (enabled(context) && permitted(context)) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
