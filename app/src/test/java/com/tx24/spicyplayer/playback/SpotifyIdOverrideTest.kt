package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyIdOverrideTest {
    private val id = "4uLU6hMCjMI75M1A2tKUQC"
    private val saved = mutableMapOf<String, String>()
    private fun saveAt(durationMs: Long) { saved[overrideKey("Song", "Artist")!!] = overrideValue(id, durationMs) }
    private fun read(durationMs: Long, title: String = "Song", artist: String = "Artist") =
        savedOverride(title, artist, durationMs) { saved[it] }

    @Test fun survivesTheLengthArrivingAfterTheTitle() {
        saveAt(200_000L)
        // The first update has no length yet, the next one has it.
        assertEquals(SavedOverride(id, legacy = false), read(0L))
        assertEquals(SavedOverride(id, legacy = false), read(200_000L))
    }

    @Test fun savedBeforeTheLengthCameHoldsForTheSongWithIt() {
        saveAt(0L)
        assertEquals(id, read(200_000L)?.id)
    }

    @Test fun aRecordingOfAnotherLengthDoesNotGetIt() {
        saveAt(200_000L)
        assertEquals(id, read(201_500L)?.id)  // the same recording, reported a little differently
        assertNull(read(260_000L))  // a live or extended version under the same names
    }

    @Test fun anotherSongDoesNotGetIt() {
        saveAt(200_000L)
        assertNull(read(200_000L, title = "Other Song"))
        assertNull(read(200_000L, artist = "Someone Else"))
        // Case and spacing aside, it's the same song.
        assertEquals(id, read(200_000L, title = " song ", artist = "ARTIST")?.id)
    }

    @Test fun anIdSavedUnderTheOldKeyIsStillFoundAndMarkedForMoving() {
        saved[legacyOverrideKey("Song", "Artist", 200_000L)!!] = id
        assertEquals(SavedOverride(id, legacy = true), read(200_000L))
        // The old key needs the exact length, which a first update without one can't give.
        assertNull(read(0L))
    }

    @Test fun theOldKeyIsTheOneUsedBefore() {
        // SHA-256 of "song\u001fartist\u001f200000", the key saved IDs already have.
        assertEquals(
            java.security.MessageDigest.getInstance("SHA-256").digest("song\u001fartist\u001f200000".toByteArray())
                .joinToString("") { "%02x".format(it) },
            legacyOverrideKey("Song ", "Artist", 200_000L),
        )
    }

    @Test fun noKeyWithoutBothNames() {
        assertNull(overrideKey("", "Artist"))
        assertNull(overrideKey("Song", " "))
    }
}
