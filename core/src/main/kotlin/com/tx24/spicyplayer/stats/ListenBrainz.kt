package com.tx24.spicyplayer.stats

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * ListenBrainz scrobbling (Settings → Listening stats): what a listen looks like on its way to
 * `submit-listens`, and when a song counts as listened to. Only the song's title, artist, album,
 * length and the time it was heard are sent, with the user's own token.
 */
object ListenBrainz {
    const val SUBMIT_URL = "https://api.listenbrainz.org/1/submit-listens"
    const val VALIDATE_URL = "https://api.listenbrainz.org/1/validate-token"
    const val TOKEN_URL = "https://listenbrainz.org/settings/"
    /** ListenBrainz takes at most this many listens in one request. */
    const val MAX_PER_REQUEST = 100

    data class Listen(val title: String, val artist: String, val album: String, val listenedAtSec: Long, val durationMs: Long)

    /**
     * How much of a song has to be heard for it to count: half of it or four minutes, whichever
     * comes first (ListenBrainz's own rule), and never under half a minute.
     */
    fun listenAfterMs(durationMs: Long): Long =
        if (durationMs <= 0L) 240_000L else (durationMs / 2).coerceIn(30_000L, 240_000L)

    /** The body for `submit-listens`: one listen as "single", several as "import". Null when [listens] is empty. */
    fun payload(listens: List<Listen>): String? {
        val valid = listens.filter { it.title.isNotBlank() && it.artist.isNotBlank() }.take(MAX_PER_REQUEST)
        if (valid.isEmpty()) return null
        return JsonObject().apply {
            addProperty("listen_type", if (valid.size == 1) "single" else "import")
            add("payload", JsonArray().apply { valid.forEach { add(it.toJson()) } })
        }.toString()
    }

    private fun Listen.toJson() = JsonObject().apply {
        addProperty("listened_at", listenedAtSec)
        add("track_metadata", JsonObject().apply {
            addProperty("artist_name", artist.trim())
            addProperty("track_name", title.trim())
            if (album.isNotBlank()) addProperty("release_name", album.trim())
            add("additional_info", JsonObject().apply {
                addProperty("submission_client", "Spicy Lyrics Plus")
                if (durationMs > 0L) addProperty("duration_ms", durationMs)
            })
        })
    }

    /** Listens waiting to be sent, as kept on disk between tries. */
    fun queueToJson(listens: List<Listen>): String = JsonArray().apply {
        listens.forEach { l ->
            add(JsonObject().apply {
                addProperty("t", l.title); addProperty("a", l.artist); addProperty("b", l.album)
                addProperty("at", l.listenedAtSec); addProperty("d", l.durationMs)
            })
        }
    }.toString()

    fun queueFromJson(json: String?): List<Listen> = runCatching {
        JsonParser.parseString(json.orEmpty()).asJsonArray.map { e ->
            val o = e.asJsonObject
            Listen(o.get("t").asString, o.get("a").asString, o.get("b").asString, o.get("at").asLong, o.get("d").asLong)
        }
    }.getOrDefault(emptyList())

    /** The user name in a `validate-token` answer, or null when the token isn't valid. */
    fun validatedUser(json: String?): String? = runCatching {
        val o = JsonParser.parseString(json.orEmpty()).asJsonObject
        if (o.get("valid")?.asBoolean == true) o.get("user_name")?.asString else null
    }.getOrNull()
}
