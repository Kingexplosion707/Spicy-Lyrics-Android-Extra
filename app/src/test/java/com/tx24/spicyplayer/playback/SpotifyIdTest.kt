package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyIdTest {
    @Test fun parsesSupportedTrackIdentifiers() {
        val id = "4uLU6hMCjMI75M1A2tKUQC"
        assertEquals(id, id.spotifyTrackId())
        assertEquals(id, "spotify:track:$id".spotifyTrackId())
        assertEquals(id, "https://open.spotify.com/track/$id?si=test".spotifyTrackId())
    }

    @Test fun rejectsNonTrackMetadata() {
        assertNull("spotify:album:4uLU6hMCjMI75M1A2tKUQC".spotifyTrackId())
        assertNull("not-an-id".spotifyTrackId())
    }
}
