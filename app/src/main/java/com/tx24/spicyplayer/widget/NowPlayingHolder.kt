package com.tx24.spicyplayer.widget

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpSnapshot
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.playback.AudioOutputProfiles
import com.tx24.spicyplayer.playback.LyricClock
import com.tx24.spicyplayer.playback.SessionAccessService
import com.tx24.spicyplayer.playback.SessionStickiness
import com.tx24.spicyplayer.playback.PlayerDelays
import com.tx24.spicyplayer.playback.SongDelays
import com.tx24.spicyplayer.playback.bestSession
import com.tx24.spicyplayer.playback.lookupRequest
import com.tx24.spicyplayer.playback.lyricPositionMs
import com.tx24.spicyplayer.playback.lyricsKey
import com.tx24.spicyplayer.playback.savedSpotifyId
import com.tx24.spicyplayer.playback.sessionSpotifyId
import com.tx24.spicyplayer.ui.background.CoverColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The playing song and its current line for the home screen widget, kept with the lyrics screen
 * closed. It lives in the app's process, which the system keeps running for the notification
 * listener ([SessionAccessService]) while access is granted, and runs only while a widget is placed.
 *
 * It follows the same session as the lyrics screen ([bestSession]) on a [LyricClock] of its own,
 * and wakes only when the shown line changes, never with the screen off, under the full-size
 * lyrics screen or with the music paused.
 * While the lyrics screen is up, the screen's own pick is used ([offer]); otherwise
 * [BackgroundLyrics] looks the song up, and with the screen off only once it's on again
 * ([ScreenOffLookup]). Main thread only.
 */
internal object NowPlayingHolder {
    private const val WAITING = "Play something to see its lyrics here"
    private const val NO_ACCESS = "Open Spicy Lyrics to allow notification access"
    private const val LOOKING = "Looking for lyrics…"
    private const val NO_LYRICS = "No lyrics for this song"
    private const val LOOKUP_FAILED = "Couldn't load lyrics"
    private const val NO_SONG_INFO = "The player doesn't say what's playing"
    /** The longest the widget sleeps while playing, so a missed report can't leave a line up for long. */
    private const val MAX_SLEEP_MS = 10_000L
    /** How often the widget wakes while playing when a placed one shows the progress bar. */
    private const val PROGRESS_STEP_MS = 3_000L
    private const val SAMPLE_PX = 8
    private const val COVER_PX = 96
    private const val COVER_RADIUS_PX = 14f
    /** How long the followed session may stay stopped before another playing one takes over, as on the lyrics screen. */
    private const val SESSION_SWITCH_GRACE_MS = 2_000L

    private val main = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var scope: CoroutineScope? = null
    private var lookup: BackgroundLyrics? = null
    private var outputProfiles: AudioOutputProfiles? = null
    private var songDelays: SongDelays? = null
    private var playerDelays: PlayerDelays? = null
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val observedSessions = HashMap<MediaSession.Token, MediaController>()
    private val observedCallbacks = HashMap<MediaSession.Token, MediaController.Callback>()
    private val stickiness = SessionStickiness(SESSION_SWITCH_GRACE_MS)
    /** Looks at the sessions again once the followed one has been stopped for its grace. */
    private val sessionRecheck = Runnable { refreshSessions() }
    private val clock = LyricClock(SystemClock.elapsedRealtime())
    /** A new song whose first report hasn't come yet: the clock takes it outright. */
    private var awaitingReport = false

