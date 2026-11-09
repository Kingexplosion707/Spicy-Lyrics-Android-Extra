package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {
    @Test
    fun `minutes left round up so a timer never reads 0 before it ends`() {
        val timer = SleepTimerService.SleepTimer(endsAtElapsed = 10 * 60_000L, endOfSong = false)
        assertEquals(10, timer.minutesLeft(now = 0L))
        assertEquals(10, timer.minutesLeft(now = 1L))
        assertEquals(1, timer.minutesLeft(now = 9 * 60_000L + 59_000L))
        assertEquals(0, timer.minutesLeft(now = 10 * 60_000L))
        assertEquals(0, timer.minutesLeft(now = 11 * 60_000L))
    }

    @Test
    fun `end of song has no minutes`() {
        assertEquals(0, SleepTimerService.SleepTimer(0L, endOfSong = true).minutesLeft(now = 5L))
    }
}
