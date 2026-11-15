package com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp

import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMotionTest {
    private val t = BackgroundMotionTuning

    /** 1 s segments alternating -30 dB (quiet) and -6 dB (loud) in 20 s blocks: quiet 0-20, loud 20-40, quiet 40-60. */
    private fun analysis(): AudioAnalysis {
        val segments = (0 until 60).map { i ->
            val db = if (i in 20..39) -6f else -30f
            AudioAnalysis.Segment(start = i.toFloat(), loudnessStart = db, loudnessMax = db, peakAt = i + 0.5f)
        }
        return AudioAnalysis(
            tempo = 120f, loudness = -12f,
            sections = listOf(AudioAnalysis.Section(0f, 60f, -12f, 120f)),
            beats = emptyList(), segments = segments,
        )
    }

    @Test fun `energy maps the track's own quiet and loud to 0 and 1`() {
        val track = TrackMotion(analysis())
        assertEquals(0f, track.energyAt(5f), 1e-4f)
        assertEquals(1f, track.energyAt(30f), 1e-4f)
        assertEquals(0f, track.energyAt(50f), 1e-4f)
    }

    @Test fun `a flat track keeps a minimum loudness window so noise isn't motion`() {
        val segments = (0 until 20).map {
            AudioAnalysis.Segment(it.toFloat(), -10f + (it % 2) * 0.5f, -10f + (it % 2) * 0.5f, it + 0.5f)
        }
        val track = TrackMotion(AudioAnalysis(120f, -10f, listOf(AudioAnalysis.Section(0f, 20f, -10f, 120f)), emptyList(), segments = segments))
        // Half a dB of difference over a 6 dB window stays well away from the extremes.
        assertTrue(track.energyAt(5f) in 0.3f..0.7f)
        assertTrue(track.energyAt(6f) in 0.3f..0.7f)
    }

    @Test fun `outside the analysed span energy is zero and a seek finds the right segment`() {
        val track = TrackMotion(analysis())
        assertEquals(0f, track.energyAt(-1f), 0f)
        assertEquals(0f, track.energyAt(61f), 0f)
        assertEquals(1f, track.energyAt(30f), 1e-4f)
        assertEquals(0f, track.energyAt(3f), 1e-4f) // backwards seek
        assertEquals(1f, track.energyAt(39f), 1e-4f) // forward jump
    }

    @Test fun `without segments the sections' loudness stands in`() {
        val a = AudioAnalysis(
            120f, -10f,
            listOf(AudioAnalysis.Section(0f, 10f, -30f, 120f), AudioAnalysis.Section(10f, 10f, -6f, 120f)),
            emptyList(),
        )
        val track = TrackMotion(a)
        assertEquals(0f, track.energyAt(5f), 1e-4f)
        assertEquals(1f, track.energyAt(15f), 1e-4f)
    }

    private fun run(clock: MotionClock, seconds: Float, position: (Float) -> Float, playing: Boolean, track: TrackMotion?) {
        var elapsed = 0f
        while (elapsed < seconds) {
            clock.step(1f / 60f, position(elapsed), playing, track)
            elapsed += 1f / 60f
        }
    }

    @Test fun `energy rises faster than it falls`() {
        val track = TrackMotion(analysis())
        val rising = MotionClock()
        run(rising, 1.1f, { 30f }, true, track)
        // One attack time constant: about 63% of the way from the neutral start to 1.
        assertTrue(rising.energy in 0.7f..0.85f)

        val falling = MotionClock()
        run(falling, 20f, { 30f }, true, track) // settle high
        assertTrue(falling.energy > 0.99f)
        run(falling, 1.1f, { 5f }, true, track)
        // Same time into a fall has barely moved: release is slower than attack.
        assertTrue(falling.energy > 0.6f)
    }

    @Test fun `the envelope is read ahead of the position`() {
        val track = TrackMotion(analysis())
        val clock = MotionClock()
        // 0.3 s before the loud part begins the lead already sees it.
        run(clock, 2f, { 19.7f }, true, track)
        assertTrue(clock.energy > t.NEUTRAL_ENERGY)
    }

    @Test fun `loud flows faster than quiet and zooms in`() {
        val track = TrackMotion(analysis())
        val loud = MotionClock().also { run(it, 15f, { 30f }, true, track) }
        val quiet = MotionClock().also { run(it, 15f, { 5f }, true, track) }
        assertEquals((t.RATE_LOUD) * t.MOTION_INTENSITY, loud.rate, 0.05f)
        assertEquals((t.RATE_QUIET) * t.MOTION_INTENSITY, quiet.rate, 0.05f)
        assertEquals(1f + t.SCALE_PER_ENERGY * t.MOTION_INTENSITY, loud.scale, 1e-3f)
        assertEquals(1f, quiet.scale, 1e-3f)
        assertTrue(loud.time > quiet.time)
    }

    @Test fun `paused settles at the paused rate with no zoom`() {
        val track = TrackMotion(analysis())
        val clock = MotionClock()
        run(clock, 10f, { 30f }, true, track)
        run(clock, 6f, { 30f }, false, track)
        assertEquals(t.PAUSED_RATE, clock.rate, 0.01f)
        assertEquals(1f, clock.scale, 1e-3f)
    }

    @Test fun `no analysis falls back to a steady unanalysed rate`() {
        val clock = MotionClock()
        run(clock, 10f, { 30f }, true, null)
        assertEquals(t.RATE_UNANALYZED * t.MOTION_INTENSITY, clock.rate, 0.01f)
        assertEquals(1f, clock.scale, 1e-6f)
    }

    @Test fun `an analysis arriving eases in instead of jumping`() {
        val track = TrackMotion(analysis())
        val clock = MotionClock()
        run(clock, 10f, { 30f }, true, null)
        val before = clock.rate
        clock.step(1f / 60f, 30f, true, track)
        assertEquals(before, clock.rate, 0.1f)
    }

    @Test fun `a stalled frame advances at most the capped step`() {
        val clock = MotionClock()
        clock.step(0.016f, 0f, true, null)
        val before = clock.time
        clock.step(5f, 0f, true, null)
        assertTrue(clock.time - before <= t.MAX_FRAME_STEP * clock.rate + 1e-4f)
        val frozen = clock.time
        clock.step(-1f, 0f, true, null)
        assertEquals(frozen, clock.time, 0f)
    }

    @Test fun `seed continues from a given time`() {
        val clock = MotionClock()
        clock.seed(12.5f)
        assertEquals(12.5f, clock.time, 0f)
        clock.seed(Float.NaN)
        assertEquals(12.5f, clock.time, 0f)
    }
}
