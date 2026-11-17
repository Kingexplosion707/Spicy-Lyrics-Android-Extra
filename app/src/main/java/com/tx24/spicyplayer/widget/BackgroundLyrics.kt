package com.tx24.spicyplayer.widget

import android.content.Context
import android.os.SystemClock
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.lyrics.LyricsFolder
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.NextLyricsBackend
import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSelection
import com.tx24.spicyplayer.playback.RapidSwitchGate
import com.tx24.spicyplayer.playback.savedLyricsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the playing song's lyrics for the widget while the lyrics screen is closed, in the
 * screen's order: the Lyrics Manager (a saved song, then the lyrics folder), the source chosen for
 * the song, the disk cache, then the sources. What the sources answer goes into the same disk
 * cache, so the screen opens on it. Only the screen's in-memory picks (a just-once upload) are
 * left out; while the screen is up it hands the widget its own pick instead.
 */
internal class BackgroundLyrics(context: Context) {
    sealed interface Result {
        /** [sourceId]: the source that answered, as the lyrics screen keys its translations on it. */
        data class Found(val lyrics: LyricsState.Ready, val sourceId: String = "") : Result
        data object None : Result
        data object Failed : Result
    }

    private val context = context.applicationContext
    private val gate = RapidSwitchGate(windowMs = 1_000L, backoffMs = 400L)

    /**
     * [romanize]: romanize on the device too (only when a widget shows it: it loads a large dictionary for Japanese).
     * [manualSpotifyId]: the Spotify ID the user set for the song, whose picks are stored apart.
     */
    suspend fun lookUp(request: LyricsLookupRequest, romanize: Boolean, manualSpotifyId: String? = null): Result {
        // Found and rendered ahead from the queue ([prepareAhead]): up at once, with no work at the song change.
        if (manualSpotifyId == null) {
            synchronized(shared) { shared.ahead[aheadKey(request, romanize)] }
                ?.takeIf { (_, seconds) -> seconds == 0 || request.durationSeconds == 0 || kotlin.math.abs(seconds - request.durationSeconds) <= 3 }
                ?.let { return it.first }
        }
        // The wallpaper, overlay, always-on display, notification, haptics and widget often ask
        // for the same song at once: they share one lookup, and its answer for a few minutes.
        val key = "${request.title}\u0000${request.artist}\u0000${request.album}\u0000${request.durationSeconds}\u0000$romanize\u0000$manualSpotifyId"
        val job = synchronized(shared) {
            shared.recent[key]?.takeIf { !it.isCancelled && (!it.isCompleted || SystemClock.elapsedRealtime() - (shared.at[key] ?: 0L) < SHARED_FOR_MS) }
                ?: shared.scope.async {
                    lookUpNow(request, romanize, manualSpotifyId).also { result ->
                        // A failure (offline, a source down) isn't kept: the next ask tries again.
                        if (result is Result.Failed) synchronized(shared) { shared.recent.remove(key); shared.at.remove(key) }
                    }
                }.also {
                    shared.recent[key] = it
                    shared.at[key] = SystemClock.elapsedRealtime()
                    while (shared.recent.size > SHARED_MAX) {
                        val oldest = shared.recent.keys.first()
                        shared.recent.remove(oldest)
                        shared.at.remove(oldest)
                    }
                }
        }
        return job.await()
    }

