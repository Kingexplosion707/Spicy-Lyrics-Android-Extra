package com.tx24.spicyplayer.haptics

import android.app.Application
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.playback.BackgroundSession
import com.tx24.spicyplayer.wallpaper.PowerSaving
import com.tx24.spicyplayer.widget.BackgroundLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the music haptics vibrating while the app is not in front: on the home screen or in
 * another app. The lyrics screen vibrates while it is up ([com.tx24.spicyplayer.MainActivity]'s
 * own loop), so this service rests whenever it is resumed and takes over once it isn't.
 *
 * A foreground service, so Android lets it keep running with the screen off: its notification
 * says what is playing and offers to stop it. It never vibrates while playback is paused or
 * stopped, and it stops itself when the background haptics are turned off.
 */
class BackgroundHapticsService : Service() {
    private lateinit var session: BackgroundSession
    private lateinit var player: HapticPlayer
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hapticsJob: Job? = null
    /** The vibrations for the song playing, or null while its analysis hasn't arrived. */
    @Volatile private var score: List<MusicHaptic>? = null
    private var scoreKey: String? = null
    /** The song whose vibrations are being built, if any. */
    private var building: String? = null
    /** Whether the lyrics screen is up: its own loop vibrates then, not this service. */
    private var lyricsResumed = false
    private var foreground = false
    /** Power saving can stop the haptics: they follow its setting and the phone's battery saver. */
    private val batterySaver = BatterySaverReceiver { updateHaptics() }
    private val savingSettings = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PowerSaving.PREF || key == PowerSaving.PREF_NO_HAPTICS) updateHaptics()
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity !is LyricsActivity) return
            if (!lyricsResumed) {
                lyricsResumed = true
                updateHaptics()
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity !is LyricsActivity) return
            if (lyricsResumed) {
                lyricsResumed = false
                updateHaptics()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("ui", Context.MODE_PRIVATE)
        player = HapticPlayer(this)
        createChannel()
        (application as? Application)?.registerActivityLifecycleCallbacks(activityCallbacks)
        session = BackgroundSession(this).apply {
            onTrack = { track -> onSong(track) }
            onPlayingChanged = { updateHaptics() }
        }
        session.start()
        batterySaver.register(this)
        prefs.registerOnSharedPreferenceChangeListener(savingSettings)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!backgroundHapticsOn()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        foreground = true
        updateHaptics()
        return START_STICKY
    }

    override fun onDestroy() {
        hapticsJob?.cancel()
        hapticsJob = null
        player.stop()
        session.stop()
        batterySaver.unregister(this)
        prefs.unregisterOnSharedPreferenceChangeListener(savingSettings)
        (application as? Application)?.unregisterActivityLifecycleCallbacks(activityCallbacks)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** A new song: its vibrations are rebuilt from its audio analysis, as the screen does. */
    private fun onSong(track: BackgroundSession.Track?) {
        score = null
        scoreKey = null
        updateNotification()
        // While power saving stops the haptics, the song's analysis waits until they're back.
        if (track == null || hapticsStoppedBySaving(this, prefs)) {
            updateHaptics()
            return
        }
        if (building != track.key) buildScore(track)
    }

    /** Builds [track]'s vibrations from its audio analysis and, where they help, its lyrics. */
    private fun buildScore(track: BackgroundSession.Track) {
        val key = track.key
        building = key
        scope.launch(Dispatchers.IO) {
            val style = hapticsStyle()
            val beat = hapticBeatOptions()
            val meta = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
            val trackId = runCatching {
                SharedSpotify.extras(cacheDir).trackIds(meta, track.request.spotifyTrackId).firstOrNull()
            }.getOrNull()
            val analysis = trackId?.let {
                runCatching { SharedSpotify.extras(cacheDir).audioAnalysis(it) }.getOrNull()
            }
            // The lyrics' word and line times, when the timing or detection fuses them in.
            var times = LyricHapticTimes.NONE
            val needsLyrics = beat.timing != HapticBeatTiming.Spotify ||
                beat.detection == HapticBeatDetection.Combined ||
                beat.detection == HapticBeatDetection.AllInOne
            if (needsLyrics && analysis != null) {
                val found = runCatching {
                    BackgroundLyrics(this@BackgroundHapticsService)
                        .lookUp(track.request, romanize = false, track.spotifyId)
                }.getOrNull()
                val ready = (found as? BackgroundLyrics.Result.Found)?.lyrics
                times = ready?.let { LyricHapticTimes.build(it) } ?: LyricHapticTimes.NONE
            }
            val built = analysis?.let {
                MusicHapticScore.buildWithBeat(it, style, beat, times.wordTimes, times.lineTimes, times.wordsEstimated)
            }
            withContext(Dispatchers.Main) {
                if (building == key) building = null
                if (session.track?.key != key) return@withContext
                score = built
                scoreKey = key
                updateHaptics()
            }
        }
    }

    /**
     * Vibrates while the music plays, the lyrics screen is not up and power saving doesn't stop
     * the haptics; rests the moment any of that stops being true. The style or strength changing
     * mid-song rebuilds the score on the next song; the strength applies at once.
     */
    private fun updateHaptics() {
        player.renderEngine = hapticBeatEngine()
        val saving = hapticsStoppedBySaving(this, prefs)
        val run = backgroundHapticsOn() && session.isPlaying && !lyricsResumed && player.canPlayMusic && !saving
        // Power saving ended mid-song: this song's vibrations weren't built yet.
        val track = session.track
        if (!saving && track != null && scoreKey != track.key && building != track.key) buildScore(track)
        if (run && hapticsJob == null) {
            val beat = hapticBeatOptions()
            hapticsJob = scope.launch {
                playMusicHaptics(
                    player,
                    score = { score },
                    positionMs = { session.positionMs() + remoteOffset() },
                    strength = { prefs.getInt("musicHapticsStrength", 100) / 100f },
                    continuous = beat.mode == HapticBeatMode.Continuous,
                )
            }
        } else if (!run && hapticsJob != null) {
            hapticsJob?.cancel()
            hapticsJob = null
            player.stop()
        }
        updateNotification()
    }

    private fun backgroundHapticsOn(): Boolean =
        prefs.getBoolean("musicHaptics", false) && prefs.getBoolean("backgroundMusicHaptics", true)

    private fun hapticsStyle(): MusicHapticsStyle = runCatching {
        MusicHapticsStyle.valueOf(prefs.getString("musicHapticsStyle", null) ?: MusicHapticsStyle.Drums.name)
    }.getOrDefault(MusicHapticsStyle.Drums)

    /** HapticBeat's choices, from the same preferences Settings writes. */
    private fun hapticBeatOptions(): HapticBeatOptions = HapticBeatOptions(
        mode = runCatching {
            HapticBeatMode.valueOf(prefs.getString("hapticBeatMode", null) ?: HapticBeatMode.Full.name)
        }.getOrDefault(HapticBeatMode.Full),
        timing = runCatching {
            HapticBeatTiming.valueOf(prefs.getString("hapticBeatTiming", null) ?: HapticBeatTiming.Auto.name)
        }.getOrDefault(HapticBeatTiming.Auto),
        detection = runCatching {
            HapticBeatDetection.valueOf(prefs.getString("hapticBeatDetection", null) ?: HapticBeatDetection.Auto.name)
        }.getOrDefault(HapticBeatDetection.Auto),
        sensitivity = prefs.getInt("hapticBeatSensitivity", 50),
        vocalsOnly = prefs.getBoolean("hapticBeatVocalsOnly", false),
    )

    private fun hapticBeatEngine(): HapticBeatEngine = runCatching {
        HapticBeatEngine.valueOf(prefs.getString("hapticBeatEngine", null) ?: HapticBeatEngine.Auto.name)
    }.getOrDefault(HapticBeatEngine.Auto)

    private fun remoteOffset(): Long = prefs.getInt("hapticBeatRemoteOffset", 150).toLong()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.background_haptics_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.background_haptics_channel_description)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        if (!foreground) return
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val track = session.track
        val text = when {
            track == null -> getString(R.string.background_haptics_nothing)
            hapticsStoppedBySaving(this, prefs) -> getString(R.string.background_haptics_power_saving)
            session.isPlaying -> "${track.title} — ${track.artist}"
            else -> getString(R.string.background_haptics_paused)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LyricsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, BackgroundHapticsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_glyph)
            .setContentTitle(getString(R.string.background_haptics_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(R.drawable.app_glyph, getString(R.string.background_haptics_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "background_haptics"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_START = "com.tx24.spicyplayer.haptics.START"
        const val ACTION_STOP = "com.tx24.spicyplayer.haptics.STOP"

        /** Starts the service while the background haptics are on, stops it when they are off. */
        fun sync(context: Context) {
            val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
            val on = prefs.getBoolean("musicHaptics", false) &&
                prefs.getBoolean("backgroundMusicHaptics", true)
            if (on) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, BackgroundHapticsService::class.java).setAction(ACTION_START),
                )
            } else {
                context.stopService(Intent(context, BackgroundHapticsService::class.java))
            }
        }
    }
}
