package com.tx24.spicyplayer.playback

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.network.data.LyricsLookupRequest

/**
 * Follows the playing song outside the lyrics screen, for the live wallpaper, the overlay, the
 * always-on display, the lyrics notification and the background haptics: the same session the
 * screen follows ([bestSession]) on a [LyricClock] of its own.
 *
 * Every one of them shares one follower ([SessionCore]): one set of player callbacks and one
 * clock however many are on, instead of each asking the system about every player change.
 * [start] joins it (and hears the song playing now at once), [stop] leaves it; the last to leave
 * ends it. Main thread only, apart from [positionMs].
 */
internal class BackgroundSession(context: Context) {
    /** A new song (or a new cover for it) to follow; null when nothing is playing. */
    var onTrack: ((Track?) -> Unit)? = null
    /** Playing or not, whenever it changes. */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val app = context.applicationContext
    @Volatile private var core: SessionCore? = null

    val isPlaying: Boolean get() = core?.isPlaying ?: false
    val track: Track? get() = core?.track
    val playerPackage: String? get() = core?.playerPackage

    /** Play or pause the followed player. */
    fun playPause() { core?.playPause() }
    /** Pause the followed player (nothing when it isn't playing). */
    fun pause() { core?.pause() }
    fun skipNext() { core?.skipNext() }
    fun skipPrevious() { core?.skipPrevious() }

    /** The player's position, held back by every delay, as the lyrics screen reads it. Any thread. */
    fun positionMs(): Long = core?.positionMs() ?: 0L

    fun start() {
        if (core != null) return
        val shared = SessionCore.obtain(app)
        core = shared
        shared.join(this)
    }

    fun stop() {
        val shared = core ?: return
        core = null
        shared.leave(this)
    }

    internal fun deliverTrack(track: Track?) { onTrack?.invoke(track) }
    internal fun deliverPlaying(playing: Boolean) { onPlayingChanged?.invoke(playing) }

    /** The song being followed, with what a lookup needs and the player's cover art. */
    data class Track(
        /** What lyrics are keyed on: title + artist only ([lyricsKey]). */
        val key: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val request: LyricsLookupRequest,
        val spotifyId: String?,
        val cover: Bitmap?,
    )
}

/**
 * The one follower every [BackgroundSession] shares ([obtain]): it listens to the players while
 * anyone has joined, and tells each of them about the song and playing.
 */
private class SessionCore(private val app: Context) {
    private val listeners = ArrayList<BackgroundSession>()

    /** Joins [listener]: the first starts following; a later one hears the song playing now. */
    fun join(listener: BackgroundSession) {
        if (listener in listeners) return
        listeners += listener
        // Not following (the first to join, or access was lost and may be back): connect, which
        // tells everyone. Otherwise only the newcomer needs to hear what plays.
        if (sessionManager == null) {
            start()
        } else {
            track?.let(listener::deliverTrack)
            listener.deliverPlaying(isPlaying)
        }
    }

    /** [listener] leaves; the last one out stops following. */
    fun leave(listener: BackgroundSession) {
        listeners -= listener
        if (listeners.isEmpty()) {
            stop()
            if (shared === this) shared = null
        }
    }

    private fun onTrack(track: BackgroundSession.Track?) = listeners.toList().forEach { it.deliverTrack(track) }
    private fun onPlayingChanged(playing: Boolean) = listeners.toList().forEach { it.deliverPlaying(playing) }

    private val main = Handler(Looper.getMainLooper())
    private val clock = LyricClock(SystemClock.elapsedRealtime())
    /** How long the followed session may stay stopped before another playing one takes over, as on the lyrics screen. */
    private val stickiness = SessionStickiness(SESSION_SWITCH_GRACE_MS)
    /** Looks at the sessions again once the followed one has been stopped for its grace. */
    private val sessionRecheck = Runnable { refreshSessions() }
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val observedSessions = HashMap<MediaSession.Token, MediaController>()
    private val observedCallbacks = HashMap<MediaSession.Token, MediaController.Callback>()
    /** A new song whose first report hasn't come yet: the clock takes it outright. */
    private var awaitingReport = false
    /**
     * The followed player's metadata changed (or it's a new player) since the cover was last read.
     * Every player's playback changes run [update] too, and reading and comparing a cover's pixels
     * each time costs far more than the update itself, so it's read only after this is set.
     */
    private var coverStale = true
    /** The crossfade lead for this song ([AutomixResync] has the Automix side), added to its reports until a seek. */
    private var transitionOffsetMs = 0L
    private var automixSongKey: String? = null
    private val automixResync = Runnable { automixResyncNow() }
    private var outputProfiles: AudioOutputProfiles? = null
    private var songDelays: SongDelays? = null
    private var playerDelays: PlayerDelays? = null