    /**
     * Finds and renders the songs coming up next ([upcoming], from the player's queue) one at a
     * time while the current song plays, so each is ready the moment it starts: the song change
     * then costs no lookup and no parsing on the phone. A new call stops the last one.
     */
    fun prepareAhead(upcoming: List<LyricsLookupRequest>, romanize: Boolean) {
        synchronized(shared) {
            shared.aheadJob?.cancel()
            if (upcoming.isEmpty()) return
            shared.aheadJob = shared.scope.launch {
                // Out of the current song's way: its lookup and first frames come first.
                delay(AHEAD_START_DELAY_MS)
                for (request in upcoming) {
                    val key = aheadKey(request, romanize)
                    if (synchronized(shared) { shared.ahead.containsKey(key) }) continue
                    val result = runCatching { lookUpNow(request, romanize, null) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        .getOrNull() as? Result.Found ?: continue
                    synchronized(shared) {
                        shared.ahead[key] = result to request.durationSeconds
                        while (shared.ahead.size > AHEAD_MAX) shared.ahead.remove(shared.ahead.keys.first())
                    }
                }
            }
        }
    }

    private fun aheadKey(request: LyricsLookupRequest, romanize: Boolean) =
        "${request.title.trim().lowercase()}\u0000${request.artist.trim().lowercase()}\u0000$romanize"

    private suspend fun lookUpNow(request: LyricsLookupRequest, romanize: Boolean, manualSpotifyId: String?): Result = withContext(Dispatchers.IO) {
        val folder = LyricsFolder(context, File(context.filesDir, "lyrics-folder-index.json"))
        val store = LocalLyricsStore(File(context.filesDir, "local-lyrics"))
        val writtenRomaji = context.getSharedPreferences("lyrics_sources", 0).getBoolean("writtenRomaji", true)
        savedLyricsFor(request, store, folder)?.let { return@withContext rendered(it, request, splitBackgroundVocals = true, romanize = romanize, writtenRomaji = writtenRomaji) }
        val backend = backend()
        val known = ConcurrentHashMap<String, ProviderResult>()
        context.getSharedPreferences("source_overrides", 0)
            .getString(LocalLyricsStore.keyOf(request.title, request.artist), null)
            ?.let { chosen ->
                val found = backend.resolveFrom(request, chosen, known) as? RemoteLyricsResolution.Found
                if (found?.selection?.source?.id == chosen) return@withContext rendered(found.selection, request, backend.splitBackgroundVocals, romanize, writtenRomaji)
            }
        backend.cachedResolution(request, manualSpotifyId)?.takeIf { it.settled }?.let { cached ->
            val selection = (cached.resolution as? RemoteLyricsResolution.Found)?.selection ?: return@withContext Result.None
            return@withContext rendered(selection, request, backend.splitBackgroundVocals, romanize, writtenRomaji)
        }
        delay(gate.waitBeforeNetwork(SystemClock.elapsedRealtime()))
        val resolution = backend.resolve(request, known)
        backend.store(request, resolution, manualSpotifyId)
        val selection = (resolution as? RemoteLyricsResolution.Found)?.selection
            ?: return@withContext if (resolution is RemoteLyricsResolution.NotFound) Result.None else Result.Failed
        rendered(selection, request, backend.splitBackgroundVocals, romanize, writtenRomaji)
    }

    private fun rendered(selection: RemoteLyricsSelection, request: LyricsLookupRequest, splitBackgroundVocals: Boolean, romanize: Boolean, writtenRomaji: Boolean): Result =
        runCatching<Result> { Result.Found(RemoteLyricsAdapter.render(selection, request.durationSeconds * 1_000L, splitBackgroundVocals, romanize, writtenRomaji), selection.source.id) }
            .getOrElse { Result.Failed }

    /** Genius's human translation of the song into [target], for the translations. */
    suspend fun humanTranslation(title: String, artist: String, target: String) =
        backend().humanTranslation(title, artist, target)

    /** The one backend for everything outside the lyrics screen: rebuilt only when the key changes. */
    private fun backend(): NextLyricsBackend {
        val key = context.getSharedPreferences("spicy_lyrics_key", 0).getString("key", null).orEmpty()
            .ifBlank { BuildConfig.SPICY_LYRICS_CLIENT_KEY }
        synchronized(shared) {
            shared.backend?.takeIf { it.first == key }?.let { return it.second }
            return NextLyricsBackend(context.applicationContext, key).also { shared.backend = key to it }
        }
    }

    /** What every BackgroundLyrics in the process shares: the backend and recent lookups. */
    private object Shared {
        var backend: Pair<String, NextLyricsBackend>? = null
        val recent = LinkedHashMap<String, kotlinx.coroutines.Deferred<Result>>()
        val at = HashMap<String, Long>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        /** Songs from the queue, found and rendered ahead, with their length in seconds. */
        val ahead = LinkedHashMap<String, Pair<Result.Found, Int>>()
        var aheadJob: kotlinx.coroutines.Job? = null
    }

    private companion object {
        val shared = Shared
        /** A finished lookup is shared this long, then asked afresh. */
        const val SHARED_FOR_MS = 5 * 60_000L
        const val SHARED_MAX = 12
        const val AHEAD_MAX = 4
        const val AHEAD_START_DELAY_MS = 8_000L
    }
}
