package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class RapidSwitchGateTest {
    private val gate = RapidSwitchGate(windowMs = 1_000L, backoffMs = 400L)

    @Test fun firstLookupGoesAtOnce() {
        assertEquals(0L, gate.waitBeforeNetwork(5_000L))
    }

    @Test fun lookupWithinTheWindowWaits() {
        gate.waitBeforeNetwork(5_000L)
        assertEquals(400L, gate.waitBeforeNetwork(5_999L))
    }

    @Test fun lookupAfterTheWindowGoesAtOnce() {
        gate.waitBeforeNetwork(5_000L)
        assertEquals(0L, gate.waitBeforeNetwork(6_000L))
    }

    @Test fun eachLookupRestartsTheWindow() {
        gate.waitBeforeNetwork(5_000L)
        assertEquals(400L, gate.waitBeforeNetwork(5_800L))
        assertEquals(400L, gate.waitBeforeNetwork(6_600L))
    }

    @Test fun forcedRetryNeverWaitsButStillCounts() {
        gate.waitBeforeNetwork(5_000L)
        assertEquals(0L, gate.waitBeforeNetwork(5_100L, force = true))
        assertEquals(400L, gate.waitBeforeNetwork(5_900L))
    }
}
