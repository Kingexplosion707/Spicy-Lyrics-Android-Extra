package com.tx24.spicyplayer.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.power.SaverRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Keeps the lyrics with the music by ear, for when the player's own position is plainly wrong
 * (Spotify's Automix reports a song seconds behind its audio, and nothing it sends later corrects
 * it). A little into each song it hears the audio the phone is playing for some seconds, lays
 * where its sounds start over where they start in the song's audio analysis ([AudioAlign]), and
 * tells the lyric clocks how far the audio is from where the player says ([Listener.onOffset]).
 * The lyrics move; the music is never touched.
 *
 * The player's reports stay the fine timing. A difference under [IN_SYNC_MS] is taken as the
 * player being right, and teaches [biasMs]: how far this phone's capture runs from its reports
 * when they are right (the output's latency), taken off every later measurement.
 *
 * Off until switched on in Settings → Scroll & Sync → Song changes, which asks for Android's
 * record-audio permission: the phone's own playback can't be heard without it. It hears what the
 * phone plays, not the microphone, keeps only how loud each moment was, in memory, for the half
 * minute it listens, and sends nothing anywhere. Works for songs Spotify has an analysis of.
 *
 * The lyrics screen and the background follower both feed it the same player's reports and both
 * take its answer. Main thread, except the capture, which has its own.
 */
internal object ListeningSync {
    /** The setting, in the "ui" preferences. */
    const val PREF = "listeningSync"
    private const val BIAS_PREF = "biasMs"

    /** What it is doing about a song. */
    enum class State { Off, Loading, Listening, Settled, Unavailable }

    interface Listener {
        /** The audio of the song with [spotifyId] is [offsetMs] ahead of where the player says it is. */
        fun onOffset(spotifyId: String, offsetMs: Long)
    }

    /** One report from the player, as it gave it (no offsets added). */
    private class Report(
        val songKey: String,
        val positionMs: Long,
        val updatedAtMs: Long,
        val speed: Float,
        val playing: Boolean,
        /** Counts up with every song change and every jump in the reports (a seek). */
        val epoch: Int,
    ) {
        fun positionAt(atMs: Long): Long = positionMs + ((atMs - updatedAtMs) * speed).roundToLong()
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<Listener>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null

    /** The song listened to: its Spotify track ID, which both followers read off the player the same way. */
    private var songKey: String? = null
    private var state = State.Off
    private var offsetMs = 0L
    private var epoch = 0
    private var loadJob: Job? = null
    private var reference: FloatArray? = null
    private var lastFedAtMs = 0L
    @Volatile private var report: Report? = null

    private var thread: HandlerThread? = null
    private var capture: Capture? = null
    private val recheck = Runnable { startEpisode(again = true) }
    /** How long until the song is listened to again; longer after a spell that found nothing. */
    private var recheckDelayMs = RECHECK_MS

    fun addListener(listener: Listener) {
        if (listener !in listeners) listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
        if (listeners.isEmpty()) stopAll()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether it's switched on, allowed, and not resting: for the battery saver, or because the
     * resync after a song change is on ([AutomixResync]), after which the player is right.
     */
    fun enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF, false) && !AutomixResync.enabled(context) && hasPermission(context) &&
            !SaverRule.Listening.stopsNow(context, prefs)
    }

    /** What it is doing about the song with Spotify ID [key]; [State.Off] for any other song. */
    fun stateFor(key: String?): State = if (key != null && key == songKey) state else State.Off

    /** The followed player's song, by the player's own Spotify track ID; null when it gives none. */
    fun onSong(context: Context, spotifyId: String?) {
        if (spotifyId == songKey) return
        appContext = context.applicationContext
        stopEpisode()
        loadJob?.cancel()
        songKey = spotifyId
        reference = null
        offsetMs = 0L
        epoch++
        report = null
        if (spotifyId == null || !enabled(context)) {
            state = State.Off
            return
        }
        state = State.Loading
        val cacheDir = context.applicationContext.cacheDir
        loadJob = scope.launch {
            val analysis = runCatching { SharedSpotify.extras(cacheDir).audioAnalysis(spotifyId) }.getOrNull()
            if (songKey != spotifyId) return@launch
            val curve = analysis?.let { kotlinx.coroutines.withContext(Dispatchers.Default) { AudioAlign.reference(it) } }
            if (songKey != spotifyId) return@launch
            if (curve == null || curve.size < WINDOW_STEPS * 2) {
                state = State.Unavailable
                trace("listening: no analysis for this song")
                return@launch
            }
            reference = curve
            startEpisode(again = false)
        }
    }

    /**
     * The followed player's latest report for [key], exactly as it gave it. Both followers call
     * this with every report and every time they read the player again; a jump in it (a seek)
     * drops the offset, since a player's position is right again after one.
     */
    fun onReport(key: String?, positionMs: Long, updatedAtMs: Long, speed: Float, playing: Boolean) {
        if (key == null || key != songKey || positionMs < 0L) return
        lastFedAtMs = SystemClock.elapsedRealtime()
        val last = report
        if (last != null && last.positionMs == positionMs && last.updatedAtMs == updatedAtMs && last.playing == playing) return
        val jumped = last != null && last.playing && playing && abs(positionMs - last.positionAt(updatedAtMs)) > JUMP_MS
        if (jumped) {
            epoch++
            if (offsetMs != 0L) publish(0L, "listening: the player jumped, its position stands again")
        }
        report = Report(key, positionMs, updatedAtMs, if (playing) speed else 0f, playing, epoch)
        if (jumped && reference != null && state != State.Unavailable) startEpisode(again = false)
    }

    private fun publish(offset: Long, line: String) {
        offsetMs = offset
        trace(line)
        val key = songKey ?: return
        listeners.toList().forEach { it.onOffset(key, offset) }
    }

    private fun trace(line: String) = SyncTrace.add(SystemClock.elapsedRealtime(), line)

    /** Starts listening to the current song, now or (to check on it later in the song) [again]. */
    private fun startEpisode(again: Boolean) {
        main.removeCallbacks(recheck)
        val context = appContext ?: return
        val curve = reference ?: return
        val key = songKey ?: return
        if (!enabled(context) || listeners.isEmpty()) {
            state = State.Off
            return
        }
        // Nobody has read the player for a while: nothing is showing lyrics.
        if (again && SystemClock.elapsedRealtime() - lastFedAtMs > FED_WITHIN_MS) return
        stopEpisode()
        val handlerThread = thread ?: HandlerThread("listening-sync").also {
            it.start()
            thread = it
        }
        if (!again) state = State.Listening
        if (!again) recheckDelayMs = RECHECK_MS
        capture = Capture(context, key, curve, Handler(handlerThread.looper), if (again) RECHECK_MAX_MS else EPISODE_MAX_MS)
            .also { it.start() }
    }

    private fun stopEpisode() {
        main.removeCallbacks(recheck)
        capture?.stop()
        capture = null
    }

    private fun stopAll() {
        stopEpisode()
        loadJob?.cancel()
        thread?.quitSafely()
        thread = null
        songKey = null
        reference = null
        report = null
        state = State.Off
        offsetMs = 0L
    }

    /** An episode ended (main thread): with the audio [measuredMs] ahead of the player's reports, or with nothing found. */
    private fun onEpisodeEnd(from: Capture, measuredMs: Long?, why: String) {
        if (capture !== from) return
        capture = null
        val context = appContext ?: return
        if (measuredMs == null) {
            trace("listening: $why")
            if (why == UNAVAILABLE) {
                state = State.Unavailable
                return
            }
            if (state == State.Listening) state = State.Settled
            recheckDelayMs = (recheckDelayMs * 2).coerceAtMost(RECHECK_SLOWEST_MS)
        } else {
            recheckDelayMs = RECHECK_MS
            // Its own file: a measurement of this phone, not a setting to back up or restore.
            val prefs = context.getSharedPreferences("listening_sync", Context.MODE_PRIVATE)
            val known = prefs.getFloat(BIAS_PREF, Float.NaN)
            val bias = if (known.isNaN()) (if (abs(measuredMs) < FIRST_BIAS_MAX_MS) measuredMs.toFloat() else 0f) else known
            val beyond = measuredMs - bias
            state = State.Settled
            if (abs(beyond) < IN_SYNC_MS) {
                // The player is right: what's left is this phone's own capture delay.
                prefs.edit().putFloat(BIAS_PREF, if (known.isNaN()) bias else known + BIAS_LEARN * (measuredMs - known)).apply()
                if (offsetMs != 0L) publish(0L, "listening: the player is right again (audio $measuredMs ms from it)")
                else trace("listening: in sync (audio $measuredMs ms from the player, bias ${bias.roundToLong()})")
            } else {
                val offset = beyond.roundToLong()
                if (abs(offset - offsetMs) >= MIN_CHANGE_MS) {
                    publish(offset, "listening: audio is $offset ms from the player, lyrics moved")
                } else {
                    trace("listening: still $offset ms from the player")
                }
            }
        }
        main.postDelayed(recheck, recheckDelayMs)
    }

    /** One spell of listening, on the capture thread. */
    private class Capture(
        private val context: Context,
        private val key: String,
        private val reference: FloatArray,
        private val handler: Handler,
        private val maxMs: Long,
    ) {
        private var visualizer: Visualizer? = null
        private var onsets: OnsetListener? = null
        private var block = ByteArray(0)
        private val votes = AlignVotes()
        private var epoch = -1
        private var nextWindowAtMs = 0L
        private var heardMs = 0L
        private var lastPollAtMs = 0L
        @Volatile private var stopped = false

        private val poll = Runnable { poll() }

        fun start() {
            handler.post { open() }
        }

        fun stop() {
            stopped = true
            handler.removeCallbacks(poll)
            handler.post { close() }
        }

        private fun open() {
            if (stopped) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                end(null, UNAVAILABLE)
                return
            }
            try {
                // Session 0: everything the phone plays.
                val created = Visualizer(0)
                created.enabled = false
                val size = Visualizer.getCaptureSizeRange().let { range -> BLOCK.coerceIn(range[0], range[1]) }
                created.captureSize = size
                // As played: the default rescales every block to full scale, which hides what got louder.
                created.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
                created.enabled = true
                visualizer = created
                block = ByteArray(created.captureSize)
                onsets = OnsetListener(created.samplingRate / 1000, created.captureSize)
            } catch (_: Throwable) {
                close()
                end(null, UNAVAILABLE)
                return
            }
            lastPollAtMs = SystemClock.elapsedRealtime()
            handler.postDelayed(poll, POLL_MS)
        }

        private fun close() {
            runCatching { visualizer?.enabled = false }
            runCatching { visualizer?.release() }
            visualizer = null
        }

        private fun poll() {
            if (stopped) return
            val active = visualizer ?: return
            val listener = onsets ?: return
            val now = SystemClock.elapsedRealtime()
            val report = ListeningSync.report
            if (report == null || report.songKey != key || !report.playing) {
                // Paused, or between songs: what was heard so far is no use after it.
                listener.reset()
                votes.reset()
                epoch = -1
                lastPollAtMs = now
                handler.postDelayed(poll, IDLE_POLL_MS)
                return
            }
            if (report.epoch != epoch) {
                // A new song or a seek: start hearing from here.
                epoch = report.epoch
                listener.reset()
                votes.reset()
                nextWindowAtMs = now + (AudioAlign.STEP_MS * WINDOW_STEPS) + WINDOW_SLACK_MS
            }
            heardMs += now - lastPollAtMs
            lastPollAtMs = now
            val ok = runCatching { active.getWaveForm(block) == Visualizer.SUCCESS }.getOrDefault(false)
            if (ok) listener.add(block, now)
            if (now >= nextWindowAtMs) {
                nextWindowAtMs = now + WINDOW_HOP_MS
                val decided = judge(listener, report)
                if (decided != null) {
                    end(decided, "")
                    return
                }
            }
            if (heardMs >= maxMs) {
                end(null, "nothing clear in ${maxMs / 1000} s")
                return
            }
            handler.postDelayed(poll, POLL_MS)
        }

        /** Lays the last window over the song; the agreed offset once three windows agree. */
        private fun judge(listener: OnsetListener, report: Report): Long? {
            val live = listener.recent(WINDOW_STEPS) ?: return null
            if (listener.heard(WINDOW_STEPS) < WINDOW_STEPS * MIN_HEARD) {
                votes.reset()
                return null
            }
            val windowStartMs = listener.startMs + (listener.steps - WINDOW_STEPS).toLong() * AudioAlign.STEP_MS
            val says = report.positionAt(windowStartMs)
            val fit = AudioAlign.fit(reference, live, (says / AudioAlign.STEP_MS).toInt(), MIN_SHIFT_STEPS, MAX_SHIFT_STEPS)
            return votes.add(fit)
        }

        private fun end(measuredMs: Long?, why: String) {
            stopped = true
            handler.removeCallbacks(poll)
            close()
            val self = this
            Handler(Looper.getMainLooper()).post { onEpisodeEnd(self, measuredMs, why) }
        }
    }

    private const val UNAVAILABLE = "this phone's playback can't be heard"
    private const val BLOCK = 1024
    /** How often a block of audio is taken while listening. */
    private const val POLL_MS = 20L
    private const val IDLE_POLL_MS = 250L
    /** Ten seconds of audio are laid over the song at a time. */
    private const val WINDOW_STEPS = 400
    private const val WINDOW_SLACK_MS = 400L
    /** How far apart the windows are that have to agree. */
    private const val WINDOW_HOP_MS = 3_000L
    /** A window with fewer of its steps actually heard is skipped. */
    private const val MIN_HEARD = 0.6f
    /** The audio is looked for from four seconds behind the player to twenty ahead. */
    private const val MIN_SHIFT_STEPS = -160
    private const val MAX_SHIFT_STEPS = 800
    /** A song's first spell of listening gives up after this much playing audio. */
    private const val EPISODE_MAX_MS = 40_000L
    /** A later check takes three windows and no more. */
    private const val RECHECK_MAX_MS = 20_000L
    /** How long after a spell the song is listened to again, in case it drifts. */
    private const val RECHECK_MS = 60_000L
    private const val RECHECK_SLOWEST_MS = 240_000L
    /** A check later in the song is skipped unless a follower read the player this recently. */
    private const val FED_WITHIN_MS = 10_000L
    /** A report this far from where the last one led is a seek. */
    private const val JUMP_MS = 400L
    /** Closer than this to the player (after the bias), the player is right. */
    private const val IN_SYNC_MS = 300L
    /** The first measurement ever is the bias if it's smaller than this. */
    private const val FIRST_BIAS_MAX_MS = 600L
    private const val BIAS_LEARN = 0.25f
    /** An offset is only republished when it moved by this much. */
    private const val MIN_CHANGE_MS = 80L
}
