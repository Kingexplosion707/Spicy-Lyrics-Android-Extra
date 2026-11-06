package com.tx24.spicyplayer.playback

import android.content.Context
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.network.data.counted
import com.tx24.spicyplayer.translation.DeepLTranslator
import com.tx24.spicyplayer.translation.DiskTranslationCache
import com.tx24.spicyplayer.translation.GeminiNanoTranslator
import com.tx24.spicyplayer.translation.GeminiTranslator
import com.tx24.spicyplayer.translation.GeniusTranslationPair
import com.tx24.spicyplayer.translation.GoogleTranslator
import com.tx24.spicyplayer.translation.OpenAiTranslator
import com.tx24.spicyplayer.translation.SongLanguageDetector
import com.tx24.spicyplayer.translation.SystemLanguageDetector
import com.tx24.spicyplayer.translation.TranslationDocument
import com.tx24.spicyplayer.translation.TranslationEngine
import com.tx24.spicyplayer.translation.TranslationOutcome
import com.tx24.spicyplayer.translation.TranslationProvider
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.translation.languageCode
import com.tx24.spicyplayer.translation.mixedScripts
import com.tx24.spicyplayer.translation.openAiEndpoint
import com.tx24.spicyplayer.translation.skipTranslation
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The lyrics screen's automatic translation, for what shows its lyrics while the screen is
 * closed (the live wallpaper). It decides as the screen does, from the same settings: only with
 * Translate automatically on, never for a song already in the target language or a Don't
 * translate one, the lyrics' own translations first, then the chosen translator. It shares the
 * screen's translation cache, keyed the same way, so a song the screen has translated is shown
 * from there without asking again.
 *
 * It never asks for consent: a translator the screen hasn't been allowed to use yet is not sent
 * anything here, and the song stays untranslated until it is.
 */
internal class BackgroundTranslation(context: Context) {
    private val app = context.applicationContext
    private val store = TranslationSettingsStore(app)
    private val guesser = SystemLanguageDetector(app)
    private val engine = TranslationEngine(
        SongLanguageDetector { null },
        DiskTranslationCache(File(app.cacheDir, "translations")),
        ownDetector = guesser,
    )
    private val client = okhttp3.OkHttpClient.Builder().counted()
        .callTimeout(45, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    /**
     * [lyrics]' translation for the song [title] by [artist], found by the source [sourceId] and
     * the provider named in [lyrics], or null when it isn't to be translated or can't be now.
     * Runs off the main thread.
     */
    suspend fun translate(
        lyrics: LyricsState.Ready,
        title: String,
        artist: String,
        sourceId: String,
        human: suspend (String) -> GeniusTranslationPair?,
    ): TranslationResult? = runCatching {
        val preferences = store.read()
        if (!preferences.automatic || lyrics.lines.isEmpty()) return null
        val song = LocalLyricsStore.keyOf(title, artist)
        val document = TranslationDocument.from(song, "$sourceId:${lyrics.provider}:${lyrics.source.orEmpty()}", lyrics.lines, lyrics.translationLanguage)
        val override = store.language(song)
        val source = engine.language(document, override)
        if (skipTranslation(source, preferences.targetLanguage, preferences.excludedLanguages)) return null
        if (source == null) {
            // The phone sure it's in the target language: nothing is sent, as on the screen.
            val lines = document.lines.filterNot { it.boundary }.map { it.text }
            val guess = if (mixedScripts(lines)) null else languageCode(guesser.detect(document.context))
            if (skipTranslation(guess, preferences.targetLanguage, preferences.excludedLanguages)) return null
        }
        engine.ownResult(document, preferences, source)?.let { return it }
        val provider = preferences.provider
        val service = if (provider == TranslationProvider.OpenAi) openAiEndpoint(store.ai().baseUrl)?.host else null
        if (!store.disclosed(provider, preferences.humanTranslations, service)) return null
        val translator = when (provider) {
            TranslationProvider.Google -> GoogleTranslator(client)
            TranslationProvider.DeepL -> DeepLTranslator(client, store.key())
            TranslationProvider.OpenAi -> store.ai().let {
                OpenAiTranslator(client, it.baseUrl, store.key(TranslationProvider.OpenAi), it.model)
            }
            TranslationProvider.Gemini -> GeminiTranslator(client, store.key(TranslationProvider.Gemini), store.ai().geminiModel)
            TranslationProvider.GeminiNano -> GeminiNanoTranslator()
        }
        val fallback = GoogleTranslator(client).takeIf {
            provider != TranslationProvider.Google && store.disclosed(TranslationProvider.Google, false)
        }
        val outcome = engine.translate(
            document, preferences, source, translator,
            fallback = fallback,
            human = { human(preferences.targetLanguage) },
        )
        (outcome as? TranslationOutcome.Translated)?.result
    }.getOrNull()
}
