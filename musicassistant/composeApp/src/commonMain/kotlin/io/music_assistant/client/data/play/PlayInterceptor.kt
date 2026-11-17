package io.music_assistant.client.data.play

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Something that can play a request itself instead of the server, given its Spotify URIs: Spicy
 * Lyrics Plus plays Spotify songs through its own Spotify sign-in, on the phone's Spotify app for
 * this device, or on the Spotify Connect device Music Assistant made for another player.
 */
interface PlayInterceptor {
    /** Whether to try at all for this player ([local]: this device). Cheap; no network. */
    fun handles(local: Boolean): Boolean

    /**
     * Plays [uris] itself: track URIs, or one album, playlist or artist URI. [startUri] is the
     * track to start from, where the request names one. True when it did; false leaves the
     * request to the server as usual.
     */
    suspend fun play(
        local: Boolean,
        playerName: String?,
        uris: List<String>,
        startUri: String?,
        shuffle: Boolean,
        option: String,
    ): Boolean
}

/** Finds the Spotify URIs of a request's media through the server, for a [PlayInterceptor]. */
internal object SpotifyMedia {
    private val kinds = setOf("track", "album", "playlist", "artist")

    /** Each of [media] as a Spotify URI, or null when any of them has none (then the server plays it all). */
    suspend fun uris(apiClient: ServiceClient, media: List<String>): List<String>? {
        if (media.isEmpty()) return null
        val out = ArrayList<String>(media.size)
        for (uri in media) out += uri(apiClient, uri) ?: return null
        // A context (album, playlist, artist) plays alone; with anything else, the server plays it.
        if (out.size > 1 && out.any { !it.startsWith("spotify:track:") }) return null
        return out
    }

    private suspend fun uri(apiClient: ServiceClient, maUri: String): String? {
        direct(maUri)?.let { return it }
        val item = apiClient.sendRequest(Request.Library.itemByUri(maUri)).getOrNull()?.result as? JsonObject ?: return null
        val kind = item["media_type"]?.jsonPrimitive?.contentOrNull?.takeIf { it in kinds } ?: return null
        if (item["provider"]?.jsonPrimitive?.contentOrNull?.startsWith("spotify") == true) {
            item["item_id"]?.jsonPrimitive?.contentOrNull?.let { return "spotify:$kind:$it" }
        }
        val mappings = item["provider_mappings"] as? JsonArray ?: return null
        for (mapping in mappings) {
            val m = mapping as? JsonObject ?: continue
            val domain = m["provider_domain"]?.jsonPrimitive?.contentOrNull ?: continue
            if (!domain.startsWith("spotify")) continue
            if (m["available"]?.jsonPrimitive?.contentOrNull == "false") continue
            val id = m["item_id"]?.jsonPrimitive?.contentOrNull ?: continue
            return "spotify:$kind:$id"
        }
        return null
    }

    /** `spotify://track/<id>` (a Spotify provider's own URI) without asking the server. */
    private fun direct(maUri: String): String? {
        val match = Regex("^spotify[^:]*://(track|album|playlist|artist)/([A-Za-z0-9]{22})$").find(maUri) ?: return null
        return "spotify:${match.groupValues[1]}:${match.groupValues[2]}"
    }

    /** The player's name as the server shows it, for finding its Spotify Connect device. */
    suspend fun playerName(apiClient: ServiceClient, playerId: String): String? {
        val players = apiClient.sendRequest(Request.Player.all()).getOrNull()?.result as? JsonArray ?: return null
        for (player in players) {
            val p = player as? JsonObject ?: continue
            if (p["player_id"]?.jsonPrimitive?.contentOrNull != playerId) continue
            return (p["display_name"] ?: p["name"])?.jsonPrimitive?.contentOrNull
        }
        return null
    }
}
