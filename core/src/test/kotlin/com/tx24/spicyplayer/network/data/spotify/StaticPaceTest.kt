package com.tx24.spicyplayer.network.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPaceTest {
    /** A segment per second: [loud] seconds, each at that loudness, quiet ones at -60 dB. */
    private fun analysis(vararg loud: Boolean) = AudioAnalysis(
        tempo = 120f, loudness = -8f, sections = emptyList(), beats = emptyList(),
        segments = loud.mapIndexed { i, isLoud ->
            AudioAnalysis.Segment(start = i.toFloat(), loudnessStart = -20f, loudnessMax = if (isLoud) -10f else -60f, peakAt = 0f)
        },
    )

    private fun song(quietIntro: Int, loud1: Int, gap: Int, loud2: Int, quietOutro: Int): AudioAnalysis {
        val seconds = List(quietIntro) { false } + List(loud1) { true } + List(gap) { false } + List(loud2) { true } + List(quietOutro) { false }
        return analysis(*seconds.toBooleanArray())
    }

    @Test fun `a quiet intro and outro take no scroll`() {
        val pace = StaticPace.of(song(quietIntro = 10, loud1 = 20, gap = 1, loud2 = 20, quietOutro = 10))!!
        assertEquals(0f, pace.progress(0), 0f)
        assertEquals(0f, pace.progress(10_000), 0.001f)
        assertEquals(1f, pace.progress(51_000), 0.02f)
        assertEquals(1f, pace.progress(61_000), 0f)
        // Halfway through the singing is halfway through the lyrics.
        assertEquals(0.5f, pace.progress(30_500), 0.02f)
    }

    @Test fun `a break of a few seconds holds the lyrics, a short pause does not`() {
        val short = StaticPace.of(song(0, 20, 2, 20, 0))!!   // filled in: moves straight through
        val long = StaticPace.of(song(0, 20, 6, 20, 0))!!    // a real break: holds
        assertTrue(short.progress(21_000) > short.progress(20_000) + 0.01f)
        assertEquals(long.progress(20_000), long.progress(26_000), 0.001f)
    }

    @Test fun `never goes backwards and stays between 0 and 1`() {
        val pace = StaticPace.of(song(5, 15, 8, 15, 5))!!
        var last = -1f
        for (ms in -1_000L..50_000L step 250) {
            val p = pace.progress(ms)
            assertTrue("at $ms", p in 0f..1f && p >= last)
            last = p
        }
    }

    @Test fun `no analysis or no sound is no pace`() {
        assertNull(StaticPace.of(analysis()))
        assertNull(StaticPace.of(analysis(true)))
        assertNull(StaticPace.of(analysis(false, false, false, false)))
        assertNotNull(StaticPace.of(analysis(true, true)))
    }
}
