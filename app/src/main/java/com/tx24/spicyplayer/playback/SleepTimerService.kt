package com.tx24.spicyplayer.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R
import kotlin.math.roundToInt

/**
 * The sleep timer: pauses the player after a set time, or as the song playing now ends. The
 * music fades out over its last seconds first, and the volume goes back to where it was once
 * the player has paused. Its notification counts down and turns it off.
 *
 * It runs as a foreground service, so it keeps time with the app closed; while music plays the
 * phone stays awake for it anyway. [state] tells the lyrics screen what's set.
 */
class SleepTimerService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var session: BackgroundSession
    private var audio: AudioManager? = null
    /** When to pause (elapsed realtime); 0 for the end of the song. */
    private var endsAt = 0L
    private var endOfSong = false
    /** The song playing when "end of song" was set: a new one starting means it's ended. */
    private var songKey: String? = null
    private var fading = false
    private var volumeBefore = -1

    private val check = Runnable { step() }
    private val restoreVolume = Runnable { restore() }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        createChannel()
        session = BackgroundSession(this).apply {
            onTrack = { track ->
                when {
                    !endOfSong || state.value == null -> Unit
                    // Set before a song was known: the first one is the song to end.
                    songKey == null -> songKey = track?.key
                    // The song ended (or was skipped) while waiting for its end: pause the next at once.
                    track?.key != songKey -> finish(fade = false)
                }
            }
            onPlayingChanged = { step() }
        }
        session.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                restore()
                state.value = null
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
                main.removeCallbacks(restoreVolume)
                restore()
                endOfSong = minutes <= 0
                songKey = session.track?.key
                endsAt = if (endOfSong) 0L else SystemClock.elapsedRealtime() + minutes * 60_000L
                state.value = SleepTimer(endsAt, endOfSong)
            }
        }
        if (state.value == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        step()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(check)
        main.removeCallbacks(restoreVolume)
        restore()
        session.stop()
        if (state.value != null) state.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Looks at the time left: fades the music over the last [FADE_MS], pauses at the end, else waits. */
    private fun step() {
        main.removeCallbacks(check)
        if (state.value == null) return
        val left = timeLeftMs() ?: run {
            // The end of a song whose length isn't known yet: look again shortly.
            main.postDelayed(check, CHECK_MS)
            return
        }
        when {
            left <= 0L -> finish(fade = false)
            left <= FADE_MS && session.isPlaying -> {
                fade(left)
                main.postDelayed(check, FADE_STEP_MS)
            }
            else -> main.postDelayed(check, (left - FADE_MS).coerceIn(FADE_STEP_MS, CHECK_MS))
        }
    }

    /** What's left before pausing, in ms; null when the song's end can't be told yet. */
    private fun timeLeftMs(): Long? {
        if (!endOfSong) return endsAt - SystemClock.elapsedRealtime()
        val track = session.track ?: return null
        if (track.durationMs <= 0L) return null
        return track.durationMs - session.positionMs() - END_OF_SONG_EARLY_MS
    }

    /** Lowers the music volume toward silence as [left] runs out. */
    private fun fade(left: Long) {
        val manager = audio ?: return
        if (!fading) {
            fading = true
            volumeBefore = runCatching { manager.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(-1)
        }
        if (volumeBefore <= 0) return
        val share = (left.toFloat() / FADE_MS).coerceIn(0f, 1f)
        val target = (volumeBefore * share).roundToInt().coerceAtLeast(if (left > FADE_STEP_MS) 1 else 0)
        runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    /** Pauses the player and ends the timer; the volume comes back once the pause has landed. */
    private fun finish(fade: Boolean) {
        main.removeCallbacks(check)
        if (fade && session.isPlaying) fade(0L)
        session.pause()
        state.value = null
        main.postDelayed(restoreVolume, RESTORE_AFTER_MS)
        main.postDelayed({ stopSelf() }, RESTORE_AFTER_MS + 100L)
        runCatching { androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    private fun restore() {
        if (!fading) return
        fading = false
        val manager = audio ?: return
        if (volumeBefore >= 0) runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBefore, 0) }
        volumeBefore = -1
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, LyricsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, SleepTimerService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_glyph)
            .setContentTitle(getString(R.string.sleep_timer_title))
            .setContentIntent(open)
            .addAction(R.drawable.app_glyph, getString(R.string.sleep_timer_cancel), cancel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (endOfSong) {
            builder.setContentText(getString(R.string.sleep_timer_end_of_song)).setShowWhen(false)
        } else {
            // A countdown the system keeps ticking, with no updates from here.
            val wallEnd = System.currentTimeMillis() + (endsAt - SystemClock.elapsedRealtime())
            builder.setContentText(getString(R.string.sleep_timer_counting))
                .setWhen(wallEnd)
                .setShowWhen(true)
            // Counting down needs Android 7; before it, the notification shows when it pauses.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setUsesChronometer(true).setChronometerCountDown(true)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.sleep_timer_title), NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** A set sleep timer: when it pauses (elapsed realtime), or at the end of the song. */
    data class SleepTimer(val endsAtElapsed: Long, val endOfSong: Boolean) {
        /** Whole minutes left, rounded up; 0 for the end of the song. */
        fun minutesLeft(now: Long = SystemClock.elapsedRealtime()): Int =
            if (endOfSong) 0 else ((endsAtElapsed - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
    }

    companion object {
        private const val CHANNEL_ID = "sleep_timer"
        private const val NOTIFICATION_ID = 4207
        private const val ACTION_START = "com.tx24.spicyplayer.sleep.START"
        private const val ACTION_CANCEL = "com.tx24.spicyplayer.sleep.CANCEL"
        private const val EXTRA_MINUTES = "minutes"
        /** The music fades out over this long before the pause. */
        private const val FADE_MS = 15_000L
        private const val FADE_STEP_MS = 500L
        /** The longest wait between looks at the time left. */
        private const val CHECK_MS = 30_000L
        /** "End of song" pauses this much before the end, so the next song doesn't start. */
        private const val END_OF_SONG_EARLY_MS = 1_200L
        /** The volume comes back this long after the pause. */
        private const val RESTORE_AFTER_MS = 1_500L

        /** The timer that's set, or null: Compose state, for the lyrics screen. Main thread. */
        val state = mutableStateOf<SleepTimer?>(null)

        /** Sets the timer to pause in [minutes], or at the end of the song with 0. */
        fun start(context: Context, minutes: Int) {
            val intent = Intent(context, SleepTimerService::class.java).setAction(ACTION_START).putExtra(EXTRA_MINUTES, minutes)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun cancel(context: Context) {
            state.value = null
            context.stopService(Intent(context, SleepTimerService::class.java))
        }
    }
}
