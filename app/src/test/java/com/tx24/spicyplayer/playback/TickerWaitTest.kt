package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class TickerWaitTest {
    @Test fun aPendingSeekIsWatchedEveryTenthOfASecond() {
        assertEquals(100L, tickerWaitMs(seekPending = true, sinceCheckMs = 0L))
        assertEquals(100L, tickerWaitMs(seekPending = true, sinceCheckMs = 1_500L))
    }

    @Test fun otherwiseItSleepsUntilTheNextPeriodicCheck() {
        assertEquals(2_000L, tickerWaitMs(seekPending = false, sinceCheckMs = 0L))
        assertEquals(1_300L, tickerWaitMs(seekPending = false, sinceCheckMs = 700L))
    }

    @Test fun anOverdueCheckStillWaitsAMoment() {
        assertEquals(100L, tickerWaitMs(seekPending = false, sinceCheckMs = 1_990L))
        assertEquals(100L, tickerWaitMs(seekPending = false, sinceCheckMs = 5_000L))
    }

    @Test fun aCheckNeverRunSleepsAtMostTheCadence() {
        // lastPeriodicCheckMs starts at 0, so the time since it is the whole uptime.
        assertEquals(100L, tickerWaitMs(seekPending = false, sinceCheckMs = Long.MAX_VALUE / 2))
        assertEquals(2_000L, tickerWaitMs(seekPending = false, sinceCheckMs = -10L))
    }
}
