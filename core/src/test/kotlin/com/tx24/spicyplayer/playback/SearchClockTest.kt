package com.tx24.spicyplayer.playback

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchClockTest {
    private fun line(start: Long, end: Long, untimed: Boolean = false) =
        TimedLine(start, end, listOf(TimedWord("la", start, end, false)), untimed = untimed)

    private fun lyrics(type: LyricsType, vararg lines: TimedLine) =
        LyricsState.Ready(lines.toList(), source = null, maker = null, uploader = null, songwriters = emptyList(), lyricsType = type)

    @Test fun lengthRunsPastTheLastLine() {
        val ready = lyrics(LyricsType.Syllable, line(1_000L, 4_000L), line(5_000L, 9_000L))
        assertEquals(9_000L + SearchClock.TAIL_MS, SearchClock.lengthOf(ready))
    }

    @Test fun untimedLinesDoNotCount() {
        val ready = lyrics(LyricsType.Line, line(1_000L, 4_000L), line(0L, 99_000L, untimed = true))
        assertEquals(4_000L + SearchClock.TAIL_MS, SearchClock.lengthOf(ready))
    }

    @Test fun unsyncedLyricsGiveNoLength() {
        assertEquals(0L, SearchClock.lengthOf(lyrics(LyricsType.Static, line(0L, 0L))))
        assertEquals(0L, SearchClock.lengthOf(lyrics(LyricsType.Line)))
    }

    @Test fun runsWhilePlayingAndStopsAtItsEnd() {
        val clock = SearchClock(10_000L).play(0L)
        assertEquals(3_000L, clock.positionAt(3_000L))
        assertEquals(10_000L, clock.positionAt(60_000L))
        assertEquals(7_000L, clock.remainingMs(3_000L))
    }

    @Test fun pauseHoldsThePosition() {
        val clock = SearchClock(10_000L).play(0L).pause(2_500L)
        assertFalse(clock.playing)
        assertEquals(2_500L, clock.positionAt(9_000L))
        assertNull(clock.remainingMs(9_000L))
    }

    @Test fun playAtTheEndStartsAgain() {
        val clock = SearchClock(10_000L).play(0L).ended(10_000L).play(12_000L)
        assertTrue(clock.playing)
        assertEquals(0L, clock.positionAt(12_000L))
    }

    @Test fun seekStaysInsideTheSong() {
        val clock = SearchClock(10_000L).play(0L)
        assertEquals(10_000L, clock.seek(50_000L, 1_000L).positionAt(1_000L))
        assertEquals(0L, clock.seek(-5L, 1_000L).positionAt(1_000L))
        assertEquals(4_500L, clock.seek(4_000L, 1_000L).positionAt(1_500L))
    }

    @Test fun aSongWithoutALengthDoesNotPlay() {
        val clock = SearchClock(0L).toggle(0L)
        assertFalse(clock.playing)
        assertEquals(0L, clock.positionAt(5_000L))
    }
}
