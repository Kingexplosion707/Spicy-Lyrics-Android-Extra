package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SongDelaysTest {
    @Test
    fun `both delays hold the lyrics back`() {
        assertEquals(9_650L, lyricPositionMs(10_000L, outputDelayMs = 200, songDelayMs = 150))
    }

    @Test
    fun `a negative song delay cancels the output's`() {
        assertEquals(10_000L, lyricPositionMs(10_000L, outputDelayMs = 200, songDelayMs = -200))
    }

    @Test
    fun `the lyrics never go before the start`() {
        assertEquals(0L, lyricPositionMs(100L, outputDelayMs = 200, songDelayMs = 300))
    }

    @Test
    fun `delays are clamped each way`() {
        assertEquals(MAX_DELAY_MS, clampDelay(5_000))
        assertEquals(-MAX_DELAY_MS, clampDelay(-5_000))
        assertEquals(40, clampDelay(40))
    }
}
