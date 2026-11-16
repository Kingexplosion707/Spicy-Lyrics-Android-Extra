package com.tx24.spicyplayer.spotify

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import com.tx24.spicyplayer.SpicyLyricsApplication
import com.tx24.spicyplayer.network.data.spotify.ConnectClock
import com.tx24.spicyplayer.network.data.spotify.ConnectPlayback
import com.tx24.spicyplayer.network.data.spotify.ConnectPolling
import com.tx24.spicyplayer.network.data.spotify.ConnectState
import com.tx24.spicyplayer.playback.SessionAccessService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Follows the signed-in Spotify account when the Spotify app isn't what's playing: on another
 * device, or on this phone with its app closed. Spotify's Web API says what the account plays
 * ([ConnectPlayback]); this turns that into a media session of Spicy's own, so everything that
 * follows players (the lyrics screen, the widget, the overlay, the island, other apps' shades)
 * sees the song, its position and its controls without knowing where they came from.
 *
 * It stays out of the way: while Spotify's own session on this phone plays, nothing is
 * published; with the screen off it asks only now and then; and it asks nothing while nothing
 * needs it (not signed in, switched off, no screen or listener access).
 */
internal object SpotifyConnectSession {
    private const val SPOTIFY = "com.spotify.music"
    private const val API = "https://api.spotify.com/v1"
    private const val PREFS = "ui"
    private const val SETTING = "followSpotifyAccount"
    /** A position this far behind the one a running song reached is a pause that was noticed late. */
    private const val LATE_PAUSE_MS = 150L

    private val main = Handler(Looper.getMainLooper())
    private val nudges = Channel<Unit>(Channel.CONFLATED)
    private val clock = ConnectClock()

    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var job: Job? = null

    /** The listener service is bound: the process stays alive, so following can go on without a screen. */
    @Volatile var accessConnected = false

    // Everything below is touched on the main thread only.
    private var session: MediaSession? = null
    private var current: ConnectState? = null
    private var cover: Bitmap? = null
    private var coverFor: String? = null
    private var publishedPlaying = false
    private var warned = false
    private var appRef: Context? = null

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SETTING, true)

    /** Starts or stops following to match what's wanted now; called whenever any of that may have changed. */
    @Synchronized
    fun sync(context: Context) {
        val app = context.applicationContext
        appRef = app
        val wanted = enabled(app) && SpotifyAccount.signedIn(app) && (SpicyLyricsApplication.visible || accessConnected)
        if (wanted && job?.isActive != true) {
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = newScope
            job = newScope.launch { follow(app) }
        } else if (!wanted && job != null) {
            job?.cancel()
            job = null
            scope = null
            main.post { retire() }
        } else if (wanted) {
            nudge()
        }
    }

    /** Ask again now (a screen came on, or a command was sent). */
    fun nudge() { nudges.trySend(Unit) }

    private sealed interface Fetch {
        data class Playing(val state: ConnectState, val atMs: Long) : Fetch
        data object Nothing : Fetch
        data class Limited(val waitMs: Long) : Fetch
        data object Failed : Fetch
    }

    private suspend fun follow(app: Context) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val interactive = app.getSystemService(PowerManager::class.java)?.isInteractive != false
            val visible = SpicyLyricsApplication.visible
            var wait: Long
            if (spotifyAppPlaying(app)) {
                main.post { retire() }
                wait = 8_000L
            } else {
                when (val result = fetch(app)) {
                    is Fetch.Playing -> {
                        failures = 0
                        withContext(Dispatchers.Main.immediate) { apply(app, result.state, result.atMs) }
                        val position = clock.positionAt(SystemClock.elapsedRealtime(), result.state.durationMs)
                        wait = ConnectPolling.intervalMs(result.state, position, visible)
                    }
                    Fetch.Nothing -> {
                        failures = 0
                        withContext(Dispatchers.Main.immediate) { retire() }
                        wait = ConnectPolling.intervalMs(null, 0L, visible)
                    }
                    is Fetch.Limited -> wait = result.waitMs
                    Fetch.Failed -> {
                        failures++
                        wait = (10_000L * failures).coerceAtMost(60_000L)
                    }
                }
                // A phone in the pocket needs the song only roughly: the clock runs by itself in between.
                if (!interactive) wait = wait.coerceAtLeast(30_000L)
            }
            withTimeoutOrNull(wait) { nudges.receive() }
        }
    }

    /** Whether Spotify's own app on this phone has a session that plays: then it is the source, not the account. */
    private fun spotifyAppPlaying(app: Context): Boolean = runCatching {
        val manager = app.getSystemService(MediaSessionManager::class.java)
        manager.getActiveSessions(ComponentName(app, SessionAccessService::class.java)).any {
            it.packageName == SPOTIFY && when (it.playbackState?.state) {
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> true
                else -> false
            }
        }
    }.getOrDefault(false)

    private suspend fun fetch(app: Context): Fetch = withContext(Dispatchers.IO) {
        val token = SpotifyAccount.accessToken(app) ?: return@withContext Fetch.Nothing
        val request = Request.Builder().url("$API/me/player").header("Authorization", "Bearer $token").build()
        val sent = SystemClock.elapsedRealtime()
        runCatching {
            SpotifyAccount.client.newCall(request).execute().use { response ->
                val received = SystemClock.elapsedRealtime()
                when {
                    response.code == 204 -> Fetch.Nothing
                    response.code == 429 -> Fetch.Limited(ConnectPolling.retryAfterMs(response.header("Retry-After")))
                    response.code == 401 -> {
                        SpotifyAccount.expireAccessToken(app)
                        Fetch.Failed
                    }
                    !response.isSuccessful -> Fetch.Failed
                    else -> ConnectPlayback.parse(response.body?.string().orEmpty())
                        // The answer is as old as half the trip: that is where its progress belongs.
                        ?.let { Fetch.Playing(it, (sent + received) / 2) } ?: Fetch.Nothing
                }
            }
        }.getOrDefault(Fetch.Failed)
    }

    // ---- the session (main thread) ----

    private fun apply(app: Context, state: ConnectState, atMs: Long) {
        val newSong = !state.sameSong(current)
        val moved = clock.accept(state, atMs)
        current = state
        val media = session ?: create(app)
        if (newSong) {
            cover = null
            coverFor = state.trackId
            media.setMetadata(metadata(state))
            loadCover(app, state)
        }
        if (newSong || moved) publish(media, state)
    }

    private fun create(app: Context): MediaSession {
        val media = MediaSession(app, "SpicyLyricsSpotify")
        media.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = command(app, { clock.setPlaying(true, SystemClock.elapsedRealtime()) }) { put(app, "play", it) }
            override fun onPause() = command(app, { clock.setPlaying(false, SystemClock.elapsedRealtime()) }) { put(app, "pause", it) }
            override fun onSkipToNext() = command(app, {}) { post(app, "next") }
            override fun onSkipToPrevious() = command(app, {}) { post(app, "previous") }
            override fun onSeekTo(pos: Long) = command(app, { clock.jump(pos, SystemClock.elapsedRealtime()) }) {
                put(app, "seek", it, "position_ms=${pos.coerceAtLeast(0L)}")
            }
        })
        media.setSessionActivity(openSpotify(app))
        media.isActive = true
        session = media
        publishedPlaying = false
        return media
    }

    /**
     * A command from the session's controls (main thread): [local] applies it to the clock at
     * once, since the song shouldn't wait on the network, and [send] tells Spotify.
     */
    private fun command(app: Context, local: () -> Unit, send: suspend (ConnectState?) -> Unit) {
        val state = current
        if (!SpotifyAccount.canControl(app)) {
            warn(app, "Sign in to Spotify again in Settings → Sources to control playback from here")
            return
        }
        local()
        val media = session
        if (state != null && media != null) publish(media, state)
        scope?.launch {
            send(state)
            // Spotify takes a moment to report what it did.
            kotlinx.coroutines.delay(700L)
            nudge()
        }
    }

    private fun publish(media: MediaSession, state: ConnectState) {
        val now = SystemClock.elapsedRealtime()
        val position = clock.positionAt(now, state.durationMs)
        val playing = clock.isPlaying()
        val canControl = appRef?.let(SpotifyAccount::canControl) == true
        fun report(playing: Boolean, position: Long) = PlaybackState.Builder()
            .setActions(actions(canControl))
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                position,
                if (playing) 1f else 0f,
                SystemClock.elapsedRealtime(),
            ).build()
        val lastRan = publishedPlaying
        publishedPlaying = playing
        if (!playing && lastRan) {
            // A pause noticed late names a position behind the clock, which the clock would take for
            // a player's stale pause report and not believe. Say where the song really is while it
            // plays, then pause it there.
            media.setPlaybackState(report(true, position))
            main.postDelayed({ if (session === media) media.setPlaybackState(report(false, position)) }, LATE_PAUSE_MS)
        } else {
            media.setPlaybackState(report(playing, position))
        }
    }

    private fun actions(canControl: Boolean): Long =
        if (canControl) {
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
        } else {
            0L
        }

    private fun metadata(state: ConnectState): MediaMetadata =
        MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "spotify:track:${state.trackId}")
            .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, state.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, state.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, state.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
            .apply {
                state.coverUrl?.let { putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, it) }
                cover?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
            }
            .build()

    /** The cover as a picture, which the players that read one need; the song shows meanwhile without it. */
    private fun loadCover(app: Context, state: ConnectState) {
        val url = state.coverUrl ?: return
        scope?.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                SpotifyAccount.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.byteStream()?.let(BitmapFactory::decodeStream)
                }
            }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                // Only if it's still this song's.
                if (coverFor == state.trackId && current?.trackId == state.trackId) {
                    cover = bitmap
                    session?.setMetadata(metadata(current ?: state))
                }
            }
        }
    }

    /** Takes the session down: nothing is playing, Spotify's own app is, or following has stopped. */
    private fun retire() {
        val media = session ?: return
        session = null
        current = null
        cover = null
        coverFor = null
        publishedPlaying = false
        clock.reset()
        runCatching {
            media.isActive = false
            media.release()
        }
    }

    // ---- commands to Spotify ----

    private suspend fun put(app: Context, action: String, state: ConnectState?, query: String = "") =
        send(app, "PUT", action, state, query)

    private suspend fun post(app: Context, action: String) = send(app, "POST", action, null, "")

    private suspend fun send(app: Context, method: String, action: String, state: ConnectState?, query: String) {
        withContext(Dispatchers.IO) {
            val token = SpotifyAccount.accessToken(app) ?: return@withContext
            val params = listOfNotNull(
                query.takeIf { it.isNotBlank() },
                state?.deviceId?.let { "device_id=${Uri.encode(it)}" },
            ).joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
            val request = Request.Builder()
                .url("$API/me/player/$action$params")
                .header("Authorization", "Bearer $token")
                .method(method, ByteArray(0).toRequestBody())
                .build()
            runCatching {
                SpotifyAccount.client.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> Unit
                        403 -> warn(app, "Spotify only lets Premium accounts control playback from other apps")
                        404 -> warn(app, "Spotify has no active device to control")
                        else -> Unit
                    }
                }
            }
        }
    }

    /** Says why a command did nothing, once a session: it's usually a setting of the account, not a fault. */
    private fun warn(app: Context, message: String) {
        main.post {
            if (warned) return@post
            warned = true
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSpotify(app: Context): PendingIntent {
        val intent = app.packageManager.getLaunchIntentForPackage(SPOTIFY)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
        return PendingIntent.getActivity(
            app, 0, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
