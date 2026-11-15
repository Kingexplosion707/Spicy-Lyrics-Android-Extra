package com.tx24.spicyplayer.network.data.spotify

import com.google.gson.Gson
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The track search goes first; the Spotify match takes over for anything it can't vouch for. */
class SpicyTrackSearchTest {
    private val track = LocalTrackMetadata("Song", "Artist", "Album", 200_000)

    private val apiCalls = AtomicInteger()
    private val spotifyCalls = AtomicInteger()
    private var lastUrl: HttpUrl? = null
    private var lastAuth: String? = null

    /** What Spotify's own search answers: enough for the matcher to pick "spotify-id". */
    private val catalog = object : SpotifyCatalogSearch {
        override suspend fun search(track: LocalTrackMetadata): List<SpotifyTrackCandidate> {
            spotifyCalls.incrementAndGet()
            return listOf(SpotifyTrackCandidate("spotify-id", "Song", listOf("Artist"), "Album", 200_000))
        }
    }

    private fun resolver(key: String = "key", answer: () -> Pair<Int, String>): SpotifyTrackResolver {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            apiCalls.incrementAndGet()
            lastUrl = chain.request().url
            lastAuth = chain.request().header("Authorization")
            val (code, body) = answer()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("-")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return SpotifyTrackResolver(catalog, SpicyTrackSearch(client, Gson(), key))
    }

    private fun result(id: String, tier: String, duration: Double = 200.0, variants: List<String> = emptyList()) = """
        {"id":"$id","ids":{"spotify":"$id"},"title":"Song","artists":["Artist"],"album":null,"duration":$duration,
         "explicit":false,"artwork":null,"tier":"$tier",
         "variants":[${variants.joinToString { """{"id":"$it","album":"Other","duration":$duration}""" }}]}"""

    private fun results(vararg items: String) =
        200 to """{"Body":{"results":[${items.joinToString()}]},"Status":200,"Type":"object"}"""

    private fun SpotifyTrackResolution.ids() =
        (this as SpotifyTrackResolution.Matched).let { listOf(it.track) + it.alternates }.map { it.candidate.id }

    @Test fun anExactResultWinsAndSpotifyIsNeverAsked() = runBlocking {
        val resolver = resolver { results(result("api-id", "exact", variants = listOf("variant"))) }

        val match = resolver.resolve(track)

        assertEquals(listOf("api-id", "variant"), match.ids())
        assertEquals(0, spotifyCalls.get())
        assertEquals("Bearer key", lastAuth)
        assertEquals("Song", lastUrl?.queryParameter("title"))
        assertEquals("Artist", lastUrl?.queryParameter("artist"))
        assertEquals("Album", lastUrl?.queryParameter("album"))
        assertEquals("200.0", lastUrl?.queryParameter("duration"))
    }

    @Test fun theMatchIsRememberedForEveryoneElseAsking() = runBlocking {
        val resolver = resolver { results(result("api-id", "exact")) }

        resolver.resolve(track)
        resolver.resolve(track)

        assertEquals(1, apiCalls.get())
        assertEquals("api-id", resolver.remembered(track)?.track?.candidate?.id)
    }

    @Test fun alternatesAreCappedSoASongCannotSpendTheRequestWindow() = runBlocking {
        val resolver = resolver { results(result("api-id", "exact", variants = (1..7).map { "v$it" })) }

        assertEquals(listOf("api-id", "v1", "v2", "v3"), resolver.resolve(track).ids())
    }

    @Test fun anotherExactResultCountsOnlyAtTheSameLength() = runBlocking {
        val resolver = resolver {
            results(
                result("api-id", "exact", duration = 200.0),
                result("same-length", "exact", duration = 201.5),
                result("other-cut", "exact", duration = 203.0),
                result("near-but-likely", "likely", duration = 200.0),
            )
        }

        assertEquals(listOf("api-id", "same-length"), resolver.resolve(track).ids())
    }

    @Test fun aWeakerTopResultFallsBackToSpotify() = runBlocking {
        for (tier in listOf("likely", "version", "other_artist", "related")) {
            val resolver = resolver { results(result("api-id", tier)) }
            assertEquals(tier, listOf("spotify-id"), resolver.resolve(track).ids())
        }
        assertEquals(4, spotifyCalls.get())
    }

    @Test fun noResultsFallBackToSpotify() = runBlocking {
        assertEquals(listOf("spotify-id"), resolver { results() }.resolve(track).ids())
    }

