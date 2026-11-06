package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.animation.ElementState
import org.junit.Assert.*
import org.junit.Test

class RefreshedRenderingTest {
    @Test fun fixedSoftEdgeStartsDimAndFinishesFullyLit() {
        val start = refreshedWipe(-50f, 100f, 22f)
        val middle = refreshedWipe(25f, 100f, 22f)
        val end = refreshedWipe(100f, 100f, 22f)
        assertEquals(-0.22f, start.first, 1e-6f)
        assertEquals(0f, start.second, 1e-6f)
        assertEquals(0.22f, middle.second - middle.first, 1e-6f)
        assertEquals(1f, end.first, 1e-6f)
    }

    @Test fun rippleMovesUpperRowsFirstThenSettlesAndSnapsOnSeek() {
        val ripple = RippleScroll()
        val tops = floatArrayOf(0f, 100f, 200f, 300f)
        val heights = FloatArray(4) { 80f }
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

    @Test fun rippleOverridesSmoothScrolling() {
        val scroll = ScrollManager().apply { smoothScrolling = true; refreshedSimple = true }
        scroll.updateScroll(0.016f, 2000f, 0, 0f)
        scroll.updateScroll(0.016f, 2000f, 1, -200f)
        assertEquals(-200f, scroll.animScrollY, 0f)
    }
    @Test fun brighterUnsungTailOnlyAppliesInsideTheActiveLine() {
        val config = RenderConfig.SIMPLE.copy(refreshedSimple = true)
        assertEquals(0.4f, config.activeGradientAlphaDim, 0f)
        val waiting = lyricPaintPlan(ElementState.NotSung, false, 1f, 0f, config)
            as LyricPaintPlan.InactiveShadow
        assertEquals(0.3f, waiting.alpha, 0f)
        assertEquals(0.3f, config.copy(refreshedSimple = false).activeGradientAlphaDim, 0f)
    }

}
