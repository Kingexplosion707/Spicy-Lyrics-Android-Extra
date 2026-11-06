package com.tx24.spicyplayer.backup

import android.content.Context
import android.content.SharedPreferences
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.ui.settings.AppSettings
import kotlin.reflect.KClass

/** Reads [SettingsBackup.STORES] out of the app's SharedPreferences, and writes a backup back in. */
class SettingsBackupStore(private val context: Context) {

    fun export(): String = SettingsBackup.encode(
        SettingsBackup.STORES.associateWith { name -> prefs(name).all.filterKeys { it !in kept(name) } },
        BuildConfig.VERSION_NAME,
    )

    /**
     * Replaces each store the backup has with its contents; stores it doesn't have stay as they
     * are. Throws [SettingsBackup.InvalidBackup] before changing anything if the file is bad.
     */
    fun restore(text: String) {
        val stores = SettingsBackup.decode(text)
        for ((name, values) in stores) {
            val prefs = prefs(name)
            val kept = kept(name)
            val expected = expectedType(name, prefs)
            // A value of the wrong type would throw every time the app reads it, launch included.
            val restored = values.filter { (key, value) -> key !in kept && expected(key)?.isInstance(value) != false }.toMutableMap()
            // The custom font's file isn't in the backup, so "Custom" only comes back if this
            // install already has one picked.
            if (name == "ui" && restored["lyricsFont"] == "Custom" && prefs.getString("customFontFile", "").isNullOrEmpty()) {
                restored.remove("lyricsFont")
            }
            prefs.edit().apply {
                prefs.all.keys.filter { it !in kept }.forEach(::remove)
                restored.forEach { (key, value) -> put(key, value) }
            }.commit()
        }
    }

    /**
     * The type [store] reads each key as: the settings' own for `ui`, a fixed one for the stores
     * that hold one kind of value, else whatever this install already has saved there.
     */
    private fun expectedType(store: String, prefs: SharedPreferences): (String) -> KClass<*>? {
        val known: Map<String, KClass<*>> = when (store) {
            "ui" -> AppSettings(prefs).storedTypes
            "lyrics_sources" -> mapOf(
                "defaults" to Int::class, "order" to String::class, "custom" to String::class,
                "disabled" to Set::class, "blends" to Set::class,
                "sourceOrder" to String::class, "sourcesOff" to Set::class, "pairsOn" to Set::class,
                "pairsOff" to Set::class, "syncTypesOff" to Set::class,
                "humanRomanizations" to Boolean::class,
                "disclosed" to Set::class, "switchedOff" to Set::class,
            )
            "translation" -> mapOf(
                "targetLanguage" to String::class, "automatic" to Boolean::class,
                "provider" to String::class, "excludedLanguages" to Set::class,
                "humanTranslations" to Boolean::class,
                "aiBaseUrl" to String::class, "aiModel" to String::class, "geminiModel" to String::class,
            )
            else -> emptyMap()
        }
        val current = prefs.all
        return { key ->
            when (store) {
                "song_delays", "lyric_output_delays" -> Int::class
                "spotify_id_overrides", "song_languages" -> String::class
                else -> known[key] ?: current[key]?.let { it::class }
            }
        }
    }

    private fun prefs(name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * Keys left out of a backup and left alone by a restore: the custom font's file is on this
     * device, and the usage-stats answer and the finished intro happened on this device.
     */
    private fun kept(store: String): Set<String> =
        when (store) {
            "ui" -> setOf("customFontFile", "customFontName", UsageStats.KEY_ENABLED, UsageStats.KEY_ASKED, "introDone")
            "translation" -> setOf("disclosed")
            else -> emptySet()
        }

    @Suppress("UNCHECKED_CAST")
    private fun SharedPreferences.Editor.put(key: String, value: Any) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }
}
