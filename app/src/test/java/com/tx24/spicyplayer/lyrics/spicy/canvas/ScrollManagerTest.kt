package com.tx24.spicyplayer.lyrics.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollManagerTest {
    private var now = 1_000L
    private val scroll = ScrollManager(clockMs = { now })
    private val height = 5_000f

    /** Runs [seconds] of 60fps frames following line [index] centred at [targetY]. */
    private fun frames(seconds: Float, index: Int, targetY: Float, visible: Float = 100f) {
        repeat((seconds * 60).toInt()) {
            now += 16
            scroll.updateScroll(1f / 60f, height, index, targetY, targetVisiblePx = visible)
        }
    }

    private fun drag(dy: Float) {
        scroll.onDragStart()
        now += 16
        scroll.onDrag(dy, height)
        now += 200 // held still before lifting: no fling
        scroll.onDragEnd()
    }

    @Test fun firstFrameSnapsToTheLine() {
        frames(0.02f, index = 0, targetY = -300f)
        assertEquals(-300f, scroll.animScrollY, 0.01f)
    }

    @Test fun aRaisedTopHoldsTheOpeningLinesHighUntilCentringNeedsAMove() {
        scroll.topY = -500f
        // Before the first line: straight to the top.
        now += 16
        scroll.updateScroll(1f / 60f, height, null, null)
        assertEquals(-500f, scroll.animScrollY, 0.01f)
        // A line centred above the top stays at the top...
        frames(1f, 0, -100f)
        assertEquals(-500f, scroll.animScrollY, 0.01f)
        // ...and one further down is centred.
        frames(2f, 3, -900f)
        assertEquals(-900f, scroll.animScrollY, 0.01f)
    }

    @Test fun lyricsShorterThanARaisedTopRestAtTheTop() {
        // The crash: one ~92px line under a top raised to -156px made the scroll range empty.
        scroll.topY = -156f
        repeat(120) {
            now += 16
            scroll.updateScroll(1f / 60f, 92f, 0, -40f, targetVisiblePx = 92f)
        }
        assertEquals(-156f, scroll.animScrollY, 0.01f)
        drag(-300f)
        repeat(120) {
            now += 16
            scroll.updateScroll(1f / 60f, 92f, 0, -40f, targetVisiblePx = 92f)
        }
        assertTrue(scroll.animScrollY.isFinite())
    }

    @Test fun aLineChangeGlidesFrontLoadedLikeDesktopChromium() {
        frames(0.1f, 0, 0f)
        // 400px: sqrt(400) = 20 frames. cubic-bezier(0.4, 0, 0, 1) covers ~86% of it by the
        // halfway mark, where ease-in-out is only at 50%.
        frames(10f / 60f, 1, -400f)
        assertTrue(scroll.animScrollY < -320f)
        frames(15f / 60f, 1, -400f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
    }

    @Test fun touchingHidesBlurAndStopsAutoScrollUntilCooldown() {
        frames(0.1f, 0, -300f)
        drag(-200f)
        assertTrue(scroll.hideLineBlur)
        val left = scroll.animScrollY

        // A new line starts while the user is still in control: the view stays put.
        frames(0.5f, 1, -600f)
        assertEquals(left, scroll.animScrollY, 0.01f)
        assertTrue(scroll.hideLineBlur)

        // After the cooldown auto-scroll resumes, and the line has changed, so it glides there.
        frames(2f, 1, -600f)
        assertFalse(scroll.hideLineBlur)
        assertEquals(-600f, scroll.animScrollY, 1f)
    }

    @Test fun afterResumingANudgeIsLeftAloneUntilTheLineChanges() {
        frames(0.1f, 0, -300f)
        drag(-50f)
        frames(2f, 0, -300f)
        assertFalse(scroll.hideLineBlur)
        assertEquals(-350f, scroll.animScrollY, 0.01f)

        frames(2f, 1, -600f)
        assertEquals(-600f, scroll.animScrollY, 1f)
    }

    @Test fun scrolledAwayFromTheLineStaysManual() {
        frames(0.1f, 0, -300f)
        drag(-2_000f)
        frames(3f, 1, -600f, visible = 0f)
        assertTrue(scroll.hideLineBlur)
        assertEquals(-2_300f, scroll.animScrollY, 0.01f)
    }

    @Test fun aSeekSnapsBackEvenWhenScrolledAway() {
        frames(0.1f, 0, -300f)
        drag(-2_000f)
        now += 16
        scroll.updateScroll(1f / 60f, height, 5, -1_500f, snap = true, targetVisiblePx = 0f)
        assertFalse(scroll.hideLineBlur)
        assertEquals(-1_500f, scroll.animScrollY, 0.01f)
    }

    @Test fun aFlingKeepsControlUntilItStops() {
        frames(0.1f, 0, -300f)
        scroll.onDragStart()
        now += 16
        scroll.onDrag(-40f, height)
        scroll.onDragEnd() // lifted mid-motion: flings
        val start = scroll.animScrollY
        frames(0.3f, 1, -600f)
        assertTrue(scroll.hideLineBlur)
        assertNotEquals(start, scroll.animScrollY)
    }

    @Test fun smoothScrollingSpringsToTheLineWithoutOvershooting() {
        scroll.smoothScrolling = true
        frames(0.1f, 0, 0f)
        var furthest = 0f
        repeat(150) {
            frames(1f / 60f, 1, -400f)
            furthest = minOf(furthest, scroll.animScrollY)
        }
        assertTrue(furthest >= -400f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
    }

    @Test fun smoothScrollingCarriesItsSpeedIntoTheNextLine() {
        val glide = ScrollManager(clockMs = { now })
        scroll.smoothScrolling = true
        for (manager in listOf(scroll, glide)) {
            manager.updateScroll(1f / 60f, height, 0, 0f)
            repeat(12) { manager.updateScroll(1f / 60f, height, 1, -400f) }
        }
        // Mid-move the line changes again: the spring's next step keeps going at speed, the
        // glide starts over from a standstill.
        val springBefore = scroll.animScrollY
        val glideBefore = glide.animScrollY
        scroll.updateScroll(1f / 60f, height, 2, -800f)
        glide.updateScroll(1f / 60f, height, 2, -800f)
        assertTrue(springBefore - scroll.animScrollY > glideBefore - glide.animScrollY)
    }

    @Test fun switchingSmoothScrollingOffMidMoveStillLands() {
        scroll.smoothScrolling = true
        frames(0.1f, 0, 0f)
        frames(0.1f, 1, -400f)
        scroll.smoothScrolling = false
        frames(2f, 1, -400f)
        assertEquals(-400f, scroll.animScrollY, 0.01f)
    }

    @Test fun aRelayoutKeepsTheUsersScrollAtTheSamePointOfTheLyrics() {
        frames(0.5f, 0, -300f)
        drag(-1_000f)
        val scrolled = scroll.animScrollY
        // Bigger text: the same lyrics, twice as tall.
        scroll.onRelayout()
        now += 16
        scroll.updateScroll(1f / 60f, height * 2, 0, -600f, targetVisiblePx = 0f)
        assertEquals(scrolled * 2, scroll.animScrollY, 0.01f)
        assertTrue(scroll.hideLineBlur)
    }

    @Test fun aRelayoutOfStaticLyricsKeepsThePlaceInsteadOfJumpingToTheTop() {
        now += 16
        scroll.updateScroll(1f / 60f, height, null, null)
        drag(-800f)
        repeat(120) {
            now += 16
            scroll.updateScroll(1f / 60f, height, null, null)
        }
        val scrolled = scroll.animScrollY
        scroll.onRelayout()
        now += 16
        scroll.updateScroll(1f / 60f, height, null, null)
        assertEquals(scrolled, scroll.animScrollY, 0.01f)
    }

    @Test fun aRelayoutWhileFollowingStillJumpsToTheLine() {
        frames(0.5f, 2, -300f)
        scroll.onRelayout()
        now += 16
        scroll.updateScroll(1f / 60f, height, 2, -700f, targetVisiblePx = 100f)
        assertEquals(-700f, scroll.animScrollY, 0.01f)
    }
}
