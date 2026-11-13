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
 * - **Device audio**: what the phone plays (a film, a video, a game). Heard through Android's
 *   audio capture once allowed (its "start recording or casting" prompt; apps that keep their
 *   sound private stay silent), else its output visualizer. Rests while Spotify plays: its songs
 *   have the music haptics.
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
    /** Started in the foreground: the sources wait for it (the microphone needs it). */
    private var foreground = false
    /** What it's hearing, for its notification: hits felt, and whether its source works. */
    @Volatile private var hits = 0
    @Volatile private var problem: String? = null
    private var lastStatus = ""
    /** When the source last handed over a block: a source that goes quiet this way has stalled. */
    @Volatile private var lastBlockAt = 0L
    /** When it last heard sound: it stays awake for a while after, not for silence. */
    @Volatile private var lastLoudAt = 0L
    /** Keeps the processor awake while it hears sound, so the screen going off doesn't stop it. */
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    /** Android stopped the capture (the screen locking, say): it waits for a tap to allow it again. */
    @Volatile private var captureStopped = false
    private val watchdog = object : Runnable {
        override fun run() {
            checkSource()
            main.postDelayed(this, WATCHDOG_MS)
        }
    }
    /** How loud the last blocks were, 0..1, for the settings' meter. */
    @Volatile private var level = 0f
    /** Android's audio capture, when allowed: what the phone plays, from the apps that share it. */
    private var projection: android.media.projection.MediaProjection? = null
    private val statusTick = object : Runnable {
        override fun run() {
            publishStatus()
            main.postDelayed(this, STATUS_TICK_MS)
        }
    }
    private val main = Handler(android.os.Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            postNotification()
            main.postDelayed(this, STATUS_MS)
        }
    }
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
        // A capture just allowed in settings comes with this start, once.
        val grant = takeGrant()
        val capture = mode == Mode.DeviceAudio && grant != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = when {
                    mode == Mode.Microphone -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    capture || projection != null -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
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
        if (capture && grant != null) {
            projection?.stop()
            projection = runCatching {
                getSystemService(android.media.projection.MediaProjectionManager::class.java)?.getMediaProjection(grant.first, grant.second)
            }.getOrNull()?.also { granted ->
                granted.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                    override fun onStop() {
                        main.post {
                            if (projection === granted) {
                                projection = null
                                captureStopped = true
                                // Back to the visualizer.
                                source?.stop()
                                source = null
                                update()
                            }
                        }
                    }
                }, main)
            }
            if (projection != null) captureStopped = false
            // A new way to hear: start over with it.
            source?.stop()
            source = null
        }
        foreground = true
        update()
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, STATUS_MS)
        main.removeCallbacks(statusTick)
        main.post(statusTick)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(watchdog)
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        main.removeCallbacks(refresh)
        main.removeCallbacks(statusTick)
        runCatching { projection?.stop() }
        projection = null
        status.value = Status()
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
        if (!foreground) return
        val wanted = if (resting()) null else mode
        if (source?.mode != wanted) {
            source?.stop()
            problem = null
            lastBlockAt = SystemClock.elapsedRealtime()
            source = wanted?.let { start(it) }
        }
        holdAwake(source != null)
        postNotification()
    }

    /**
     * Every few seconds: a source that stopped handing over blocks (its read failed, Android
     * took the microphone or the sound away for a moment) is started again, so the haptics carry
     * on instead of going quiet for good.
     */
    private fun checkSource() {
        val running = source ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < STALL_MS) {
            holdAwake(now - lastLoudAt < AWAKE_AFTER_SOUND_MS)
            return
        }
        running.stop()
        lastBlockAt = now
        source = start(running.mode)
    }

    /** Holds the processor awake while a source runs (renewed by the watchdog, never forever). */
    private fun holdAwake(on: Boolean) {
        if (!on) {
            runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            return
        }
        val lock = wakeLock ?: getSystemService(android.os.PowerManager::class.java)
            ?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SpicyLyrics:LiveHaptics")
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
        runCatching { lock?.acquire(WAKE_MS) }
    }

    private fun resting(): Boolean =
        (session.isPlaying && session.playerPackage == SPOTIFY) || SaverRule.BackgroundHaptics.stopsNow(this, prefs)

    /** What it's doing, for the settings page. */
    private fun publishStatus() {
        val mode = wantedMode(this)
        val text = mode?.let { statusText(it, resting()) } ?: "Off"
        level *= LEVEL_FALL
        status.value = Status(running = mode != null, text = text, level = level.coerceIn(0f, 1f), hits = hits,
            capturing = projection != null && mode == Mode.DeviceAudio)
    }

    private fun postNotification() {
        val mode = wantedMode(this) ?: return
        val built = notification(mode, resting())
        val status = built.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (status == lastStatus) return
        lastStatus = status
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, built) }
    }

    private fun start(mode: Mode): Source {
        val detector = LiveBeatDetector(prefs.getInt(PREF_SENSITIVITY, 50) / 100f)
        val strength = prefs.getInt("musicHapticsStrength", 100) / 100f
        val onHit: (LiveBeatDetector.Hit) -> Unit = { hit ->
            hits++
            val pulse = if (hit.kind == LiveBeatDetector.Kind.Low) MusicPulse.Kick else MusicPulse.Snare
            player.play(pulse, (hit.strength * strength).coerceIn(0.05f, 1f))
        }
        val current = projection
        return when {
            mode == Mode.Microphone -> MicSource(detector, onHit)
            current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> CaptureSource(current, detector, onHit)
            else -> PlaybackSource(detector, onHit)
        }.also { it.start() }
    }

    /** Takes in a block's loudness, for the meter. */
    private fun hear(samples: FloatArray, count: Int) {
        var sum = 0f
        for (i in 0 until count) sum += samples[i] * samples[i]
        val rms = kotlin.math.sqrt(sum / count.coerceAtLeast(1))
        val loud = (rms * 3f).coerceIn(0f, 1f)
        if (rms > 0.001f) lastLoudAt = SystemClock.elapsedRealtime()
        if (loud > level) level = loud
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
                    // Normalized: the same at any volume, even with the volume right down or on headphones.
                    created.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                    created.enabled = true
                    visualizer = created
                    bytes = ByteArray(created.captureSize)
                    samples = FloatArray(created.captureSize)
                    rate = created.samplingRate / 1000
                    quietSince = SystemClock.elapsedRealtime()
                    worker.post(poll)
                }.onFailure {
                    problem = "Android won't share the phone's sound right now"
                    close()
                }
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
            if (ok) lastBlockAt = now
            var loud = false
            if (ok) {
                // Unsigned 8-bit, centred on 128.
                for (i in bytes.indices) {
                    val s = ((bytes[i].toInt() and 0xFF) - 128) / 128f
                    samples[i] = s
                    if (s != 0f) loud = true
                }
                if (loud) {
                    quietSince = now
                    if (problem == NOTHING_HEARD) problem = null
                    hear(samples, samples.size)
                    detector.frame(samples, samples.size, rate, now)?.let(onHit)
                } else if (now - quietSince > SILENT_PROBLEM_MS && hits == 0) {
                    problem = NOTHING_HEARD
                }
            }
            val idle = now - quietSince > IDLE_AFTER_MS
            if (idle) detector.reset()
            worker.postDelayed(poll, if (idle) IDLE_POLL_MS else POLL_MS)
        }
    }

    /**
     * What the phone plays, through Android's audio capture ([projection]): the media, game and
     * unlabelled sound of every app that shares it, at 48 kHz in blocks of about 20 ms.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private inner class CaptureSource(
        private val granted: android.media.projection.MediaProjection,
        private val detector: LiveBeatDetector,
        private val onHit: (LiveBeatDetector.Hit) -> Unit,
    ) : Source(Mode.DeviceAudio) {
        @Volatile private var stopped = false
        private var record: AudioRecord? = null

        @SuppressLint("MissingPermission")
        override fun start() {
            worker.post {
                if (stopped || !hasPermission(this@LiveHapticsService)) return@post
                val config = android.media.AudioPlaybackCaptureConfiguration.Builder(granted)
                    .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(CAPTURE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                val min = AudioRecord.getMinBufferSize(CAPTURE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val created = runCatching {
                    AudioRecord.Builder()
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(maxOf(min, CAPTURE_BLOCK * 8))
                        .setAudioPlaybackCaptureConfig(config)
                        .build()
                }.getOrNull()
                if (created == null || created.state != AudioRecord.STATE_INITIALIZED) {
                    created?.release()
                    problem = "Couldn't start capturing the phone's sound"
                    return@post
                }
                record = created
                if (runCatching { created.startRecording() }.isFailure) {
                    problem = "Couldn't start capturing the phone's sound"
                    close()
                    return@post
                }
                val pcm = ShortArray(CAPTURE_BLOCK)
                val samples = FloatArray(CAPTURE_BLOCK)
                var quietSince = SystemClock.elapsedRealtime()
                while (!stopped) {
                    val read = created.read(pcm, 0, pcm.size)
                    // A failed read ends this loop; the watchdog starts the source again.
                    if (read <= 0) break
                    lastBlockAt = SystemClock.elapsedRealtime()
                    var loud = false
                    for (i in 0 until read) {
                        samples[i] = pcm[i] / 32768f
                        if (pcm[i].toInt() != 0) loud = true
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (loud) {
                        quietSince = now
                        hear(samples, read)
                        detector.frame(samples, read, CAPTURE_RATE, now)?.let(onHit)
                    } else if (now - quietSince > IDLE_AFTER_MS) {
                        detector.reset()
                    }
                }
                close()
            }
        }

        override fun stop() {
            stopped = true
            runCatching { record?.stop() }
        }

        private fun close() {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            record = null
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
                // Unprocessed where the phone has it: the voice clean-up would turn music down.
                val audio = getSystemService(android.media.AudioManager::class.java)
                val unprocessed = audio?.getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
                val created = listOfNotNull(
                    MediaRecorder.AudioSource.UNPROCESSED.takeIf { unprocessed },
                    MediaRecorder.AudioSource.MIC,
                ).firstNotNullOfOrNull { input ->
                    runCatching {
                        AudioRecord(input, MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, MIC_BLOCK * 4))
                    }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED || run { it.release(); false } }
                }
                if (created == null) {
                    problem = "Couldn't open the microphone"
                    return@post
                }
                record = created
                runCatching { created.startRecording() }.onFailure {
                    problem = "Couldn't open the microphone"
                    close()
                    return@post
                }
                if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    problem = "Another app is using the microphone"
                    close()
                    return@post
                }
                val pcm = ShortArray(MIC_BLOCK)
                val samples = FloatArray(MIC_BLOCK)
                while (!stopped) {
                    val read = created.read(pcm, 0, pcm.size)
                    // A failed read ends this loop; the watchdog starts the source again.
                    if (read <= 0) break
                    lastBlockAt = SystemClock.elapsedRealtime()
                    for (i in 0 until read) samples[i] = pcm[i] / 32768f
                    hear(samples, read)
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
        val text = statusText(mode, resting)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_glyph)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .apply {
                // Tapped, it asks to capture again (Android's prompt), then carries on.
                if (mode == Mode.DeviceAudio) {
                    setContentIntent(
                        PendingIntent.getActivity(
                            this@LiveHapticsService, 2, Intent(this@LiveHapticsService, CaptureConsentActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                }
            }
            .addAction(R.drawable.app_glyph, "Stop", stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun statusText(mode: Mode, resting: Boolean): String {
        return when {
            resting && session.isPlaying && session.playerPackage == SPOTIFY -> "Resting while Spotify plays"
            resting -> "Resting while the battery saver is on"
            mode == Mode.DeviceAudio && captureStopped && projection == null -> "Android stopped the capture. Tap to allow it again"
            hits > 0 -> "$hits hits felt so far"
            problem != null -> problem!!
            mode == Mode.Microphone -> "Listening for music nearby. Nothing is recorded."
            projection != null -> "Capturing the phone's sound, waiting for something to play"
            else -> "Waiting for sound"
        }
    }

    enum class Mode { DeviceAudio, Microphone }

    /** What the service is doing, for the settings page: its state, how loud, and hits felt. */
    data class Status(
        val running: Boolean = false,
        val text: String = "Off",
        val level: Float = 0f,
        val hits: Int = 0,
        val capturing: Boolean = false,
    )

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
        /** How often its notification catches up with what it's hearing. */
        private const val STATUS_MS = 3_000L
        private const val NOTHING_HEARD = "Hearing nothing: the phone isn't sharing its sound this way. Turn the switch off and on and allow the capture"
        private const val STATUS_TICK_MS = 250L
        private const val WATCHDOG_MS = 2_000L
        /** No block for this long and the source is started again. */
        private const val STALL_MS = 5_000L
        private const val WAKE_MS = 10_000L
        private const val AWAKE_AFTER_SOUND_MS = 60_000L
        private const val LEVEL_FALL = 0.7f
        /** This long in silence with nothing felt, it says the phone isn't sharing its sound. */
        private const val SILENT_PROBLEM_MS = 15_000L
        private const val CAPTURE_RATE = 48_000
        private const val CAPTURE_BLOCK = 1024
        private const val MIC_RATE = 16_000
        private const val MIC_BLOCK = 512

        /** What it's doing now, for the settings page. */
        val status = kotlinx.coroutines.flow.MutableStateFlow(Status())

        /** The capture just allowed in settings, waiting for the service to take it. */
        private var grant: Pair<Int, Intent>? = null

        @Synchronized
        private fun takeGrant(): Pair<Int, Intent>? = grant.also { grant = null }

        /** Starts (or restarts) the service with a capture allowed in settings. */
        @Synchronized
        fun startWithCapture(context: Context, resultCode: Int, data: Intent) {
            grant = resultCode to data
            sync(context)
        }

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
