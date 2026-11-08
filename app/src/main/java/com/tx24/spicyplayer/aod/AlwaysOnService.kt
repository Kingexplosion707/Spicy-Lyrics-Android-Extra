package com.tx24.spicyplayer.aod

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.R

/**
 * The lyrics as the always-on display. When the screen turns off (its timeout, or the power
 * button anywhere but on this display), the screen comes straight back on as [AlwaysOnActivity]:
 * the time and the lyrics, dim on black, over the lock screen. The power button on that display
 * turns the screen off for real; the next press brings back the lock screen as usual.
 *
 * Starting a screen from the background needs "Display over other apps"; without it, a
 * full-screen notification asks the system to show it instead, which Android 14 and later only
 * does once "Full-screen notifications" is allowed for the app. Not during a call.
 *
 * Settings → Device → Always-on display turns it on.
 */
class AlwaysOnService : Service() {
    private val main = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            if (AlwaysOnActivity.showing) {
                // Turned off on the display itself: off for real this time.
                AlwaysOnActivity.dismissAll()
                return
            }
            if (inCall()) return
            // A beat for the system to finish turning the screen off before it comes back.
            main.postDelayed({ showDisplay() }, RELAUNCH_DELAY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, statusNotification())
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun inCall(): Boolean {
        val mode = getSystemService(AudioManager::class.java)?.mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
    }

    private fun showDisplay() {
        val intent = Intent(this, AlwaysOnActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val started = Settings.canDrawOverlays(this) && runCatching { startActivity(intent) }.isSuccess
        if (started) return
        // The system shows a full-screen notification's screen right away while the screen is off.
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val launcher = NotificationCompat.Builder(this, LAUNCH_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_glyph)
            .setContentTitle(getString(R.string.always_on_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pending, true)
            .setTimeoutAfter(LAUNCH_NOTIFICATION_TIMEOUT_MS)
            .setSilent(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java)?.notify(LAUNCH_NOTIFICATION_ID, launcher) }
    }

    private fun statusNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.app_glyph)
        .setContentTitle(getString(R.string.always_on_title))
        .setContentText(getString(R.string.always_on_text))
        .addAction(
            R.drawable.app_glyph, getString(R.string.background_haptics_stop),
            PendingIntent.getService(
                this, 1, Intent(this, AlwaysOnService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.always_on_title), NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(LAUNCH_CHANNEL_ID, getString(R.string.always_on_launch_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        /** The `ui` setting behind Settings → Device → Always-on display. */
        const val PREF_ENABLED = "ambientAsAod"
        private const val CHANNEL_ID = "always_on"
        private const val LAUNCH_CHANNEL_ID = "always_on_launch"
        private const val NOTIFICATION_ID = 4204
        private const val LAUNCH_NOTIFICATION_ID = 4205
        private const val ACTION_STOP = "com.tx24.spicyplayer.aod.STOP"
        private const val RELAUNCH_DELAY_MS = 300L
        private const val LAUNCH_NOTIFICATION_TIMEOUT_MS = 5_000L

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

        /** Starts it while it's on, stops it otherwise; false when it should run but couldn't start. */
        fun sync(context: Context): Boolean {
            val intent = Intent(context, AlwaysOnService::class.java)
            return if (enabled(context)) {
                runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
            } else {
                context.stopService(intent)
                true
            }
        }
    }
}
