package com.tx24.spicyplayer.playback

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.widget.BackgroundLyrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The song playing in another app and its lyrics, as the lyrics screen would show them, for what
 * shows the lyrics outside it (the overlay, the always-on display): the same sources in the same
 * order (Spicy Lyrics, its word-by-word timing, saved lyrics), romanized when Romanize is on and
 * translated as Translate automatically would ([BackgroundTranslation]).
 *
 * Its fields are Compose state, read by the composables that draw them. Main thread only;
 * [start] and [stop] bracket its life.
 */
internal class LiveLyrics(context: Context) {
    private val app = context.applicationContext
    /** The app's context, for what draws these to read the settings. */
    val appContext: Context get() = app
    private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val session = BackgroundSession(app)
    private val lookup = BackgroundLyrics(app)
    private val translations = BackgroundTranslation(app)
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var key: String? = null

    var track by mutableStateOf<BackgroundSession.Track?>(null)
        private set
    /** The lyrics, or null while looking or with none. */
    var lyrics by mutableStateOf<LyricsState.Ready?>(null)
        private set
    var translation by mutableStateOf<TranslationResult?>(null)
        private set
    /** The lookup is done: [lyrics] null now means there are none. */
    var settled by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    /** The player's own buttons (like, shuffle, ...), read again with every change and after a press. */
    var actions by mutableStateOf<List<android.media.session.PlaybackState.CustomAction>>(emptyList())
        private set
    /** The player whose [actions] these are, for their icons. */
    val playerPackage: String? get() = session.playerPackage
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val rereadActions = Runnable { actions = session.customActions }

    /** The lyrics as a [LyricsState], for song changes: loading, none ([LyricsState.Idle]) or ready. */
    val lyricsState: LyricsState get() = lyrics ?: if (settled || track == null) LyricsState.Idle else LyricsState.Loading

    /** Told when the song, its lyrics or playing changes, beside the state above. */
    var onChange: () -> Unit = {}

    fun positionMs(): Long = session.positionMs()

    /** The followed player's controls. */
    fun playPause() = session.playPause()
    fun skipNext() = session.skipNext()
    fun skipPrevious() = session.skipPrevious()

    /** Presses one of the player's own buttons; its new state shows once the player publishes it. */
    fun sendCustomAction(action: String) {
        session.sendCustomAction(action)
        main.removeCallbacks(rereadActions)
        main.postDelayed(rereadActions, 400L)
        main.postDelayed({ actions = session.customActions }, 1_200L)
    }

    fun start() {
        session.onTrack = { onTrack(it) }
        session.onPlayingChanged = {
            playing = it
            actions = session.customActions
            onChange()
        }
        session.start()
        playing = session.isPlaying
        actions = session.customActions
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        session.stop()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        key = null
    }

    private fun onTrack(next: BackgroundSession.Track?) {
        track = next
        playing = session.isPlaying
        actions = session.customActions
        if (next?.key == key) {
            onChange()
            return
        }
        key = next?.key
        job?.cancel()
        lyrics = null
        translation = null
        settled = false
        onChange()
        if (next == null) return
        job = scope.launch {
            val romanize = prefs.getBoolean("romanize", false)
            val result = withContext(Dispatchers.IO) {
                runCatching { lookup.lookUp(next.request, romanize = romanize, next.spotifyId) }.getOrNull()
            }
            if (key != next.key) return@launch
            val found = result as? BackgroundLyrics.Result.Found
            lyrics = found?.lyrics
            settled = true
            onChange()
            if (found == null) return@launch
            val translated = withContext(Dispatchers.IO) {
                translations.translate(found.lyrics, next.title, next.artist, found.sourceId) { target ->
                    runCatching { lookup.humanTranslation(next.title, next.artist, target) }.getOrNull()
                }
            }
            if (key == next.key && lyrics === found.lyrics) translation = translated
        }
    }
}
