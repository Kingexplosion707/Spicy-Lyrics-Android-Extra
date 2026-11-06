package com.tx24.spicyplayer.playback

import android.content.Context
import androidx.core.content.edit
import com.tx24.spicyplayer.translation.TranslationPreferences
import com.tx24.spicyplayer.translation.TranslationProvider
import com.tx24.spicyplayer.translation.TranslationConsent
import com.tx24.spicyplayer.translation.languageCode

/** Where an AI translator is: an OpenAI-compatible service's address and model, and the Gemini model. */
data class AiTranslationSettings(val baseUrl: String = OPENAI_BASE_URL, val model: String = "", val geminiModel: String = "") {
    /** Which service and model [provider] translates with, for telling their translations apart. */
    fun variant(provider: TranslationProvider): String = when (provider) {
        TranslationProvider.OpenAi -> "${com.tx24.spicyplayer.translation.openAiEndpoint(baseUrl)?.host.orEmpty()} $model"
        TranslationProvider.Gemini -> geminiModel
        else -> ""
    }

    companion object {
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"
    }
}

/** Lookup preferences travel in backups; credentials and consent stay on this device. */
internal class TranslationSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("translation", 0)
    private val keys = context.getSharedPreferences("deepl_key", 0)
    private val languages = context.getSharedPreferences("song_languages", 0)

    fun read(): TranslationPreferences {
        val stored = preferences.getString("provider", null)
        val provider = TranslationProvider.stored(stored)
        if (stored != provider.name) preferences.edit { putString("provider", provider.name) }
        return TranslationPreferences(
            // Read as the listed code: a phone set to Filipino reports `fil`, an older one `iw` for Hebrew.
            targetLanguage = languageCode(preferences.getString("targetLanguage", null) ?: java.util.Locale.getDefault().language) ?: "en",
            automatic = preferences.getBoolean("automatic", false),
            provider = provider,
            excludedLanguages = preferences.getStringSet("excludedLanguages", emptySet()).orEmpty().mapNotNull(::languageCode).toSet(),
            humanTranslations = preferences.getBoolean("humanTranslations", true),
            aiVariant = ai().variant(provider),
        )
    }
    fun save(value: TranslationPreferences) {
        preferences.edit {
            putString("targetLanguage", value.targetLanguage)
            putBoolean("automatic", value.automatic)
            putString("provider", value.provider.name)
            putStringSet("excludedLanguages", value.excludedLanguages)
            putBoolean("humanTranslations", value.humanTranslations)
        }
    }
    fun key(): String = keys.getString("key", "").orEmpty()
    fun saveKey(key: String) { keys.edit { putString("key", key) } }

    /** [provider]'s API key, kept on this device only (the DeepL key's store). */
    fun key(provider: TranslationProvider): String = keys.getString(keyName(provider), "").orEmpty()
    fun saveKey(provider: TranslationProvider, key: String) { keys.edit { putString(keyName(provider), key) } }
    private fun keyName(provider: TranslationProvider) = when (provider) {
        TranslationProvider.DeepL -> "key"
        else -> provider.name.lowercase(java.util.Locale.ROOT) + "_key"
    }

    fun ai(): AiTranslationSettings = AiTranslationSettings(
        baseUrl = preferences.getString("aiBaseUrl", null) ?: AiTranslationSettings.OPENAI_BASE_URL,
        model = preferences.getString("aiModel", null).orEmpty(),
        geminiModel = preferences.getString("geminiModel", null) ?: com.tx24.spicyplayer.translation.GeminiModels.DEFAULT,
    )
    fun saveAi(value: AiTranslationSettings) {
        preferences.edit {
            putString("aiBaseUrl", value.baseUrl.trim())
            putString("aiModel", value.model.trim())
            putString("geminiModel", value.geminiModel.trim())
        }
    }
    fun language(song: String?): String? = song?.let { languageCode(languages.getString(it, null)) }
    fun saveLanguage(song: String, language: String?) {
        languages.edit { if (language == null) remove(song) else putString(song, language) }
    }
    fun disclosed(provider: TranslationProvider, human: Boolean, service: String? = null): Boolean =
        TranslationConsent.covered(preferences.getStringSet("disclosed", emptySet()).orEmpty(), provider, human, service)
    fun disclose(id: String) {
        preferences.edit { putStringSet("disclosed", preferences.getStringSet("disclosed", emptySet()).orEmpty() + id) }
    }
}
