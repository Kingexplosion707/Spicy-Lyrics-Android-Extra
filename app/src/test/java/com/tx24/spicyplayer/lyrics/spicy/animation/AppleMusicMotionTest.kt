package com.tx24.spicyplayer.lyrics.spicy.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicMotionTest {
    @Test
    fun `a word springs up as measured, whatever its length, and stays up while its line goes on`() {
        assertEquals(0f, AppleMusicMotion.wordRise(0.0, 0, 300, null), 0f)
        // Measured on the Music app: about 43% at 320ms, 82% at 720ms, 93% at 980ms.
        assertEquals(0.43f, AppleMusicMotion.wordRise(320.0, 0, 300, null), 0.08f)
        assertEquals(0.82f, AppleMusicMotion.wordRise(720.0, 0, 300, null), 0.06f)
        assertEquals(0.93f, AppleMusicMotion.wordRise(980.0, 0, 300, null), 0.04f)
        assertEquals(AppleMusicMotion.wordRise(500.0, 0, 300, null), AppleMusicMotion.wordRise(500.0, 0, 3000, null), 0f)
        assertEquals(1f, AppleMusicMotion.wordRise(9000.0, 0, 300, null), 1e-3f)
    }

    @Test
    fun `once the line ends a word springs back down`() {
        val atEnd = AppleMusicMotion.wordRise(2000.0, 0, 300, 2000)
        assertEquals(1f, atEnd, 1e-2f)
        assertTrue(AppleMusicMotion.wordRise(2400.0, 0, 300, 2000) in 0.1f..0.9f)
        assertEquals(0f, AppleMusicMotion.wordRise(6000.0, 0, 300, 2000), 1e-3f)
        assertEquals(0f, AppleMusicMotion.wordRise(Double.POSITIVE_INFINITY, 0, 300, 2000), 0f)
    }

    @Test
    fun `held words of two to seven letters, or any CJK, get the emphasis`() {
        assertTrue(AppleMusicMotion.emphasizes("love", 4, 1200))
        assertFalse(AppleMusicMotion.emphasizes("love", 4, 900))
        assertFalse(AppleMusicMotion.emphasizes("I", 1, 2000))
        assertFalse(AppleMusicMotion.emphasizes("everything", 10, 2000))
        assertTrue(AppleMusicMotion.emphasizes("愛してる", 4, 1000))
        assertFalse(AppleMusicMotion.emphasizes("حب", 2, 2000))
    }

    @Test
    fun `emphasized letters swell, spread and glow mid-word, then settle`() {
        fun pose(t: Double, i: Int) = AppleMusicMotion.emphasisLetter(t, 0, 2000, i, 4, lastWord = false, background = false)
        val rest = pose(-1000.0, 0)
        assertEquals(1f, rest.scale, 1e-4f)
        assertEquals(0f, rest.glow, 1e-4f)
        val mid = pose(1000.0, 0)
        assertTrue(mid.scale > 1f)
        assertTrue(mid.glow > 0f)
        assertTrue("first letter pushed left", mid.xOffset < 0f)
        assertTrue("last letter pushed right", pose(1000.0, 3).xOffset > 0f)
        assertTrue(mid.yOffset < 0f)
        val done = pose(20_000.0, 2)
        assertEquals(1f, done.scale, 1e-4f)
        assertEquals(0f, done.glow, 1e-4f)
        assertEquals(0f, done.yOffset, 1e-3f)
        // Toned down: never past a 1.08 swell, and a line's last word no bigger.
        assertTrue((0..40).all { pose(it * 100.0, 0).scale <= 1.08f })
        val last = AppleMusicMotion.emphasisLetter(1200.0, 0, 2000, 0, 4, lastWord = true, background = false)
        assertTrue(last.scale <= 1.08f)
    }

    @Test
    fun `dots grow in, breathe, and pop away at the end`() {
        val dots = AppleMusicMotion.Dots(10_000f)
        assertEquals(0f, dots.alpha(0f), 1e-4f)
        assertEquals(1f, dots.alpha(5000f), 1e-4f)
        assertEquals(0f, dots.alpha(10_000f), 1e-4f)
        // Breathing gently, as measured: no more than ~8% across.
        assertTrue((30..60).all { dots.scale(it * 100f) in 0.92f..1f })
        // They light in turn.
        assertTrue(dots.dotAlpha(0, 4000f) > dots.dotAlpha(2, 4000f))
    }

    @Test
    fun `other lines dim alike and blur more with distance, as measured`() {
        assertEquals(1f, AppleMusicMotion.lineOpacity(0, false), 0f)
        assertEquals(AppleMusicMotion.lineOpacity(1, false), AppleMusicMotion.lineOpacity(5, false), 0f)
        // 0.35 of a lit line's brightness.
        assertEquals(0.35f, AppleMusicMotion.lineOpacity(1, false) * AppleMusicMotion.DIM_ALPHA, 0.01f)
        val coming = (1..5).map { AppleMusicMotion.blurEm(it) }
        assertTrue(coming.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0f, AppleMusicMotion.blurEm(0), 0f)
    }

    @Test
    fun `the cubic bezier matches CSS ease-out at its ends and stays monotonic`() {
        val ease = CubicBezier(0f, 0f, 0.58f, 1f)
        assertEquals(0f, ease(0f), 0f)
        assertEquals(1f, ease(1f), 0f)
        val samples = (0..100).map { ease(it / 100f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - 1e-5f })
        assertTrue("ease-out is ahead of linear", ease(0.3f) > 0.3f)
    }

    @Test
    fun `a long held word's letters swell about 6 percent and rise about a tenth of an em`() {
        // Four seconds: emphasis grows with the hold, so this is near the most a word gets.
        val poses = (0..5600 step 20).map { AppleMusicMotion.emphasisLetter(it.toDouble(), 0, 4000, 1, 4, lastWord = false, background = false) }
        val scale = poses.maxOf { it.scale }
        val lift = -poses.minOf { it.yOffset }
        assertTrue("scale $scale", scale in 1.05f..1.08f)
        assertTrue("lift $lift", lift in 0.08f..0.14f)
    }
}
