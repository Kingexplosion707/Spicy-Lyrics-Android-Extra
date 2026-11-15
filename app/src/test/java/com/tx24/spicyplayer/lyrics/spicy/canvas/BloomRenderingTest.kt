package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.animation.BloomMotion
import com.tx24.spicyplayer.lyrics.spicy.animation.ElementState
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import org.junit.Assert.*
import org.junit.Test

class BloomRenderingTest {
    private val tops = floatArrayOf(0f, 100f, 200f, 300f)
    private val heights = FloatArray(4) { 80f }

    @Test fun rippleMovesUpperRowsFirstThenSettlesAndSnapsOnSeek() {
        val ripple = RippleScroll()
        fun frame(from: Float, to: Float, target: Int, snap: Boolean = false) =
            ripple.update(from, to, target, tops, heights, 200f, 800f, 1f / 60, snap, false)
        frame(0f, 0f, 0, true)
        assertTrue(frame(0f, -100f, 1))
        assertTrue(ripple.offset(0) < ripple.offset(2))
        assertTrue(ripple.offset(0) > 0f)
        repeat(120) { frame(-100f, -100f, 1) }
        assertEquals(0f, ripple.offset(3), 0f)
        frame(-100f, -500f, 3, true)
        assertEquals(0f, ripple.offset(0), 0f)
        assertEquals(0f, ripple.offset(3), 0f)
    }

    @Test fun timedRowsAllTakeTheSameTimeTheRowsBelowSettingOffLater() {
        val ripple = RippleScroll()
        fun frame(from: Float, to: Float, dt: Float = 1f / 60) =
            ripple.update(from, to, 1, tops, heights, 200f, 800f, dt, false, false, timedMs = 600)
        ripple.update(0f, 0f, 0, tops, heights, 200f, 800f, 1f / 60, true, false)
        assertTrue(frame(0f, -100f))
        // Rows at and above the line set off together; the ones below wait their turn.
        repeat(5) { frame(-100f, -100f) }
        assertEquals(ripple.offset(0), ripple.offset(1), 1e-4f)
        assertTrue(ripple.offset(2) > ripple.offset(1))
        assertTrue(ripple.offset(3) > ripple.offset(2))
        // 600 ms plus the longest wait, and every row is home.
        repeat(45) { frame(-100f, -100f) }
        assertFalse(frame(-100f, -100f))
        assertEquals(0f, ripple.offset(3), 0f)
    }

    @Test fun rowsOverrideSmoothScrolling() {
        val scroll = ScrollManager().apply { smoothScrolling = true; rowsScroll = true }
        scroll.updateScroll(0.016f, 2000f, 0, 0f)
        scroll.updateScroll(0.016f, 2000f, 1, -200f)
        assertEquals(-200f, scroll.animScrollY, 0f)
    }

    @Test fun bloomRestsEveryLineAtOneToneAndASungLineDimsBackToIt() {
        val config = RenderConfig.BLOOM
        val waiting = lyricPaintPlan(ElementState.NotSung, false, 1f, 0f, config) as LyricPaintPlan.InactiveShadow
        assertEquals(BloomMotion.UNLIT, waiting.alpha, 0f)
        val justSung = lyricPaintPlan(ElementState.Sung, false, 1f, 0f, config, lit = 1f) as LyricPaintPlan.InactiveShadow
        assertEquals(BloomMotion.LIT, justSung.alpha, 1e-6f)
        val rested = lyricPaintPlan(ElementState.Sung, false, 1f, 0f, config, lit = 0f) as LyricPaintPlan.InactiveShadow
        assertEquals(BloomMotion.UNLIT, rested.alpha, 0f)
        val background = lyricPaintPlan(ElementState.Sung, true, 1f, 0f, config, lit = 1f) as LyricPaintPlan.InactiveShadow
        assertEquals(BloomMotion.BACKGROUND_LIT, background.alpha, 1e-6f)
        // Spring keeps its own colours.
        val spring = lyricPaintPlan(ElementState.Sung, false, 1f, 0f, RenderConfig.FULL) as LyricPaintPlan.InactiveShadow
        assertEquals(0.85f, spring.alpha, 0f)
    }

    @Test fun bloomsLineGapIsWiderThanSpringsByAThird() {
        val metrics = LyricsLayoutMetrics(400f * 2.625f, 2.625f, LyricsType.Syllable, 1f)
        val em = metrics.baseFontSizeSp * 2.625f
        assertEquals(em * 0.8f, metrics.lineGapPx(bloom = true), 1e-3f)
        assertEquals(em * 0.6f, metrics.lineGapPx(bloom = false), 1e-3f)
    }
}
