package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.network.data.LyricsCapability
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderAttempt
import com.tx24.spicyplayer.network.data.ProviderAttemptOutcome
import com.tx24.spicyplayer.playback.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSummaryTest {
    private val rmm = LyricsState.Ready(emptyList(), null, null, null, emptyList(), provider = "RMM Revival")
    private val sources = listOf(
        LyricsSourceDescriptor("spicy_lyrics", "Spicy Lyrics", 10, setOf(LyricsCapability.WORD_SYNC)),
        LyricsSourceDescriptor("rmm_revival", "RMM Revival", 45, setOf(LyricsCapability.WORD_SYNC)),
    )

    @Test fun `a stand-in says which source is still being asked`() {
        val state = PlayerUiState(
            lyrics = rmm,
            sourceDescriptors = sources,
            providerAttempts = listOf(
                ProviderAttempt("spicy_lyrics", ProviderAttemptOutcome.PENDING),
                ProviderAttempt("rmm_revival", ProviderAttemptOutcome.HIT),
            ),
        )
        assertEquals("RMM Revival, word synced · checking Spicy Lyrics…", songSummary(state))
    }

    @Test fun `a finished lookup is just the lyrics`() {
        val state = PlayerUiState(
            lyrics = rmm,
            sourceDescriptors = sources,
            providerAttempts = listOf(
                ProviderAttempt("spicy_lyrics", ProviderAttemptOutcome.MISS),
                ProviderAttempt("rmm_revival", ProviderAttemptOutcome.HIT),
            ),
        )
        assertEquals("RMM Revival, word synced", songSummary(state))
    }
}
