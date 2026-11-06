package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundPopTest {
    private val lines = listOf(
        Line(listOf(Word("lead", 0, 1000)), 0, 1000, groupId = 1),
        Line(listOf(Word("echo", 2000, 3000)), 2000, 3000, role = LineRole.BACKGROUND, groupId = 1),
    )

    private fun presence(config: RenderConfig, time: Long) =
        LyricsAnimator(CoroutineScope(Job().apply { cancel() }), config).animate(lines, time, 0.016f)[1].presence

    @Test fun backgroundLineIsTuckedAwayUntilItsLeadOrItselfIsSung() {
        val on = RenderConfig.FULL.copy(backgroundPop = true)
        assertEquals(1f, presence(on, 500), 0f)
        assertEquals(0f, presence(on, 1500), 0f)
        assertEquals(1f, presence(on, 2500), 0f)
    }

    @Test fun offByDefault() {
        assertEquals(1f, presence(RenderConfig.FULL, 1500), 0f)
    }

    private val appleLines = listOf(
        Line(listOf(Word("lead", 0, 1000)), 0, 1000, groupId = 1),
        Line(listOf(Word("echo", 200, 1000)), 200, 1000, role = LineRole.BACKGROUND, groupId = 1),
        Line(listOf(Word("next", 2000, 3000)), 2000, 3000, groupId = 2),
        Line(listOf(Word("echo", 2000, 3000)), 2000, 3000, role = LineRole.BACKGROUND, groupId = 2),
    )
    private fun appleAnimator() =
        LyricsAnimator(CoroutineScope(Job().apply { cancel() }), RenderConfig.FULL.copy(appleMusic = true))

    @Test fun appleMusicKeepsASungBackgroundLineOpenUntilALaterLineStarts() {
        val animator = appleAnimator()
        assertEquals(1f, animator.animate(appleLines, 500, 0.016f)[1].presenceGoal, 0f)
        assertEquals(1f, animator.animate(appleLines, 1500, 0.016f)[1].presenceGoal, 0f)
        val next = animator.animate(appleLines, 2000, 0.016f)
        assertEquals(0f, next[1].presenceGoal, 0f)
        assertEquals(1f, next[3].presenceGoal, 0f)
    }

    @Test fun appleMusicSeekIntoAGapDoesNotOpenASungBackgroundLine() {
        assertEquals(0f, appleAnimator().animate(appleLines, 1500, 0.016f)[1].presenceGoal, 0f)
    }

    /** The whole point: the fold, the open and the scroll are one move, frame by frame. */
    @Test fun appleMusicFoldAndOpenFollowTheScrollsSpring() {
        val animator = appleAnimator()
        animator.animate(appleLines, 500, 0.016f)
        val scroll = SpringSimulation(0f, AppleMusicMotion.SCROLL_FREQUENCY_HZ, AppleMusicMotion.SCROLL_DAMPING)
        scroll.setGoal(1f)
        repeat(30) { frame ->
            val states = animator.animate(appleLines, 2000L + frame * 16, 0.016f)
            val moved = scroll.step(0.016f)
            assertEquals(moved, states[3].presence, 0.001f)
            assertEquals(1f - moved, states[1].presence, 0.001f)
        }
    }
}
