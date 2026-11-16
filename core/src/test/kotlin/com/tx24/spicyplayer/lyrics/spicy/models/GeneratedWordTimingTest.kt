package com.tx24.spicyplayer.lyrics.spicy.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedWordTimingTest {
    private fun lineTimed(start: Long, end: Long, vararg words: String) =
        Line(words.map { Word(it, start, end) }, start, end)

    @Test fun `line-timed words get one after another inside the line`() {
        val line = GeneratedWordTiming.fillIn(listOf(lineTimed(1_000, 4_000, "Never", "gonna", "give", "you", "up"), lineTimed(4_000, 7_000, "x")))[0]
        assertFalse(line.lineTimed)
        assertEquals(1_000L, line.words.first().startMs)
        line.words.zipWithNext().forEach { (a, b) ->
            assertTrue(a.endMs <= b.startMs + 1)
            assertTrue(a.startMs < b.startMs)
        }
        assertTrue(line.words.last().endMs <= 4_000L)
    }

    @Test fun `longer words take longer`() {
        val words = GeneratedWordTiming.fillIn(listOf(lineTimed(0, 3_000, "a", "beautiful")))[0].words
        assertTrue(words[1].endMs - words[1].startMs > words[0].endMs - words[0].startMs)
    }

    @Test fun `a long gap after a line doesn't stretch the words`() {
        val words = GeneratedWordTiming.fillIn(listOf(lineTimed(0, 30_000, "hey", "you")))[0].words
        assertTrue(words.last().endMs < 2_000L)
    }

    @Test fun `a short line squeezes its words into its time`() {
        val words = GeneratedWordTiming.fillIn(listOf(lineTimed(0, 600, "incredibly", "complicated", "phrasing"), lineTimed(600, 900, "x")))[0].words
        assertTrue(words.last().endMs <= 600L)
    }

    @Test fun `word-synced lines are left alone`() {
        val synced = Line(listOf(Word("a", 0, 100), Word("b", 200, 300)), 0, 300)
        val lines = listOf(synced)
        assertFalse(GeneratedWordTiming.applies(lines))
        assertSame(lines, GeneratedWordTiming.fillIn(lines))
    }

    @Test fun `untimed lines and breaks keep their timing`() {
        val untimed = lineTimed(0, 1_000, "a", "b").copy(untimed = true)
        val pause = lineTimed(1_000, 5_000, "...").copy(role = LineRole.INTERLUDE)
        val out = GeneratedWordTiming.fillIn(listOf(untimed, pause, lineTimed(5_000, 8_000, "c", "d")))
        assertSame(untimed, out[0])
        assertSame(pause, out[1])
        assertTrue(out[2].words[1].startMs > 5_000L)
    }
}
