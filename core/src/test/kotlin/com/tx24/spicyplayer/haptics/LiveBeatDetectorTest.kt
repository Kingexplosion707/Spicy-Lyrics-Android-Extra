package com.tx24.spicyplayer.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class LiveBeatDetectorTest {
    private val rate = 44_100
    private val block = 1024
    private val blockMs = block * 1000L / rate

    /** [seconds] of quiet noise with a kick (a decaying 60 Hz thump) every [everyMs], and optional hats. */
    private fun render(seconds: Int, everyMs: Long, hats: Boolean = false): FloatArray {
        val random = Random(7)
        val out = FloatArray(rate * seconds)
        for (i in out.indices) {
            val t = i * 1000L / rate
            var s = (random.nextFloat() - 0.5f) * 0.01f
            val sinceKick = (t % everyMs) / 1000.0
            s += (0.8 * exp(-sinceKick * 18) * sin(2 * PI * 60 * sinceKick)).toFloat()
            if (hats) {
                val sinceHat = ((t + everyMs / 2) % everyMs) / 1000.0
                s += ((random.nextFloat() - 0.5f) * 0.6f * exp(-sinceHat * 60)).toFloat()
            }
            out[i] = s
        }
        return out
    }

    private fun hits(audio: FloatArray, detector: LiveBeatDetector): List<Pair<Long, LiveBeatDetector.Kind>> {
        val found = ArrayList<Pair<Long, LiveBeatDetector.Kind>>()
        var at = 0
        while (at + block <= audio.size) {
            val ms = (at + block) * 1000L / rate
            detector.frame(audio.copyOfRange(at, at + block), block, rate, ms)?.let { found += ms to it.kind }
            at += block / 2
        }
        return found
    }

    @Test
    fun `finds a steady kick about once per beat`() {
        val found = hits(render(8, 500L), LiveBeatDetector()).filter { it.second == LiveBeatDetector.Kind.Low }
        // 16 beats in 8 s; the first second or so is spent learning the level.
        assertTrue("found ${found.size}", found.size in 12..17)
        found.zipWithNext().forEach { (a, b) -> assertTrue("gap ${b.first - a.first} in $found", b.first - a.first in 400L..600L) }
    }

    @Test
    fun `hears the hats between the kicks`() {
        val found = hits(render(8, 500L, hats = true), LiveBeatDetector())
        assertTrue("highs in $found", found.count { it.second == LiveBeatDetector.Kind.High } >= 8)
        assertTrue("lows in $found", found.count { it.second == LiveBeatDetector.Kind.Low } >= 10)
    }

    @Test
    fun `stays still in silence and steady noise`() {
        val random = Random(1)
        val noise = FloatArray(rate * 4) { (random.nextFloat() - 0.5f) * 0.05f }
        assertEquals(0, hits(FloatArray(rate * 4), LiveBeatDetector()).size)
        val inNoise = hits(noise, LiveBeatDetector())
        assertTrue("noise $inNoise", inNoise.size <= 2)
    }
}
