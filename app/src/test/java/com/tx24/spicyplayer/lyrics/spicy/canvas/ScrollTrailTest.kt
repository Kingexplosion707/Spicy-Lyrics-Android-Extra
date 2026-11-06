package com.tx24.spicyplayer.lyrics.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollTrailTest {
    private val frame = 16_000_000L

    @Test
    fun `a late line takes its shift and its scroll from the same moment`() {
        val trail = ScrollTrail()
        trail.reference = 2
        // Line 4 (two past the active line) sits at 400 with the view at -100.
        val before = floatArrayOf(0f, 100f, 200f, 300f, 400f)
        repeat(10) { trail.record(it * frame, -100f, before) }
        // The list moves on to the next line (50px up) while a background vocal above it folds
        // 40px: every line below moves up by 40, and the scroll makes up for it, going 10px net.
        val folded = floatArrayOf(0f, 100f, 160f, 260f, 360f)
        val now = 10 * frame
        trail.record(now, -110f, folded)

        val delay = trail.delayNanos(4)
        assertTrue(delay > 0)
        val drawnAt = trail.at(now - delay) + trail.shiftAt(now - delay, 4, folded[4])
        // Where it was before the fold: neither ahead (the old bug) nor behind.
        assertEquals(300f, drawnAt, 0.01f)
    }

    @Test
    fun `a change in the shifts alone keeps the late lines catching up`() {
        val trail = ScrollTrail()
        val shifts = floatArrayOf(0f, 100f)
        repeat(20) { trail.record(it * frame, 0f, shifts) }
        assertFalse(trail.catchingUp(19 * frame))
        // A background vocal opens below the active line: the scroll stays put.
        trail.record(20 * frame, 0f, floatArrayOf(0f, 120f))
        assertTrue(trail.catchingUp(20 * frame))
        assertFalse(trail.catchingUp(20 * frame + 1_000_000_000L))
    }

    @Test
    fun `a scroll to a later line that backs up for a moment keeps the lines below late`() {
        val trail = ScrollTrail()
        trail.reference = 2
        repeat(10) { trail.record(it * frame, -100f, floatArrayOf()) }
        // On to line 3: the view goes up, then comes back down a little as a fold above is made up for.
        trail.reference = 3
        trail.record(10 * frame, -130f, floatArrayOf())
        trail.record(11 * frame, -120f, floatArrayOf())
        assertEquals(-1, trail.direction)
        assertTrue(trail.delayNanos(5) > 0)
        assertEquals(0L, trail.delayNanos(1))
    }

    @Test
    fun `a drag after the scroll has settled sets the direction`() {
        val trail = ScrollTrail()
        trail.reference = 3
        trail.record(0L, -100f, floatArrayOf())
        val later = 2_000_000_000L
        trail.record(later, -100f, floatArrayOf())
        trail.record(later + frame, -80f, floatArrayOf())
        assertEquals(1, trail.direction)
    }

    @Test
    fun `going back to an earlier line makes the lines above late`() {
        val trail = ScrollTrail()
        trail.reference = 5
        trail.record(0L, -500f, floatArrayOf())
        trail.reference = 2
        trail.record(frame, -480f, floatArrayOf())
        assertEquals(1, trail.direction)
        assertTrue(trail.delayNanos(0) > 0)
    }

    @Test
    fun `background vocals move with their lead in the stagger`() {
        val trail = ScrollTrail()
        // Lead, its background vocal, then two more leads.
        trail.places = intArrayOf(0, 0, 1, 2)
        assertEquals(0L, trail.delayNanos(1))
        assertEquals(50_000_000L, trail.delayNanos(2))
        assertEquals(100_000_000L, trail.delayNanos(3))
    }

    @Test
    fun `a line no frame knows keeps its current shift`() {
        val trail = ScrollTrail()
        trail.record(0L, 0f, floatArrayOf(0f))
        assertEquals(55f, trail.shiftAt(0L, 3, 55f), 0f)
    }
}
