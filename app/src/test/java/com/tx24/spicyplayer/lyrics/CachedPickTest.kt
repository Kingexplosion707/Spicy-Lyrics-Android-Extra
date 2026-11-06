package com.tx24.spicyplayer.lyrics

import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderAttempt
import com.tx24.spicyplayer.network.data.ProviderAttemptOutcome
import com.tx24.spicyplayer.network.data.ProviderFailureCategory
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedPickTest {
    private val queueEntry = LyricsLookupRequest(artist = "A", title = "Song", album = "", durationSeconds = 0)
    private val playing = queueEntry.copy(album = "Album", durationSeconds = 200)
    private val apple = LyricsSourceDescriptor("apple_music", "Apple Music", 20, emptySet())
    private val payload = RemoteLyricsPayload(syncedLyrics = "[00:01.00]la")

    private fun found(vararg above: ProviderAttempt) = RemoteLyricsResolution.Found(
        RemoteLyricsSelection(apple, payload, RemoteLyricsQuality.LINE_SYNCED),
        above.toList() + ProviderAttempt(apple.id, ProviderAttemptOutcome.HIT, RemoteLyricsQuality.LINE_SYNCED) +
            ProviderAttempt("lrclib", ProviderAttemptOutcome.UNAVAILABLE),
    )

    @Test fun spicyWithoutAMatchOnAQueueEntryIsAskedAgain() {
        val resolution = found(ProviderAttempt("spicy_lyrics", ProviderAttemptOutcome.NEEDS_MATCH))
        assertFalse(NextLyricsBackend.isSettled(queueEntry, resolution))
        // With the length known, no match is an answer.
        assertTrue(NextLyricsBackend.isSettled(playing, resolution))
    }

    @Test fun anErrorAboveThePickIsAskedAgain() {
        val resolution = found(ProviderAttempt("spicy_lyrics", ProviderAttemptOutcome.UNAVAILABLE, failureCategory = ProviderFailureCategory.NETWORK))
        assertFalse(NextLyricsBackend.isSettled(playing, resolution))
    }

    @Test fun anErrorBelowThePickDoesNotMatter() {
        assertTrue(NextLyricsBackend.isSettled(playing, found(ProviderAttempt("spicy_lyrics", ProviderAttemptOutcome.MISS))))
    }

    @Test fun noLyricsWithASourceUnmatchedIsAskedAgain() {
        val resolution = RemoteLyricsResolution.NotFound(listOf(
            ProviderAttempt("spicy_lyrics", ProviderAttemptOutcome.NEEDS_MATCH),
            ProviderAttempt("lrclib", ProviderAttemptOutcome.MISS),
        ))
        assertFalse(NextLyricsBackend.isSettled(queueEntry, resolution))
    }

    @Test fun reusedAnswersAreThePickAndTheMisses() {
        val pick = NextLyricsBackend.CachedPick(found(ProviderAttempt("amll", ProviderAttemptOutcome.MISS),
            ProviderAttempt("spicy_lyrics", ProviderAttemptOutcome.NEEDS_MATCH)), settled = false)
        assertEquals(mapOf("amll" to ProviderResult.Miss, "apple_music" to ProviderResult.Hit(payload)), pick.answers)
    }

    @Test fun aManualSpotifyIdIsStoredApartFromTheUsualPick() {
        val usual = NextLyricsBackend.cacheName("Song", "A")
        val manual = NextLyricsBackend.cacheName("Song", "A", "4uLU6hMCjMI75M1A2tKUQC")
        assertNotEquals(usual, manual)
        assertNotEquals(manual, NextLyricsBackend.cacheName("Song", "A", "4uLU6hMCjMI75M1A2tKUQD"))
        // Same song, same entry, however the names are cased or spaced.
        assertEquals(manual, NextLyricsBackend.cacheName(" song ", "a", "4uLU6hMCjMI75M1A2tKUQC"))
    }
}
