package com.tx24.spicyplayer.network.data.spotify

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.abs

/**
 * What the signed-in Spotify account is playing right now, read from the Web API's player state
 * (`GET /v1/me/player`): the song, whether it plays, and where it is, on whichever device
 * plays it.
 */
data class ConnectState(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val playing: Boolean,
    val progressMs: Long,
    val deviceId: String?,
    val deviceName: String?,
) {
    /** Two states are the same song when their Spotify IDs agree. */
    fun sameSong(other: ConnectState?): Boolean = other != null && other.trackId == trackId
}

object ConnectPlayback {
    /**
     * The state in [json], or null when nothing is playing, the player is on a podcast episode or
     * an ad (no lyrics to follow), or the text isn't a player state.
     */
    fun parse(json: String): ConnectState? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val item = root.obj("item") ?: return null
        if (item.str("type") != "track") return null
        val id = item.str("id")?.takeIf { it.length == 22 } ?: return null
        val title = item.str("name")?.takeIf { it.isNotBlank() } ?: return null
        val artists = item.get("artists")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.str("name") }
            ?.filter { it.isNotBlank() }.orEmpty()
        val album = item.obj("album")
        val cover = album?.get("images")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            // The largest picture that is still a sensible size to decode for a lock-screen style cover.
            ?.sortedByDescending { it.long("width") }
            ?.firstOrNull { it.long("width") in 1..800 }?.str("url")
            ?: album?.get("images")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.str("url")
        val device = root.obj("device")
        return ConnectState(
            trackId = id,
            title = title,
            artist = artists.joinToString(", "),
            album = album?.str("name").orEmpty(),
            durationMs = item.long("duration_ms").coerceAtLeast(0L),
            coverUrl = cover,
            playing = root.get("is_playing")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            progressMs = root.long("progress_ms").coerceAtLeast(0L),
            deviceId = device?.str("id"),
            deviceName = device?.str("name"),
        )
    }

    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.long(name: String): Long =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
}

/**
 * Keeps the position of a song followed through polling steady. The Web API reports progress to
 * about a second and every answer is late by a different amount, so taking each one at face value
 * makes the lyrics hop. Between answers the position runs on the phone's own clock; an answer only
 * moves it when it disagrees by more than [toleranceMs] (a seek, a skip, a pause or a song change).
 */
class ConnectClock(private val toleranceMs: Long = TOLERANCE_MS) {
    private var anchorPositionMs = 0L
    private var anchorAtMs = 0L
    private var playing = false
    private var trackId: String? = null

    fun isPlaying(): Boolean = playing

    /** Where the song is at [nowMs] (a monotonic clock). */
    fun positionAt(nowMs: Long, durationMs: Long): Long {
        val position = if (playing) anchorPositionMs + (nowMs - anchorAtMs) else anchorPositionMs
        return if (durationMs > 0) position.coerceIn(0L, durationMs) else position.coerceAtLeast(0L)
    }

    /**
     * Takes an answer that was valid at [atMs] (the middle of the request, which is where its
     * progress belongs). Returns true when the position had to move, so the session must
     * publish it again; false when the running clock already agrees and nothing needs saying.
     */
    fun accept(state: ConnectState, atMs: Long): Boolean {
        val sameSong = trackId == state.trackId
        val expected = positionAt(atMs, state.durationMs)
        val agrees = sameSong && playing == state.playing && abs(expected - state.progressMs) <= toleranceMs
        if (agrees) return false
        trackId = state.trackId
        playing = state.playing
        anchorPositionMs = state.progressMs
        anchorAtMs = atMs
        return true
    }

    /** Moves the position at once: our own seek, before Spotify has confirmed it. */
    fun jump(positionMs: Long, atMs: Long) {
        anchorPositionMs = positionMs.coerceAtLeast(0L)
        anchorAtMs = atMs
    }

    /** Pauses or resumes at once: our own command, before Spotify has confirmed it. */
    fun setPlaying(value: Boolean, atMs: Long) {
        anchorPositionMs = positionAt(atMs, 0L)
        anchorAtMs = atMs
        playing = value
    }

    fun reset() {
        trackId = null
        playing = false
        anchorPositionMs = 0L
        anchorAtMs = 0L
    }

    companion object {
        const val TOLERANCE_MS = 900L
    }
}

/** How long to wait before asking Spotify again, so a song change is seen soon without hammering the API. */
object ConnectPolling {
    /** Quicker while the screen shows the lyrics, since a skip from another device should show quickly. */
    fun intervalMs(state: ConnectState?, positionMs: Long, visible: Boolean): Long {
        if (state == null) return if (visible) 8_000L else 20_000L
        if (!state.playing) return if (visible) 5_000L else 12_000L
        val base = if (visible) 3_000L else 6_000L
        val untilEnd = state.durationMs - positionMs
        // Look right after the song ends, to catch the next one at its start.
        return if (state.durationMs > 0 && untilEnd in 0 until base) (untilEnd + 400L).coerceAtLeast(800L) else base
    }

    /** The wait the API asked for with a 429, with a floor and a ceiling. */
    fun retryAfterMs(headerSeconds: String?): Long =
        ((headerSeconds?.trim()?.toLongOrNull() ?: 5L) * 1_000L).coerceIn(2_000L, 120_000L)
}
