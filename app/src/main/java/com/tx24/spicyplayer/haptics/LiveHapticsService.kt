package com.tx24.spicyplayer.haptics

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.playback.BackgroundSession
import com.tx24.spicyplayer.power.SaverRule

/**
 * Haptics from live sound, for what has no song analysis (Settings → Device → Haptics), each its
 * own switch:
 *
 * - **Device audio**: what the phone plays (a film, a video, a game), heard through Android's
 *   output visualizer. Rests while Spotify plays: its songs have the music haptics.
 * - **Microphone**: music from someone else's speaker. Also rests while Spotify plays on this
 *   phone, which the music haptics already follow.
 *
 * With both on, the microphone is used. A foreground service, so it keeps going in the
 * background; its notification says which it's hearing and stops it. It hears and drops: nothing
 * is recorded, kept or sent. The battery saver's background haptics rule rests it too.
 */
class LiveHapticsService : Service() {
    private lateinit var prefs: SharedPreferences
    private lateinit var player: HapticPlayer
    private lateinit var session: BackgroundSession
    private val thread = HandlerThread("live-haptics").apply { start() }
    private val worker = Handler(thread.looper)
    private var source: Source? = null
    private val settings = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in WATCHED) update()
    }
    private val batterySaver = BatterySaverReceiver { update() }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("ui", Context.MODE_PRIVATE)
        player = HapticPlayer(this)
        createChannel()
        session = BackgroundSession(this).apply {
            onTrack = { update() }
            onPlayingChanged = { update() }
        }
        session.start()
        prefs.registerOnSharedPreferenceChangeListener(settings)
        batterySaver.register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(PREF_DEVICE, false).putBoolean(PREF_MIC, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        val mode = wantedMode(this)
        if (mode == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (mode == Mode.Microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                startForeground(NOTIFICATION_ID, notification(mode, resting = false), type)
            } else {
                startForeground(NOTIFICATION_ID, notification(mode, resting = false))
            }
        }.isSuccess
        // Android lets the microphone start only while the app is in front.
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        update()
        return START_STICKY
    }

    override fun onDestroy() {
        source?.stop()
        source = null
        player.stop()
        session.stop()
        prefs.unregisterOnSharedPreferenceChangeListener(settings)
        batterySaver.unregister(this)
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Starts, swaps or rests the source to match the switches, Spotify and the battery saver. */
    private fun update() {
        val mode = wantedMode(this)
        if (mode == null) {
            stopSelf()
            return
        }
        val spotifyPlaying = session.isPlaying && session.playerPackage == SPOTIFY
        val resting = spotifyPlaying || SaverRule.BackgroundHaptics.stopsNow(this, prefs)
        val wanted = if (resting) null else mode
        if (source?.mode != wanted) {
            source?.stop()
            source = wanted?.let { start(it) }
        }
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(mode, resting)) }
    }

    private fun start(mode: Mode): Source {
        val detector = LiveBeatDetector(prefs.getInt(PREF_SENSITIVITY, 50) / 100f)
        val strength = prefs.getInt("musicHapticsStrength", 100) / 100f
        val onHit: (LiveBeatDetector.Hit) -> Unit = { hit ->
            val pulse = if (hit.kind == LiveBeatDetector.Kind.Low) MusicPulse.Kick else MusicPulse.Snare
            player.play(pulse, (hit.strength * strength).coerceIn(0.05f, 1f))
        }
        return when (mode) {
            Mode.DeviceAudio -> PlaybackSource(detector, onHit)
            Mode.Microphone -> MicSource(detector, onHit)
        }.also { it.start() }
    }

    private abstract class Source(val mode: Mode) {
        abstract fun start()
        abstract fun stop()
    }

    /**
     * What the phone plays, from Android's output visualizer (session 0), read in blocks a few
     * dozen times a second; once a few seconds pass in silence, a few times a second instead.
     */
    private inner class PlaybackSource(
        private val detector: LiveBeatDetector,
        private val onHit: (LiveBeatDetector.Hit) -> Unit,
    ) : Source(Mode.DeviceAudio) {
        private var visualizer: Visualizer? = null
        private var bytes = ByteArray(0)
        private var samples = FloatArray(0)
        private var rate = 44_100
        private var quietSince = 0L
        @Volatile private var stopped = false
        private val poll = Runnable { poll() }

        override fun start() {
            worker.post {
                if (stopped || !hasPermission(this@LiveHapticsService)) return@post
                runCatching {
                    val created = Visualizer(0)
                    created.enabled = false
                    created.captureSize = Visualizer.getCaptureSizeRange().let { BLOCK.coerceIn(it[0], it[1]) }
                    created.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
                    created.enabled = true
                    visualizer = created
                    bytes = ByteArray(created.captureSize)
                    samples = FloatArray(created.captureSize)
                    rate = created.samplingRate / 1000
                    quietSince = SystemClock.elapsedRealtime()
                    worker.post(poll)
                }.onFailure { close() }
            }
        }

        override fun stop() {
            stopped = true
            worker.removeCallbacks(poll)
            worker.post { close() }
        }

        private fun close() {
            runCatching { visualizer?.enabled = false }
            runCatching { visualizer?.release() }
            visualizer = null
        }

        private fun poll() {
            if (stopped) return
            val active = visualizer ?: return
            val now = SystemClock.elapsedRealtime()
            val ok = runCatching { active.getWaveForm(bytes) == Visualizer.SUCCESS }.getOrDefault(false)
            var loud = false
            if (ok) {
                // Unsigned 8-bit, centred on 128.
                for (i in bytes.indices) {
                    val s = ((bytes[i].toInt() and 0xFF) - 128) / 128f
                    samples[i] = s
                    if (s > SILENT || s < -SILENT) loud = true
                }
                if (loud) {
                    quietSince = now
                    detector.frame(samples, samples.size, rate, now)?.let(onHit)
                }
            }
            val idle = now - quietSince > IDLE_AFTER_MS
            if (idle) detector.reset()
            worker.postDelayed(poll, if (idle) IDLE_POLL_MS else POLL_MS)
        }
    }

    /** The microphone, at 16 kHz in blocks of about 30 ms. */
    private inner class MicSource(
        private val detector: LiveBeatDetector,
        private val onHit: (LiveBeatDetector.Hit) -> Unit,
    ) : Source(Mode.Microphone) {
        @Volatile private var stopped = false
        private var record: AudioRecord? = null

        @SuppressLint("MissingPermission")
        override fun start() {
            worker.post {
                if (stopped || !hasPermission(this@LiveHapticsService)) return@post
                val min = AudioRecord.getMinBufferSize(MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (min <= 0) return@post
                val created = runCatching {
                    AudioRecord(MediaRecorder.AudioSource.MIC, MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, MIC_BLOCK * 4))
                }.getOrNull() ?: return@post
                if (created.state != AudioRecord.STATE_INITIALIZED) {
                    created.release()
                    return@post
                }
                record = created
                runCatching { created.startRecording() }.onFailure { close(); return@post }
                val pcm = ShortArray(MIC_BLOCK)
                val samples = FloatArray(MIC_BLOCK)
                while (!stopped) {
                    val read = created.read(pcm, 0, pcm.size)
                    if (read <= 0) break
                    for (i in 0 until read) samples[i] = pcm[i] / 32768f
                    detector.frame(samples, read, MIC_RATE, SystemClock.elapsedRealtime())?.let(onHit)
                }
                close()
            }
        }

        override fun stop() {
            stopped = true
            // The read loop holds the thread; stopping the recording ends its read.
            runCatching { record?.stop() }
        }

        private fun close() {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            record = null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Live haptics", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the haptics follow the phone's sound or the microphone."
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun notification(mode: Mode, resting: Boolean): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LiveHapticsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (mode == Mode.Microphone) "Haptics from the microphone" else "Haptics from the phone's sound"
        val text = when {
            resting && session.isPlaying && session.playerPackage == SPOTIFY -> "Resting while Spotify plays"
            resting -> "Resting while the battery saver is on"
            mode == Mode.Microphone -> "Listening for music nearby. Nothing is recorded."
            else -> "Vibrating to what the phone plays"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_glyph)
            .setContentTitle(title)
            .setContentText(text)
            .addAction(R.drawable.app_glyph, "Stop", stop)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    enum class Mode { DeviceAudio, Microphone }

    companion object {
        /** Haptics to the phone's own sound while Spotify isn't playing. */
        const val PREF_DEVICE = "liveHapticsDevice"
        /** Haptics to the microphone: music from another device. */
        const val PREF_MIC = "liveHapticsMic"
        /** 0..100: higher lets softer hits through. */
        const val PREF_SENSITIVITY = "liveHapticsSensitivity"
        private val WATCHED = setOf(PREF_DEVICE, PREF_MIC, PREF_SENSITIVITY, "musicHapticsStrength", SaverRule.BackgroundHaptics.pref)
        private const val SPOTIFY = "com.spotify.music"
        private const val CHANNEL_ID = "live_haptics"
        private const val NOTIFICATION_ID = 4207
        private const val ACTION_STOP = "com.tx24.spicyplayer.haptics.LIVE_STOP"
        private const val BLOCK = 1024
        private const val POLL_MS = 20L
        private const val IDLE_POLL_MS = 400L
        private const val IDLE_AFTER_MS = 4_000L
        /** A block whose every sample stays under this is silence. */
        private const val SILENT = 2f / 128f
        private const val MIC_RATE = 16_000
        private const val MIC_BLOCK = 512

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** Which source the switches ask for, or null for none (or without the permission). */
        fun wantedMode(context: Context): Mode? {
            if (!hasPermission(context)) return null
            val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
            return when {
                prefs.getBoolean(PREF_MIC, false) -> Mode.Microphone
                prefs.getBoolean(PREF_DEVICE, false) -> Mode.DeviceAudio
                else -> null
            }
        }

        /** Starts the service while one of the switches is on, stops it when both are off. */
        fun sync(context: Context) {
            if (wantedMode(context) != null) {
                runCatching {
                    ContextCompat.startForegroundService(context, Intent(context, LiveHapticsService::class.java))
                }
            } else {
                context.stopService(Intent(context, LiveHapticsService::class.java))
            }
        }
    }
}
