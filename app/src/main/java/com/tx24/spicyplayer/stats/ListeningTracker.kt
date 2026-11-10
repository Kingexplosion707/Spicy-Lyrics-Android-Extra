package com.tx24.spicyplayer.stats

import android.content.Context
import android.os.SystemClock
import com.tx24.spicyplayer.playback.BackgroundSession
import java.io.File
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Listening stats: while they're on (Settings → Listening stats), follows what plays, whichever
 * player it is and whether or not the app is open, and adds each song's time and plays to a
 * [ListeningLog] kept in the app's storage. Nothing leaves the phone. Main thread only, apart from
 * [log] and [clear].
 */
internal object ListeningTracker {
    const val PREF = "listeningStats"

    private var session: BackgroundSession? = null
    private var current: BackgroundSession.Track? = null
    /** When the song last started playing (elapsed time), or 0 while it's paused. */
    private var playingSince = 0L
    /** Listened but not yet in the log. */
    private var pendingMs = 0L
    /** Listened of the song since it came up, for counting it as played once. */
    private var heardMs = 0L
    private var counted = false
    @Volatile private var loaded: ListeningLog? = null
    private val disk = Executors.newSingleThreadExecutor()

    /** Starts or stops following to match the setting; [reconnect] starts over (access was just granted). */
    fun sync(context: Context, reconnect: Boolean = false) {
        val app = context.applicationContext
        val on = app.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, false)
        if (!on || reconnect) stop(app)
        if (!on || session != null) return
        val follower = BackgroundSession(app)
        follower.onTrack = { track -> onTrack(app, follower, track) }
        follower.onPlayingChanged = { playing -> onPlaying(app, playing) }
        session = follower
        follower.start()
    }

    private fun stop(app: Context) {
        val follower = session ?: return
        checkpoint()
        flush(app)
        follower.stop()
        session = null
        current = null
        playingSince = 0L
    }

    private fun onTrack(app: Context, follower: BackgroundSession, track: BackgroundSession.Track?) {
        if (track?.key == current?.key) {
            if (track != null) current = track
            return
        }
        checkpoint()
        flush(app)
        current = track
        heardMs = 0L
        counted = false
        playingSince = if (track != null && follower.isPlaying) SystemClock.elapsedRealtime() else 0L
    }

    private fun onPlaying(app: Context, playing: Boolean) {
        checkpoint()
        if (!playing) {
            playingSince = 0L
            flush(app)
        } else if (playingSince == 0L && current != null) {
            playingSince = SystemClock.elapsedRealtime()
        }
    }

    /** Moves the time played since the last look into [pendingMs]. */
    private fun checkpoint() {
        if (playingSince == 0L) return
        val now = SystemClock.elapsedRealtime()
        val played = (now - playingSince).coerceIn(0L, MAX_STRETCH_MS)
        playingSince = now
        pendingMs += played
        heardMs += played
    }

    /** Writes what's pending into the log, and the log to disk. */
    private fun flush(app: Context) {
        val track = current ?: run { pendingMs = 0L; return }
        if (pendingMs < MIN_LOGGED_MS) return
        val plays = if (!counted && heardMs >= PLAY_AFTER_MS) { counted = true; 1 } else 0
        val ms = pendingMs
        pendingMs = 0L
        val day = today()
        disk.execute {
            val log = log(app)
            log.add(day, track.key, track.title, track.artist, ms, plays)
            log.prune(day - KEEP_DAYS)
            runCatching { file(app).writeText(log.toJson()) }
        }
    }

    /** The log, read from disk the first time. Any thread (it reads a file: not the main one). */
    fun log(context: Context): ListeningLog = loaded ?: synchronized(this) {
        loaded ?: (runCatching { ListeningLog.fromJson(file(context).readText()) }.getOrNull() ?: ListeningLog()).also { loaded = it }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        disk.execute {
            log(app).clear()
            file(app).delete()
        }
    }

    /** Today's day number, in the phone's time zone. */
    fun today(): Long {
        val now = System.currentTimeMillis()
        return Math.floorDiv(now + TimeZone.getDefault().getOffset(now), DAY_MS)
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, "listening-stats.json")

    private const val DAY_MS = 86_400_000L
    /** A song counts as played once this much of it has been heard. */
    private const val PLAY_AFTER_MS = 30_000L
    private const val MIN_LOGGED_MS = 1_000L
    /** The most one stretch of playing adds: a player that died without saying so doesn't count for hours. */
    private const val MAX_STRETCH_MS = 20 * 60_000L
    private const val KEEP_DAYS = 800L
}
