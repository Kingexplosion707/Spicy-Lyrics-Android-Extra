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
        data class Found(val lyrics: LyricsState.Ready) : Result
        data object None : Result
        data object Failed : Result
    }

    private val context = context.applicationContext
    private val gate = RapidSwitchGate(windowMs = 1_000L, backoffMs = 400L)
    /** The backend, with the Spicy Lyrics key it was built for: rebuilt when the key changes. */
    private var backend: Pair<String, NextLyricsBackend>? = null

    /**
     * [romanize]: romanize on the device too (only when a widget shows it: it loads a large dictionary for Japanese).
     * [manualSpotifyId]: the Spotify ID the user set for the song, whose picks are stored apart.
     */
    suspend fun lookUp(request: LyricsLookupRequest, romanize: Boolean, manualSpotifyId: String? = null): Result = withContext(Dispatchers.IO) {
        val folder = LyricsFolder(context, File(context.filesDir, "lyrics-folder-index.json"))
        val store = LocalLyricsStore(File(context.filesDir, "local-lyrics"))
        savedLyricsFor(request, store, folder)?.let { return@withContext rendered(it, request, splitBackgroundVocals = true, romanize = romanize) }
        val backend = backend()
        val known = ConcurrentHashMap<String, ProviderResult>()
        context.getSharedPreferences("source_overrides", 0)
            .getString(LocalLyricsStore.keyOf(request.title, request.artist), null)
            ?.let { chosen ->
                val found = backend.resolveFrom(request, chosen, known) as? RemoteLyricsResolution.Found
                if (found?.selection?.source?.id == chosen) return@withContext rendered(found.selection, request, backend.splitBackgroundVocals, romanize)
            }
        backend.cachedResolution(request, manualSpotifyId)?.takeIf { it.settled }?.let { cached ->
            val selection = (cached.resolution as? RemoteLyricsResolution.Found)?.selection ?: return@withContext Result.None
            return@withContext rendered(selection, request, backend.splitBackgroundVocals, romanize)
        }
        delay(gate.waitBeforeNetwork(SystemClock.elapsedRealtime()))
        val resolution = backend.resolve(request, known)
        backend.store(request, resolution, manualSpotifyId)
        val selection = (resolution as? RemoteLyricsResolution.Found)?.selection
            ?: return@withContext if (resolution is RemoteLyricsResolution.NotFound) Result.None else Result.Failed
        rendered(selection, request, backend.splitBackgroundVocals, romanize)
    }

    private fun rendered(selection: RemoteLyricsSelection, request: LyricsLookupRequest, splitBackgroundVocals: Boolean, romanize: Boolean): Result =
        runCatching<Result> { Result.Found(RemoteLyricsAdapter.render(selection, request.durationSeconds * 1_000L, splitBackgroundVocals, romanize)) }
            .getOrElse { Result.Failed }

    private fun backend(): NextLyricsBackend {
        val key = context.getSharedPreferences("spicy_lyrics_key", 0).getString("key", null).orEmpty()
            .ifBlank { BuildConfig.SPICY_LYRICS_CLIENT_KEY }
        backend?.takeIf { it.first == key }?.let { return it.second }
        return NextLyricsBackend(context, key).also { backend = key to it }
    }
}
