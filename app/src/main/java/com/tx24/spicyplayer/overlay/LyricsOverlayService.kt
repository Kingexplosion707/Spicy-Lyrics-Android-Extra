package com.tx24.spicyplayer.overlay

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.playback.LiveLyrics
import com.tx24.spicyplayer.wallpaper.WallpaperPresence

/**
 * Keeps the lyrics overlay ([LyricsOverlayWindow]) floating over other apps while music plays:
 * the song and lyrics come from [LiveLyrics], the same sources and timing as the lyrics screen.
 *
 * It steps aside while the lyrics screen is up, and (by default) while the music is paused or
 * the song has no lyrics. Its notification hides or shows it, locks it in place, and stops it.
 * Settings → Device → Lyrics overlay turns it on.
 */
class LyricsOverlayService : Service() {
    private lateinit var prefs: SharedPreferences
    private lateinit var live: LiveLyrics
    private lateinit var window: LyricsOverlayWindow
    private val main = Handler(Looper.getMainLooper())
    private var lyricsResumed = false
    /** Hidden from the notification: stays hidden until shown again or the song changes. */
    private var userHidden = false
    private var hiddenFor: String? = null
    private var pausedLongEnough = false
    private var shownInNotification: Boolean? = null

    private val wallpaperListener: (Boolean) -> Unit = { update() }

    private val pauseTimeout = Runnable {
        pausedLongEnough = true
        update()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("overlay")) {
            if (!enabled(this)) stopSelf() else {
                window.applyBehaviour()
                update()
            }
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity is LyricsActivity) { lyricsResumed = true; update() }
        }
        override fun onActivityPaused(activity: Activity) {
            if (activity is LyricsActivity) { lyricsResumed = false; update() }
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("ui", MODE_PRIVATE)
        createChannel()
        live = LiveLyrics(this).apply { onChange = { update() } }
        window = LyricsOverlayWindow(this) {
            OverlayContent(live, prefs, visible = { window.isShown })
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        (application as Application).registerActivityLifecycleCallbacks(activityCallbacks)
        WallpaperPresence.addListener(wallpaperListener)
        live.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.edit().putBoolean(PREF_ENABLED, false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                userHidden = !userHidden && window.isShown
                hiddenFor = live.track?.key
                if (!userHidden) { pausedLongEnough = false }
            }
            ACTION_LOCK -> prefs.edit().putBoolean(LyricsOverlayWindow.PREF_LOCKED, !prefs.getBoolean(LyricsOverlayWindow.PREF_LOCKED, false)).apply()
        }
        if (!enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        update()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.applyBehaviour()
        update()
    }

    override fun onDestroy() {
        main.removeCallbacks(pauseTimeout)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        (application as Application).unregisterActivityLifecycleCallbacks(activityCallbacks)
        WallpaperPresence.removeListener(wallpaperListener)
        window.hide()
        StatusBarClock.restore(this)
        live.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Shows or hides the window for what plays now, and keeps the notification's buttons true. */
    private fun update() {
        val track = live.track
        if (userHidden && hiddenFor != track?.key) userHidden = false
        // Paused for a moment (a skip, a seek) it stays; paused for longer, it goes.
        if (live.playing) {
            main.removeCallbacks(pauseTimeout)
            pausedLongEnough = false
        } else if (!pausedLongEnough) {
            main.removeCallbacks(pauseTimeout)
            main.postDelayed(pauseTimeout, PAUSE_HIDE_MS)
        }
        val hideWhenPaused = prefs.getBoolean(PREF_HIDE_WHEN_PAUSED, true)
        val noLyrics = live.settled && live.lyrics?.lines.isNullOrEmpty()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // On the home screen the live wallpaper shows the lyrics already.
        val onWallpaper = WallpaperPresence.visible && prefs.getBoolean(PREF_HIDE_ON_WALLPAPER, true)
        val show = track != null && !lyricsResumed && !userHidden && !noLyrics && !onWallpaper &&
            !(hideWhenPaused && !live.playing && pausedLongEnough) &&
            !(landscape && prefs.getBoolean(PREF_HIDE_IN_LANDSCAPE, false))
        if (show) {
            if (!window.show() && !Settings.canDrawOverlays(this)) {
                // The permission was taken back: off until it's given again.
                prefs.edit().putBoolean(PREF_ENABLED, false).apply()
                stopSelf()
                return
            }
        } else {
            window.hide()
        }
        val shown = window.isShown
        // The status bar lyrics in the clock's place: hidden while they show, back after.
        if (shown && OverlayStyle.from(prefs) == OverlayStyle.StatusBar && prefs.getBoolean(StatusBarClock.PREF_HIDE, false)) {
            StatusBarClock.hide(this)
        } else {
            StatusBarClock.restore(this)
        }
        if (shownInNotification != shown) {
            shownInNotification = shown
            runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification()) }
        }
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.app_glyph)
        .setContentTitle(getString(R.string.lyrics_overlay_title))
        .setContentText(getString(if (window.isShown) R.string.lyrics_overlay_showing else R.string.lyrics_overlay_waiting))
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, LyricsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(R.drawable.app_glyph, getString(if (window.isShown) R.string.lyrics_overlay_hide else R.string.lyrics_overlay_show), action(ACTION_TOGGLE, 1))
        .addAction(
            R.drawable.app_glyph,
            getString(if (prefs.getBoolean(LyricsOverlayWindow.PREF_LOCKED, false)) R.string.lyrics_overlay_unlock else R.string.lyrics_overlay_lock),
            action(ACTION_LOCK, 2),
        )
        .addAction(R.drawable.app_glyph, getString(R.string.background_haptics_stop), action(ACTION_STOP, 3))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun action(name: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, LyricsOverlayService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.lyrics_overlay_title), NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val PREF_ENABLED = "overlayLyrics"
        const val PREF_HIDE_WHEN_PAUSED = "overlayHideWhenPaused"
        const val PREF_HIDE_IN_LANDSCAPE = "overlayHideInLandscape"
        const val PREF_HIDE_ON_WALLPAPER = "overlayHideOnWallpaper"
        private const val CHANNEL_ID = "lyrics_overlay"
        private const val NOTIFICATION_ID = 4203
        private const val ACTION_STOP = "com.tx24.spicyplayer.overlay.STOP"
        private const val ACTION_TOGGLE = "com.tx24.spicyplayer.overlay.TOGGLE"
        private const val ACTION_LOCK = "com.tx24.spicyplayer.overlay.LOCK"
        /** Paused this long, the overlay hides (when Hide when paused is on). */
        private const val PAUSE_HIDE_MS = 2_500L

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

        /** Starts it while it's on and allowed to draw over other apps, stops it otherwise. */
        fun sync(context: Context) {
            val intent = Intent(context, LyricsOverlayService::class.java)
            if (enabled(context) && Settings.canDrawOverlays(context)) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
