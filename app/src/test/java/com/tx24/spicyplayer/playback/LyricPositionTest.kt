package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricPositionTest {
    @Test
    fun `every delay holds the lyrics back, together`() {
        assertEquals(10_000L, lyricPositionMs(10_000L, 0, 0))
        assertEquals(9_550L, lyricPositionMs(10_000L, outputDelayMs = 300, songDelayMs = 100, playerDelayMs = 50))
        // A negative delay brings them forward.
        assertEquals(10_200L, lyricPositionMs(10_000L, 0, 0, playerDelayMs = -200))
    }

    @Test
    fun `the lyrics never go before the start`() {
        assertEquals(0L, lyricPositionMs(100L, 0, 0, playerDelayMs = 500))
    }

    @Test
    fun `a delay is kept within its reach`() {
        assertEquals(MAX_DELAY_MS, clampDelay(MAX_DELAY_MS + 1))
        assertEquals(-MAX_DELAY_MS, clampDelay(-MAX_DELAY_MS - 1))
    }
}
