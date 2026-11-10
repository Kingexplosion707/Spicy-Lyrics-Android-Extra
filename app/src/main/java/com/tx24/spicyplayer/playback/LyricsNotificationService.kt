package com.tx24.spicyplayer.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.haptics.BatterySaverReceiver
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.power.SaverRule
import com.tx24.spicyplayer.widget.BackgroundLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The line being sung, live, in one ongoing notification: for apps that show notifications in a
 * pill or island at the top of the screen (Essentials' Island, say) and for Android 16's Live
 * Updates chip, which it asks to be promoted to. The notification is silent, never alerts, and
 * changes once per lyric line, at the line's start.
 *
 * Settings → Device → Lyrics notification turns it on. A foreground service, so it keeps up with
 * the screen off and the app closed, following the same session and lyrics as the wallpaper.
 *
 * With the screen off nobody sees the line change, so it changes then only when the song or
 * playing does, and catches up when the screen comes back on. While the phone's battery saver is
 * on it rests (Settings → Device → Battery saver): it stops following the player and says so.
 */
class LyricsNotificationService : Service() {
    private lateinit var session: BackgroundSession
    private lateinit var lookup: BackgroundLyrics
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lyrics: LyricsState.Ready? = null
    /** The lead lines, in time order: what the notification shows. */
    private var sung: List<TimedLine> = emptyList()
    private var trackKey: String? = null
    private var shownLine = Int.MIN_VALUE
    private var shownPlaying: Boolean? = null
    private var shownTrack: String? = null
    private var foreground = false

    private val tick = Runnable { refresh() }
    /** Whether the screen is on: with it off, the line isn't followed (see the class comment). */
    private var screenOn = true
    /** Resting for the battery saver: not following the player. */
    private var resting = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            if (screenOn) refresh() else main.removeCallbacks(tick)
        }
    }

    private val batterySaver = BatterySaverReceiver { applySaver() }

    private val saverSettings = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SaverRule.Notification.pref) applySaver()
    }

    override fun onCreate() {
        super.onCreate()
        lookup = BackgroundLyrics(this)
        createChannel()
        session = BackgroundSession(this).apply {
            onTrack = { onSong(it) }
            onPlayingChanged = { refresh() }
        }
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive != false
        val screen = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(this, screenReceiver, screen, ContextCompat.RECEIVER_NOT_EXPORTED)
        batterySaver.register(this)
        getSharedPreferences("ui", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(saverSettings)
        resting = SaverRule.Notification.stopsNow(this)
        if (!resting) session.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !enabled(this)) {
            if (intent?.action == ACTION_STOP) {
                getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, false).apply()
            }
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, if (resting) restingNotification() else build(currentLine(), session.isPlaying))
        foreground = true
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        runCatching { unregisterReceiver(screenReceiver) }
        batterySaver.unregister(this)
        getSharedPreferences("ui", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(saverSettings)
        session.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onSong(track: BackgroundSession.Track?) {
        if (track?.key == trackKey) {
            refresh()
            return
        }
        trackKey = track?.key
        lyrics = null
        sung = emptyList()
        refresh()
        if (track == null) return
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { lookup.lookUp(track.request, romanize = false, track.spotifyId) }.getOrNull()
            }
            if (trackKey != track.key) return@launch
            val ready = (found as? BackgroundLyrics.Result.Found)?.lyrics
            lyrics = ready
            sung = ready?.takeIf { it.lyricsType != LyricsType.Static }?.lines.orEmpty()
                .filter { it.role == LineRole.LEAD && !it.untimed && it.words.any { w -> w.text.isNotBlank() } }
                .sortedBy { it.startMs }
            shownLine = Int.MIN_VALUE
            refresh()
        }
    }

    /** The lead line being sung now, its index, or -1 before the first / in a gap. */
    private fun currentLine(): Int {
        if (sung.isEmpty()) return -1
        val pos = session.positionMs()
        val i = sung.indexOfLast { it.startMs <= pos }
        if (i < 0) return -1
        // Past its end with the next not started: an instrumental, shown as such.
        val line = sung[i]
        val next = sung.getOrNull(i + 1)
        return if (pos >= line.endMs + GAP_GRACE_MS && (next == null || pos < next.startMs)) -1 else i
    }

    /** Follows the battery saver: stops following the player while it's on, and starts again after. */
    private fun applySaver() {
        val rest = SaverRule.Notification.stopsNow(this)
        if (rest == resting) return
        resting = rest
        main.removeCallbacks(tick)
        if (rest) {
            session.stop()
            if (foreground) runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, restingNotification()) }
        } else {
            shownLine = Int.MIN_VALUE
            shownPlaying = null
            shownTrack = null
            session.start()
            refresh()
        }
    }

    /** Updates the notification if the line (or playing) changed, then waits for the next line. */
    private fun refresh() {
        main.removeCallbacks(tick)
        if (!foreground || resting) return
        val line = currentLine()
        val playing = session.isPlaying
        val title = session.track?.key
        if (line != shownLine || playing != shownPlaying || title != shownTrack) {
            shownLine = line
            shownPlaying = playing
            shownTrack = title
            runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(line, playing)) }
        }
        if (!playing || sung.isEmpty() || !screenOn) return
        // Woken at the next line's start (or its end, for a gap), never sooner than a frame.
        val pos = session.positionMs()
        val nextStart = sung.firstOrNull { it.startMs > pos }?.startMs
        val currentEnd = sung.getOrNull(line)?.let { it.endMs + GAP_GRACE_MS }?.takeIf { it > pos }
        val wake = listOfNotNull(nextStart, currentEnd).minOrNull() ?: return
        main.postDelayed(tick, (wake - pos).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS))
    }

    /** What shows while it rests for the battery saver. */
    private fun restingNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.app_glyph)
        .setContentTitle(getString(R.string.lyrics_notification_channel))
        .setContentText(getString(R.string.battery_saver_resting))
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, LyricsActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setShowWhen(false)
        .build()

    private fun build(line: Int, playing: Boolean): Notification {
        val track = session.track
        val text = when {
            track == null -> getString(R.string.lyrics_notification_nothing)
            lyrics == null -> track.title
            line >= 0 -> sung[line].words.joinToString("") { word ->
                if (word.attached) word.text else " " + word.text
            }.trim().replace(Regex("\\s+"), " ")
            else -> INSTRUMENTAL
        }
        val song = track?.let { "${it.title} — ${it.artist}" }.orEmpty()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, LyricsActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LyricsNotificationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_glyph)
            .setContentTitle(text)
            .setContentText(if (playing || track == null) song else getString(R.string.background_haptics_paused))
            .setContentIntent(open)
            .addAction(R.drawable.app_glyph, getString(R.string.background_haptics_stop), stop)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            // Android 16's Live Updates: asked to be promoted, with the line as its chip's text.
            .addExtras(Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                putCharSequence(EXTRA_SHORT_CRITICAL_TEXT, text.take(SHORT_TEXT_CHARS))
            })
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.lyrics_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.lyrics_notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        /** The `ui` setting that turns it on. */
        const val PREF_ENABLED = "lyricsNotification"
        private const val CHANNEL_ID = "live_lyrics"
        private const val NOTIFICATION_ID = 4202
        private const val ACTION_STOP = "com.tx24.spicyplayer.lyrics.STOP_NOTIFICATION"
        private const val INSTRUMENTAL = "♪"
        /** `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` and `EXTRA_SHORT_CRITICAL_TEXT` (Android 16). */
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
        /** A status-bar chip shows only a few characters; the full line is in the notification. */
        private const val SHORT_TEXT_CHARS = 24
        /** A line counts as sung this long after its end, so a held note doesn't flicker to ♪. */
        private const val GAP_GRACE_MS = 1_500L
        private const val MIN_WAIT_MS = 16L
        /** Looked at again at least this often, for a seek. */
        private const val MAX_WAIT_MS = 2_000L

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

        /** Starts it while it's on, stops it when it's off. */
        fun sync(context: Context) {
            if (enabled(context)) {
                runCatching {
                    ContextCompat.startForegroundService(context, Intent(context, LyricsNotificationService::class.java))
                }
            } else {
                context.stopService(Intent(context, LyricsNotificationService::class.java))
            }
        }
    }
}