    @Test fun aRefusalFallsBackToSpotify() = runBlocking {
        for (code in listOf(400, 401, 429, 500, 503)) {
            val resolver = resolver { code to """{"Body":{"error":"x","message":"y"},"Status":$code,"Type":"object"}""" }
            assertEquals("HTTP $code", listOf("spotify-id"), resolver.resolve(track).ids())
        }
    }

    @Test fun aNetworkFailureFallsBackToSpotify() = runBlocking {
        val resolver = resolver { throw IOException("offline") }
        assertEquals(listOf("spotify-id"), resolver.resolve(track).ids())
    }

    @Test fun anUnreadableAnswerFallsBackToSpotify() = runBlocking {
        assertEquals(listOf("spotify-id"), resolver { 200 to "<html>" }.resolve(track).ids())
    }

    @Test fun anOutageStopsBeingAskedAfterTwoFailures() = runBlocking {
        val resolver = resolver { 503 to "{}" }

        // Each lookup is another song: nothing is remembered from a match Spotify made on a miss.
        repeat(4) { resolver.resolve(track.copy(title = "Song", album = "Album $it")) }

        assertEquals(2, apiCalls.get())
        assertEquals(4, spotifyCalls.get())
    }

    @Test fun aBadRequestIsAboutTheSongSoItDoesNotStopTheSearch() = runBlocking {
        val resolver = resolver { 400 to "{}" }

        repeat(3) { resolver.resolve(track.copy(album = "Album $it")) }

        assertEquals(3, apiCalls.get())
    }

    @Test fun withoutAKeyOrAnArtistNothingIsSent() = runBlocking {
        val noKey = resolver(key = "") { results(result("api-id", "exact")) }
        assertEquals(listOf("spotify-id"), noKey.resolve(track).ids())

        val noArtist = resolver { results(result("api-id", "exact")) }
        noArtist.resolve(track.copy(artist = ""))

        assertEquals(0, apiCalls.get())
    }

    /** [answer] plays BiniLink; Spotify's metadata service answers "SPOTIFYISRC". */
    private fun isrcResolver(answer: () -> Pair<Int, String>): SpotifyTrackResolver {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            apiCalls.incrementAndGet()
            lastUrl = chain.request().url
            val (code, body) = answer()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("-")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val spotify = object : SpotifyCatalogSearch {
            override suspend fun search(track: LocalTrackMetadata) = emptyList<SpotifyTrackCandidate>()
            override suspend fun isrc(trackId: String): String {
                spotifyCalls.incrementAndGet()
                return "SPOTIFYISRC"
            }
        }
        return SpotifyTrackResolver(spotify, null, BiniLinkIds(client, Gson()))
    }

    @Test fun theIsrcComesFromBiniLinkWithoutAskingSpotify() = runBlocking {
        val resolver = isrcResolver { 200 to """{"isrc":"gbaye9200070","spotify":"abc"}""" }

        assertEquals("GBAYE9200070", resolver.isrc("abc"))
        assertEquals(0, spotifyCalls.get())
        assertEquals("/api/v1/resolve/spotify/abc", lastUrl?.encodedPath)
    }

    @Test fun anIsrcBiniLinkCannotGiveComesFromSpotify() = runBlocking {
        for ((code, body) in listOf(404 to """{"error":"track not found"}""", 422 to "{}", 503 to "{}", 200 to "{}", 200 to "<html>")) {
            assertEquals("HTTP $code $body", "SPOTIFYISRC", isrcResolver { code to body }.isrc("abc"))
        }
        assertEquals("SPOTIFYISRC", isrcResolver { throw IOException("offline") }.isrc("abc"))
    }

    @Test fun aBiniLinkOutageStopsBeingAskedButASingleMissingTrackDoesNot() = runBlocking {
        val down = isrcResolver { 503 to "{}" }
        repeat(4) { down.isrc("abc") }
        assertEquals(2, apiCalls.get())

        apiCalls.set(0)
        val unknown = isrcResolver { 404 to "{}" }
        repeat(4) { unknown.isrc("abc") }
        assertEquals(4, apiCalls.get())
    }

    @Test fun anUnknownLengthAndAlbumAreLeftOutOfTheQuery() = runBlocking {
        resolver { results(result("api-id", "exact")) }.resolve(track.copy(album = "", durationMs = 0))

        assertNull(lastUrl?.queryParameter("album"))
        assertNull(lastUrl?.queryParameter("duration"))
        assertTrue(lastUrl.toString().startsWith("https://api.spicylyrics.org/v1/tracks/search?"))
    }
}
