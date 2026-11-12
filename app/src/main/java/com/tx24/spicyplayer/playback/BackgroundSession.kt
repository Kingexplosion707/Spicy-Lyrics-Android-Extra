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
    /** How far [ListeningSync] heard this song's audio from the player's reports, added to them until a seek. */
    private var transitionOffsetMs = 0L
    private var automixSongKey: String? = null
    private var automixRetries = 0
    /** The song [ListeningSync] is told about: the player's own Spotify track ID. */
    private var listeningId: String? = null
    private val automixResync = Runnable { automixResyncNow() }
    /** Every couple of seconds while playing, the clock is put back on the player ([LyricClock.resyncTo]). */
    private val periodicResync = Runnable { periodicResyncNow() }
    private var periodicResyncPosted = false

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

    /**
     * What the followed session last gave its callbacks, so a burst of updates (Spotify sends
     * dozens at a song change or seek) isn't a binder read of the metadata, cover and all, and of
     * the playback state for each.
     */
    private var heldMetadata: MediaMetadata? = null
    private var hasHeldMetadata = false
    private var heldPlayback: PlaybackState? = null
    private var hasHeldPlayback = false
    private var updateScheduled = false
    private var lastUpdateAt = 0L
    private val scheduledUpdate = Runnable {
        updateScheduled = false
        lastUpdateAt = SystemClock.elapsedRealtime()
        update()
    }

    /** Takes the session's update now, or once for all of a burst that arrive within [UPDATE_COALESCE_MS]. */
    private fun scheduleUpdate() {
        if (updateScheduled) return
        val since = SystemClock.elapsedRealtime() - lastUpdateAt
        if (since >= UPDATE_COALESCE_MS) {
            scheduledUpdate.run()
        } else {
            updateScheduled = true
            main.postDelayed(scheduledUpdate, UPDATE_COALESCE_MS - since)
        }
    }

    private fun forgetHeld() {
        main.removeCallbacks(scheduledUpdate)
        updateScheduled = false
        heldMetadata = null
        hasHeldMetadata = false
        heldPlayback = null
        hasHeldPlayback = false
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            heldMetadata = metadata
            hasHeldMetadata = true
            coverStale = true
            scheduleUpdate()
        }
        // The session observers below look at the sessions again when its state changes.
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            heldPlayback = state
            hasHeldPlayback = true
            scheduleUpdate()
        }
        override fun onSessionDestroyed() = refreshSessions()
    }

    fun start() {
        if (sessionManager != null) return
        ListeningSync.addListener(listeningListener)
        outputProfiles = AudioOutputProfiles(app)
        songDelays = SongDelays(app)
        playerDelays = PlayerDelays(app)
        connect()
    }

    fun stop() {
        ListeningSync.removeListener(listeningListener)
        listeningId = null
        main.removeCallbacks(sessionRecheck)
        main.removeCallbacks(automixResync)
        main.removeCallbacks(periodicResync)
        periodicResyncPosted = false
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        forgetHeld()
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
        forgetHeld()
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
                    // Which session to follow turns on whether each plays, not on where it is.
                    private var lastState = Int.MIN_VALUE
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        val code = state?.state ?: -1
                        if (code == lastState) return
                        lastState = code
                        refreshSessions()
                    }
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
            forgetHeld()
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
        if (session != null && !hasHeldMetadata) {
            heldMetadata = session.metadata
            hasHeldMetadata = true
        }
        val metadata = if (session == null) null else heldMetadata
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
        val spotifyId = metadata.sessionSpotifyId()
        if (spotifyId != listeningId) {
            listeningId = spotifyId
            ListeningSync.onSong(app, spotifyId)
        }
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

    /** A new song: nothing heard about it yet, and Spotify's is resynced a moment in ([AutomixResync]). */
    private fun onSongChange(session: MediaController, metadata: MediaMetadata) {
        transitionOffsetMs = 0L
        main.removeCallbacks(automixResync)
        automixSongKey = null
        automixRetries = 0
        scheduleAutomixResync(
            session,
            AutomixResync.songKey(
                session.packageName,
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            ),
        )
    }

    private fun scheduleAutomixResync(session: MediaController, songKey: String) {
        if (session.packageName != AutomixResync.PLAYER || !AutomixResync.enabled(app)) return
        automixSongKey = songKey
        main.removeCallbacks(automixResync)
        main.postDelayed(automixResync, AutomixResync.DELAY_MS)
    }

    /** A moment into a new Spotify song ([AutomixResync]): the player is sent to where the lyrics are. */
    private fun automixResyncNow() {
        val now = SystemClock.elapsedRealtime()
        val session = controller ?: return
        val key = automixSongKey ?: return
        if (songKeyOf(session) != key) return
        val playback = heldPlayback
        if (playback?.state != PlaybackState.STATE_PLAYING) {
            if (automixRetries++ < AutomixResync.MAX_RETRIES) main.postDelayed(automixResync, AutomixResync.RETRY_MS)
            return
        }
        if ((playback.actions and PlaybackState.ACTION_SEEK_TO) == 0L) return
        if (!AutomixResync.claim(key)) return
        val target = clock.positionAt(now) + AutomixResync.seekLeadMs(null)
        SyncTrace.add(now, "resync (background): seek to $target")
        transitionOffsetMs = 0L
        clock.seekTo(target, now)
        session.transportControls.seekTo(target)
    }

    private fun songKeyOf(session: MediaController) = AutomixResync.songKey(
        session.packageName,
        heldMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
        heldMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
    )

    /** [ListeningSync] heard where the audio is: the lyrics move there, the music is left alone. */
    private val listeningListener = object : ListeningSync.Listener {
        override fun onOffset(spotifyId: String, offsetMs: Long) {
            if (spotifyId != listeningId) return
            val delta = offsetMs - transitionOffsetMs
            if (delta == 0L) return
            val now = SystemClock.elapsedRealtime()
            transitionOffsetMs = offsetMs
            main.removeCallbacks(automixResync)
            clock.seekTo(clock.positionAt(now) + delta, now)
        }
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
    /** Reads the player again and puts the clock where it says, every couple of seconds while it plays. */
    private fun periodicResyncNow() {
        periodicResyncPosted = false
        val session = controller ?: return
        val playback = session.playbackState ?: return
        heldPlayback = playback
        hasHeldPlayback = true
        if (playback.state != PlaybackState.STATE_PLAYING) return
        ListeningSync.onReport(
            listeningId, playback.position, playback.lastPositionUpdateTime,
            LyricClock.playingSpeed(true, playback.playbackSpeed), true,
        )
        if (!awaitingReport) {
            val relayed = session.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
            val report = LyricClock.Report(
                playback.state, true, playback.position + transitionOffsetMs, playback.lastPositionUpdateTime,
                LyricClock.playingSpeed(true, playback.playbackSpeed), relayed,
            )
            clock.resyncTo(report, SystemClock.elapsedRealtime())
        }
        periodicResyncPosted = true
        main.postDelayed(periodicResync, LyricClock.RESYNC_EVERY_MS)
    }

    private fun report(session: MediaController, newSong: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!hasHeldPlayback) {
            heldPlayback = session.playbackState
            hasHeldPlayback = true
        }
        val playback = heldPlayback
        if (playback?.state == PlaybackState.STATE_PLAYING && !periodicResyncPosted) {
            periodicResyncPosted = true
            main.postDelayed(periodicResync, LyricClock.RESYNC_EVERY_MS)
        }
        if (playback == null || playback.position < 0L) {
            if (newSong) clock.reset(now)
            return
        }
        val playing = playback.state == PlaybackState.STATE_PLAYING
        val speed = LyricClock.playingSpeed(playing, playback.playbackSpeed)
        val relayed = session.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        // The report as the player gave it; a jump in it drops a heard offset before it's added below.
        ListeningSync.onReport(listeningId, playback.position, playback.lastPositionUpdateTime, speed, playing)
        val position = playback.position + if (playing) transitionOffsetMs else 0L
        val report = LyricClock.Report(playback.state, playing, position, playback.lastPositionUpdateTime, speed, relayed)
        val fresh = clock.isFresh(report)
        // The report is the last song's, and the new one starts from 0 until the player reports,
        // unless it's the new song's own start that came before the song change.
        val startedSong = newSong && !fresh && clock.startedSong(report, now)
        if (newSong && !fresh && !startedSong) {
            clock.startTrack(now, speed, playing)
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
        /** A burst of session updates is taken at most this often. */
        private const val UPDATE_COALESCE_MS = 40L
        /** How long a looked-up delay is used before it's looked up again (a new output, a changed setting). */
        private const val DELAYS_FRESH_MS = 1_000L
    }
}
