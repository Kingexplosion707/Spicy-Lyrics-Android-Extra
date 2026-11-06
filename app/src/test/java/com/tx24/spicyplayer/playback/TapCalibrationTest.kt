package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapCalibrationTest {
    /** 120 bpm from 1 s. */
    private val beats = LongArray(200) { 1_000L + it * 500L }

    /** Taps on beats [from] onwards, [lagMs] after each in player time, jittered by [jitter]. */
    private fun taps(lagMs: Long, count: Int = 20, from: Int = 10, jitter: (Int) -> Long = { 0L }) =
        (0 until count).map { beats[from + it] + lagMs + jitter(it) }

    @Test
    fun `taps behind the beats call for that delay`() {
        val estimate = TapCalibration.estimate(taps(180), beats, currentDelayMs = 0)!!
        assertEquals(180, estimate.delayMs)
        assertTrue(estimate.steady)
    }

    @Test
    fun `taps ahead call for a negative delay`() {
        assertEquals(-200, TapCalibration.estimate(taps(-200), beats, currentDelayMs = 0)!!.delayMs)
    }

    @Test
    fun `the delay already set is kept in, so a long one matches the right beat`() {
        // 300 ms is past half a 500 ms beat: from 0 it would match the next beat, from 250 it doesn't.
        assertEquals(300, TapCalibration.estimate(taps(300), beats, currentDelayMs = 250)!!.delayMs)
    }

    @Test
    fun `too few taps give no answer yet`() {
        assertNull(TapCalibration.estimate(taps(100, count = TapCalibration.WARMUP_TAPS + TapCalibration.MIN_TAPS - 1), beats, 0))
    }

    @Test
    fun `the warm-up taps and stray taps don't move the answer`() {
        val wild = taps(100, count = 24) { i -> if (i < TapCalibration.WARMUP_TAPS) 160L else if (i % 7 == 0) -150L else (i % 3 - 1) * 10L }
        assertEquals(100, TapCalibration.estimate(wild, beats, 0)!!.delayMs)
    }

    @Test
    fun `uneven taps are not steady`() {
        val uneven = taps(0, count = 30) { i -> if (i % 2 == 0) 90L else -90L }
        assertFalse(TapCalibration.estimate(uneven, beats, 0)!!.steady)
    }
}
