package com.tx24.spicyplayer.network.data.spotify

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Checks the real services still answer as expected. Set RUN_TRACK_LOOKUP_TEST=1 and SPICY_LYRICS_CLIENT_KEY. */
class TrackLookupLiveTest {
    private val client = OkHttpClient()
    private val gson = Gson()

    private fun assumeOptedIn() = assumeTrue(System.getenv("RUN_TRACK_LOOKUP_TEST") == "1")

    @Test fun theTrackSearchFindsAWellKnownSongWithItsOtherReleases() = runBlocking {
        assumeOptedIn()
        val search = SpicyTrackSearch(client, gson, requireNotNull(System.getenv("SPICY_LYRICS_CLIENT_KEY")))

        val match = requireNotNull(search.match(LocalTrackMetadata("Creep", "Radiohead", "Pablo Honey", 238_640)))

        assertEquals("70LcF31zb1H0PyJoS1Sx1r", match.track.candidate.id)
        assertEquals(listOf("Radiohead"), match.track.candidate.artists)
        assertEquals(238_640L, match.track.candidate.durationMs)
        assert(match.alternates.isNotEmpty()) { "no alternates" }
        assert(match.alternates.all { it.candidate.id.length == 22 && it.candidate.durationMs > 0 }) { match.alternates.toString() }
    }

    @Test fun theTrackSearchLeavesPlayerMangledMetadataToTheSpotifyMatch() = runBlocking {
        assumeOptedIn()
        val search = SpicyTrackSearch(client, gson, requireNotNull(System.getenv("SPICY_LYRICS_CLIENT_KEY")))

        assertNull(search.match(LocalTrackMetadata("Blinding Lights", "The Weeknd - Topic", "", 200_000)))
    }

    @Test fun biniLinkGivesTheIsrc() = runBlocking {
        assumeOptedIn()
        assertEquals("GBAYE9200070", BiniLinkIds(client, gson).isrc("70LcF31zb1H0PyJoS1Sx1r"))
        assertNull(BiniLinkIds(client, gson).isrc("0000000000000000000000"))
    }
}
