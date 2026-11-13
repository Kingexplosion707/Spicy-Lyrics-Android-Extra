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
    private fun render(seconds: Int, everyMs: Long, hats: Boolean = false, bass: Float = 0f, pad: Float = 0f): FloatArray {
        val random = Random(7)
        val out = FloatArray(rate * seconds)
        for (i in out.indices) {
            val t = i * 1000L / rate
            var s = (random.nextFloat() - 0.5f) * 0.01f
            val sec = i.toDouble() / rate
            // A held bass note and a chord under the beat, as in a real mix.
            s += (bass * sin(2 * PI * 55 * sec)).toFloat()
            s += (pad * (sin(2 * PI * 220 * sec) + sin(2 * PI * 277 * sec) + sin(2 * PI * 330 * sec)) / 3).toFloat()
            val sinceKick = (t % everyMs) / 1000.0
            // A kick: a thump that drops from 120 Hz to 50 Hz as it fades.
            val phase = 2 * PI * (50 * sinceKick + 70 * (1 - exp(-sinceKick * 30)) / 30)
            s += (0.8 * exp(-sinceKick * 18) * sin(phase)).toFloat()
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
    fun `finds kicks over a held bass note and chords`() {
        val found = hits(render(8, 500L, hats = true, bass = 0.3f, pad = 0.3f), LiveBeatDetector())
        val lows = found.filter { it.second == LiveBeatDetector.Kind.Low }
        assertTrue("lows in $found", lows.size >= 10)
    }

    @Test
    fun `finds quieter kicks over a loud bass line`() {
        val found = hits(render(8, 500L, bass = 0.5f), LiveBeatDetector())
        assertTrue("found $found", found.count { it.second == LiveBeatDetector.Kind.Low } >= 8)
    }

    @Test
    fun `keeps finding kicks through a long loud song`() {
        // A minute of a full mix, then a louder chorus: the last stretch must still be felt.
        val verse = render(40, 500L, hats = true, bass = 0.3f, pad = 0.3f)
        val chorus = render(30, 500L, hats = true, bass = 0.5f, pad = 0.5f).map { it * 1.4f }.toFloatArray()
        val found = hits(verse + chorus, LiveBeatDetector())
        val lateLows = found.filter { it.first > 60_000L && it.second == LiveBeatDetector.Kind.Low }
        assertTrue("late lows ${lateLows.size}", lateLows.size >= 15)
    }

    @Test
    fun `comes back after a quiet passage`() {
        val loud = render(20, 500L, bass = 0.4f)
        val quiet = render(10, 500L, bass = 0.4f).map { it * 0.15f }.toFloatArray()
        val found = hits(loud + quiet, LiveBeatDetector())
        val inQuiet = found.filter { it.first > 23_000L && it.second == LiveBeatDetector.Kind.Low }
        assertTrue("quiet lows ${inQuiet.size}", inQuiet.size >= 8)
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