    val isPlaying: Boolean get() = clock.isPlaying

    var track: BackgroundSession.Track? = null
        private set

    val playerPackage: String? get() = controller?.packageName

    /** Play or pause the followed player. */
    fun playPause() {
        val session = controller ?: return
        val controls = session.transportControls
        if (session.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
    }

    fun pause() { controller?.transportControls?.pause() }
    fun skipNext() { controller?.transportControls?.skipToNext() }
    fun skipPrevious() { controller?.transportControls?.skipToPrevious() }

    /**
     * The player's position, held back by every delay, as the lyrics screen reads it. Read every
     * frame by what draws the lyrics, so the delays are looked up again only when the song or
     * player changes, or every [DELAYS_FRESH_MS]: finding the audio output asks the system, too
     * slow to do on every frame.
     */
    fun positionMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val song = track
        val player = controller
        if (song !== delaysTrack || player !== delaysPlayer || now - delaysAt >= DELAYS_FRESH_MS) {
            delaysTrack = song
            delaysPlayer = player
            delaysAt = now
            outputDelayMs = outputProfiles?.let { it.delayMs(it.currentRoute()) } ?: 0
            songDelayMs = songDelays?.delayMs(song?.let { LocalLyricsStore.keyOf(it.title, it.artist) }) ?: 0
            playerDelayMs = playerDelays?.delayMs(player?.packageName) ?: 0
        }
        return lyricPositionMs(clock.positionAt(now), outputDelayMs, songDelayMs, playerDelayMs)
    }

