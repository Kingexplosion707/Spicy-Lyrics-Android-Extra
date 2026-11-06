package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.animation.LineAnimState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticLinesTest {
    private val config = RenderConfig(false, false)
    private val layouts = emptyList<LineLayout>()

    private fun anim(opacity: Float, active: Boolean = false) =
        LineAnimState(opacity, 0f, 1f, active, emptyList(), isBackground = false, isSongwriter = false)

    private fun set(vararg lines: DrawnLine, layouts: List<LineLayout> = this.layouts) =
        StillSet(layouts, config, 1080f, 2000f, lines.toList())

    @Test
    fun `a line is live when active or changed since the last frame`() {
        val cache = StaticLines()
        assertTrue("no last frame yet", cache.isLive(0, anim(0.5f)))
        cache.endFrame(listOf(anim(0.5f), anim(1f, active = true)))
        assertFalse(cache.isLive(0, anim(0.5f)))
        assertTrue("changed", cache.isLive(0, anim(0.6f)))
        assertTrue("active", cache.isLive(1, anim(1f, active = true)))
        assertTrue("new line", cache.isLive(2, anim(0.5f)))
    }

    @Test
    fun `still lines are recorded after holding for two frames, then replayed`() {
        val cache = StaticLines()
        val line = DrawnLine(0, anim(0.5f), 100f, 40f)
        assertEquals(StaticLines.Plan.Direct, cache.plan(set(line)))
        assertEquals(StaticLines.Plan.Record, cache.plan(set(line)))
        cache.recordedAs(set(line))
        assertEquals(StaticLines.Plan.Replay, cache.plan(set(line)))
        assertEquals(StaticLines.Plan.Replay, cache.plan(set(line)))
    }

    @Test
    fun `a moving set draws directly instead of recording every frame`() {
        val cache = StaticLines()
        for (frame in 0 until 5) {
            assertEquals(StaticLines.Plan.Direct, cache.plan(set(DrawnLine(0, anim(0.5f), 100f - frame, 40f))))
        }
    }

    @Test
    fun `a re-measure or no still lines never replays the old layer`() {
        val cache = StaticLines()
        val line = DrawnLine(0, anim(0.5f), 100f, 40f)
        cache.plan(set(line))
        cache.plan(set(line))
        cache.recordedAs(set(line))
        assertEquals(StaticLines.Plan.Direct, cache.plan(set(line, layouts = ArrayList())))
        assertEquals(StaticLines.Plan.Direct, cache.plan(set()))
    }
}
