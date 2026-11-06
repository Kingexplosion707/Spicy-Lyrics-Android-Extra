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
 * Follows the playing song outside the lyrics screen, for the live wallpaper and the background
 * haptics: the same session the screen follows ([bestSession]) on a [LyricClock] of its own.
 * Main thread only, mirroring [com.tx24.spicyplayer.widget.NowPlayingHolder]'s session handling
 * without its widget work.
 */
internal class BackgroundSession(context: Context) {
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

    /** A new song (or a new cover for it) to follow; null when nothing is playing. */
    var onTrack: ((Track?) -> Unit)? = null
    /** Playing or not, whenever it changes. */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val app = context.applicationContext
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
    private var outputProfiles: AudioOutputProfiles? = null
    private var songDelays: SongDelays? = null
    private var playerDelays: PlayerDelays? = null

    val isPlaying: Boolean get() = clock.isPlaying

    var track: Track? = null
        private set

    val playerPackage: String? get() = controller?.packageName

    /** Play or pause the followed player. */
    fun playPause() {
        val session = controller ?: return
        val controls = session.transportControls
        if (session.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
    }

    fun skipNext() { controller?.transportControls?.skipToNext() }
    fun skipPrevious() { controller?.transportControls?.skipToPrevious() }

    /** The player's position, held back by every delay, as the lyrics screen reads it. */
    fun positionMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val outputDelay = outputProfiles?.let { it.delayMs(it.currentRoute()) } ?: 0
        val songDelay = songDelays?.delayMs(track?.let { LocalLyricsStore.keyOf(it.title, it.artist) }) ?: 0
        val playerDelay = playerDelays?.delayMs(controller?.packageName) ?: 0
        return lyricPositionMs(clock.positionAt(now), outputDelay, songDelay, playerDelay)
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
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
            onTrack?.invoke(null)
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
        onTrack?.invoke(null)
        onPlayingChanged?.invoke(false)
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
            if (track != null) {
                track = null
                onTrack?.invoke(null)
            }
            clock.reset(SystemClock.elapsedRealtime())
            onPlayingChanged?.invoke(false)
            return
        }
        val newSong = key != track?.key
        if (newSong) startSong(key, metadata)
        // Players may publish the duration after the title, or briefly omit it during
        // playback updates. Learn it when available without hiding an existing timeline.
        val reportedDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val current = track
        if (current != null && reportedDuration > 0L && reportedDuration != current.durationMs) {
            track = current.copy(durationMs = reportedDuration)
            onTrack?.invoke(track)
        }
        // With the screen off the cover waits: it's read again once the screen is on. Here the
        // cover is read on every update; a new bitmap only replaces the old one when its pixels differ.
        val wasPlaying = clock.isPlaying
        val coverChanged = refreshCover(metadata)
        report(session, newSong)
        if (coverChanged) onTrack?.invoke(track)
        if (wasPlaying != clock.isPlaying) onPlayingChanged?.invoke(clock.isPlaying)
    }

    private fun startSong(key: String, metadata: MediaMetadata) {
        val manual = metadata.savedSpotifyId(app.getSharedPreferences("spotify_id_overrides", 0))
        val request = metadata.lookupRequest(manual ?: metadata.sessionSpotifyId())
        track = Track(
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
        onTrack?.invoke(track)
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

    companion object {
        /** How long the followed session may stay stopped before another playing one takes over. */
        private const val SESSION_SWITCH_GRACE_MS = 2_000L
    }
}