    // The delays as last looked up, for whom and when (see positionMs).
    @Volatile private var delaysTrack: BackgroundSession.Track? = null
    @Volatile private var delaysPlayer: MediaController? = null
    @Volatile private var delaysAt = Long.MIN_VALUE / 2
    @Volatile private var outputDelayMs = 0
    @Volatile private var songDelayMs = 0
    @Volatile private var playerDelayMs = 0

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            coverStale = true
            update()
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshSessions()
        override fun onSessionDestroyed() = refreshSessions()
    }

    fun start() {
        if (sessionManager != null) return
        outputProfiles = AudioOutputProfiles(app)
        songDelays = SongDelays(app)
        playerDelays = PlayerDelays(app)
        connect()
    }

    fun stop() {
        main.removeCallbacks(sessionRecheck)
        main.removeCallbacks(automixResync)
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        observedSessions.forEach { (token, session) -> observedCallbacks[token]?.let(session::unregisterCallback) }
        observedSessions.clear()
        observedCallbacks.clear()
        stickiness.reset()
        sessionManager = null
        controller = null
        clock.reset(SystemClock.elapsedRealtime())
        track = null
    }

    /** Listens for the players' sessions, or clears the song when access isn't granted. */
    private fun connect() {
        val manager = app.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(app, SessionAccessService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            sessionManager = manager
            follow(manager.getActiveSessions(listener))
        }.onFailure {
            // Access isn't granted: the app asks for it when opened.
            track = null
            onTrack(null)
        }
    }

    private fun refreshSessions() {
        val manager = sessionManager ?: return
        runCatching { manager.getActiveSessions(ComponentName(app, SessionAccessService::class.java)) }
            .onSuccess(::follow)
            .onFailure { lostAccess() }
    }

    /** Access was taken away: stop following, and connect again when it's back ([start]). */
    private fun lostAccess() {
        main.removeCallbacks(sessionRecheck)
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        sessionManager = null
        controller?.unregisterCallback(controllerCallback)
        controller = null
        observedSessions.forEach { (token, session) -> observedCallbacks[token]?.let(session::unregisterCallback) }
        observedSessions.clear()
        observedCallbacks.clear()
        stickiness.reset()
        track = null
        clock.reset(SystemClock.elapsedRealtime())
        onTrack(null)
        onPlayingChanged(false)
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
            coverStale = true
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
            if (track != null) {
                track = null
                onTrack(null)
            }
            clock.reset(SystemClock.elapsedRealtime())
            onPlayingChanged(false)
            return
        }
        val newSong = key != track?.key
        if (newSong) onSongChange(session, metadata)
        if (newSong) startSong(key, metadata)
        // Players may publish the duration after the title, or briefly omit it during
        // playback updates. Learn it when available without hiding an existing timeline.
        val reportedDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val current = track
        if (current != null && reportedDuration > 0L && reportedDuration != current.durationMs) {
            track = current.copy(durationMs = reportedDuration)
            onTrack(track)
        }
        // The cover is read again only when the metadata changed (see coverStale); a new bitmap
        // only replaces the old one when its pixels differ.
        val wasPlaying = clock.isPlaying
        val coverChanged = if (newSong || coverStale) {
            coverStale = false
            refreshCover(metadata)
        } else {
            false
        }
        report(session, newSong)
        if (coverChanged) onTrack(track)
        if (wasPlaying != clock.isPlaying) onPlayingChanged(clock.isPlaying)
    }

    /** Before the new song replaces the last: its crossfade lead, and Spotify's Automix resync. */
    private fun onSongChange(session: MediaController, metadata: MediaMetadata) {
        val now = SystemClock.elapsedRealtime()
        val crossfadeMs = app.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt(CROSSFADE_PREF, 0) * 1_000L
        val lastDuration = track?.durationMs ?: 0L
        val endedOnItsOwn = lastDuration > 0L && clock.lastSongPositionAt(now) >= lastDuration - crossfadeMs - 2_500L
        transitionOffsetMs = if (crossfadeMs > 0L && endedOnItsOwn) crossfadeMs else 0L
        main.removeCallbacks(automixResync)
        automixSongKey = null
        if (transitionOffsetMs == 0L && session.packageName == AutomixResync.PLAYER && AutomixResync.enabled(app)) {
            automixSongKey = AutomixResync.songKey(
                session.packageName,
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            )
            main.postDelayed(automixResync, AutomixResync.DELAY_MS)
        }
    }

    /** Sends the player to where the lyrics are, once per song across the app ([AutomixResync]). */
    private fun automixResyncNow() {
        val session = controller ?: return
        val key = automixSongKey ?: return
        val playback = session.playbackState ?: return
        if (playback.state != PlaybackState.STATE_PLAYING || (playback.actions and PlaybackState.ACTION_SEEK_TO) == 0L) return
        val current = AutomixResync.songKey(
            session.packageName,
            session.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            session.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        )
        if (current != key || !AutomixResync.claim(key)) return
        val now = SystemClock.elapsedRealtime()
        val target = clock.positionAt(now)
        transitionOffsetMs = 0L
        clock.seekTo(target, now)
        session.transportControls.seekTo(target)
    }

    private fun startSong(key: String, metadata: MediaMetadata) {
        val manual = metadata.savedSpotifyId(app.getSharedPreferences("spotify_id_overrides", 0))
        val request = metadata.lookupRequest(manual ?: metadata.sessionSpotifyId())
        track = BackgroundSession.Track(
            key = key,
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
            request = request,
            spotifyId = manual ?: metadata.sessionSpotifyId(),
            cover = null,
        )
        onTrack(track)
    }

    /** Rebuilds the track's cover when the player's cover changes; true when it did. */
    private fun refreshCover(metadata: MediaMetadata): Boolean {
        val current = track ?: return false
        val picture = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        // Each read of the metadata brings the cover as a new bitmap, so it's compared by its pixels.
        if (picture === current.cover ||
            (picture != null && current.cover?.let { runCatching { picture.sameAs(it) }.getOrDefault(false) } == true)
        ) return false
        track = current.copy(cover = picture)
        return true
    }

    /** Takes the session's latest position report, as the widget does but without its corrections for seeks. */
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
        val position = playback.position + if (playing) transitionOffsetMs else 0L
        val report = LyricClock.Report(playback.state, playing, position, playback.lastPositionUpdateTime, speed, relayed)
        val fresh = clock.isFresh(report)
        // The report is the last song's, and the new one starts from 0 until the player reports,
        // unless it's the new song's own start that came before the song change.
        val startedSong = newSong && !fresh && clock.startedSong(report, now)
        if (newSong && !fresh && !startedSong) {
            clock.startTrack(now, speed, playing)
            if (transitionOffsetMs > 0L) clock.seekTo(transitionOffsetMs, now)
            awaitingReport = true
            return
        }
        if (!fresh && !startedSong) return
        clock.onReport(report, now, snap = newSong || awaitingReport)
        awaitingReport = false
    }

    companion object {
        /** The follower while anyone has joined (it holds only the application context). Main thread. */
        @android.annotation.SuppressLint("StaticFieldLeak")
        private var shared: SessionCore? = null

        fun obtain(context: Context): SessionCore =
            shared ?: SessionCore(context.applicationContext).also { shared = it }

        /** How long the followed session may stay stopped before another playing one takes over. */
        private const val SESSION_SWITCH_GRACE_MS = 2_000L
        /** How long a looked-up delay is used before it's looked up again (a new output, a changed setting). */
        private const val DELAYS_FRESH_MS = 1_000L
    }
}