    private var songKey: String? = null
    private var request: LyricsLookupRequest? = null
    /** The Spotify ID the user set for the song, if any. */
    private var manualSpotifyId: String? = null
    private var title = ""
    private var artist = ""
    private var durationMs = 0L
    private var credit: String? = null
    private var leads: List<WidgetLines.Lead> = emptyList()
    private var synced = false
    /** Shown in place of lyrics while there are none. */
    private var notice: String? = WAITING
    private var art: Bitmap? = null
    private var backdrop: Bitmap? = null
    private var cover: Bitmap? = null
    private var colors: CoverColors? = null
    /** Whether any placed widget shows romanization, and any the progress bar ([LyricsWidget.wants]). */
    private var wantsRomanization = true
    private var wantsProgress = true
    /** The song's lyrics came from this holder's own lookup without romanization. */
    private var lookedUpPlain = false
    /** What the widget was last sent, for the settings screen's preview. */
    var last: LyricsWidget.Content? = null
        private set
    private var lookupJob: Job? = null
    /** The lyrics screen is up and picks the lyrics itself. */
    private var appShowing = false
    /** Lyrics the screen offered for a song this holder hasn't seen yet. */
    private var pendingOffer: Pair<String, LyricsState>? = null
    /** Nothing is sent with the screen off or under the full-size lyrics screen. */
    private val visibility = WidgetVisibility()
    /** No lookups for songs that change with the screen off: the current one is looked up once it's on. */
    private val screenOffLookup = ScreenOffLookup()
    private var shown: WidgetLines? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshSessions()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            visibility.screenOn = intent.action == Intent.ACTION_SCREEN_ON
            if (!visibility.screenOn) {
                main.removeCallbacks(tick)
                return
            }
            catchUp()
            // Only when that didn't find a new song, which is looked up on its own.
            if (screenOffLookup.screenOn() && !appShowing && leads.isEmpty() && lookupJob?.isActive != true) lookUpSong()
        }
    }

    private val tick = Runnable { refreshSessions() }

    /** The lyrics screen in front at full size hides the home screen; in the floating window it doesn't. */
    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity !is LyricsActivity) return
            val floating = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.isInPictureInPictureMode
            setAppCovering(!floating)
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity is LyricsActivity) setAppCovering(false)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private fun setAppCovering(covering: Boolean) {
        if (visibility.appCovering == covering) return
        visibility.appCovering = covering
        if (covering) main.removeCallbacks(tick) else catchUp()
    }

    /** Seen again: reads the player afresh (with the screen back on, the cover too) and sends what was missed in full. */
    private fun catchUp() {
        refreshSessions()
        if (visibility.missed) show(full = true)
    }

    /** Whether the screen was wide when the widgets were last drawn ([LyricsWidget.screenDp]). */
    private var wide: Boolean? = null

    /** Turned: each widget is drawn again for its size this way round ([LyricsWidget.sizeDp]). */
    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(config: Configuration) {
            val app = context ?: return
            val now = LyricsWidget.screenDp(app).let { (width, height) -> width > height }
            if (now == wide) return
            wide = now
            show(full = true)
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = Unit
    }

    val running: Boolean get() = scope != null

    /**
     * Starts following the music if a widget is placed. Again while running, it takes up any
     * changed widget settings and redraws every widget.
     */
    fun start(context: Context) {
        val app = context.applicationContext
        if (running) {
            readWants(app)
            // Started before access was granted: follow the music now that it may be.
            if (sessionManager == null) connect(app) else refreshSessions()
            show(full = true)
            return
        }
        if (!LyricsWidget.placed(app)) return
        this.context = app
        readWants(app)
        scope = MainScope()
        lookup = BackgroundLyrics(app)
        outputProfiles = AudioOutputProfiles(app)
        songDelays = SongDelays(app)
        playerDelays = PlayerDelays(app)
        visibility.screenOn = app.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val screen = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        ContextCompat.registerReceiver(app, screenReceiver, screen, ContextCompat.RECEIVER_NOT_EXPORTED)
        (app as? Application)?.registerActivityLifecycleCallbacks(activityCallbacks)
        wide = LyricsWidget.screenDp(app).let { (width, height) -> width > height }
        app.registerComponentCallbacks(configCallbacks)
        connect(app)
    }

    /** Listens for the players' sessions, or shows that access isn't granted. */
    private fun connect(app: Context) {
        val manager = app.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(app, SessionAccessService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            sessionManager = manager
            follow(manager.getActiveSessions(listener))
        }.onFailure {
            // Access isn't granted: the app asks for it when opened.
            notice = NO_ACCESS
            show(full = true)
        }
    }

    /** Stops following the music: the last widget was removed. */
    fun stop() {
        val app = context ?: return
        main.removeCallbacks(tick)
        runCatching { app.unregisterReceiver(screenReceiver) }
        (app as? Application)?.unregisterActivityLifecycleCallbacks(activityCallbacks)
        visibility.appCovering = false
        app.unregisterComponentCallbacks(configCallbacks)
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        observedSessions.forEach { (token, session) -> observedCallbacks[token]?.let(session::unregisterCallback) }
        observedSessions.clear()
        observedCallbacks.clear()
        main.removeCallbacks(sessionRecheck)
        stickiness.reset()
        scope?.cancel()
        scope = null
        sessionManager = null
        controller = null
        lookup = null
        context = null
        songKey = null
        pendingOffer = null
        clearSong(WAITING)
    }

    /** Whether the lyrics screen is up, picking the lyrics itself; once it isn't, the widget looks songs up. */
    fun setAppShowing(showing: Boolean) {
        appShowing = showing
        if (!showing && running && leads.isEmpty() && lookupJob?.isActive != true) lookUpSong()
    }

    /** The lyrics screen's own [lyrics] for song [key] (by [lyricsKey]), over the widget's lookup. */
    fun offer(key: String?, lyrics: LyricsState) {
        if (key == null || (lyrics !is LyricsState.Ready && lyrics !is LyricsState.Error)) return
        if (!running || key != songKey) {
            pendingOffer = key to lyrics
            return
        }
        take(lyrics)
        show()
    }

    fun playPause() {
        val session = controller ?: return
        if (session.playbackState?.state == PlaybackState.STATE_PLAYING) session.transportControls.pause()
        else session.transportControls.play()
    }

    fun skipPrevious() {
        controller?.transportControls?.skipToPrevious()
    }

    fun skipNext() {
        controller?.transportControls?.skipToNext()
    }

    private fun readWants(context: Context) {
        val (romanization, progress) = LyricsWidget.wants(context)
        wantsProgress = progress
        val romanizationOn = romanization && !wantsRomanization
        wantsRomanization = romanization
        // Looked up without it: again, from the cache, now that a widget shows it.
        if (romanizationOn && lookedUpPlain && !appShowing) {
            leads = emptyList()
            lookUpSong()
        }
    }

    private fun refreshSessions() {
        val app = context ?: return
        val manager = sessionManager ?: return
        runCatching { manager.getActiveSessions(ComponentName(app, SessionAccessService::class.java)) }
            .onSuccess(::follow)
            .onFailure { lostAccess() }
    }

    /** Access was taken away: stop following, and connect again when it's back ([start]). */
    private fun lostAccess() {
        main.removeCallbacks(tick)
        main.removeCallbacks(sessionRecheck)
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        sessionManager = null
        controller?.unregisterCallback(controllerCallback)
        controller = null
        observedSessions.forEach { (token, session) -> observedCallbacks[token]?.let(session::unregisterCallback) }
        observedSessions.clear()
        observedCallbacks.clear()
        stickiness.reset()
        songKey = null
        clock.reset(SystemClock.elapsedRealtime())
        clearSong(NO_ACCESS)
        show(full = true)
    }

    private fun follow(sessions: List<MediaController>) {
        // Session-list changes do not report a paused player starting again. Watch every
        // player, just as the main screen does, including those we are not following yet.
        val tokens = sessions.map { it.sessionToken }.toSet()
        (observedSessions.keys - tokens).forEach { token ->
            observedSessions.remove(token)?.let { session -> observedCallbacks.remove(token)?.let(session::unregisterCallback) }
        }
        sessions.forEach { session ->
            val token = session.sessionToken
            if (token !in observedSessions) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = refreshSessions()
                    override fun onSessionDestroyed() = refreshSessions()
                }
                observedSessions[token] = session
                observedCallbacks[token] = callback
                session.registerCallback(callback, main)
            }
        }
        val now = SystemClock.elapsedRealtime()
        val best = bestSession(sessions, controller?.sessionToken, stickiness, now)
        main.removeCallbacks(sessionRecheck)
        if (best?.sessionToken == controller?.sessionToken) {
            stickiness.remainingMs(now)?.takeIf { it > 0 }?.let { main.postDelayed(sessionRecheck, it + 50L) }
        } else {
            stickiness.reset()
            controller?.unregisterCallback(controllerCallback)
            controller = best
            clock.reset(now)
            awaitingReport = true
            best?.registerCallback(controllerCallback, main)
        }
        update()
    }

    private fun update() {
        val session = controller
        val metadata = session?.metadata
        val key = metadata.lyricsKey()
        if (session == null || metadata == null || key == null) {
            songKey = null
            clock.reset(SystemClock.elapsedRealtime())
            clearSong(if (session == null) WAITING else NO_SONG_INFO)
            show(full = true)
            return
        }
        val newSong = key != songKey
        if (newSong) startSong(key, metadata)
        // Players may publish the duration after the title, or briefly omit it during
        // playback updates. Learn it when available without hiding an existing timeline.
        val reportedDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val durationChanged = reportedDuration > 0L && reportedDuration != durationMs
        if (durationChanged) durationMs = reportedDuration
        // A manual Spotify ID holds while the song's length agrees, so the length coming late can bring or drop one.
        if (durationChanged && !newSong) recheckSpotifyId(metadata)
        // With the screen off the cover waits: it's read again once the screen is on ([catchUp]).
        val coverChanged = visibility.screenOn && refreshCover(metadata)
        val wasPlaying = clock.isPlaying
        report(session, newSong)
        show(full = newSong || coverChanged || durationChanged || wasPlaying != clock.isPlaying)
    }

    private fun startSong(key: String, metadata: MediaMetadata) {
        songKey = key
        clearSong(LOOKING)
        val app = context ?: return
        val manual = metadata.savedSpotifyId(app.getSharedPreferences("spotify_id_overrides", 0))
        val request = metadata.lookupRequest(manual ?: metadata.sessionSpotifyId())
        this.request = request
        manualSpotifyId = manual
        title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        if (request.title.isBlank() || request.artist.isBlank()) {
            notice = NO_SONG_INFO
            return
        }
        pendingOffer?.takeIf { it.first == key }?.let { (_, lyrics) ->
            pendingOffer = null
            take(lyrics)
            return
        }
        // While the lyrics screen is up it offers its own lyrics, kept even from before a widget
        // was placed ([offer]), so looking up here would only ask the sources twice.
        if (!appShowing) lookUpSong()
    }

    private fun recheckSpotifyId(metadata: MediaMetadata) {
        val app = context ?: return
        val manual = metadata.savedSpotifyId(app.getSharedPreferences("spotify_id_overrides", 0))
        if (manual == manualSpotifyId) return
        manualSpotifyId = manual
        request = metadata.lookupRequest(manual ?: metadata.sessionSpotifyId())
        if (!appShowing) lookUpSong()
    }

    private fun lookUpSong() {
        val request = request?.takeIf { it.title.isNotBlank() && it.artist.isNotBlank() } ?: return
        val key = songKey ?: return
        val lookup = lookup ?: return
        if (!screenOffLookup.request(visibility.screenOn)) return
        val romanize = wantsRomanization
        val manual = manualSpotifyId
        lookupJob?.cancel()
        lookupJob = scope?.launch {
            val result = runCatching { lookup.lookUp(request, romanize, manual) }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                BackgroundLyrics.Result.Failed
            }
            if (songKey != key || leads.isNotEmpty()) return@launch
            when (result) {
                is BackgroundLyrics.Result.Found -> {
                    take(result.lyrics)
                    lookedUpPlain = !romanize
                }
                BackgroundLyrics.Result.None -> notice = NO_LYRICS
                BackgroundLyrics.Result.Failed -> notice = LOOKUP_FAILED
            }
            show()
        }
    }

    private fun take(lyrics: LyricsState) {
        when (lyrics) {
            is LyricsState.Ready -> {
                lookupJob?.cancel()
                screenOffLookup.clear()
                leads = WidgetLines.leads(lyrics.lines)
                synced = lyrics.lyricsType != LyricsType.Static
                credit = LyricsFooter(
                    provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                    maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                    uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                ).miniCredit()?.text
                lookedUpPlain = false
                notice = if (leads.isEmpty()) NO_LYRICS else null
            }
            is LyricsState.Error -> if (leads.isEmpty()) notice = NO_LYRICS
            else -> Unit
        }
        shown = null
    }

    private fun clearSong(notice: String) {
        lookupJob?.cancel()
        screenOffLookup.clear()
        request = null
        title = ""
        artist = ""
        durationMs = 0L
        credit = null
        lookedUpPlain = false
        leads = emptyList()
        synced = false
        this.notice = notice
        shown = null
    }

    /** Takes the session's latest position report, as the lyrics screen does but without its corrections for seeks. */
    private fun report(session: MediaController, newSong: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val playback = session.playbackState
        if (playback == null || playback.position < 0L) {
            if (newSong) clock.reset(now)
            return
        }
        val playing = playback.state == PlaybackState.STATE_PLAYING
        val speed = LyricClock.playingSpeed(playing, playback.playbackSpeed)
        val relayed = session.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        val report = LyricClock.Report(playback.state, playing, playback.position, playback.lastPositionUpdateTime, speed, relayed)
        val fresh = clock.isFresh(report)
        if (newSong && !fresh) {
            // The report is the last song's: the new one starts from 0 until the player reports.
            clock.startTrack(now, speed, playing)
            awaitingReport = true
            return
        }
        if (!fresh) return
        clock.onReport(report, now, snap = newSong || awaitingReport)
        awaitingReport = false
    }

    /** Rebuilds the cover's pictures and colours when the player's cover changes; true when it did. */
    private fun refreshCover(metadata: MediaMetadata): Boolean {
        val picture = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        // Each read of the metadata brings the cover as a new bitmap, so it's compared by its pixels:
        // redoing the pictures each time would redraw the whole widget on every wake.
        if (picture === art || (picture != null && art?.let { runCatching { picture.sameAs(it) }.getOrDefault(false) } == true)) return false
        art = picture
        backdrop = null
        cover = null
        colors = null
        if (picture != null) runCatching { readCover(picture) }
        return true
    }

    /** The cover's colours (from it shrunk to [SAMPLE_PX]), a still of the background, and a rounded thumbnail. */
    private fun readCover(picture: Bitmap) {
        val hardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && picture.config == Bitmap.Config.HARDWARE
        val readable = if (hardware) picture.copy(Bitmap.Config.ARGB_8888, false) else picture
        val small = Bitmap.createScaledBitmap(readable, SAMPLE_PX, SAMPLE_PX, true)
        val pixels = IntArray(SAMPLE_PX * SAMPLE_PX)
        small.getPixels(pixels, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX)
        colors = CoverColors.of(pixels)
        backdrop = runCatching { KawarpSnapshot.render(readable) }.getOrNull()
        val thumb = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
        Canvas(thumb).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.shader = BitmapShader(
                Bitmap.createScaledBitmap(readable, COVER_PX, COVER_PX, true),
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )
            drawRoundRect(RectF(0f, 0f, COVER_PX.toFloat(), COVER_PX.toFloat()), COVER_RADIUS_PX, COVER_RADIUS_PX, paint)
        }
        cover = thumb
    }

    private fun positionMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val outputDelay = outputProfiles?.let { it.delayMs(it.currentRoute()) } ?: 0
        val songDelay = songDelays?.delayMs(request?.let { LocalLyricsStore.keyOf(it.title, it.artist) }) ?: 0
        val playerDelay = playerDelays?.delayMs(controller?.packageName) ?: 0
        return lyricPositionMs(clock.positionAt(now), outputDelay, songDelay, playerDelay)
    }

    /**
     * Sends the widget its lines if they changed, and the progress bar's step ([full]:
     * everything, cover and buttons too), then sleeps until the next line or step. While the
     * widget can't be seen ([visibility]) it sends nothing and doesn't wake; the next send is full.
     */
    private fun show(full: Boolean = false) {
        main.removeCallbacks(tick)
        val app = context ?: return
        val sendFull = visibility.send(full) ?: return
        val position = positionMs()
        val lines = notice?.let { WidgetLines.notice(it) }
            ?: if (synced) WidgetLines.at(leads, position) else WidgetLines.unsynced(leads)
        val playing = clock.isPlaying
        if (sendFull || lines != shown || (wantsProgress && playing)) {
            shown = lines
            val content = LyricsWidget.Content(
                title = title,
                artist = artist,
                lines = lines,
                playing = playing,
                positionMs = clock.positionAt(SystemClock.elapsedRealtime()),
                durationMs = durationMs,
                credit = credit.takeIf { notice == null },
                cover = cover,
                backdrop = backdrop,
                colors = colors,
                duet = leads.any { it.oppositeAligned },
            )
            last = content
            LyricsWidget.show(app, content, sendFull)
        }
        // Paused, every player's own callbacks wake it again ([follow]).
        if (!playing) return
        val speed = clock.anchor.speed.takeIf { it > 0f } ?: 1f
        val toLine = lines.changesAtMs?.let { ((it - position) / speed).toLong() }
        val wait = listOfNotNull(toLine, PROGRESS_STEP_MS.takeIf { wantsProgress }).minOrNull() ?: MAX_SLEEP_MS
        main.postDelayed(tick, wait.coerceIn(16L, MAX_SLEEP_MS))
    }
}
