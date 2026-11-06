package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStickinessTest {
    private val sticky = SessionStickiness(graceMs = 2_000L)

    @Test fun playingSessionIsKept() {
        assertTrue(sticky.keep(active = true, nowMs = 1_000L))
        assertNull(sticky.remainingMs(1_000L))
    }

    @Test fun briefStopIsKept() {
        assertTrue(sticky.keep(active = false, nowMs = 1_000L))
        assertTrue(sticky.keep(active = false, nowMs = 2_999L))
        assertEquals(500L, sticky.remainingMs(2_500L))
    }

    @Test fun longStopLetsGo() {
        sticky.keep(active = false, nowMs = 1_000L)
        assertFalse(sticky.keep(active = false, nowMs = 3_000L))
    }

    @Test fun playingAgainRestartsTheGrace() {
        sticky.keep(active = false, nowMs = 1_000L)
        sticky.keep(active = true, nowMs = 2_500L)
        assertTrue(sticky.keep(active = false, nowMs = 2_600L))
        assertTrue(sticky.keep(active = false, nowMs = 4_500L))
        assertFalse(sticky.keep(active = false, nowMs = 4_600L))
    }

    @Test fun resetForgetsTheStop() {
        sticky.keep(active = false, nowMs = 1_000L)
        sticky.reset()
        assertTrue(sticky.keep(active = false, nowMs = 5_000L))
    }
}
