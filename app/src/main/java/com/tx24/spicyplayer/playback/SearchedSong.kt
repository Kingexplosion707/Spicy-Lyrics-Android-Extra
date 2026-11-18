package com.tx24.spicyplayer.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.TrackNameCleaner
import com.tx24.spicyplayer.widget.BackgroundLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lyrics search: a song found by its title and artist, followed with no player at all. Its
 * lyrics are looked up first (the usual order: the Lyrics Manager, the source chosen for it, the
 * cache, the sources), and timed lyrics give it a length; then it is published as a media
 * session of the app's own that plays on its own timer ([SearchClock]), so the lyrics screen,
 * the wallpaper, the overlay, the always-on display, the widget and the island follow it like
 * any player, with play, pause, the timeline and Previous (start again).
 *
 * While it is up, every session picker prefers it ([bestSession]). It gives way when a player
 * starts a song of its own; a song a player was already playing when the search began doesn't
 * replace it.
 */
internal object SearchedSong {
    sealed interface Outcome {
        /** Found with timing: it plays. */
        data object Playing : Outcome
        /** Found unsynced: it shows, without a timer to run. */
        data object Untimed : Outcome
        data object NotFound : Outcome
        data object Failed : Outcome
    }

    private const val MEDIA_ID_PREFIX = "spicy:search:"

    /** The searched song's session while it is up; [bestSession] picks it over every player. */
    @Volatile var token: MediaSession.Token? = null
        private set

    private val main by lazy { Handler(Looper.getMainLooper()) }

    // Everything below is touched on the main thread only.
    private var session: MediaSession? = null
    private var clock = SearchClock(0L)
    private var title = ""
    private var artist = ""
    /** The players' songs that were playing when the search began, which don't replace it. */
    private var held: Set<String> = emptySet()
    private var manager: MediaSessionManager? = null
    private val watched = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { watch(it.orEmpty()) }
    private val end = Runnable {
        val media = session ?: return@Runnable
        clock = clock.ended(SystemClock.elapsedRealtime())
        publish(media)
    }

    /** Finds [title] by [artist]'s lyrics and, when there are any, plays the song on its own timer. */
    suspend fun search(context: Context, title: String, artist: String): Outcome {
        val app = context.applicationContext
        val names = TrackNameCleaner.clean(title = title.trim(), artist = artist.trim())
        val request = LyricsLookupRequest(artist = names.artist, title = names.title, album = "", durationSeconds = 0)
        val result = runCatching { BackgroundLyrics(app).lookUp(request, romanize = false) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrElse { return Outcome.Failed }
        return when (result) {
            is BackgroundLyrics.Result.Found -> {
                val length = SearchClock.lengthOf(result.lyrics)
                withContext(Dispatchers.Main.immediate) { start(app, title.trim(), artist.trim(), length) }
                if (length > 0L) Outcome.Playing else Outcome.Untimed
            }
            BackgroundLyrics.Result.None -> Outcome.NotFound
            BackgroundLyrics.Result.Failed -> Outcome.Failed
        }
    }

    /** Takes the searched song down, as when a player takes over. */
    fun stop() {
        main.post { retire() }
    }

    // ---- the session (main thread) ----

    private fun start(app: Context, title: String, artist: String, lengthMs: Long) {
        retire()
        this.title = title
        this.artist = artist
        val now = SystemClock.elapsedRealtime()
        clock = SearchClock(lengthMs, atMs = now).play(now)
        val sessions = activeSessions(app)
        held = sessions.filter(::playing).mapTo(HashSet(), ::songOf)
        val media = MediaSession(app, "SpicyLyricsSearch")
        media.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = command { clock.play(it) }
            override fun onPause() = command { clock.pause(it) }
            override fun onSeekTo(pos: Long) = command { clock.seek(pos, it) }
            override fun onSkipToPrevious() = command { clock.seek(0L, it) }
            override fun onStop() = retire()
        })
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
            media.setSessionActivity(
                PendingIntent.getActivity(app, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        media.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "$MEDIA_ID_PREFIX$title\u001f$artist")
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, lengthMs)
                .build(),
        )
        session = media
        token = media.sessionToken
        publish(media)
        // Active last: the pickers look again as it appears, and find it ready.
        media.isActive = true
        startWatching(app)
    }

    private fun command(change: (Long) -> SearchClock) {
        val media = session ?: return
        clock = change(SystemClock.elapsedRealtime())
        publish(media)
    }

    private fun publish(media: MediaSession) {
        val now = SystemClock.elapsedRealtime()
        media.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (clock.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    clock.positionAt(now),
                    if (clock.playing) 1f else 0f,
                    now,
                )
                .build(),
        )
        main.removeCallbacks(end)
        clock.remainingMs(now)?.let { main.postDelayed(end, it + 50L) }
    }

    private fun retire() {
        main.removeCallbacks(end)
        stopWatching()
        val media = session ?: return
        session = null
        token = null
        held = emptySet()
        runCatching {
            media.isActive = false
            media.release()
        }
    }

    // ---- giving way to a player ----

    private fun activeSessions(app: Context): List<MediaController> = runCatching {
        app.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(app, SessionAccessService::class.java))
    }.getOrDefault(emptyList())

    private fun startWatching(app: Context) {
        val sessions = app.getSystemService(MediaSessionManager::class.java) ?: return
        runCatching {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, ComponentName(app, SessionAccessService::class.java), main)
            manager = sessions
        }
        watch(activeSessions(app))
    }

    private fun stopWatching() {
        manager?.let { runCatching { it.removeOnActiveSessionsChangedListener(sessionsChanged) } }
        manager = null
        watched.values.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        watched.clear()
    }

    private fun watch(sessions: List<MediaController>) {
        if (session == null) return
        val own = token
        val players = sessions.filter { it.sessionToken != own }
        val tokens = players.mapTo(HashSet()) { it.sessionToken }
        (watched.keys - tokens).forEach { gone ->
            watched.remove(gone)?.let { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        }
        players.forEach { controller ->
            if (controller.sessionToken in watched) return@forEach
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = takeOverIf(controller)
                override fun onMetadataChanged(metadata: MediaMetadata?) = takeOverIf(controller)
            }
            runCatching { controller.registerCallback(callback, main) }
            watched[controller.sessionToken] = controller to callback
        }
        players.forEach(::takeOverIf)
    }

    /** A player playing a song of its own takes over. */
    private fun takeOverIf(controller: MediaController) {
        if (session == null || !playing(controller)) return
        if (songOf(controller) in held) return
        retire()
    }

    private fun playing(controller: MediaController): Boolean =
        controller.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun songOf(controller: MediaController): String =
        controller.packageName + "\u001f" + controller.metadata.lyricsKey().orEmpty()
}
