package com.tx24.spicyplayer.network.data.spotify

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tx24.spicyplayer.network.data.BackoffLadder
import com.tx24.spicyplayer.network.data.RetryAfterParser
import com.tx24.spicyplayer.network.data.awaitResponse
import com.tx24.spicyplayer.platform.CoreLog
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Finds a track's Spotify IDs through the Spicy Lyrics track search.
 *
 * [match] only answers when the API calls its top result `exact`, since another version of a song
 * has other timings. Everything else (a weaker tier, no results, any failure) is null, which sends
 * the caller to its own Spotify match.
 */
class SpicyTrackSearch(
    client: OkHttpClient,
    private val gson: Gson,
    /** Settable because the user can swap the key while the app runs. Blank: never asked. */
    @Volatile var apiKey: String = "",
) {
    /** The fallback waits behind this search, so a stalled one gets dropped sooner than a lyric request. */
    private val deadlineClient = client.newBuilder().callTimeout(DEADLINE_S, TimeUnit.SECONDS).build()
    private val backoff = BackoffLadder()

    suspend fun match(track: LocalTrackMetadata): SpotifyTrackResolution.Matched? {
        val key = apiKey
        // Without an artist no result can be exact, so the request would be wasted.
        if (key.isBlank() || track.title.isBlank() || track.artist.isBlank()) return null
        if (backoff.openUntil(System.currentTimeMillis()) != null) return null
        return try {
            withContext(Dispatchers.IO) { request(track, key) }?.let { pick(track, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            backoff.failure(System.currentTimeMillis())
            CoreLog.d("SpicyTrackSearch") { "failed: ${error.message}" }
            null
        } catch (error: Exception) {
            CoreLog.d("SpicyTrackSearch") { "unreadable answer: ${error.message}" }
            null
        }
    }

    /** The answer's body, or null after a refusal. */
    private suspend fun request(track: LocalTrackMetadata, key: String): String? {
        val url = URL.toHttpUrl().newBuilder()
            .addQueryParameter("title", track.title.take(MAX_FIELD))
            .addQueryParameter("artist", track.artist.take(MAX_FIELD))
            .apply {
                if (track.album.isNotBlank()) addQueryParameter("album", track.album.take(MAX_FIELD))
                if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1_000.0).toString())
            }
            .build()
        val call = deadlineClient.newCall(
            Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
        )
        return call.awaitResponse().use { response ->
            if (response.isSuccessful) {
                backoff.success()
                return@use response.body?.string()
            }
            // A 400 is about this song's fields, not about the service.
            if (response.code != 400) {
                val retryAfter = (response.header("Retry-After") ?: response.header("RateLimit-Reset")?.takeIf { response.code == 429 })
                    ?.let(RetryAfterParser::deadline)
                backoff.failure(System.currentTimeMillis(), retryAfter?.toEpochMilli())
            }
            CoreLog.d("SpicyTrackSearch") { "HTTP ${response.code}" }
            null
        }
    }

    private fun pick(track: LocalTrackMetadata, json: String): SpotifyTrackResolution.Matched? {
        val results = gson.fromJson(json, JsonObject::class.java)
            ?.getAsJsonObject("Body")?.getAsJsonArray("results")
            ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .orEmpty()
        val best = results.firstOrNull()?.takeIf { it.isExact() } ?: return null
        val winner = best.candidate(track) ?: return null

        // Lyrics are uploaded per ID. `variants` are the API's own merged releases; it can also
        // leave a copy unmerged as a second exact result, which counts only at the same length.
        val variants = best.getAsJsonArray("variants")
            ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject?.variant(winner) }
            .orEmpty()
        val otherCopies = results.drop(1).filter { it.isExact() }
            .mapNotNull { it.candidate(track) }
            .filter { abs(it.durationMs - winner.durationMs) <= SAME_RECORDING_DURATION_MS }
        val alternates = (variants + otherCopies)
            .distinctBy(SpotifyTrackCandidate::id)
            .filter { it.id != winner.id }
            .take(MAXIMUM_ALTERNATES)
        return SpotifyTrackResolution.Matched(winner.trusted(track), alternates.map { it.trusted(track) })
    }

    private fun JsonObject.isExact() = text("tier") == "exact"

    private fun JsonObject.candidate(track: LocalTrackMetadata): SpotifyTrackCandidate? {
        val id = text("id") ?: return null
        return SpotifyTrackCandidate(
            id = id,
            title = text("title") ?: track.title,
            artists = getAsJsonArray("artists")?.mapNotNull { it.takeIf { a -> a.isJsonPrimitive }?.asString }.orEmpty(),
            album = text("album").orEmpty(),
            durationMs = seconds("duration"),
            coverUrl = text("artwork"),
        )
    }

    /** A variant only carries its ID, album and length; the rest is the recording it belongs to. */
    private fun JsonObject.variant(of: SpotifyTrackCandidate): SpotifyTrackCandidate? {
        val id = text("id") ?: return null
        return of.copy(id = id, album = text("album").orEmpty(), durationMs = seconds("duration"), coverUrl = null)
    }

    private fun SpotifyTrackCandidate.trusted(track: LocalTrackMetadata) = ScoredSpotifyTrack(
        candidate = this,
        score = 100,
        titleSimilarity = 1.0,
        artistSimilarity = 1.0,
        durationDeltaMs = if (track.durationMs <= 0) 0L else abs(track.durationMs - durationMs),
        hasVersionConflict = false,
    )

    private fun JsonObject.text(name: String) =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)

    private fun JsonObject.seconds(name: String) =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.let { (it.asDouble * 1_000).toLong() } ?: 0L

    private companion object {
        const val URL = "https://api.spicylyrics.org/v1/tracks/search"
        const val DEADLINE_S = 5L
        const val MAX_FIELD = 200
        const val SAME_RECORDING_DURATION_MS = 2_000L
        /** Each alternate costs a lyric request when the lookup asks them all at once. */
        const val MAXIMUM_ALTERNATES = 3
    }
}
