package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrackIdentityTest {
    @Test fun metadataChangesDetectNextSongEvenWithSameMediaId() {
        val first = trackIdentity("queue-item", "First", "Artist", "Album", 180_000L)
        val next = trackIdentity("queue-item", "Second", "Artist", "Album", 180_000L)
        assertNotEquals(first, next)
    }

    @Test fun stableMetadataDoesNotRestartMatching() {
        val first = trackIdentity(null, "Song", "Artist", "Album", 180_000L)
        val same = trackIdentity(null, "Song", "Artist", "Album", 180_000L)
        assertEquals(first, same)
    }

    @Test fun durationDistinguishesVersions() {
        val studio = trackIdentity(null, "Song", "Artist", "Album", 180_000L)
        val live = trackIdentity(null, "Song", "Artist", "Album", 210_000L)
        assertNotEquals(studio, live)
    }
}
