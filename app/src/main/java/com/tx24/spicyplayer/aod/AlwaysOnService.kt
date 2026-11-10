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
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.haptics.BatterySaverReceiver
import com.tx24.spicyplayer.power.SaverRule

/**
 * The lyrics as the always-on display. When the screen turns off (its timeout, or the power
 * button anywhere but on this display), the screen comes straight back on as [AlwaysOnActivity]:
 * the time and the lyrics, dim on black, over the lock screen. The power button on that display
 * turns the screen off for real; the next press brings back the lock screen as usual.
 *
 * It comes up only once the screen is really off: a second press of the power button (or the
 * camera's double press, or a tap to wake) that turns the screen back on first means no display.
 *
 * Starting a screen from the background needs "Display over other apps"; without it, a
 * full-screen notification asks the system to show it instead, which Android 14 and later only
 * does once "Full-screen notifications" is allowed for the app. Not during a call.
 *
 * Settings → Device → Always-on display turns it on. While the phone's battery saver is on it
 * rests (Settings → Device → Battery saver): the screen turns off as usual.
 */
class AlwaysOnService : Service() {
    private val main = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Back on before the display came up: whoever turned it on wants the screen.
                Intent.ACTION_SCREEN_ON -> main.removeCallbacks(launch)
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
            }
        }
    }

    /** The battery saver turned on: an open display goes, and none comes up until it's off. */
    private val batterySaver = BatterySaverReceiver { onSaverChanged() }

    private val saverSettings = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SaverRule.AlwaysOn.pref) onSaverChanged()
    }

    private var resting: Boolean? = null

    private fun onSaverChanged() {
        val rest = SaverRule.AlwaysOn.stopsNow(this)
        if (rest == resting) return
        resting = rest
        if (rest) {
            main.removeCallbacks(launch)
            if (AlwaysOnActivity.showing) AlwaysOnActivity.dismissAll()
        }
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, statusNotification()) }
    }

    private fun onScreenOff() {
        main.removeCallbacks(launch)
        if (AlwaysOnActivity.showing) {
            // Turned off on the display itself: off for real this time.
            AlwaysOnActivity.dismissAll()
            return
        }
        if (inCall() || SaverRule.AlwaysOn.stopsNow(this)) return
        main.postDelayed(launch, LAUNCH_DELAY_MS)
    }

    /**
     * Brings the display up if the screen is still off. Only checked here: Android wakes the
     * screen for the display (its turnScreenOn) before the display itself could look.
     */
    private val launch = Runnable {
        if (getSystemService(PowerManager::class.java)?.isInteractive != true) showDisplay()
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val screen = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(this, screenReceiver, screen, ContextCompat.RECEIVER_NOT_EXPORTED)
        batterySaver.register(this)
        getSharedPreferences("ui", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(saverSettings)
        resting = SaverRule.AlwaysOn.stopsNow(this)
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
        batterySaver.unregister(this)
        getSharedPreferences("ui", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(saverSettings)
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
        .setContentText(getString(if (SaverRule.AlwaysOn.stopsNow(this)) R.string.battery_saver_resting else R.string.always_on_text))
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
        /**
         * From the screen turning off to the display: time for a second press of the power button
         * (the camera's double press allows 300 ms) to turn it back on first, and for the system
         * to finish turning it off.
         */
        private const val LAUNCH_DELAY_MS = 500L
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
