package com.tx24.spicyplayer.playback

import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.analytics.UsageStats
import android.net.Uri
import android.app.Application
import android.content.Context
import com.tx24.spicyplayer.spotify.SpotifyAccount
import com.tx24.spicyplayer.spotify.SpotifyPosition
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.haptics.HapticBeatOptions
import com.tx24.spicyplayer.haptics.MusicHaptic
import com.tx24.spicyplayer.haptics.MusicHapticScore
import com.tx24.spicyplayer.haptics.MusicHapticsStyle
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.lyrics.LyricsFolder
import com.tx24.spicyplayer.network.data.providers.CustomLyricsSource
import com.tx24.spicyplayer.network.data.providers.ModelSyncResult
import com.tx24.spicyplayer.lyrics.LyricsNotices
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.NoticeKind
import com.tx24.spicyplayer.lyrics.NextLyricsBackend
import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import com.tx24.spicyplayer.widget.NowPlayingHolder
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderAttempt
import com.tx24.spicyplayer.network.data.ProviderAttemptOutcome
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSelection
import com.tx24.spicyplayer.network.data.TrackNameCleaner
import com.tx24.spicyplayer.network.data.LyricsOrigins
import com.tx24.spicyplayer.network.data.TrafficCounter
import com.tx24.spicyplayer.network.data.counted
import com.tx24.spicyplayer.network.data.measuredQuality
import com.tx24.spicyplayer.network.data.SpicyLyricsKey
import com.tx24.spicyplayer.translation.*
import com.tx24.spicyplayer.network.data.RemoteLyricsSource
import com.tx24.spicyplayer.network.data.LyricsCapability
import com.tx24.spicyplayer.network.data.measuredQuality
import com.tx24.spicyplayer.network.data.SourceDisclosure
import com.tx24.spicyplayer.network.data.SourceDisclosures
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.RemoteLyricsPolicy
import com.tx24.spicyplayer.network.data.SourcePair
import com.tx24.spicyplayer.network.data.SourceRanking
import com.tx24.spicyplayer.network.data.LyricsBlends
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.tx24.spicyplayer.network.data.ItunesReleaseYear
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackCandidate
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.BackgroundSpeed
import com.tx24.spicyplayer.ui.nowplaying.SessionCustomAction
import com.tx24.spicyplayer.ui.nowplaying.TrackDirection
import com.tx24.spicyplayer.ui.settings.CustomSourceForm
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * While skipping (track changes closer together than this), how long a track must stay current
 * before its lyrics are fetched, so skipping past it costs no requests.
 */
private const val TRACK_SETTLE_MS = 700L

/**
 * A lyric lookup that reaches the network within [RAPID_SWITCH_WINDOW_MS] of the previous one
 * waits [RAPID_SWITCH_BACKOFF_MS] first (loader stays up), and goes out only if the track is still
 * current. Cache hits never wait.
 */
private const val RAPID_SWITCH_WINDOW_MS = 1_000L
private const val RAPID_SWITCH_BACKOFF_MS = 400L

/** Beats Spotify is less sure of than this aren't tapped to. */
private const val CALIBRATION_BEAT_CONFIDENCE = 0.2f

/** Upcoming queue entries whose lyrics are fetched ahead. */
private const val FETCH_AHEAD = 3

/** How long after our own skip a track change is still taken to be its result. */
private const val SKIP_DIRECTION_WINDOW_MS = 3_000L
/** The crossfade lead setting, in seconds ([ExternalPlaybackViewModel.onSongChange]). */
internal const val CROSSFADE_PREF = "crossfadeSeconds"
/** A song whose clock was this close to its end (beyond the crossfade) ended on its own. */
private const val NATURAL_END_SLACK_MS = 2_500L

/** How often the ticker reads the player and the audio output while the screen is up. */
private const val PERIODIC_CHECK_MS = 2_000L
/** How often the ticker looks while a seek waits for the player to confirm it. */
private const val SEEK_CHECK_MS = 100L

/** How long the ticker sleeps: briefly while a seek waits for confirmation, else until the next periodic check. */
internal fun tickerWaitMs(seekPending: Boolean, sinceCheckMs: Long): Long =
    if (seekPending) SEEK_CHECK_MS else (PERIODIC_CHECK_MS - sinceCheckMs).coerceIn(SEEK_CHECK_MS, PERIODIC_CHECK_MS)
private const val SYNC_TAG = "SpicySync"
private const val TRANSLATE_TAG = "SpicyTranslate"

/**
 * A source the user can choose for one song: what it had, whether it can be picked, whether it's up,
 * and whether lrc.red's model could word-sync what it had ([ExternalPlaybackViewModel.requestModelSync]).
 */
data class SourceChoice(
    val id: String,
    val name: String,
    val status: String,
    val available: Boolean,
    val shown: Boolean,
    val canModelSync: Boolean = false,
)
private const val SPOTIFY_PACKAGE = "com.spotify.music"
/** How long a skip may take before Spotify is taken to have refused it. */
private const val SKIP_REFUSED_AFTER_MS = 2_500L
/** How long after a track change a pause is taken for the player loading the song. */
private const val LOADING_PAUSE_GRACE_MS = 3_000L
/**
 * A report this recent when the song changes may already be the new song's: players often send
 * the new position a moment before the new title. An older one is the previous song's.
 */
private const val TRACK_REPORT_FRESH_MS = 1_000L
/** A buffering report this close to the song's end is the player loading the next one, not a stall. */
private const val END_BUFFERING_MS = 2_000L
/** How long the followed session may stay stopped before another playing one takes over ([SessionStickiness]). */
private const val SESSION_SWITCH_GRACE_MS = 2_000L
/** Lyrics saved in the Lyrics Manager, and lyrics uploaded for one song "just once". */
internal val LOCAL_SOURCE = LyricsSourceDescriptor("local", "Local Lyrics DB", 0, setOf(LyricsCapability.WORD_SYNC))
internal val UPLOADED_SOURCE = LyricsSourceDescriptor("uploaded", "Uploaded TTML", 0, setOf(LyricsCapability.WORD_SYNC))
internal val FOLDER_SOURCE = LyricsSourceDescriptor("folder", "Lyrics folder", 0, setOf(LyricsCapability.WORD_SYNC, LyricsCapability.LINE_SYNC))
/** How long after one the folder is listed again on its own, when the app comes back. */
private const val FOLDER_RESCAN_MS = 30_000L
/** How wide a saved song's cover is kept for the Lyrics Manager's list. */
private const val COVER_THUMB_PX = 128

/**
 * Something Spotify Free doesn't allow, noticed when Spotify leaves a command out of its session
 * or ignores it. Android can't tell which plan an account is on, so this is how it shows.
 */
enum class PlayerLimit { Seek, Previous, Skips }

data class PlayerUiState(
    val accessGranted: Boolean = false,
    val sourcePackage: String? = null,
    val title: String = "Nothing playing",
    val artist: String = "Start playback in another app",
    val album: String = "",
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val canSeek: Boolean = false,
    val outputLabel: String = "Detecting output",
    val lyricDelayMs: Int = 0,
    /** This song's own delay, on top of [lyricDelayMs] ([SongDelays]). */
    val songDelayMs: Int = 0,
    /** The player app's own delay, on top of [lyricDelayMs] ([PlayerDelays]). */
    val playerDelayMs: Int = 0,
    /** The player app's name, for its delay; its package when the name can't be read. */
    val playerLabel: String? = null,
    val trackDirection: TrackDirection = TrackDirection.Forward,
    val customActions: List<SessionCustomAction> = emptyList(),
    val clockDriftMs: Long? = null,
    val matchInfo: String? = null,
    val detectedSpotifyId: String? = null,
    val manualSpotifyId: String? = null,
    val lyrics: LyricsState = LyricsState.Idle,
    val lastCommandLatencyMs: Long? = null,
    val status: String? = null,
    val artwork: Bitmap? = null,
    val artworkUri: String? = null,
    val sourceDescriptors: List<LyricsSourceDescriptor> = emptyList(),
    /** The sources in order with every switch spelled out ([LyricsSourceSettings.normalized]). */
    val sourcePolicy: RemoteLyricsPolicy = RemoteLyricsPolicy(),
    val providerAttempts: List<ProviderAttempt> = emptyList(),
    val lookupStatus: String? = null,
    val humanRomanizations: Boolean = false,
    /** Lines of the lyrics on screen that carry Genius's human romanization. */
    val humanRomanizedLines: Int = 0,
    /** What a source the user is switching on would send, waiting for their OK. */
    val sourceDisclosure: SourceDisclosure? = null,
    /** What this update switched off (source IDs, [SourceDisclosures.GENIUS_ROMANIZATION_ID], Musixmatch); said once. */
    val switchedOffSources: Set<String> = emptySet(),
    /** The person's own Spicy Lyrics key, shortened ([SpicyLyricsKey.hint]); null while on the built-in one. */
    val ownKeyHint: String? = null,
    /** A Spotify Free limit to explain, until dismissed. */
    val limitNotice: PlayerLimit? = null,
    /** The song's release year, when asked for ([ExternalPlaybackViewModel.setTrackExtrasWanted]) and found. */
    val releaseYear: String? = null,
    /** Still looking the year up: the header keeps room for it meanwhile. */
    val releaseYearPending: Boolean = false,
    /** The lead artist's Spotify header image, when asked for and they have one. */
    val artistHeaderUrl: String? = null,
    val artistHeaderPending: Boolean = false,
    /**
     * The song's Spotify Canvas video, when the cover stage asks for it
     * ([ExternalPlaybackViewModel.setCanvasWanted]) and the song has one.
     */
    val canvasUrl: String? = null,
    /** No canvas answer for this song yet, so the stage waits before falling back. */
    val canvasPending: Boolean = true,
    /** The Lyrics Manager's saved songs, newest first. */
    val localLyrics: List<LocalLyricsStore.Entry> = emptyList(),
    /** The playing song's key in the Lyrics Manager, saved there or not. */
    val localLyricsKey: String? = null,
    /** The source chosen for this song, over the automatic pick; null when the pick is automatic. */
    val sourceOverride: String? = null,
    /** lrc.red's model is syncing this song at the user's request. */
    val modelSyncing: Boolean = false,
    /** Brackets at a synced line's ends shown as background vocals. */
    val splitBackgroundVocals: Boolean = true,
    /** Live captions: YouTube's captions, word by word, for songs no source has. */
    val liveCaptions: Boolean = false,
    /** The user's own sources, in the order they were added; their place in the lookup is in [sourcePolicy]. */
    val customSources: List<CustomLyricsSource> = emptyList(),
    /** The player's queue, when it shares one; empty otherwise. */
    val queue: List<QueueEntry> = emptyList(),
    /** The linked lyrics folder, if any. */
    val lyricsFolder: LyricsFolder.Status? = null,
    val lyricsFolderScanning: Boolean = false,
    /** The output delay is being found by tapping along ([TapCalibration]); the music haptics rest. */
    val calibratingDelay: Boolean = false,
    val translationPreferences: TranslationPreferences = TranslationPreferences(),
    val translationEnabled: Boolean = false,
    /** A translation is being fetched for the lyrics on screen. */
    val translating: Boolean = false,
    /** The last translation failed: the button shows off, and a tap tries again. */
    val translationFailed: Boolean = false,
    val translation: TranslationResult? = null,
    val lyricsLanguage: String? = null,
    val lyricsLanguageOverride: String? = null,
    /**
     * The lyrics' language as the phone guesses it, before any translation: only for deciding
     * whether the translate button would help. Never sent as a translation's source language.
     */
    val guessedLanguage: String? = null,
    val deepLKeyPresent: Boolean = false,
    /** The translation providers with an API key saved on this device. */
    val translationKeys: Set<TranslationProvider> = emptySet(),
    val aiTranslation: AiTranslationSettings = AiTranslationSettings(),
    /** Gemini Nano on this phone, once checked; null until then. */
    val onDeviceModel: OnDeviceModel.Status? = null,
    /** How much of Gemini Nano has downloaded, while it does. */
    val onDeviceDownloadedBytes: Long? = null,
) {
    /** [sourcePolicy] read against the providers there are. */
    val sourceRanking: SourceRanking
        get() = SourceRanking(sourceDescriptors.filter { LyricsBlends.byId(it.id) == null }, sourcePolicy)
}

/** One song in the player's queue. */
data class QueueEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
    val icon: Bitmap?,
    val iconUri: String?,
    val current: Boolean,
)

/** The lookups beyond the lyrics that the screen currently shows. */
data class TrackExtrasWanted(
    val releaseYear: Boolean = false,
    val artistHeader: Boolean = false,
    val beats: Boolean = false,
)

class ExternalPlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = application.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(application, SessionAccessService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    // Optional user-supplied Spicy Lyrics key overriding the shipped one; kept on device (backups are disabled).
    private val keyStore = application.getSharedPreferences("spicy_lyrics_key", 0)
    private var runtimeApiKey: String = keyStore.getString("key", null).orEmpty()
    private var lyricsBackend = NextLyricsBackend(application, runtimeApiKey.ifBlank { BuildConfig.SPICY_LYRICS_CLIENT_KEY })
    private val overrideStore = application.getSharedPreferences("spotify_id_overrides", 0)
    /** The lyrics source chosen per song (by [LocalLyricsStore.keyOf]), over the automatic pick. */
    private val sourceOverrides = application.getSharedPreferences("source_overrides", 0)
    private val outputProfiles = AudioOutputProfiles(application)
    private val songDelays = SongDelays(application)
    private val playerDelays = PlayerDelays(application)
    private val translationStore = TranslationSettingsStore(application)
    // The song's language comes from the provider's answer, kept in the translation cache. The
    // phone only tells which language the lyrics' own translations are in, when they don't say.
    private val translationCache = DiskTranslationCache(File(application.cacheDir, "translations"))
    private val languageGuesser = SystemLanguageDetector(application)
    private val translationEngine = TranslationEngine(SongLanguageDetector { null }, translationCache, ownDetector = languageGuesser)
    private val translationSession = TranslationSession()
    // An AI service sends nothing until the whole answer is written, so the read timeout (10 s
    // by default) can't be shorter than the call's.
    private val translationClient = okhttp3.OkHttpClient.Builder().counted().callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private var translationJob: Job? = null
    private var translatedDocument: String? = null
    private var guessedDocument: String? = null
    private var pendingTranslationAction: String? = null
    /** The rest a provider's limit was last announced for, so it's said once. */
    private var restingNotice = 0L
    private var outputRoute = outputProfiles.currentRoute()
    private val mutableState = MutableStateFlow(PlayerUiState(
        translationPreferences = translationStore.read(),
        translationEnabled = translationStore.read().automatic,
        deepLKeyPresent = translationStore.key().isNotBlank(),
        translationKeys = savedTranslationKeys(),
        aiTranslation = translationStore.ai(),
        outputLabel = outputRoute.label,
        lyricDelayMs = outputProfiles.delayMs(outputRoute),
        sourceDescriptors = lyricsBackend.descriptors + lyricsBackend.blendDescriptors,
        sourcePolicy = lyricsBackend.policy(),
        humanRomanizations = lyricsBackend.humanRomanizations,
        splitBackgroundVocals = lyricsBackend.splitBackgroundVocals,
        liveCaptions = lyricsBackend.liveCaptionsEnabled,
        switchedOffSources = lyricsBackend.switchedOffSources,
        ownKeyHint = runtimeApiKey.takeIf { it.isNotBlank() }?.let(SpicyLyricsKey::hint),
    ))
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short notes on what an action did ("Removed from Local DB."), shown as toasts. */
    val messages: SharedFlow<String> = mutableMessages

    fun toggleTranslation() {
        UsageStats.count(UsageCounter.TRANSLATE)
        // After a failure the button shows off, so a tap tries again instead of turning it off.
        if (mutableState.value.translationEnabled && mutableState.value.translationFailed) {
            translateCurrent(manual = true, force = true)
        } else if (mutableState.value.translationEnabled) {
            clearTranslation()
            mutableState.value = mutableState.value.copy(translationEnabled = false)
        } else if (!translationConsent("manual")) {
            mutableState.value = mutableState.value.copy(translationEnabled = true)
            translateCurrent(manual = true, force = true)
        }
    }

    fun setTranslateAutomatically(enabled: Boolean) {
        if (enabled && translationConsent("automatic")) return
        val preferences = mutableState.value.translationPreferences.copy(automatic = enabled)
        translationStore.save(preferences)
        clearTranslation()
        mutableState.value = mutableState.value.copy(translationPreferences = preferences, translationEnabled = enabled)
        if (enabled) translateCurrent()
    }

    fun setTranslationTarget(language: String) = saveTranslationPreferences(mutableState.value.translationPreferences.copy(targetLanguage = language))
    fun setTranslationProvider(provider: TranslationProvider) = saveTranslationPreferences(mutableState.value.translationPreferences.copy(provider = provider))
    fun setTranslationExcluded(languages: Set<String>) = saveTranslationPreferences(mutableState.value.translationPreferences.copy(excludedLanguages = languages))
    fun setHumanTranslations(enabled: Boolean) = saveTranslationPreferences(mutableState.value.translationPreferences.copy(humanTranslations = enabled))

    private fun saveTranslationPreferences(changed: TranslationPreferences) {
        val preferences = changed.copy(aiVariant = translationStore.ai().variant(changed.provider))
        translationStore.save(preferences)
        clearTranslation()
        mutableState.value = mutableState.value.copy(translationPreferences = preferences)
        translateCurrent()
    }

    private fun savedTranslationKeys(): Set<TranslationProvider> =
        listOf(TranslationProvider.OpenAi, TranslationProvider.Gemini).filter { translationStore.key(it).isNotBlank() }.toSet()

    /** Saves (or, blank, removes) [provider]'s API key on this device. */
    fun useTranslationKey(provider: TranslationProvider, input: String) {
        val key = input.trim()
        translationStore.saveKey(provider, key)
        mutableState.value = mutableState.value.copy(translationKeys = savedTranslationKeys())
        clearTranslation()
        translateCurrent()
        showMessage(if (key.isEmpty()) "Removed the ${provider.label} key." else "Using your ${provider.label} key.")
    }

    fun setAiTranslation(value: AiTranslationSettings) {
        translationStore.saveAi(value)
        mutableState.value = mutableState.value.copy(aiTranslation = translationStore.ai(), translationPreferences = translationStore.read())
        clearTranslation()
        translateCurrent()
    }

    /** Looks at whether this phone has Gemini Nano, for Translation settings. */
    fun checkOnDeviceModel() {
        viewModelScope.launch {
            val status = OnDeviceModel.status()
            mutableState.value = mutableState.value.copy(onDeviceModel = status)
        }
    }

    fun downloadOnDeviceModel() {
        if (mutableState.value.onDeviceDownloadedBytes != null) return
        mutableState.value = mutableState.value.copy(onDeviceModel = OnDeviceModel.Status.Downloading, onDeviceDownloadedBytes = 0L)
        viewModelScope.launch {
            val done = OnDeviceModel.download { bytes -> mutableState.value = mutableState.value.copy(onDeviceDownloadedBytes = bytes) }
            mutableState.value = mutableState.value.copy(onDeviceModel = OnDeviceModel.status(), onDeviceDownloadedBytes = null)
            if (!done) showMessage("Gemini Nano couldn't be downloaded. Try again later.")
            else translateCurrent(force = true)
        }
    }

    fun useDeepLKey(input: String) {
        val key = input.trim()
        if (key.isNotEmpty() && !DeepLKey.valid(key)) {
            showMessage("That isn't a DeepL API key.")
            return
        }
        translationStore.saveKey(key)
        mutableState.value = mutableState.value.copy(deepLKeyPresent = key.isNotEmpty())
        clearTranslation()
        translateCurrent()
        showMessage(if (key.isEmpty()) "Removed the DeepL key." else "Using your DeepL key.")
    }

    fun setLyricsLanguage(language: String?) {
        val song = mutableState.value.localLyricsKey ?: return
        translationStore.saveLanguage(song, languageCode(language))
        clearTranslation()
        mutableState.value = mutableState.value.copy(lyricsLanguageOverride = languageCode(language), lyricsLanguage = null)
        translateCurrent(detectOnly = true)
    }

    private fun translationConsent(action: String): Boolean {
        val preferences = mutableState.value.translationPreferences
        val provider = preferences.provider
        val service = if (provider == TranslationProvider.OpenAi) openAiEndpoint(translationStore.ai().baseUrl)?.host else null
        if (translationStore.disclosed(provider, preferences.humanTranslations, service)) return false
        // On the phone with no Genius lookup, nothing leaves it: nothing to consent to.
        if (provider.onDevice && !preferences.humanTranslations) return false
        pendingTranslationAction = action
        // A source notice already on screen finishes first; its answer resumes this action.
        if (mutableState.value.sourceDisclosure != null) return true
        mutableState.value = mutableState.value.copy(sourceDisclosure = SourceDisclosures.translation(provider, preferences.humanTranslations, service))
        return true
    }

    private fun resumeTranslationAction() {
        val action = pendingTranslationAction ?: return
        pendingTranslationAction = null
        if (translationConsent(action)) return
        when (action) {
            "automatic" -> setTranslateAutomatically(true)
            "manual" -> { mutableState.value = mutableState.value.copy(translationEnabled = true); translateCurrent(manual = true, force = true) }
            else -> translateCurrent(force = true)
        }
    }

    private fun clearTranslation() {
        translationJob?.cancel()
        translationSession.invalidate()
        translatedDocument = null
        mutableState.value = mutableState.value.copy(translation = null, translating = false, translationFailed = false)
    }

    private fun translationDocument(): TranslationDocument? {
        val state = mutableState.value
        val lyrics = state.lyrics as? LyricsState.Ready ?: return null
        val song = state.localLyricsKey ?: return null
        return TranslationDocument.from(song, "${shownSelection?.source?.id.orEmpty()}:${lyrics.provider}:${lyrics.source.orEmpty()}", lyrics.lines,
            lyrics.translationLanguage)
    }

    fun detectLyricsLanguage() = translateCurrent(detectOnly = true)

    /** Guesses the lyrics' language on the phone, once per lyrics, for the translate button. */
    private fun guessLanguage() {
        val document = translationDocument() ?: return
        if (guessedDocument == document.hash) return
        guessedDocument = document.hash
        viewModelScope.launch {
            val language = guessedLanguage(document)
            if (translationDocument()?.hash == document.hash) {
                mutableState.value = mutableState.value.copy(guessedLanguage = language)
            }
        }
    }

    /** The phone's guess at [document]'s language, worked out once per lyrics; null when unsure. */
    private val guesses = object : LinkedHashMap<String, String?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>) = size > 8
    }

    private suspend fun guessedLanguage(document: TranslationDocument): String? {
        synchronized(guesses) { if (guesses.containsKey(document.hash)) return guesses[document.hash] }
        // A song in two scripts is in two languages: unknown, so the button stays.
        val (mixed, language) = withContext(Dispatchers.Default) {
            val mixed = mixedScripts(document.lines.filterNot { it.boundary }.map { it.text })
            mixed to if (mixed) null else languageCode(languageGuesser.detect(document.context))
        }
        synchronized(guesses) {
            if (!guesses.containsKey(document.hash)) traceTranslation("guess: " + if (mixed) "two scripts, unknown" else language ?: "unknown")
            guesses[document.hash] = language
        }
        return language
    }

    private fun traceTranslation(line: String) {
        Log.d(TRANSLATE_TAG, line)
        TranslationTrace.add(SystemClock.elapsedRealtime(), line)
    }

    private fun translateCurrent(manual: Boolean = false, force: Boolean = false, detectOnly: Boolean = false) {
        if (!mutableState.value.translationEnabled && !detectOnly) return
        val document = translationDocument() ?: return
        val state = mutableState.value
        // Re-detect when the exact original document changes, never on the playback ticker.
        if (!force && translatedDocument == document.hash) return
        clearTranslation()
        translatedDocument = document.hash
        val preferences = state.translationPreferences
        val override = translationStore.language(state.localLyricsKey)
        val ticket = translationSession.begin(translationEngine.key(document, preferences, override))
        traceTranslation("start: ${preferences.provider}" + (if (manual) ", tapped" else "") + ", lyrics ${document.hash.take(8)}")
        translationJob = viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.Default) { translationEngine.language(document, override) }
                if (!translationSession.accepts(ticket) || translationDocument()?.hash != document.hash) return@launch
                mutableState.value = mutableState.value.copy(lyricsLanguage = source, lyricsLanguageOverride = override)
                if (!mutableState.value.translationEnabled) return@launch
                if (skipTranslation(source, preferences.targetLanguage, preferences.excludedLanguages)) {
                    if (manual) showMessage("Already in ${TranslationLanguages.name(source!!)}.")
                    return@launch
                }
                // Automatically, a song the phone is sure is in the target language isn't sent
                // anywhere (the button hides for it too); a tap still asks the translator.
                if (source == null && !manual) {
                    val guess = guessedLanguage(document)
                    if (skipTranslation(guess, preferences.targetLanguage, preferences.excludedLanguages)) {
                        traceTranslation("skipped: the phone is sure it's in $guess")
                        return@launch
                    }
                    if (!translationSession.accepts(ticket) || translationDocument()?.hash != document.hash) return@launch
                }
                // Translations that came with the lyrics and cover the song are shown without
                // asking a translator, so nothing needs consenting to.
                val own = withContext(Dispatchers.Default) { translationEngine.ownResult(document, preferences, source) }
                if (own != null) {
                    if (!translationSession.accepts(ticket) || translationDocument()?.hash != document.hash) return@launch
                    traceTranslation("translated: ${own.lineSources()}")
                    mutableState.value = mutableState.value.copy(translation = own)
                    if (manual) showMessage("Translated: ${own.lineSources()}.")
                    return@launch
                }
                if (translationConsent("resume")) return@launch
                val translator = when (preferences.provider) {
                    TranslationProvider.Google -> GoogleTranslator(translationClient)
                    TranslationProvider.DeepL -> DeepLTranslator(translationClient, translationStore.key())
                    TranslationProvider.OpenAi -> translationStore.ai().let {
                        OpenAiTranslator(translationClient, it.baseUrl, translationStore.key(TranslationProvider.OpenAi), it.model)
                    }
                    TranslationProvider.Gemini ->
                        GeminiTranslator(translationClient, translationStore.key(TranslationProvider.Gemini), translationStore.ai().geminiModel)
                    TranslationProvider.GeminiNano -> GeminiNanoTranslator()
                }
                // Google stands in for a slow or busy translator, only once it's been agreed to.
                val fallback = GoogleTranslator(translationClient).takeIf {
                    preferences.provider != TranslationProvider.Google && translationStore.disclosed(TranslationProvider.Google, false)
                }
                mutableState.value = mutableState.value.copy(translating = true)
                val result = try {
                    withContext(Dispatchers.IO) {
                        translationEngine.translate(document, preferences, source, translator,
                            human = { lyricsBackend.humanTranslation(state.title, state.artist, preferences.targetLanguage) },
                            trace = ::traceTranslation,
                            fallback = fallback,
                            onPartial = { partial ->
                                withContext(Dispatchers.Main) {
                                    if (translationSession.accepts(ticket) && translationDocument()?.hash == document.hash && mutableState.value.translationEnabled) {
                                        traceTranslation("machine translation shown, Genius still looking")
                                        mutableState.value = mutableState.value.copy(translation = partial)
                                    }
                                }
                            },
                        )
                    }
                } finally {
                    if (translationSession.accepts(ticket)) mutableState.value = mutableState.value.copy(translating = false)
                }
                if (!translationSession.accepts(ticket) || translationDocument()?.hash != document.hash || !mutableState.value.translationEnabled) {
                    traceTranslation("result dropped: the lyrics or a setting changed meanwhile")
                    return@launch
                }
                traceTranslation(when (result) {
                    is TranslationOutcome.Translated -> "translated: ${result.result.lineSources()}"
                    is TranslationOutcome.Skipped -> "skipped: already in ${result.language ?: "the target language"}"
                })
                when (result) {
                    is TranslationOutcome.Translated -> {
                        mutableState.value = mutableState.value.copy(translation = result.result, lyricsLanguage = result.result.detectedLanguage ?: source)
                        // A tap gets told what answered; a song change just shows it.
                        if (manual) showMessage("Translated: ${result.result.lineSources()}.")
                    }
                    is TranslationOutcome.Skipped -> {
                        val language = result.language
                        mutableState.value = mutableState.value.copy(lyricsLanguage = language ?: source)
                        if (manual && language != null) showMessage("Already in ${TranslationLanguages.name(language)}.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (resting: ProviderResting) {
                traceTranslation("waiting: ${resting.provider.label} is limiting translations")
                if (!translationSession.accepts(ticket) || !mutableState.value.translationEnabled) return@launch
                // Said once per rest, not again for every song that comes up meanwhile; a tap
                // always gets an answer.
                if (manual || restingNotice != resting.untilMs) showMessage(resting.message ?: "Translation is busy.")
                restingNotice = resting.untilMs
                delay((resting.untilMs - System.currentTimeMillis()).coerceAtLeast(0L) + 1_000L)
                if (translationSession.accepts(ticket) && translationDocument()?.hash == document.hash && mutableState.value.translationEnabled) {
                    traceTranslation("trying again after the wait")
                    translateCurrent(force = true)
                }
            } catch (error: Exception) {
                Log.w(TRANSLATE_TAG, "translation failed", error)
                traceTranslation("failed: ${error.message}")
                if (translationSession.accepts(ticket) && mutableState.value.translationEnabled) {
                    mutableState.value = mutableState.value.copy(translationFailed = true)
                    // Never surface provider bodies, request contents, or credentials in a message.
                    showMessage(if (error is TranslationFailure) error.message ?: "Couldn't translate these lyrics." else "Couldn't translate these lyrics. Try again later.")
                }
            }
        }
    }

    private val localLyrics = LocalLyricsStore(File(application.filesDir, "local-lyrics"))
    private val lyricsFolder = LyricsFolder(application, File(application.filesDir, "lyrics-folder-index.json"))
    private var folderScan: Job? = null
    private var lastFolderScanAt = Long.MIN_VALUE / 2
    /** A TTML applied to one song "just once" (by lyrics key): it holds until the song changes or is reset. */
    private var temporaryLyrics: Pair<String, RemoteLyricsSelection>? = null

    private var controller: MediaController? = null
    // Which way our own skip buttons last moved, so the next track change can say so.
    private var pendingSkip: Pair<TrackDirection, Long>? = null
    private var lastQueueIndex = -1
    private val actionIcons = HashMap<Pair<String, Int>, Bitmap?>()
    private val observedSessions = mutableMapOf<MediaSession.Token, MediaController>()
    private val observedCallbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()
    private var lyricsJob: Job? = null
    /** When the last track change arrived, to tell skipping from a song handing over. */
    private var lastTrackChangeAt = Long.MIN_VALUE / 2
    /** Shared by the song on screen and the queue fetched ahead, so neither bursts requests. */
    private val networkGate = RapidSwitchGate(RAPID_SWITCH_WINDOW_MS, RAPID_SWITCH_BACKOFF_MS)
    private var warmJob: Job? = null
    private var shownSelection: RemoteLyricsSelection? = null
    private var shownRequest: LyricsLookupRequest? = null
    /** What the last lookup asked for, to notice the player filling in the song's length later. */
    private var lookedUp: LyricsLookupRequest? = null
    private var humanJob: Job? = null
    // Lyrics with Genius's romanization laid over, per pick, so a replay doesn't wait for it again.
    private val humanCache = object : LinkedHashMap<RemoteLyricsSelection, LyricsState.Ready>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RemoteLyricsSelection, LyricsState.Ready>) = size > 12
    }
    // Per-source results for recent requests, so a policy change re-picks without refetching.
    private val lookupCache = object : LinkedHashMap<LyricsLookupRequest, MutableMap<String, ProviderResult>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LyricsLookupRequest, MutableMap<String, ProviderResult>>) = size > 30
    }
    // Rendered lyrics for recent picks. Rendering (on-device romanization above all) is the slow
    // step of showing cached lyrics, so a song played again, or warmed ahead, skips it.
    private val renderedCache = object : LinkedHashMap<RemoteLyricsSelection, RenderedLyrics>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RemoteLyricsSelection, RenderedLyrics>) = size > 12
    }
    private var manualSpotifyId: String? = null
    /** The song length [manualSpotifyId] was last looked up with. */
    private var overrideCheckedAtMs = 0L
    private var currentTrackIdentity: String? = null
    // Players fill metadata in over several updates (YouTube Music adds length and album after
    // the title), so lyrics follow title + artist only; the clock follows the full identity.
    private var currentLyricsKey: String? = null
    /** Spotify's report at a song's end, which the clock ran past ([reconcileClock]). */
    private var endBufferingReport: LyricClock.Report? = null
    /** Whether [currentLyricsKey] is the followed player's, not left from before it ([detachController]). */
    private var lyricsKeyTaken = false
    private var pendingCommand: PendingCommand? = null
    private val clock = LyricClock(SystemClock.elapsedRealtime())
    private var lastPeriodicCheckMs = 0L
    /** Wakes the ticker from a long sleep when a seek is sent, so its confirmation is watched for at once. */
    private val tickerWake = Channel<Unit>(Channel.CONFLATED)
    /** Whether the lyrics screen is started. The ticker only polls while it is. */
    private val uiStarted = MutableStateFlow(true)
    /** The song changed while the screen was stopped: its lookups wait for the screen to return. */
    private var lookupDeferred = false
    /** When the last fresh report arrived. */
    private var lastReportAt = Long.MIN_VALUE / 2
    /** The song changed before its first report: that report re-anchors the clock outright. */
    private var awaitingTrackReport = false
    /** Limits already explained this session: each is said once, not on every refused tap. */
    private val explainedLimits = mutableSetOf<PlayerLimit>()
    private var skipCheckJob: Job? = null
    /** When the track last changed, for a pause some players report while they load the next one. */
    private var trackChangedAt = Long.MIN_VALUE / 2
    /**
     * How far the player's reports run behind its audio for this song: the crossfade lead
     * (Settings → Scroll & Sync → Crossfade) when the last song ended on its own. Added to every
     * report until our own seek, whose report is the audio's own position.
     */
    private var transitionOffsetMs = 0L
    /** Spotify's Automix head start, resynced a moment into each new song ([AutomixResync]). */
    private val automixResync = Runnable { automixResyncNow() }
    private var automixSongKey: String? = null
    private val sessionStickiness = SessionStickiness(SESSION_SWITCH_GRACE_MS)
    /** Looks at the sessions again once the followed one has been stopped for its grace. */
    private val sessionRecheck = Runnable { refreshSessionSelection() }
    /** Resync on a player that can't seek: the next fresh report re-anchors the clock outright. */
    private var resyncOnNextReport = false
    /** The player that last ignored a seek it lists (Spotify Free): its resyncs go by its reports. */
    private var seekRefusedBy: String? = null

    private val spotifyExtras = SharedSpotify.extras(application.cacheDir)
    private val itunesYear = ItunesReleaseYear(okhttp3.OkHttpClient.Builder().counted().build())
    private var extrasWanted = TrackExtrasWanted()
    /** What the screen asks for; [extrasWanted] adds the beats while calibrating. */
    private var screenExtrasWanted = TrackExtrasWanted()
    private var extrasJob: Job? = null
    private val canvasFinder = SharedSpotify.canvas(application.cacheDir)
    /** The cover stage shows the song's Spotify Canvas; nothing is looked up otherwise. */
    private var canvasWanted = false
    private var canvasJob: Job? = null
    /** The Spotify track ID [PlayerUiState.canvasUrl] was found for, to forget it if it won't play. */
    private var canvasTrackId: String? = null
    /** The playing song's audio analysis, for the beat-reactive background. */
    @Volatile private var audioAnalysis: AudioAnalysis? = null
    /** The last music haptics built: from which analysis, in which style and HapticBeat options. */
    @Volatile private var musicHaptics: MusicHapticsCache? = null

    private data class MusicHapticsCache(
        val analysis: AudioAnalysis,
        val style: MusicHapticsStyle,
        val beat: HapticBeatOptions,
        val wordTimes: List<Long>,
        val lineTimes: List<Long>,
        val wordsEstimated: Boolean,
        val score: List<MusicHaptic>,
    )

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateFromController(metadataChanged = true)
        override fun onPlaybackStateChanged(playbackState: PlaybackState?) = updateFromController()
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = refreshQueue()
        override fun onSessionDestroyed() = refresh()
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        attachBestSession(it.orEmpty())
    }

    init {
        // The Spotify token that matching needs is fetched now, not by the first song.
        viewModelScope.launch(Dispatchers.IO) { lyricsBackend.warmUp() }
        startTicker()
        refresh()
        refreshSourcePolicy()
        viewModelScope.launch(Dispatchers.IO) {
            val status = lyricsFolder.status()
            mutableState.value = mutableState.value.copy(lyricsFolder = status)
        }
        // The widget shows the screen's own pick while the screen is up.
        viewModelScope.launch {
            state.map { it.lyrics }.distinctUntilChanged().collect { NowPlayingHolder.offer(currentLyricsKey, it) }
        }
    }

    fun refresh() {
        refreshOutputRoute()
        if (SystemClock.elapsedRealtime() - lastFolderScanAt >= FOLDER_RESCAN_MS) rescanLyricsFolder()
        // The grant is per listener class, not per app: after the class moves, the package can
        // still be listed while this component is not, and every session read is refused.
        val granted = Settings.Secure.getString(getApplication<Application>().contentResolver, "enabled_notification_listeners")
            .orEmpty().split(':')
            .any { ComponentName.unflattenFromString(it) == listenerComponent }
        mutableState.value = mutableState.value.copy(accessGranted = granted)
        if (!granted) {
            stopObservingSessions()
            detachController()
            mutableState.value = PlayerUiState(
                accessGranted = false,
                translationPreferences = translationStore.read(),
                translationEnabled = translationStore.read().automatic,
                deepLKeyPresent = translationStore.key().isNotBlank(),
                translationKeys = savedTranslationKeys(),
                aiTranslation = translationStore.ai(),
                outputLabel = outputRoute.label,
                lyricDelayMs = outputProfiles.delayMs(outputRoute),
                sourceDescriptors = lyricsBackend.descriptors + lyricsBackend.blendDescriptors,
                sourcePolicy = lyricsBackend.policy(),
            )
            return
        }
        runCatching {
            sessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener)
            sessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent, mainHandler)
            attachBestSession(sessionManager.getActiveSessions(listenerComponent))
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(status = error.message ?: "Could not read media sessions")
        }
    }

    fun playPause() {
        val active = controller ?: return
        val expectPlaying = active.playbackState?.state != PlaybackState.STATE_PLAYING
        pendingCommand = PendingCommand.PlayState(expectPlaying, SystemClock.elapsedRealtime())
        // Only the button changes now. The clock waits for the player's report: the song goes on
        // for a moment after a pause is sent (and starts a moment after a play), and stopping the
        // lyrics on the tap lost that moment on every pause.
        if (expectPlaying) active.transportControls.play() else active.transportControls.pause()
        mutableState.value = mutableState.value.copy(isPlaying = expectPlaying)
    }

    fun skipNext() {
        pendingSkip = TrackDirection.Forward to SystemClock.elapsedRealtime()
        watchForRefusedSkip(PlayerLimit.Skips, PlaybackState.ACTION_SKIP_TO_NEXT)
        commandForTrackChange { skipToNext() }
    }

    fun skipPrevious() {
        pendingSkip = TrackDirection.Backward to SystemClock.elapsedRealtime()
        watchForRefusedSkip(PlayerLimit.Previous, PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        commandForTrackChange { skipToPrevious() }
    }

    /** Jumps to a song in the player's queue. */
    fun skipToQueueItem(id: Long) {
        val active = controller ?: return
        val queue = active.queue.orEmpty()
        val from = queue.indexOfFirst { it.queueId == active.playbackState?.activeQueueItemId }
        val to = queue.indexOfFirst { it.queueId == id }
        if (to < 0 || to == from) return
        pendingSkip = (if (from in 0 until to) TrackDirection.Forward else TrackDirection.Backward) to SystemClock.elapsedRealtime()
        // Sent even when the player doesn't list queue jumps (Spotify doesn't): some take them anyway.
        val before = active.metadata.trackIdentity()
        commandForTrackChange { skipToQueueItem(id) }
        skipCheckJob?.cancel()
        skipCheckJob = viewModelScope.launch {
            delay(SKIP_REFUSED_AFTER_MS)
            if (controller === active && currentTrackIdentity == before) {
                showMessage("The player didn't jump to that song: it doesn't take queue jumps from other apps.")
            }
        }
    }

    /** Re-reads the player's queue into the state; cheap, so it runs on every change that can move it. */
    private fun refreshQueue() {
        val active = controller
        val items = active?.queue.orEmpty()
        val currentId = active?.playbackState?.activeQueueItemId
        val queue = items.map { item ->
            val d = item.description
            QueueEntry(
                id = item.queueId,
                title = d.title?.toString().orEmpty().ifBlank { "Unknown track" },
                subtitle = d.subtitle?.toString().orEmpty(),
                icon = d.iconBitmap,
                iconUri = d.iconUri?.toString(),
                current = item.queueId == currentId,
            )
        }
        val state = mutableState.value
        if (state.queue != queue) mutableState.value = state.copy(queue = queue)
    }

    fun dismissLimitNotice() {
        mutableState.value = mutableState.value.copy(limitNotice = null)
    }

    private fun explainLimit(limit: PlayerLimit) {
        if (controller?.packageName != SPOTIFY_PACKAGE || !explainedLimits.add(limit)) return
        mutableState.value = mutableState.value.copy(limitNotice = limit)
    }

    /**
     * Spotify Free drops skip actions from its session once the hourly skips run out, or keeps
     * them and ignores the press. Either way the song stays: a previous that restarts it counts.
     */
    private fun watchForRefusedSkip(limit: PlayerLimit, action: Long) {
        val active = controller ?: return
        if (active.packageName != SPOTIFY_PACKAGE) return
        if (((active.playbackState?.actions ?: 0L) and action) == 0L) {
            explainLimit(limit)
            return
        }
        val before = active.metadata.trackIdentity()
        val positionBefore = currentPositionMs()
        skipCheckJob?.cancel()
        skipCheckJob = viewModelScope.launch {
            delay(SKIP_REFUSED_AFTER_MS)
            if (controller !== active || currentTrackIdentity != before) return@launch
            val restarted = limit == PlayerLimit.Previous && currentPositionMs() < positionBefore
            if (!restarted) explainLimit(limit)
        }
    }

    /** Presses one of the player's own buttons (see [PlayerUiState.customActions]). */
    fun sendCustomAction(action: String) {
        val active = controller ?: return
        val extras = active.playbackState?.customActions?.firstOrNull { it.action == action }?.extras
        active.transportControls.sendCustomAction(action, extras)
    }

    fun seekBy(deltaMs: Long) {
        val target = (currentPositionMs() + deltaMs)
            .coerceIn(0L, mutableState.value.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        seekTo(target)
    }

    fun seekTo(targetMs: Long) = seekTo(targetMs, resync = false)

    private fun seekTo(targetMs: Long, resync: Boolean) {
        val active = controller ?: return
        if (((active.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) == 0L) {
            mutableState.value = mutableState.value.copy(
                status = "${active.packageName} does not expose seeking through MediaSession",
            )
            explainLimit(PlayerLimit.Seek)
            return
        }
        val target = targetMs.coerceIn(
            0L,
            mutableState.value.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
        )
        pendingCommand = PendingCommand.Seek(target, SystemClock.elapsedRealtime(), resync)
        // The seek's report is the audio's own position: no crossfade lead, nothing to resync.
        transitionOffsetMs = 0L
        mainHandler.removeCallbacks(automixResync)
        tickerWake.trySend(Unit)
        clock.seekTo(target, SystemClock.elapsedRealtime())
        mutableState.value = mutableState.value.copy(status = null)
        active.transportControls.seekTo(target)
    }

    /**
     * Android can't ask a player where it is, only take its last report. So this seeks the player
     * to where the lyrics think it is: it must answer with a fresh report, which the clock takes
     * outright. A player that can't seek, or lists seeking but ignores it (Spotify Free, found out
     * on the first try), snaps to its last report now and to its next one outright.
     */
    fun resync() {
        UsageStats.count(UsageCounter.RESYNC)
        pendingCommand = null
        refresh()
        val active = controller
        val seekable = ((active?.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) != 0L
        val bySeek = active != null && seekable && active.packageName != seekRefusedBy
        SyncTrace.add(
            SystemClock.elapsedRealtime(),
            "resync: clock=${currentPositionMs()} bias=${clock.biasMs}" + if (bySeek) " seek" else " from reports",
        )
        if (bySeek) {
            seekTo(currentPositionMs(), resync = true)
            return
        }
        resyncFromReports()
    }

    private fun resyncFromReports() {
        val active = controller
        active?.playbackState?.let { reconcileClock(it, force = true, snap = it.state == PlaybackState.STATE_PLAYING) }
        resyncOnNextReport = active != null
        mutableState.value = mutableState.value.copy(
            lastCommandLatencyMs = null,
            status = if (controller != null) "Timeline resynced to the media session" else "No active media session to resync",
        )
    }

    /** Positive delay makes the lyrics appear later; negative delay advances them. */
    fun adjustLyricDelay(deltaMs: Int) {
        val newDelay = clampDelay(mutableState.value.lyricDelayMs + deltaMs)
        outputProfiles.saveDelayMs(outputRoute, newDelay)
        SyncTrace.add(SystemClock.elapsedRealtime(), "output delay: $newDelay")
        mutableState.value = mutableState.value.copy(lyricDelayMs = newDelay)
    }

    fun setLyricDelay(delayMs: Int) {
        adjustLyricDelay(delayMs - mutableState.value.lyricDelayMs)
    }

    fun resetLyricDelay() {
        outputProfiles.saveDelayMs(outputRoute, 0)
        mutableState.value = mutableState.value.copy(lyricDelayMs = 0)
    }

    /** Sets this song's own delay; does nothing while no song is identified. */
    fun setSongDelay(delayMs: Int) {
        val key = mutableState.value.localLyricsKey ?: return
        val newDelay = clampDelay(delayMs)
        songDelays.save(key, newDelay)
        mutableState.value = mutableState.value.copy(songDelayMs = newDelay)
    }

    fun adjustSongDelay(deltaMs: Int) = setSongDelay(mutableState.value.songDelayMs + deltaMs)

    /** Sets the player app's own delay; does nothing while no player is known. */
    fun setPlayerDelay(delayMs: Int) {
        val player = mutableState.value.sourcePackage ?: return
        val newDelay = clampDelay(delayMs)
        playerDelays.save(player, newDelay)
        SyncTrace.add(SystemClock.elapsedRealtime(), "player delay: $newDelay")
        mutableState.value = mutableState.value.copy(playerDelayMs = newDelay)
    }

    /** Not held at the song's length like [currentPositionMs]: a line can run to the end or past it. */
    fun currentLyricPositionMs(): Long = mutableState.value.let {
        lyricPositionMs(clock.positionAt(SystemClock.elapsedRealtime()), it.lyricDelayMs, it.songDelayMs, it.playerDelayMs)
    }

    /** Checks [input] and uses it as the person's own key; blank goes back to the built-in one. */
    fun useApiKey(input: String) {
        when (val check = SpicyLyricsKey.check(input)) {
            SpicyLyricsKey.Check.Secret -> showMessage("That's a secret key. Spicy Lyrics Plus needs the client key (sl_pk_…).")
            SpicyLyricsKey.Check.Invalid -> showMessage("That isn't a Spicy Lyrics client key. It starts with sl_pk_.")
            SpicyLyricsKey.Check.Empty -> {
                applyApiKey("")
                showMessage("Back to the built-in key.")
            }
            is SpicyLyricsKey.Check.Ok -> {
                applyApiKey(check.key)
                showMessage("Your key is in use.")
            }
        }
    }

    private fun applyApiKey(key: String) {
        runtimeApiKey = key
        keyStore.edit().putString("key", runtimeApiKey).apply()
        mutableState.value = mutableState.value.copy(ownKeyHint = key.takeIf { it.isNotBlank() }?.let(SpicyLyricsKey::hint))
        lyricsBackend = NextLyricsBackend(getApplication(), runtimeApiKey.ifBlank { BuildConfig.SPICY_LYRICS_CLIENT_KEY })
        lyricsBackend.let { backend -> viewModelScope.launch(Dispatchers.IO) { backend.warmUp() } }
        lookupCache.clear()  // Spicy Lyrics answers depend on the key
        refreshSourcePolicy()
        loadLyrics()
    }

    /** Forgets this song's lyrics everywhere they are kept (bar the Lyrics Manager) and asks the sources again. */
    fun clearCurrentSongCaches() {
        val request = currentRequest() ?: return noTrack()
        translationDocument()?.hash?.let(translationCache::forget)
        // Forgotten too, so the reloaded lyrics are translated afresh.
        clearTranslation()
        forgetShown(request)
        lyricsBackend.forgetRomanization(request.title, request.artist)
        loadLyrics(force = true)
        mutableMessages.tryEmit("Lyrics for the current song have been removed from all caches.")
    }

    /**
     * Deletes every stored pick and translation, and what this session still holds of them, then
     * asks the sources again for the playing song.
     */
    fun clearStoredLyricsCache() {
        val backend = lyricsBackend
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                backend.clearCache()
                translationCache.clear()
            }
            lookupCache.clear()
            synchronized(renderedCache) { renderedCache.clear() }
            synchronized(humanCache) { humanCache.clear() }
            synchronized(guesses) { guesses.clear() }
            clearTranslation()
            currentRequest()?.let { request ->
                forgetShown(request)
                loadLyrics(force = true)
            }
            mutableMessages.tryEmit("The lyrics and translation caches have been cleared.")
        }
    }

    /** Drops this song's lyrics from memory only, so they are read back from the cache or asked for again. */
    fun clearCurrentSongFromMemory() {
        val request = currentRequest() ?: return noTrack()
        clearTranslation()
        forgetShown(request)
        loadLyrics()
        mutableMessages.tryEmit("Lyrics for the current song have been removed from memory.")
    }

    /** Shows [message] as a toast. */
    fun showMessage(message: String) {
        mutableMessages.tryEmit(message)
    }

    private fun noTrack() = showMessage("No track is currently playing.")

    /**
     * Drops a just-once upload and makes the next load pick this song's lyrics afresh.
     * [dropResults] also forgets the sources' answers and the rendered lyrics, so they are asked
     * for and rendered again; without it what is already in is re-picked at once.
     */
    private fun forgetShown(request: LyricsLookupRequest, dropResults: Boolean = true) {
        temporaryLyrics = null
        if (dropResults) lookupCache.remove(request)
        shownSelection?.takeIf { dropResults }?.let { shown ->
            synchronized(renderedCache) { renderedCache.remove(shown) }
            synchronized(humanCache) { humanCache.remove(shown) }
        }
        shownSelection = null
    }

    /** The playing song as the sources are asked for it. */
    private fun currentRequest(): LyricsLookupRequest? {
        val metadata = controller?.metadata ?: return null
        return lookupRequest(metadata, manualSpotifyId ?: mutableState.value.detectedSpotifyId)
            .takeIf { it.title.isNotBlank() && it.artist.isNotBlank() }
    }

    /** Re-reads the Lyrics Manager's list. */
    fun refreshLocalLyrics() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { localLyrics.entries() }
            mutableState.value = mutableState.value.copy(localLyrics = entries)
        }
    }

    /**
     * Shows [ttml] for the playing song: saved in the Lyrics Manager when [persistent] (it then
     * beats every source), else for now only. False, with the reason said, when it can't be used.
     */
    suspend fun importTtml(ttml: String, persistent: Boolean): Boolean {
        val request = currentRequest() ?: return false.also { noTrack() }
        // Taken together before parsing, which suspends: the song may change meanwhile.
        val key = currentLyricsKey
        val state = mutableState.value
        val artwork = state.artwork
        val selection = localSelection(ttml, persistent)
        val usable = withContext(Dispatchers.Default) {
            runCatching { RemoteLyricsAdapter.render(selection, request.durationSeconds * 1_000L) }
                .getOrNull()?.lines?.isNotEmpty() == true
        }
        if (!usable) {
            mutableMessages.tryEmit("Failed to parse TTML.")
            return false
        }
        if (!persistent && currentLyricsKey != key) {
            mutableMessages.tryEmit("The song changed before the lyrics were ready.")
            return false
        }
        forgetShown(request, dropResults = false)
        if (persistent) {
            withContext(Dispatchers.IO) {
                val entry = localLyrics.put(request.title, request.artist, state.title, state.artist, state.album, ttml)
                artwork?.let { saveCover(it, localLyrics.coverFile(entry.key)) }
            }
            refreshLocalLyrics()
            mutableMessages.tryEmit("TTML saved to Local DB!")
        } else {
            temporaryLyrics = key.orEmpty() to selection
            mutableMessages.tryEmit("Lyrics parsed and applied!")
        }
        loadLyrics()
        return true
    }

    /** Links [tree] as the lyrics folder (replacing any other) and reads it. */
    fun linkLyricsFolder(tree: Uri) {
        viewModelScope.launch {
            val linked = withContext(Dispatchers.IO) { runCatching { lyricsFolder.link(tree) }.isSuccess }
            if (!linked) return@launch showMessage("Couldn't open that folder.")
            rescanLyricsFolder(manual = true)
        }
    }

    fun unlinkLyricsFolder() {
        folderScan?.cancel()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { lyricsFolder.unlink() }
            mutableState.value = mutableState.value.copy(lyricsFolder = null, lyricsFolderScanning = false)
            showMessage("Lyrics folder unlinked.")
            if (shownSelection?.source == FOLDER_SOURCE) currentRequest()?.let { request ->
                forgetShown(request, dropResults = false)
                loadLyrics()
            }
        }
    }

    /**
     * Lists the lyrics folder again, reading new and changed files. When that changes what the
     * playing song should show (a file for it arrived, or the one showing changed), it shows that.
     */
    fun rescanLyricsFolder(manual: Boolean = false) {
        if (lyricsFolder.tree == null || folderScan?.isActive == true) return
        lastFolderScanAt = SystemClock.elapsedRealtime()
        folderScan = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(lyricsFolderScanning = true)
            val changed = withContext(Dispatchers.IO) { lyricsFolder.rescan() }
            val status = withContext(Dispatchers.IO) { lyricsFolder.status() }
            mutableState.value = mutableState.value.copy(lyricsFolder = status, lyricsFolderScanning = false)
            if (manual) showMessage(status?.let { "Lyrics folder: ${it.files} ${if (it.files == 1) "file" else "files"}." } ?: "Couldn't read the lyrics folder.")
            val request = currentRequest() ?: return@launch
            val shown = shownSelection?.source
            val affected = shown == FOLDER_SOURCE || shown != LOCAL_SOURCE && shown != UPLOADED_SOURCE &&
                withContext(Dispatchers.IO) { lyricsFolder.has(request.title, request.artist) }
            if (changed && affected) {
                forgetShown(request, dropResults = false)
                loadLyrics()
            }
        }
    }

    /** Deletes a saved song; if it's the one playing, its lyrics are looked up again. */
    fun removeLocalLyrics(key: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { localLyrics.remove(key) }
            refreshLocalLyrics()
            mutableMessages.tryEmit("Removed from Local DB.")
            val request = currentRequest() ?: return@launch
            if (LocalLyricsStore.keyOf(request.title, request.artist) == key) {
                forgetShown(request, dropResults = false)
                loadLyrics()
            }
        }
    }

    /** The TTML saved under [key], for exporting. */
    suspend fun localLyricsText(key: String): String? = withContext(Dispatchers.IO) { localLyrics.raw(key) }

    /** Where a saved song's cover thumbnail is, if it has one. */
    fun localLyricsCover(key: String): File = localLyrics.coverFile(key)

    /**
     * Takes down lyrics applied just once, back to the ones found online. A saved song's stay (they
     * beat the sources until deleted), which is said rather than seeming to do nothing.
     */
    fun resetTtml() {
        val request = currentRequest() ?: return noTrack()
        val uploaded = temporaryLyrics?.first == currentLyricsKey.orEmpty()
        forgetShown(request, dropResults = false)
        loadLyrics()
        val saved = localLyrics.contains(request.title, request.artist)
        val inFolder = !saved && lyricsFolder.has(request.title, request.artist)
        showMessage(
            when {
                uploaded && saved -> "TTML has been reset. This song's saved lyrics are showing; delete them to use the online ones."
                saved -> "This song's lyrics are saved in the Local DB. Delete them there to go back to the online ones."
                uploaded && inFolder -> "TTML has been reset. This song's lyrics from your lyrics folder are showing."
                inFolder -> "This song's lyrics come from your lyrics folder. Remove the file there to go back to the online ones."
                uploaded -> "TTML has been reset."
                else -> "No uploaded TTML to reset."
            },
        )
    }

    private fun localSelection(ttml: String, persistent: Boolean): RemoteLyricsSelection = savedSelection(ttml, persistent)

    /**
     * The playing song's lyrics from the Lyrics Manager: a just-once upload, else a saved song,
     * else a file in the lyrics folder.
     */
    private fun localLyricsFor(identity: String?, request: LyricsLookupRequest): RemoteLyricsResolution? {
        val selection = temporaryLyrics?.takeIf { it.first == identity.orEmpty() }?.second
            ?: savedLyricsFor(request, localLyrics, lyricsFolder)
            ?: return null
        val message = when (selection.source) {
            UPLOADED_SOURCE -> "applied once"
            FOLDER_SOURCE -> "from your lyrics folder"
            else -> "saved"
        }
        return RemoteLyricsResolution.Found(
            selection,
            listOf(ProviderAttempt(selection.source.id, ProviderAttemptOutcome.HIT, selection.quality, message = message)),
        )
    }

    private fun saveCover(artwork: Bitmap, file: File) {
        val height = (COVER_THUMB_PX * artwork.height / artwork.width.coerceAtLeast(1)).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(artwork, COVER_THUMB_PX, height, true)
        runCatching { file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
        if (scaled !== artwork) scaled.recycle()
    }

    /**
     * Switches source [id] on or off. Switching on a source with no provider on switches its first
     * provider on too, after that provider's disclosure.
     */
    fun setSourceEnabled(id: String, enabled: Boolean) {
        if (enabled) {
            val first = LyricsSourceSettings.providerToSwitchOn(lyricsBackend.ranking(), id)
            if (first != null && askFirst(disclosureFor(first))) {
                pendingSourceSwitch = { setSourceEnabled(id, true) }
                return
            }
        }
        changeSources { LyricsSourceSettings.setSourceEnabled(it, lyricsBackend.descriptors, id, enabled) }
    }

    /** Switches provider [providerId] on or off for source [originId], after its disclosure when it has one. */
    fun setProviderEnabled(originId: String, providerId: String, enabled: Boolean) {
        if (enabled && askFirst(disclosureFor(providerId))) {
            pendingSourceSwitch = { setProviderEnabled(originId, providerId, true) }
            return
        }
        changeSources { LyricsSourceSettings.setPairEnabled(it, lyricsBackend.descriptors, SourcePair(originId, providerId), enabled) }
    }

    /** Keeps only the [allowed] sync types of source [originId]. */
    fun setSourceSyncTypes(originId: String, allowed: Set<RemoteLyricsQuality>) =
        changeSources { LyricsSourceSettings.setSyncTypes(it, lyricsBackend.descriptors, originId, allowed) }

    /** Puts the sources in [order] (dragged into place). */
    fun setSourceOrder(order: List<String>) =
        changeSources { LyricsSourceSettings.reorder(it, lyricsBackend.descriptors, order) }

    private fun changeSources(change: (RemoteLyricsPolicy) -> RemoteLyricsPolicy) {
        lyricsBackend.savePolicy(change(lyricsBackend.policy()))
        refreshSourcePolicy()
        loadLyrics()
    }

    private fun disclosureFor(providerId: String): SourceDisclosure? =
        mutableState.value.sourceDescriptors.firstOrNull { it.id == providerId }?.let(SourceDisclosures::forSource)

    /** The switch waiting on the source disclosure being shown, done once it's agreed to. */
    private var pendingSourceSwitch: (() -> Unit)? = null

    /** Holds [disclosure] for the user's OK when it's not been agreed to yet; true while it waits. */
    private fun askFirst(disclosure: SourceDisclosure?): Boolean {
        if (disclosure == null || lyricsBackend.isDisclosed(disclosure.id)) return false
        mutableState.value = mutableState.value.copy(sourceDisclosure = disclosure)
        return true
    }

    /** The user agreed to what [SourceDisclosure] sends: switch it on. */
    fun acceptSourceDisclosure() {
        val disclosure = mutableState.value.sourceDisclosure ?: return
        if (disclosure.id.startsWith("translation_")) {
            translationStore.disclose(disclosure.id)
            mutableState.value = mutableState.value.copy(sourceDisclosure = null)
            resumeTranslationAction()
            return
        }
        lyricsBackend.markDisclosed(disclosure.id)
        mutableState.value = mutableState.value.copy(sourceDisclosure = null)
        if (disclosure.id == SourceDisclosures.GENIUS_ROMANIZATION_ID) setHumanRomanizations(true)
        else pendingSourceSwitch?.invoke()
        pendingSourceSwitch = null
        resumeTranslationAction()
    }

    fun dismissSourceDisclosure() {
        val translationNotice = mutableState.value.sourceDisclosure?.id?.startsWith("translation_") == true
        if (translationNotice) {
            pendingTranslationAction = null
            clearTranslation()
            mutableState.value = mutableState.value.copy(translationEnabled = false)
        }
        mutableState.value = mutableState.value.copy(sourceDisclosure = null)
        pendingSourceSwitch = null
        if (!translationNotice) resumeTranslationAction()
    }

    fun dismissSwitchedOff() {
        lyricsBackend.dismissSwitchedOff()
        mutableState.value = mutableState.value.copy(switchedOffSources = emptySet())
    }

    /** Picks up a settings restore: sources, delays and Spotify links are re-read from storage. */
    fun reloadSavedSettings() {
        clearTranslation()
        mutableState.value = mutableState.value.copy(
            translationPreferences = translationStore.read(), translationEnabled = translationStore.read().automatic,
            lyricsLanguageOverride = translationStore.language(mutableState.value.localLyricsKey), lyricsLanguage = null,
        )
        lyricsBackend.reloadCustomSources()
        lookupCache.clear()
        refreshSourcePolicy()
        mutableState.value = mutableState.value.copy(
            lyricDelayMs = outputProfiles.delayMs(outputRoute),
            songDelayMs = songDelays.delayMs(mutableState.value.localLyricsKey),
            humanRomanizations = lyricsBackend.humanRomanizations,
            splitBackgroundVocals = lyricsBackend.splitBackgroundVocals,
            liveCaptions = lyricsBackend.liveCaptionsEnabled,
            switchedOffSources = lyricsBackend.switchedOffSources,
        )
        // The playing song's Spotify link may have come or gone: look it up again if so.
        val restoredId = controller?.metadata.savedSpotifyId(overrideStore)
        if (restoredId != manualSpotifyId) {
            if (restoredId != null) overrideSpotifyId(restoredId) else clearSpotifyIdOverride()
        } else {
            // The restored sources, order or romanization may pick other lyrics for this song.
            currentRequest()?.let { forgetShown(it, dropResults = true) }
            loadLyrics()
        }
    }

    /** Adds [custom] or saves changes to it; the lookup asks it from now on. */
    fun saveCustomSource(custom: CustomLyricsSource) {
        lyricsBackend.saveCustomSource(custom)
        lookupCache.clear()  // its answers may differ now
        refreshSourcePolicy()
        loadLyrics()
    }

    fun removeCustomSource(id: String) {
        lyricsBackend.removeCustomSource(id)
        lookupCache.clear()
        refreshSourcePolicy()
        loadLyrics()
    }

    /** The custom source form open in settings, kept while the settings are off screen. */
    internal var customSourceForm: CustomSourceForm? = null

    /** What a custom source's test got: lyrics or not, said in a few words. */
    data class SourceTest(val found: Boolean, val message: String)

    /** Asks [custom] for the playing song, for its editor. */
    suspend fun testCustomSource(custom: CustomLyricsSource): SourceTest {
        val request = currentRequest() ?: return SourceTest(false, "Play a song to test with it.")
        val result = withContext(Dispatchers.IO) { lyricsBackend.testCustomSource(custom, request) }
        fun no(message: String) = SourceTest(false, message)
        return when (result) {
            is ProviderResult.Hit -> when (result.payload.measuredQuality()) {
                RemoteLyricsQuality.WORD_SYNCED -> SourceTest(true, "Word-synced lyrics for ${request.title}.")
                RemoteLyricsQuality.LINE_SYNCED -> SourceTest(true, "Line-synced lyrics for ${request.title}.")
                RemoteLyricsQuality.PLAIN -> SourceTest(true, "Unsynced lyrics for ${request.title}.")
                RemoteLyricsQuality.NONE -> no("It answered, but with nothing that reads as lyrics.")
            }
            ProviderResult.Miss -> no("No lyrics for ${request.title}. If it answers in JSON, check the path.")
            ProviderResult.NeedsMatch -> no("${request.title} has no Spotify match, which {spotifyId} needs.")
            is ProviderResult.CoolingDown -> no("Rate limited. Try again in a bit.")
            is ProviderResult.Queued -> no("It's busy. Try again in a bit.")
            is ProviderResult.Unavailable -> no("Failed: ${result.message ?: result.category.name.lowercase()}.")
        }
    }

    private fun refreshSourcePolicy() {
        val policy = lyricsBackend.policy()
        mutableState.value = mutableState.value.copy(
            customSources = lyricsBackend.customSources(),
            sourceDescriptors = lyricsBackend.descriptors + lyricsBackend.blendDescriptors,
            sourcePolicy = policy,
        )
    }

    fun overrideSpotifyId(input: String) {
        val id = input.spotifyTrackId()
        if (id == null) {
            mutableState.value = mutableState.value.copy(
                status = "Enter a valid Spotify track ID or URL",
            )
            return
        }
        manualSpotifyId = id
        controller?.metadata.saveSpotifyId(overrideStore, id)
        mutableState.value = mutableState.value.copy(
            detectedSpotifyId = id,
            manualSpotifyId = id,
            matchInfo = "Manual Spotify ID: $id",
            status = null,
        )
        restartCanvas()
        loadLyrics()
    }

    /**
     * Spotify's results for the playing song: the automatic match's own when [query] is null,
     * else for [query]. Null when there's no song or the search failed.
     */
    suspend fun searchSpotify(query: String?): List<SpotifyTrackCandidate>? {
        val request = currentRequest() ?: return null
        return runCatching {
            withContext(Dispatchers.IO) {
                if (query == null) {
                    SharedSpotify.resolver.candidates(
                        LocalTrackMetadata(request.title, request.artist, request.album, request.durationSeconds * 1_000L),
                    )
                } else {
                    SharedSpotify.resolver.search(query)
                }
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    fun clearSpotifyIdOverride() {
        manualSpotifyId = null
        controller?.metadata.forgetSpotifyId(overrideStore)
        lyricsJob?.cancel()
        mutableState.value = mutableState.value.copy(
            detectedSpotifyId = null,
            manualSpotifyId = null,
            lyrics = LyricsState.Idle,
            matchInfo = "Matching Spotify track…",
            status = null,
        )
        updateFromController()
        restartCanvas()
        loadLyrics()
    }

    /**
     * Uses [sourceId]'s lyrics for this song from now on, or the automatic pick again for null.
     * Kept per song, so the song plays with them every time.
     */
    fun useSource(sourceId: String?) {
        val key = mutableState.value.localLyricsKey ?: return
        sourceOverrides.edit().apply { if (sourceId == null) remove(key) else putString(key, sourceId) }.apply()
        mutableState.value = mutableState.value.copy(sourceOverride = sourceId)
        loadLyrics()
    }

    /**
     * Has lrc.red's model word-sync this song (a few seconds), then shows its sync, chosen for the
     * song. A refusal (lrc.red can't find the recording, say) shows as a message.
     */
    fun requestModelSync() {
        val request = lookedUp ?: return
        if (mutableState.value.modelSyncing) return
        UsageStats.count(UsageCounter.MODEL_SYNC)
        mutableState.value = mutableState.value.copy(modelSyncing = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { lyricsBackend.requestModelSync(request) }
            mutableState.value = mutableState.value.copy(modelSyncing = false)
            when (result) {
                is ModelSyncResult.Synced -> {
                    lookupCache[request]?.put(RemoteLyricsSource.LRC_RED_ID, ProviderResult.Hit(result.payload))
                    if (lookedUp == request) useSource(RemoteLyricsSource.LRC_RED_ID)
                    showMessage("Word-synced by lrc.red's model.")
                }
                is ModelSyncResult.Failed -> showMessage(result.message)
            }
        }
    }

    /** The providers that could be chosen for this song, by where they rank, with what each had. */
    fun sourceChoices(): List<SourceChoice> {
        val known = lookedUp?.let { lookupCache[it] }.orEmpty()
        val shown = shownSelection?.source?.id
        // Sources the lookup under way is still waiting on: they have no answer yet, but were asked.
        val checking = mutableState.value.providerAttempts
            .filter { it.outcome == ProviderAttemptOutcome.PENDING }
            .mapTo(HashSet()) { it.sourceId }
        val ranking = lyricsBackend.ranking()
        return lyricsBackend.descriptors.filter { ranking.asked(it.id) }
            .sortedBy { ranking.bestPlace(it.id) }
            .map { source ->
                val result = known[source.id]
                SourceChoice(
                    id = source.id,
                    name = source.displayName,
                    status = when (result) {
                        is ProviderResult.Hit -> when (result.payload.measuredQuality()) {
                            RemoteLyricsQuality.WORD_SYNCED -> "Word-synced"
                            RemoteLyricsQuality.LINE_SYNCED -> "Line-synced"
                            RemoteLyricsQuality.PLAIN -> "Not synced"
                            RemoteLyricsQuality.NONE -> "No usable lyrics"
                        }
                        ProviderResult.Miss -> "No lyrics for this song"
                        ProviderResult.NeedsMatch -> "Needs a Spotify link"
                        null -> if (source.id in checking) "Still checking…" else "Not asked yet"
                        else -> "Unavailable right now"
                    },
                    available = result == null || result is ProviderResult.Hit && result.payload.measuredQuality() != RemoteLyricsQuality.NONE,
                    shown = source.id == shown,
                    // lrc.red has the song, but only Apple's line sync or unsynced text.
                    canModelSync = source.id == RemoteLyricsSource.LRC_RED_ID && result is ProviderResult.Hit &&
                        result.payload.attribution?.originName != LyricsOrigins.LRC_RED_AI_NAME &&
                        result.payload.measuredQuality().let { it == RemoteLyricsQuality.LINE_SYNCED || it == RemoteLyricsQuality.PLAIN } &&
                        lyricsBackend.canRequestModelSync(),
                )
            }
    }

    /**
     * Whether the phone has no working internet connection right now: no network other than a VPN
     * that reaches the internet. A VPN (Tailscale, say) stays up and validated with mobile data
     * and Wi-Fi off, so the active network alone can't tell.
     */
    @Suppress("DEPRECATION") // allNetworks: the callback alternative needs a listener kept for this one question.
    private fun deviceOffline(): Boolean {
        val connectivity = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return false
        return connectivity.allNetworks.none { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@none false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    /**
     * [settle]: a track change that follows another within [TRACK_SETTLE_MS] waits that long before
     * any request, so skipping through tracks costs nothing (the wait is cancelled by the next
     * change). A song that simply ends and hands over is asked for at once. A lookup that still
     * has to reach the network then goes through [networkGate]. [force] drops this track's cached
     * source results, e.g. for Retry, and skips the gate.
     */
    fun loadLyrics(spotifyIdInput: String? = null, settle: Boolean = false, force: Boolean = false) {
        val metadata = controller?.metadata ?: return
        val identity = metadata.lyricsKey()
        val manual = spotifyIdInput?.spotifyTrackId() ?: manualSpotifyId
        val request = lookupRequest(metadata, manual ?: mutableState.value.detectedSpotifyId)
        lookedUp = request
        if (request.title.isBlank() || request.artist.isBlank()) {
            mutableState.value = mutableState.value.copy(lyrics = LyricsNotices.missingMetadata)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val skipping = settle && now - lastTrackChangeAt < TRACK_SETTLE_MS
        if (settle) lastTrackChangeAt = now
        lyricsJob?.cancel()
        warmJob?.cancel()  // the song on screen goes first
        if (force) lookupCache.remove(request)
        // Results per source for this request: a policy change re-picks from these instead of refetching.
        val known = lookupCache.getOrPut(request) { ConcurrentHashMap() }
        // A song coming up again asks the sources that gave no answer last time again (a timeout,
        // a refusal, a queue): kept, they'd hold it on a worse pick all session. A source still
        // resting is skipped by its cooldown.
        if (settle) known.values.removeAll { it is ProviderResult.Unavailable || it is ProviderResult.CoolingDown || it is ProviderResult.Queued }
        lyricsJob = viewModelScope.launch {
            if (mutableState.value.lyrics !is LyricsState.Ready) {
                mutableState.value = mutableState.value.copy(lyrics = LyricsState.Loading, lookupStatus = "Starting lyric lookup…")
            }
            // The Lyrics Manager's lyrics beat every source and the cache.
            withContext(Dispatchers.IO) { localLyricsFor(identity, request) }?.let { local ->
                publish(local, identity, request, final = true)
                return@launch
            }
            // A source chosen for this song comes next; if it has nothing, the usual pick follows.
            mutableState.value.sourceOverride?.let { chosen ->
                val picked = runCatching { withContext(Dispatchers.IO) { lyricsBackend.resolveFrom(request, chosen, known) } }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                val found = (picked as? RemoteLyricsResolution.Found)?.takeIf { it.selection.source.id == chosen }
                if (found != null) {
                    publish(found, identity, request, final = true)
                    return@launch
                }
                val name = lyricsBackend.descriptors.firstOrNull { it.id == chosen }?.displayName ?: chosen
                showMessage("$name has no lyrics for this song, so the usual pick is shown.")
            }
            // The disk cache is keyed on title + artist, and a manual Spotify ID has entries of its own.
            val freshLookup = known.isEmpty()
            if (known.isEmpty()) {
                val cached = withContext(Dispatchers.IO) {
                    if (force) lyricsBackend.forget(request, manual)
                    lyricsBackend.cachedResolution(request, manual)
                }
                if (cached != null) {
                    known.putAll(cached.answers)
                    if (cached.settled) {
                        publish(cached.resolution, identity, request, final = true)
                        warmAhead()
                        return@launch
                    }
                    // Picked without a real answer from a source ranked above it (often a queue
                    // entry fetched ahead, which has no length for the Spotify match): the pick
                    // shows now, and only those sources are asked again below.
                    publish(cached.resolution, identity, request, final = false)
                }
            }
            if (skipping && freshLookup) delay(TRACK_SETTLE_MS)
            delay(networkGate.waitBeforeNetwork(SystemClock.elapsedRealtime(), force))
            runCatching {
                withContext(Dispatchers.IO) {
                    lyricsBackend.resolve(request, known) { update ->
                        withContext(Dispatchers.Main) { publish(update, identity, request, final = false) }
                    }
                }
            }
                .onSuccess { resolution ->
                    withContext(Dispatchers.IO) { lyricsBackend.store(request, resolution, manual) }
                    publish(resolution, identity, request, final = true)
                    warmAhead()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableState.value = mutableState.value.copy(
                        lyrics = if (deviceOffline()) LyricsState.Error(NoticeKind.Offline) else LyricsNotices.lookupFailed(error),
                        lookupStatus = "Lyric lookup failed",
                    )
                }
        }
    }

    /**
     * How a song without a Spotify ID from the player was matched, once its lookup ends: the
     * sources that need an ID search Spotify for one, and the match is remembered there.
     */
    private fun searchedMatchInfo(request: LyricsLookupRequest, attempts: List<ProviderAttempt>): String {
        val track = LocalTrackMetadata(request.title, request.artist, request.album, request.durationSeconds * 1_000L)
        SharedSpotify.resolver.remembered(track)?.track?.candidate?.let { match ->
            return "Spotify search: ${match.title} · ${match.artists.joinToString()} (${match.id})"
        }
        return if (attempts.any { it.outcome == ProviderAttemptOutcome.NEEDS_MATCH }) {
            "Spotify search found no confident match"
        } else {
            "No Spotify search needed this time (cached, or found by title and artist)"
        }
    }

    /** What the sources are asked for [metadata]: its names cleaned up for lookup. */
    private fun lookupRequest(metadata: MediaMetadata, spotifyTrackId: String?): LyricsLookupRequest =
        metadata.lookupRequest(spotifyTrackId)

    /**
     * Fetches the next [FETCH_AHEAD] queue entries into the disk cache, one at a time: started only once the current song is settled and cancelled by
     * the next lookup, so it never competes with the song on screen. Cached entries cost nothing.
     */
    private fun warmAhead() {
        // A lookup that finished after the app was left doesn't start fetching ahead.
        if (!uiStarted.value) return
        val session = controller ?: return
        val queue = session.queue.orEmpty()
        val at = queue.indexOfFirst { it.queueId == session.playbackState?.activeQueueItemId }
        if (at < 0) return
        val upcoming = queue.drop(at + 1).take(FETCH_AHEAD).mapNotNull { item ->
            val d = item.description
            val names = TrackNameCleaner.clean(d.title?.toString().orEmpty(), d.subtitle?.toString().orEmpty())
            if (names.title.isBlank() || names.artist.isBlank()) return@mapNotNull null
            LyricsLookupRequest(
                artist = names.artist,
                title = names.title,
                album = "",
                durationSeconds = ((d.extras?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) / 1_000L).toInt(),
            )
        }
        val backend = lyricsBackend
        warmJob = viewModelScope.launch(Dispatchers.IO) {
            for (request in upcoming) {
                if (localLyrics.contains(request.title, request.artist) || lyricsFolder.has(request.title, request.artist)) continue
                val resolution = backend.cachedResolution(request)?.resolution
                    // Failures are not reported: nobody is looking at this song yet.
                    ?: runCatching {
                        delay(networkGate.waitBeforeNetwork(SystemClock.elapsedRealtime()))
                        backend.resolve(request, ConcurrentHashMap()).also { backend.store(request, it) }
                    }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                // Rendered too, so the song's lyrics are up the moment it starts.
                (resolution as? RemoteLyricsResolution.Found)?.selection?.let { selection ->
                    withContext(Dispatchers.Default) { runCatching { rendered(selection, request.durationSeconds * 1_000L) } }
                }
            }
        }
    }

    /** Shows the best answer so far; re-renders only when the pick itself changes. */
    private suspend fun publish(
        resolution: RemoteLyricsResolution,
        identity: String?,
        request: LyricsLookupRequest,
        final: Boolean,
    ) {
        if (currentLyricsKey != identity) return
        val selection = (resolution as? RemoteLyricsResolution.Found)?.selection
        val lyrics = when {
            // Nothing picked yet: whatever is up stays up until the lookup ends.
            selection == null && !final -> mutableState.value.lyrics
            selection == null -> LyricsNotices.noLyrics(resolution.attempts, offline = deviceOffline()) { id ->
                lyricsBackend.descriptors.firstOrNull { it.id == id }?.displayName ?: id
            }
            selection == shownSelection -> mutableState.value.lyrics
            lyricsBackend.humanRomanizations && synchronized(humanCache) { humanCache[selection] } != null ->
                synchronized(humanCache) { humanCache.getValue(selection) }
            // Rendering includes on-device romanization, which is too heavy for main.
            else -> withContext(Dispatchers.Default) {
                runCatching { rendered(selection, request.durationSeconds * 1_000L) }
                    .getOrElse { LyricsNotices.renderFailed(selection.source.displayName, it) }
            }
        }
        if (currentLyricsKey != identity) return
        val newPick = selection != shownSelection
        shownSelection = selection
        shownRequest = request
        if (newPick && selection != null && lyrics is LyricsState.Ready &&
            synchronized(humanCache) { humanCache[selection] } == null
        ) humanize(selection, lyrics, identity, request)
        val pending = resolution.attempts.filter { it.outcome == ProviderAttemptOutcome.PENDING }
        val names = pending.joinToString { attempt ->
            lyricsBackend.descriptors.firstOrNull { it.id == attempt.sourceId }?.displayName ?: attempt.sourceId
        }
        if (final) Log.d("LyricsProviders", resolution.attempts.joinToString { "${it.sourceId}:${it.outcome}:${it.failureCategory ?: ""}:${it.message ?: ""}" })
        mutableState.value = mutableState.value.copy(
            providerAttempts = resolution.attempts,
            matchInfo = if (final && request.spotifyTrackId == null) searchedMatchInfo(request, resolution.attempts) else mutableState.value.matchInfo,
            lyrics = lyrics,
            lookupStatus = when {
                selection != null && pending.isNotEmpty() -> "Showing ${selection.source.displayName} · still checking $names…"
                selection != null -> "Showing lyrics from ${selection.source.displayName}"
                pending.isNotEmpty() -> "Checking $names…"
                resolution is RemoteLyricsResolution.NotFound -> "No enabled source found lyrics"
                else -> "Lookup finished; some sources were unavailable"
            },
        )
        translateCurrent()
        guessLanguage()
    }

    /** [selection] rendered for the renderer, from [renderedCache] when it holds it. */
    private fun rendered(selection: RemoteLyricsSelection, durationMs: Long): LyricsState.Ready {
        val payload = selection.payload
        // Only LRC depends on the length (its last line ends with the song).
        val usesDuration = payload.ttmlLyrics.isNullOrBlank() && !payload.syncedLyrics.isNullOrBlank()
        synchronized(renderedCache) { renderedCache[selection] }
            ?.takeIf { !usesDuration || it.durationMs == durationMs }
            ?.let { return it.lyrics }
        val lyrics = RemoteLyricsAdapter.render(selection, durationMs, lyricsBackend.splitBackgroundVocals)
        synchronized(renderedCache) { renderedCache[selection] = RenderedLyrics(durationMs, lyrics) }
        return lyrics
    }

    private class RenderedLyrics(val durationMs: Long, val lyrics: LyricsState.Ready)

    /**
     * Looks up Genius's human romanization for the lyrics on screen and swaps it in when it
     * lines up, a moment after the on-device one shows. Off the main thread; dropped if the song
     * or the pick changes first.
     */
    private fun humanize(selection: RemoteLyricsSelection, lyrics: LyricsState.Ready, identity: String?, request: LyricsLookupRequest) {
        humanJob?.cancel()
        if (!lyricsBackend.humanRomanizations || !RemoteLyricsAdapter.wantsHumanRomanization(lyrics)) return
        val backend = lyricsBackend
        humanJob = viewModelScope.launch {
            val genius = runCatching { withContext(Dispatchers.IO) { backend.humanRomanization(request.title, request.artist, request.durationSeconds, RemoteLyricsAdapter.romanizedTexts(lyrics)) } }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: return@launch
            val human = withContext(Dispatchers.Default) {
                runCatching { RemoteLyricsAdapter.withHumanRomanization(lyrics, genius) }.getOrNull()
            } ?: return@launch
            synchronized(humanCache) { humanCache[selection] = human }
            if (currentLyricsKey != identity || shownSelection != selection) return@launch
            val laid = human.lines.count { it !in lyrics.lines }
            Log.d("LyricsProviders", "Human romanization laid over $laid lines")
            mutableState.value = mutableState.value.copy(lyrics = human, humanRomanizedLines = laid)
        }
    }

    /**
     * Switches live captions on (after saying what it sends to YouTube) or off. Switched on while
     * the song on screen has no lyrics, the song is looked up again so its captions show.
     */
    fun setLiveCaptions(enabled: Boolean) {
        if (enabled && askFirst(SourceDisclosures.forId(SourceDisclosures.LIVE_CAPTIONS_ID))) {
            pendingSourceSwitch = { setLiveCaptions(true) }
            return
        }
        lyricsBackend.liveCaptionsEnabled = enabled
        mutableState.value = mutableState.value.copy(liveCaptions = enabled)
        if (enabled && mutableState.value.lyrics !is LyricsState.Ready) loadLyrics()
    }

    fun setSplitBackgroundVocals(enabled: Boolean) {
        lyricsBackend.splitBackgroundVocals = enabled
        mutableState.value = mutableState.value.copy(splitBackgroundVocals = enabled)
        // Every rendered copy was cut (or not) the old way.
        synchronized(renderedCache) { renderedCache.clear() }
        synchronized(humanCache) { humanCache.clear() }
        val selection = shownSelection ?: return
        val request = shownRequest ?: return
        val lyrics = runCatching { rendered(selection, request.durationSeconds * 1_000L) }.getOrNull() ?: return
        mutableState.value = mutableState.value.copy(lyrics = lyrics)
        if (lyricsBackend.humanRomanizations) humanize(selection, lyrics, currentLyricsKey, request)
    }

    fun setHumanRomanizations(enabled: Boolean) {
        if (enabled && askFirst(SourceDisclosures.forId(SourceDisclosures.GENIUS_ROMANIZATION_ID))) return
        lyricsBackend.humanRomanizations = enabled
        mutableState.value = mutableState.value.copy(humanRomanizations = enabled)
        val selection = shownSelection ?: return
        val request = shownRequest ?: return
        val machine = runCatching { rendered(selection, request.durationSeconds * 1_000L) }.getOrNull() ?: return
        mutableState.value = mutableState.value.copy(lyrics = machine)
        humanize(selection, machine, currentLyricsKey, request)
    }

    /** A custom action's icon, which lives in the player's own resources. */
    private fun actionIcon(packageName: String, iconRes: Int): Bitmap? = actionIcons.getOrPut(packageName to iconRes) {
        try {
            val resources = getApplication<Application>().packageManager.getResourcesForApplication(packageName)
            ResourcesCompat.getDrawable(resources, iconRes, null)?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun commandForTrackChange(command: MediaController.TransportControls.() -> Unit) {
        val active = controller ?: return
        pendingCommand = PendingCommand.Track(active.metadata.trackIdentity(), SystemClock.elapsedRealtime())
        active.transportControls.command()
    }

    private fun attachBestSession(sessions: List<MediaController>) {
        val activeTokens = sessions.map { it.sessionToken }.toSet()
        (observedSessions.keys - activeTokens).forEach { token ->
            observedSessions.remove(token)?.let { old ->
                observedCallbacks.remove(token)?.let(old::unregisterCallback)
            }
        }
        sessions.forEach { session ->
            val token = session.sessionToken
            if (token !in observedSessions) {
                observedSessions[token] = session
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = refreshSessionSelection()
                    override fun onSessionDestroyed() = refreshSessionSelection()
                }
                observedCallbacks[token] = callback
                session.registerCallback(callback, mainHandler)
            }
        }
        val now = SystemClock.elapsedRealtime()
        val best = bestSession(sessions, controller?.sessionToken, sessionStickiness, now)
        mainHandler.removeCallbacks(sessionRecheck)
        if (best?.sessionToken == controller?.sessionToken) {
            // Kept through a stop: look again once its grace runs out.
            sessionStickiness.remainingMs(now)?.takeIf { it > 0 }?.let { mainHandler.postDelayed(sessionRecheck, it + 50L) }
            updateFromController()
            return
        }
        val remote = best?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        SyncTrace.add(now, "session: ${controller?.packageName ?: "none"} -> ${best?.packageName ?: "none"}" + if (remote) " (remote)" else "")
        sessionStickiness.reset()
        detachController()
        controller = best
        refreshOutputRoute()
        best?.registerCallback(controllerCallback, mainHandler)
        updateFromController(metadataChanged = true)
    }

    private fun refreshSessionSelection() {
        runCatching { sessionManager.getActiveSessions(listenerComponent) }
            .onSuccess(::attachBestSession)
    }

    /**
     * A new song: works out the crossfade lead from where the last one ended (before its clock is
     * replaced), and schedules Spotify's Automix resync.
     */
    private fun onSongChange(active: MediaController, metadata: MediaMetadata?) {
        val now = SystemClock.elapsedRealtime()
        val app = getApplication<Application>()
        val crossfadeMs = app.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt(CROSSFADE_PREF, 0) * 1_000L
        val lastDuration = mutableState.value.durationMs
        // It ended on its own when the clock had reached its last seconds, not a skip mid-song.
        val endedOnItsOwn = lastDuration > 0L &&
            clock.lastSongPositionAt(now) >= lastDuration - crossfadeMs - NATURAL_END_SLACK_MS
        transitionOffsetMs = if (crossfadeMs > 0L && endedOnItsOwn) crossfadeMs else 0L
        mainHandler.removeCallbacks(automixResync)
        automixSongKey = null
        if (transitionOffsetMs == 0L && active.packageName == AutomixResync.PLAYER && AutomixResync.enabled(app)) {
            automixSongKey = AutomixResync.songKey(
                active.packageName,
                metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            )
            mainHandler.postDelayed(automixResync, AutomixResync.DELAY_MS)
        }
        if (transitionOffsetMs > 0L) SyncTrace.add(now, "new song after a crossfade: +${transitionOffsetMs} ms")
    }

    /**
     * A moment into a new Spotify song ([AutomixResync]): with a Spotify account signed in, asks
     * Spotify where the song is and moves the lyrics there, with no jump; otherwise (or when
     * Spotify can't say) sends the player to where the lyrics are, as tapping a line does.
     */
    private fun automixResyncNow() {
        val active = controller ?: return
        val key = automixSongKey ?: return
        val playback = active.playbackState ?: return
        if (playback.state != PlaybackState.STATE_PLAYING || pendingCommand != null) return
        if (currentSongKey(active) != key) return
        val app = getApplication<Application>()
        if (!SpotifyAccount.signedIn(app)) {
            resyncBySeek(active, key)
            return
        }
        val trackId = active.metadata.sessionSpotifyId()
        viewModelScope.launch {
            val sample = SpotifyPosition.sample(app, key, trackId)
            if (controller !== active || automixSongKey != key || pendingCommand != null) return@launch
            if (sample == null) {
                resyncBySeek(active, key)
                return@launch
            }
            val now = SystemClock.elapsedRealtime()
            val offset = AutomixResync.offsetFrom(sample.positionMs + (now - sample.atMs), clock.positionAt(now))
            SyncTrace.add(now, "spotify account: ${sample.positionMs + (now - sample.atMs) - clock.positionAt(now)} ms from the lyrics, moved $offset")
            if (offset != 0L) {
                transitionOffsetMs += offset
                clock.seekTo(clock.positionAt(now) + offset, now)
            }
        }
    }

    private fun currentSongKey(active: MediaController) = AutomixResync.songKey(
        active.packageName,
        active.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
        active.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
    )

    /** Sends the player to where the lyrics are, once per song across the app. */
    private fun resyncBySeek(active: MediaController, key: String) {
        val playback = active.playbackState ?: return
        val seekable = (playback.actions and PlaybackState.ACTION_SEEK_TO) != 0L
        if (playback.state != PlaybackState.STATE_PLAYING || !seekable || active.packageName == seekRefusedBy) return
        if (!AutomixResync.claim(key)) return
        SyncTrace.add(SystemClock.elapsedRealtime(), "automix resync at ${currentPositionMs()}")
        seekTo(currentPositionMs(), resync = true)
    }

    private fun detachController() {
        mainHandler.removeCallbacks(automixResync)
        transitionOffsetMs = 0L
        clearTranslation()
        controller?.unregisterCallback(controllerCallback)
        controller = null
        currentTrackIdentity = null
        currentLyricsKey = null
        // A player that says nothing about its song has the same key (none): without this, the
        // last player's song would stay up under it.
        lyricsKeyTaken = false
        manualSpotifyId = null
        // Commands sent to the last player are no answer to wait for from this one.
        pendingCommand = null
        pendingSkip = null
        lyricsJob?.cancel()
        extrasJob?.cancel()
        clock.reset(SystemClock.elapsedRealtime())
        awaitingTrackReport = false
    }

    private fun updateFromController(metadataChanged: Boolean = false, preserveStatus: Boolean = false) {
        val active = controller
        if (active == null) {
            // Song state goes; settings the screens show (sources, keys, the Lyrics Manager) stay.
            val kept = mutableState.value
            mutableState.value = PlayerUiState(
                accessGranted = kept.accessGranted,
                translationPreferences = kept.translationPreferences,
                translationEnabled = kept.translationEnabled,
                deepLKeyPresent = kept.deepLKeyPresent,
                translationKeys = kept.translationKeys,
                aiTranslation = kept.aiTranslation,
                onDeviceModel = kept.onDeviceModel,
                onDeviceDownloadedBytes = kept.onDeviceDownloadedBytes,
                humanRomanizations = kept.humanRomanizations,
                splitBackgroundVocals = kept.splitBackgroundVocals,
                liveCaptions = kept.liveCaptions,
                sourceDisclosure = kept.sourceDisclosure,
                switchedOffSources = kept.switchedOffSources,
                ownKeyHint = kept.ownKeyHint,
                localLyrics = kept.localLyrics,
                customSources = kept.customSources,
                lyricsFolder = kept.lyricsFolder,
                lyricsFolderScanning = kept.lyricsFolderScanning,
                outputLabel = outputRoute.label,
                lyricDelayMs = outputProfiles.delayMs(outputRoute),
                sourceDescriptors = lyricsBackend.descriptors + lyricsBackend.blendDescriptors,
                sourcePolicy = lyricsBackend.policy(),
            )
            return
        }
        val metadata = active.metadata
        val playback = active.playbackState
        val trackIdentity = metadata.trackIdentity()
        val trackChanged = trackIdentity != currentTrackIdentity
        val refreshArtwork = metadataChanged || trackChanged
        // A new song settles a seek: the player moved on (a seek to the end rolls into the next
        // song at 0), and the new song's reports are the ones to follow.
        if (trackChanged && pendingCommand is PendingCommand.Seek) pendingCommand = null
        if (trackChanged) onSongChange(active, metadata)
        val waitingForSeek = pendingCommand is PendingCommand.Seek &&
            pendingCommand?.acknowledged(playback, trackIdentity) != true &&
            SystemClock.elapsedRealtime() - pendingCommand!!.issuedAtMs < 1_500L
        // A command the player has just confirmed (play, pause, seek) re-anchors on its report.
        val confirmed = pendingCommand?.acknowledged(playback, trackIdentity) == true
        val resyncConfirmed = confirmed && (pendingCommand as? PendingCommand.Seek)?.resync == true
        if (confirmed && pendingCommand is PendingCommand.Seek) seekRefusedBy = null
        if (!waitingForSeek && playback != null) {
            reconcileClock(playback, force = trackChanged || confirmed, trackChanged = trackChanged, snap = trackChanged || resyncConfirmed)
        }
        val spotifyId = metadata.sessionSpotifyId()

        val latency = pendingCommand?.takeIf { it.acknowledged(playback, trackIdentity) }
            ?.let { SystemClock.elapsedRealtime() - it.issuedAtMs }
        if (latency != null) pendingCommand = null

        val queueIndex = active.queue.orEmpty().indexOfFirst { it.queueId == playback?.activeQueueItemId }
        val direction = if (trackChanged) {
            // Our own skip says it outright; otherwise a lower queue position means we went back.
            pendingSkip?.takeIf { SystemClock.elapsedRealtime() - it.second < SKIP_DIRECTION_WINDOW_MS }?.first
                ?: if (queueIndex >= 0 && lastQueueIndex >= 0 && queueIndex < lastQueueIndex) {
                    TrackDirection.Backward
                } else TrackDirection.Forward
        } else mutableState.value.trackDirection
        // Not cleared on the change: players often send a new song in steps (title, then artist or
        // cover), and each step must still see the button that caused it. It lapses on its own.
        if (queueIndex >= 0) lastQueueIndex = queueIndex
        if (trackChanged || mutableState.value.queue.firstOrNull { it.current }?.id != playback?.activeQueueItemId) refreshQueue()
        if (trackChanged) {
            currentTrackIdentity = trackIdentity
            trackChangedAt = SystemClock.elapsedRealtime()
        }
        // Buffering, connecting and skipping are in-between states: Spotify reports them (with no
        // custom actions) while it moves between songs, then a pause for a moment. Showing each
        // would flash the play button and the floating buttons, so they keep what was shown.
        // Spotify Free answers a refused skip with an error state (no position, no actions) while
        // the song plays on, and is back to playing a second later: that is in-between too.
        val previous = mutableState.value
        val inBetween = playback?.state in IN_BETWEEN_STATES || playback?.state == PlaybackState.STATE_ERROR
        if (playback?.state == PlaybackState.STATE_ERROR) {
            pendingSkip?.takeIf { SystemClock.elapsedRealtime() - it.second < SKIP_REFUSED_AFTER_MS }?.let { (direction, _) ->
                skipCheckJob?.cancel()
                explainLimit(if (direction == TrackDirection.Forward) PlayerLimit.Skips else PlayerLimit.Previous)
            }
        }
        val loadingPause = playback?.state == PlaybackState.STATE_PAUSED && previous.isPlaying &&
            SystemClock.elapsedRealtime() - trackChangedAt < LOADING_PAUSE_GRACE_MS &&
            pendingCommand !is PendingCommand.PlayState
        val isPlaying = if (inBetween || loadingPause) previous.isPlaying else playback?.state == PlaybackState.STATE_PLAYING
        val customActions = playback?.customActions.orEmpty().map { custom ->
            SessionCustomAction(custom.action, custom.name.toString(), actionIcon(active.packageName, custom.icon))
        }.ifEmpty { if (inBetween) previous.customActions else emptyList() }
        val lyricsKey = metadata.lyricsKey()
        val lyricsChanged = !lyricsKeyTaken || lyricsKey != currentLyricsKey
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (lyricsChanged) {
            lyricsKeyTaken = true
            clearTranslation()
            guessedDocument = null
            currentLyricsKey = lyricsKey
            shownSelection = null
            temporaryLyrics = null
            manualSpotifyId = metadata.savedSpotifyId(overrideStore)
            overrideCheckedAtMs = duration
            lyricsJob?.cancel()
        }
        // Players often send the length after the title: the saved Spotify ID is looked up again
        // with it, since it's only this recording's while the lengths agree.
        var overrideChanged = false
        if (!lyricsChanged && duration != overrideCheckedAtMs) {
            overrideCheckedAtMs = duration
            val saved = metadata.savedSpotifyId(overrideStore)
            if (saved != manualSpotifyId) {
                manualSpotifyId = saved
                overrideChanged = true
            }
        }
        val localKey = if (lyricsChanged) {
            metadata?.let { lookupRequest(it, null) }?.let { LocalLyricsStore.keyOf(it.title, it.artist) }
        } else mutableState.value.localLyricsKey
        val playerChanged = active.packageName != mutableState.value.sourcePackage
        mutableState.value = mutableState.value.copy(
            accessGranted = true,
            sourcePackage = active.packageName,
            playerDelayMs = if (playerChanged) playerDelays.delayMs(active.packageName) else mutableState.value.playerDelayMs,
            playerLabel = if (playerChanged) getApplication<Application>().appLabel(active.packageName) else mutableState.value.playerLabel,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown track",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "Unknown artist",
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            isPlaying = isPlaying,
            canSeek = if (inBetween) previous.canSeek else playback?.actions?.and(PlaybackState.ACTION_SEEK_TO) != 0L && playback != null,
            detectedSpotifyId = manualSpotifyId ?: spotifyId,
            manualSpotifyId = manualSpotifyId,
            // Dropped with the song in the same update, so the last song's canvas never shows on this one.
            canvasUrl = if (lyricsChanged) null else mutableState.value.canvasUrl,
            canvasPending = lyricsChanged || mutableState.value.canvasPending,
            matchInfo = if (lyricsChanged || overrideChanged) {
                if (manualSpotifyId != null) "Manual Spotify ID"
                else if (spotifyId != null) "Spotify ID from media session"
                else "Matching through enabled lyric sources…"
            } else mutableState.value.matchInfo,
            // A new song: Loading (which shows nothing at first), not Idle's "Waiting for a song".
            lyrics = when {
                !lyricsChanged -> mutableState.value.lyrics
                lyricsKey == null -> LyricsState.Idle
                else -> LyricsState.Loading
            },
            providerAttempts = if (lyricsChanged) emptyList() else mutableState.value.providerAttempts,
            localLyricsKey = localKey,
            songDelayMs = if (lyricsChanged) songDelays.delayMs(localKey) else mutableState.value.songDelayMs,
            lyricsLanguage = if (lyricsChanged) null else mutableState.value.lyricsLanguage,
            guessedLanguage = if (lyricsChanged) null else mutableState.value.guessedLanguage,
            humanRomanizedLines = if (lyricsChanged) 0 else mutableState.value.humanRomanizedLines,
            lyricsLanguageOverride = if (lyricsChanged) translationStore.language(localKey) else mutableState.value.lyricsLanguageOverride,
            sourceOverride = if (lyricsChanged) localKey?.let { sourceOverrides.getString(it, null) } else mutableState.value.sourceOverride,
            translationEnabled = mutableState.value.translationEnabled || (lyricsChanged && mutableState.value.translationPreferences.automatic),
            lookupStatus = if (lyricsChanged) null else mutableState.value.lookupStatus,
            lastCommandLatencyMs = latency ?: mutableState.value.lastCommandLatencyMs,
            status = if (waitingForSeek || preserveStatus) mutableState.value.status else null,
            trackDirection = direction,
            customActions = customActions,
            artwork = if (refreshArtwork) {
                val fresh = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    ?: metadata?.description?.iconBitmap
                // Players re-send the same cover with every metadata update, each time as a new
                // bitmap. Keeping the one already shown spares the header and background redoing it.
                val shown = mutableState.value.artwork
                if (!trackChanged && fresh != null && shown != null && fresh.sameAs(shown)) shown else fresh
            } else mutableState.value.artwork,
            artworkUri = if (refreshArtwork) {
                metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                    ?: metadata?.description?.iconUri?.toString()
            } else mutableState.value.artworkUri,
        )
        if (lyricsChanged && !uiStarted.value) {
            // Nobody sees the lyrics with the app in the background, and a lookup per song (every
            // source, the extras, Genius, the songs ahead) kept the radio and CPU busy for nothing.
            // The song that's playing when the screen comes back is looked up then.
            extrasJob?.cancel()
            canvasJob?.cancel()
            lookupDeferred = true
        } else if (lyricsChanged) {
            loadExtras()
            loadCanvas()
            loadLyrics(settle = true)
        } else if (overrideChanged && !lookupDeferred) {
            restartCanvas()
            loadLyrics()
        } else if (metadataChanged && metadata != null && !lookupDeferred) {
            rematchWithLength(metadata)
        }
    }

    /**
     * YouTube Music reports the song's length (and album) a moment after its title, so the lookup
     * started then matched Spotify without them, and a song with several releases of different
     * lengths came out ambiguous. When the length arrives and a source still wants a match, or the
     * lookup is still out, it's asked again with it; answers that didn't need it are kept.
     */
    private fun rematchWithLength(metadata: MediaMetadata) {
        val before = lookedUp ?: return
        if (before.durationSeconds > 0) return
        val now = lookupRequest(metadata, before.spotifyTrackId)
        if (now.durationSeconds <= 0) return
        val known = lookupCache[before].orEmpty()
        if (lyricsJob?.isActive != true && known.values.none { it == ProviderResult.NeedsMatch }) return
        lookupCache[now] = ConcurrentHashMap(known.filterValues { it != ProviderResult.NeedsMatch })
        loadLyrics()
    }

    /**
     * What the screen currently shows that needs a lookup beyond the lyrics: the release year,
     * the artist's header image, the beats for the background. Asking for something new looks it
     * up for the song playing; nothing is fetched that isn't asked for.
     */
    fun setTrackExtrasWanted(wanted: TrackExtrasWanted) {
        screenExtrasWanted = wanted
        applyExtrasWanted()
    }

    private fun applyExtrasWanted() {
        val wanted = screenExtrasWanted.let { if (mutableState.value.calibratingDelay) it.copy(beats = true) else it }
        val added = (wanted.releaseYear && !extrasWanted.releaseYear) ||
            (wanted.artistHeader && !extrasWanted.artistHeader) ||
            (wanted.beats && !extrasWanted.beats)
        extrasWanted = wanted
        if (added) loadExtras()
    }

    /** Starts or ends tapping along to find the output delay: it needs the song's beats. */
    fun setCalibratingDelay(calibrating: Boolean) {
        if (calibrating) UsageStats.count(UsageCounter.CALIBRATION)
        mutableState.value = mutableState.value.copy(calibratingDelay = calibrating)
        applyExtrasWanted()
    }

    /** The playing song's beats in ms, for [TapCalibration], or null while there are none. */
    fun calibrationBeatsMs(): LongArray? = audioAnalysis?.beats
        ?.filter { it.confidence >= CALIBRATION_BEAT_CONFIDENCE }
        ?.map { (it.start * 1000f).toLong() }
        ?.takeIf { it.size >= 2 }
        ?.toLongArray()

    /** The player's own position at [elapsedRealtimeMs], with no delay taken off. */
    fun playerPositionAt(elapsedRealtimeMs: Long): Long = clock.positionAt(elapsedRealtimeMs)

    /**
     * How fast the background should move right now by the song's beats and loudness, or null when
     * there is no analysis for it (the background then keeps its normal speed).
     */
    fun backgroundSpeed(): Float? {
        val analysis = audioAnalysis ?: return null
        return BackgroundSpeed.at(currentPositionMs() / 1000f, analysis)
    }

    /**
     * The vibrations that go with the song in [style] and HapticBeat's [beat] options, or null
     * when there is no analysis for it. The lyrics' word and line times fuse in by the timing
     * and detection choices; with the timing at Spotify-only this is the old score.
     */
    fun musicHaptics(
        style: MusicHapticsStyle,
        beat: HapticBeatOptions = HapticBeatOptions(),
        wordTimes: List<Long> = emptyList(),
        lineTimes: List<Long> = emptyList(),
        wordsEstimated: Boolean = false,
    ): List<MusicHaptic>? {
        val analysis = audioAnalysis ?: return null
        musicHaptics?.takeIf {
            it.analysis === analysis && it.style == style && it.beat == beat &&
                // The same lists (one lyrics object's times) skip comparing them element by element.
                (it.wordTimes === wordTimes || it.wordTimes == wordTimes) &&
                (it.lineTimes === lineTimes || it.lineTimes == lineTimes) &&
                it.wordsEstimated == wordsEstimated
        }?.let { return it.score }
        return MusicHapticScore.buildWithBeat(analysis, style, beat, wordTimes, lineTimes, wordsEstimated)
            .also { musicHaptics = MusicHapticsCache(analysis, style, beat, wordTimes, lineTimes, wordsEstimated, it) }
    }

    /**
     * Whether the screen shows the song's Spotify Canvas: the cover stage is up and set to it.
     * Asking looks it up for the song playing, once; nothing is fetched otherwise.
     */
    fun setCanvasWanted(wanted: Boolean) {
        if (wanted == canvasWanted) return
        canvasWanted = wanted
        if (!wanted) {
            // An unanswered lookup stays pending, to be asked again when wanted.
            canvasJob?.cancel()
        } else if (mutableState.value.canvasPending && uiStarted.value) {
            loadCanvas()
        }
    }

    /** Runs [apply], which sets the stage art to Spotify Canvas, once its disclosure is agreed to. */
    fun chooseCanvas(apply: () -> Unit) {
        if (askFirst(SourceDisclosures.forId(SourceDisclosures.SPOTIFY_CANVAS_ID))) pendingSourceSwitch = apply else apply()
    }

    /** The canvas at [url] wouldn't play: it's forgotten, and the stage falls back. */
    fun canvasFailed(url: String) {
        if (mutableState.value.canvasUrl != url) return
        val trackId = canvasTrackId
        mutableState.value = mutableState.value.copy(canvasUrl = null, canvasPending = false)
        if (trackId != null) viewModelScope.launch { canvasFinder.forget(trackId) }
    }

    /** The song's Spotify ID changed by hand: its canvas is looked up again. */
    private fun restartCanvas() {
        canvasJob?.cancel()
        mutableState.value = mutableState.value.copy(canvasUrl = null, canvasPending = true)
        if (uiStarted.value) loadCanvas()
    }

    /**
     * Looks up the playing song's canvas by its Spotify ID: the player's or the manual one, else
     * the Spotify match. No ID, no canvas. The answer is kept only if the song is still the same.
     */
    private fun loadCanvas() {
        canvasJob?.cancel()
        if (!canvasWanted) return
        val metadata = controller?.metadata ?: return
        val key = metadata.lyricsKey()
        val request = lookupRequest(metadata, manualSpotifyId ?: mutableState.value.detectedSpotifyId)
        if (request.title.isBlank() || request.artist.isBlank()) {
            mutableState.value = mutableState.value.copy(canvasUrl = null, canvasPending = false)
            return
        }
        // Skipping through songs, only the one that stays is looked up.
        val skipping = SystemClock.elapsedRealtime() - lastTrackChangeAt < TRACK_SETTLE_MS
        canvasJob = viewModelScope.launch {
            if (skipping) delay(TRACK_SETTLE_MS)
            val track = LocalTrackMetadata(request.title, request.artist, request.album, request.durationSeconds * 1_000L)
            val trackId = withContext(Dispatchers.IO) { spotifyExtras.trackIds(track, request.spotifyTrackId).firstOrNull() }
            val url = trackId?.let { withContext(Dispatchers.IO) { canvasFinder.find(it) } }
            if (currentLyricsKey != key) return@launch
            canvasTrackId = trackId
            mutableState.value = mutableState.value.copy(canvasUrl = url, canvasPending = false)
        }
    }

    private fun loadExtras() {
        extrasJob?.cancel()
        audioAnalysis = null
        val metadata = controller?.metadata
        val wanted = extrasWanted
        val sessionYear = metadata?.sessionYear()
        val request = metadata?.let { lookupRequest(it, manualSpotifyId ?: mutableState.value.detectedSpotifyId) }
            ?.takeIf { it.title.isNotBlank() && it.artist.isNotBlank() }
        mutableState.value = mutableState.value.copy(
            releaseYear = sessionYear,
            releaseYearPending = wanted.releaseYear && sessionYear == null && request != null,
            artistHeaderUrl = null,
            artistHeaderPending = wanted.artistHeader && request != null,
        )
        val needYear = wanted.releaseYear && sessionYear == null
        if (request == null || !(needYear || wanted.artistHeader || wanted.beats)) return
        // Skipping through songs, only the one that stays is looked up.
        val skipping = SystemClock.elapsedRealtime() - lastTrackChangeAt < TRACK_SETTLE_MS
        extrasJob = viewModelScope.launch {
            if (skipping) delay(TRACK_SETTLE_MS)
            val track = LocalTrackMetadata(request.title, request.artist, request.album, request.durationSeconds * 1_000L)
            val trackIds = withContext(Dispatchers.IO) { spotifyExtras.trackIds(track, request.spotifyTrackId) }
            val trackId = trackIds.firstOrNull()
            if (wanted.beats && trackId != null) launch {
                audioAnalysis = withContext(Dispatchers.IO) { spotifyExtras.audioAnalysis(trackId) }
            }
            if (wanted.artistHeader) launch {
                val artistId = trackId?.let { withContext(Dispatchers.IO) { spotifyExtras.details(it) } }?.artistIds?.firstOrNull()
                val url = if (trackId != null && artistId != null) {
                    withContext(Dispatchers.IO) { spotifyExtras.artistHeaderUrl(artistId, trackId) }
                } else null
                mutableState.value = mutableState.value.copy(artistHeaderUrl = url, artistHeaderPending = false)
            }
            if (needYear) {
                val year = withContext(Dispatchers.IO) {
                    spotifyExtras.releaseYear(trackIds)
                        ?: itunesYear.find(request.title, request.artist, request.album, request.durationSeconds * 1_000L)
                }
                mutableState.value = mutableState.value.copy(releaseYear = year, releaseYearPending = false)
            }
        }
    }

    /** The year the player itself gives for the song, if any. */
    private fun MediaMetadata.sessionYear(): String? {
        getLong(MediaMetadata.METADATA_KEY_YEAR).takeIf { it in 1000L..9999L }?.let { return it.toString() }
        return getString(MediaMetadata.METADATA_KEY_DATE)?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    }

    fun setUiStarted(started: Boolean) {
        uiStarted.value = started
        NowPlayingHolder.setAppShowing(started)
        TrafficCounter.background = !started
        if (!started) {
            // Songs fetched ahead are for someone looking; nobody is now.
            warmJob?.cancel()
        } else if (lookupDeferred) {
            lookupDeferred = false
            loadExtras()
            loadCanvas()
            loadLyrics()
        } else if (canvasWanted && mutableState.value.canvasPending && canvasJob?.isActive != true) {
            loadCanvas()
        }
    }

    private fun startTicker() = viewModelScope.launch {
        while (isActive) {
            if (!uiStarted.value) {
                // Nothing is shown: park instead of polling. The session's
                // callbacks still arrive, and the first pass back runs the periodic check at once.
                uiStarted.first { it }
                lastPeriodicCheckMs = 0L
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastPeriodicCheckMs >= PERIODIC_CHECK_MS) {
                lastPeriodicCheckMs = now
                refreshOutputRoute()
                if (pendingCommand !is PendingCommand.Seek) {
                    updateFromController(preserveStatus = true)
                }
            }
            if (controller != null) {
                val seek = pendingCommand as? PendingCommand.Seek
                if (seek != null && SystemClock.elapsedRealtime() - seek.issuedAtMs >= 1_500L) {
                    pendingCommand = null
                    seekRefusedBy = controller?.packageName
                    SyncTrace.add(SystemClock.elapsedRealtime(), "seek to ${seek.targetMs} not confirmed")
                    updateFromController()
                    // Spotify Free lists seeking but ignores it: resync from its reports instead.
                    if (seek.resync) resyncFromReports()
                    mutableState.value = mutableState.value.copy(
                        status = "Seek not confirmed by ${controller?.packageName ?: "player"}; timeline restored",
                    )
                    explainLimit(PlayerLimit.Seek)
                }
            }
            val seekPending = pendingCommand is PendingCommand.Seek
            val wait = tickerWaitMs(seekPending, SystemClock.elapsedRealtime() - lastPeriodicCheckMs)
            withTimeoutOrNull(wait) { tickerWake.receive() }
        }
    }

    private fun refreshOutputRoute() {
        val active = controller
        val route = if (active?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            AudioOutputRoute("remote:${active.packageName}", "Remote playback (device unknown)")
        } else outputProfiles.currentRoute()
        if (route.key != outputRoute.key) {
            outputRoute = route
            SyncTrace.add(SystemClock.elapsedRealtime(), "output: ${route.label}")
            mutableState.value = mutableState.value.copy(
                outputLabel = route.label,
                lyricDelayMs = outputProfiles.delayMs(route),
            )
        }
    }

    /**
     * [force]: take the report even if it isn't new. [snap]: move the clock to it outright rather
     * than count it as a vote ([LyricClock]).
     */
    private fun reconcileClock(playback: PlaybackState, force: Boolean = false, trackChanged: Boolean = false, snap: Boolean = trackChanged) {
        if (playback.position < 0L) return
        val playing = playback.state == PlaybackState.STATE_PLAYING
        val speed = LyricClock.playingSpeed(playing, playback.playbackSpeed)
        val relayed = controller?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        // After a crossfade the player counts the song from its switch, behind its audio.
        val position = playback.position + if (playing) transitionOffsetMs else 0L
        val report = LyricClock.Report(playback.state, playing, position, playback.lastPositionUpdateTime, speed, relayed)
        // Spotify reports buffering at its end position about a second before the next song
        // starts. Stopping the clock there freezes a line that runs to the end halfway through,
        // so the clock carries on until the next song's report.
        val duration = mutableState.value.durationMs
        if (!trackChanged && playback.state == PlaybackState.STATE_BUFFERING && clock.isPlaying &&
            duration > 0L && playback.position >= duration - END_BUFFERING_MS
        ) {
            SyncTrace.add(SystemClock.elapsedRealtime(), "buffering at the end (pos=${playback.position} of $duration): clock runs on")
            endBufferingReport = report
            return
        }
        // The end report skipped above is the last song's, not news for the next one.
        val fresh = clock.isFresh(report) && report != endBufferingReport
        val now = SystemClock.elapsedRealtime()
        // A report that sent the clock back to the start just before the song changed is the new
        // song's own (Spotify can name the song a second or two after its report): it's kept.
        val startedSong = trackChanged && !fresh && clock.startedSong(report, now)
        if (startedSong) SyncTrace.add(now, "new song: its start came before it, kept")
        if (trackChanged && !startedSong && (report == endBufferingReport || (!fresh && now - lastReportAt > TRACK_REPORT_FRESH_MS))) {
            // No report for the new song yet: the one we have is the previous song's position.
            // Seeding the clock from it jumps the new lyrics ahead and forces a scroll, so the
            // song starts from 0 until the player reports.
            Log.d(SYNC_TAG, "track changed before its report; clock starts at 0")
            SyncTrace.add(now, "new song, no report yet: clock at 0")
            clock.startTrack(now, speed, playing)
            if (transitionOffsetMs > 0L) clock.seekTo(transitionOffsetMs, now)
            awaitingTrackReport = true
            return
        }
        if (!force && !fresh) return
        // A pause report is judged as ever ([ClockCorrection.reportBiasMs]): it is the one most often stale.
        val resync = resyncOnNextReport && fresh && playing
        if (resync) resyncOnNextReport = false
        val firstOfTrack = awaitingTrackReport && fresh
        if (firstOfTrack) awaitingTrackReport = false
        if (fresh) lastReportAt = now
        val outcome = clock.onReport(report, now, snap = snap || resync || firstOfTrack)
        Log.d(
            SYNC_TAG,
            "report ${controller?.packageName} state=${playback.state} pos=${playback.position} " +
                "age=${now - playback.lastPositionUpdateTime}ms speed=${playback.playbackSpeed} " +
                "drift=${outcome.driftMs}ms applied=${outcome.appliedMs}ms bias=${outcome.biasMs}ms force=$force snap=${snap || resync || firstOfTrack} relayed=$relayed",
        )
        SyncTrace.add(
            now,
            (if (trackChanged) "new song: " else "") +
                "state=${playback.state} pos=${playback.position} age=${now - playback.lastPositionUpdateTime} " +
                "speed=${playback.playbackSpeed} drift=${outcome.driftMs} moved=${outcome.appliedMs} bias=${outcome.biasMs}" +
                (if (snap || resync || firstOfTrack) " snap" else "") + (if (relayed) " relayed" else ""),
        )
        mutableState.value = mutableState.value.copy(clockDriftMs = outcome.driftMs)
    }

    fun currentPositionMs(): Long {
        val position = clock.positionAt(SystemClock.elapsedRealtime())
        return mutableState.value.durationMs.takeIf { it > 0L }?.let(position::coerceAtMost) ?: position
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(automixResync)
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        mainHandler.removeCallbacks(sessionRecheck)
        stopObservingSessions()
        detachController()
    }

    private fun stopObservingSessions() {
        observedSessions.forEach { (token, session) ->
            observedCallbacks[token]?.let(session::unregisterCallback)
        }
        observedSessions.clear()
        observedCallbacks.clear()
    }

    private sealed interface PendingCommand {
        val issuedAtMs: Long
        fun acknowledged(playback: PlaybackState?, trackIdentity: String?): Boolean

        data class PlayState(val playing: Boolean, override val issuedAtMs: Long) : PendingCommand {
            override fun acknowledged(playback: PlaybackState?, trackIdentity: String?) =
                playback != null && (playback.state == PlaybackState.STATE_PLAYING) == playing
        }

        /** [resync]: sent by [resync] to draw a fresh report, which the clock then takes outright. */
        data class Seek(val targetMs: Long, override val issuedAtMs: Long, val resync: Boolean = false) : PendingCommand {
            override fun acknowledged(playback: PlaybackState?, trackIdentity: String?) =
                playback != null && kotlin.math.abs(playback.position - targetMs) < 750L
        }

        data class Track(val previousIdentity: String?, override val issuedAtMs: Long) : PendingCommand {
            override fun acknowledged(playback: PlaybackState?, trackIdentity: String?) =
                trackIdentity != previousIdentity
        }
    }
}

private fun MediaMetadata?.trackIdentity(): String? = this?.let {
    trackIdentity(
        mediaId = description.mediaId ?: getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
        title = getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
        album = getString(MediaMetadata.METADATA_KEY_ALBUM),
        durationMs = getLong(MediaMetadata.METADATA_KEY_DURATION),
    )
}

internal fun trackIdentity(
    mediaId: String?,
    title: String?,
    artist: String?,
    album: String?,
    durationMs: Long,
): String? {
    if (mediaId.isNullOrBlank() && title.isNullOrBlank() && artist.isNullOrBlank()) return null
    return listOf(mediaId.orEmpty(), title.orEmpty(), artist.orEmpty(), album.orEmpty(), durationMs.toString())
        .joinToString("\u001f")
}

internal fun String?.spotifyTrackId(): String? {
    val value = this?.trim().orEmpty()
    val candidate = when {
        value.startsWith("spotify:track:") -> value.substringAfterLast(':')
        "/track/" in value -> value.substringAfter("/track/").substringBefore('?').substringBefore('/')
        else -> value
    }
    return candidate.takeIf { it.length == 22 && it.all(Char::isLetterOrDigit) }
}


/** [packageName]'s name as the launcher shows it, or the package itself when it can't be read. */
private fun android.content.Context.appLabel(packageName: String): String = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
