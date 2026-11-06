package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollPolicyControllerTest {
    private fun lead(start: Long, end: Long) = Line(emptyList(), start, end)
    private fun bg(start: Long, end: Long) = Line(emptyList(), start, end, role = LineRole.BACKGROUND)
    private fun interlude(start: Long, end: Long) = Line(emptyList(), start, end, role = LineRole.INTERLUDE)

    private fun target(lines: List<Line>, at: Long) = ScrollPolicyController.selectTargetIndex(lines, at)

    @Test fun overlappingLinesKeepTheTopOneWhileItEndsBeforeTheLineTwoDown() {
        // Line 0 runs into line 1 but ends before line 2 starts: the view stays on line 0.
        val lines = listOf(lead(0, 1_500), lead(1_000, 2_500), lead(2_000, 3_000))
        assertEquals(0, target(lines, 1_200))
    }

    @Test fun overlappingLinesMoveOnWhenTheTopOneOutlastsTheLookahead() {
        // Line 0 is still going when line 2 starts; lines 0 and 1 are adjacent, so the first.
        val lines = listOf(lead(0, 2_500), lead(1_000, 2_600), lead(2_000, 3_000), lead(4_000, 5_000))
        assertEquals(0, target(lines, 1_200))
        // Three active at once with a gap between first and last: the last one.
        assertEquals(2, target(lines, 2_200))
    }

    @Test fun aBackgroundTailUnderALaterLineIsIgnored() {
        // Line 0's background vocal outlives it into line 2: the view must not go back up.
        val lines = listOf(lead(0, 1_000), bg(500, 3_000), lead(1_000, 2_000), lead(2_000, 3_000))
        assertEquals(3, target(lines, 2_500))
    }

    @Test fun backgroundVocalsSungAheadOfTheirLineDontPullTheViewAhead() {
        // Jane Remover, "Music Baby", 2:18–2:24 (Spicy Lyrics): each "We just make music, baby"
        // has its background vocal sung first, so its group starts long before its words.
        fun sung(start: Long, end: Long, words: Long) = Line(listOf(Word("x", words, end)), start, end)
        val lines = listOf(
            sung(138_928, 140_410, 138_928), // 0 Like B-O-Y
            sung(139_110, 142_654, 141_093), // 1 We just make music, baby
            bg(139_110, 140_922), //            2   I don't give a fuck what they think about us
            sung(140_812, 143_820, 140_812), // 3 B-A-B-Y-B-O-Y
            sung(142_573, 146_085, 144_557), // 4 We just make music, baby
            bg(142_573, 144_399), //            5   I don't give a fuck…
            sung(144_285, 147_308, 144_285), // 6 B-A-B-Y-B-O-Y
        )
        assertEquals(0, target(lines, 140_000))
        // Line 3 starts while line 1 is still to sing: the view stays on line 1, line 3 under it.
        for (at in listOf(140_900L, 141_500L, 142_600L)) assertEquals("at $at", 1, target(lines, at))
        assertEquals(3, target(lines, 142_700))
    }

    @Test fun aBackgroundLineLeadingIntoItsOwnLineCounts() {
        val lines = listOf(lead(0, 1_000), lead(2_000, 3_000), bg(1_500, 2_500))
        // The background vocal (belonging to line 1) starts first: it pulls toward line 1.
        assertEquals(1, target(lines, 1_600))
    }

    @Test fun nothingActiveMeansNoNewTarget() {
        assertNull(target(listOf(lead(0, 1_000), lead(2_000, 3_000)), 1_500))
    }

    @Test fun betweenLinesTheViewStaysOnTheLastLine() {
        val policy = ScrollPolicyController()
        val lines = listOf(lead(0, 1_000), lead(2_000, 3_000))
        policy.decide(lines, 500)
        assertEquals(0, policy.decide(lines, 1_200).targetIndex)
    }

    @Test fun aSeekIntoAGapLandsOnTheLastLineThatStarted() {
        val policy = ScrollPolicyController()
        val lines = listOf(lead(0, 1_000), lead(2_000, 3_000), lead(5_000, 6_000))
        policy.decide(lines, 500)
        val decision = policy.decide(lines, 4_000)
        assertEquals(1, decision.targetIndex)
        assertEquals(ScrollMotion.SNAP, decision.motion)
    }

    @Test fun closingInterludeDotsHandOverToTheNextLine() {
        val lines = listOf(lead(0, 1_000), interlude(1_000, 6_000), lead(6_000, 7_000))
        assertEquals(1, target(lines, 3_000))
        assertEquals(2, target(lines, 5_700))
    }

    @Test fun earlyScrollPicksTheNextLineAheadOfTime() {
        val lines = listOf(lead(0, 1_000), lead(1_000, 2_000))
        val policy = ScrollPolicyController()
        policy.decide(lines, 500, leadMs = 250)
        assertEquals(0, policy.decide(lines, 700, leadMs = 250).targetIndex)
        val early = policy.decide(lines, 800, leadMs = 250)
        assertEquals(1, early.targetIndex)
        assertEquals(ScrollMotion.SMOOTH, early.motion)
    }

    @Test fun earlyScrollDoesNotMakeASeekOutOfTheLead() {
        val lines = listOf(lead(0, 1_000), lead(1_000, 2_000))
        val policy = ScrollPolicyController()
        policy.decide(lines, 500)
        // Turning the lead on mid-song moves the scroll time by 800ms, under the 1s seek threshold.
        assertEquals(ScrollMotion.SMOOTH, policy.decide(lines, 516, leadMs = 800).motion)
    }
}
