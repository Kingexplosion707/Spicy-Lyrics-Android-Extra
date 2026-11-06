package com.tx24.spicyplayer.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * The settings backup file: each saved store's values as JSON, typed so they come back as what
 * they were (an Int stays an Int, not a Double). Only [STORES] go in or come back out; anything
 * else in a file is ignored rather than written into the app.
 *
 * ```
 * { "format": "spicy-lyrics-mobile-settings", "version": 1, "app": "0.5.1",
 *   "stores": { "ui": { "lyricsSize": { "type": "string", "value": "Large" } } } }
 * ```
 */
object SettingsBackup {
    const val FORMAT = "spicy-lyrics-mobile-settings"
    /** Backups saved while the app was called Spicy Player. */
    private const val OLD_FORMAT = "spicy-player-settings"
    const val VERSION = 1

    /**
     * What a backup holds: how everything looks and behaves, the source order, both kinds of
     * delay and the songs pointed at a Spotify recording. Not update state, and not the Spicy
     * Lyrics key, which shouldn't travel in a file people pass around.
     */
    val STORES = listOf("ui", "lyrics_sources", "lyric_output_delays", "song_delays", "player_delays", "spotify_id_overrides", "translation", "song_languages")

    class InvalidBackup(message: String) : Exception(message)

    fun encode(stores: Map<String, Map<String, Any?>>, appVersion: String): String {
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            addProperty("app", appVersion)
        }
        val storesJson = JsonObject()
        for (name in STORES) {
            val values = stores[name] ?: continue
            val store = JsonObject()
            values.toSortedMap().forEach { (key, value) -> typed(value)?.let { store.add(key, it) } }
            storesJson.add(name, store)
        }
        root.add("stores", storesJson)
        return GsonBuilder().setPrettyPrinting().create().toJson(root)
    }

    /** The stores in [text], by name; throws [InvalidBackup] with a message fit to show. */
    fun decode(text: String): Map<String, Map<String, Any>> {
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw InvalidBackup("That file isn't a settings backup.")
        if (root.string("format") !in setOf(FORMAT, OLD_FORMAT)) throw InvalidBackup("That file isn't a settings backup.")
        val version = runCatching { root.get("version").asInt }.getOrNull()
            ?: throw InvalidBackup("That file isn't a settings backup.")
        if (version > VERSION) throw InvalidBackup("That backup is from a newer version of the app. Update first.")
        val stores = root.get("stores")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw InvalidBackup("That backup has no settings in it.")
        return STORES.mapNotNull { name ->
            val store = stores.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            name to store.entrySet().mapNotNull { (key, element) -> untyped(element)?.let { key to it } }.toMap()
        }.toMap()
    }

    private fun typed(value: Any?): JsonObject? {
        val (type, json) = when (value) {
            is Boolean -> "boolean" to JsonPrimitive(value)
            is Int -> "int" to JsonPrimitive(value)
            is Long -> "long" to JsonPrimitive(value)
            is Float -> "float" to JsonPrimitive(value)
            is String -> "string" to JsonPrimitive(value)
            is Set<*> -> "stringSet" to JsonArray().apply { value.filterIsInstance<String>().sorted().forEach(::add) }
            else -> return null
        }
        return JsonObject().apply {
            addProperty("type", type)
            add("value", json)
        }
    }

    private fun untyped(element: JsonElement): Any? = runCatching {
        val entry = element.asJsonObject
        val value = entry.get("value")
        when (entry.string("type")) {
            "boolean" -> value.asJsonPrimitive.takeIf { it.isBoolean }?.asBoolean
            "int" -> value.asJsonPrimitive.takeIf { it.isNumber }?.asInt
            "long" -> value.asJsonPrimitive.takeIf { it.isNumber }?.asLong
            "float" -> value.asJsonPrimitive.takeIf { it.isNumber }?.asFloat
            "string" -> value.asJsonPrimitive.takeIf { it.isString }?.asString
            "stringSet" -> value.asJsonArray.map { it.asString }.toSet()
            else -> null
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
