package com.tx24.spicyplayer.lyrics

import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsExportTest {
    private fun word(text: String, start: Long, end: Long, attached: Boolean = false) = TimedWord(text, start, end, attached)

    private val lines = listOf(
        TimedLine(1_000, 3_000, listOf(word("Hel", 1_000, 1_400), word("lo", 1_400, 1_900, attached = true), word("there", 2_000, 3_000))),
        TimedLine(3_100, 4_000, listOf(word("ooh", 3_100, 4_000)), role = LineRole.BACKGROUND),
        TimedLine(4_000, 9_000, emptyList(), role = LineRole.INTERLUDE),
        TimedLine(61_230, 63_000, listOf(word("Again", 61_230, 63_000))),
    )

    @Test fun plainTextKeepsWordsWhole() {
        assertEquals("Hello there\n(ooh)\n\nAgain", LyricsExport.plainText(lines))
    }

    @Test fun wordTimedLinesCarryEachWordsTime() {
        val lrc = LyricsExport.lrc(lines, LyricsType.Syllable, "Song", "Artist", "", 125_000)
        assertEquals(
            "[ti:Song]\n[ar:Artist]\n[length:2:05]\n" +
                "[00:01.00]<00:01.00>Hel<00:01.40>lo <00:02.00>there<00:03.00>\n" +
                "[00:03.10](ooh)\n" +
                "[01:01.23]Again\n",
            lrc,
        )
    }

    @Test fun lineTimedLyricsCarryOnlyLineTimes() {
        val lrc = LyricsExport.lrc(lines, LyricsType.Line, "", "", "", 0)
        assertEquals("[00:01.00]Hello there\n[00:03.10](ooh)\n[01:01.23]Again\n", lrc)
    }

    @Test fun fileNameIsSafe() {
        assertEquals("AC_DC - What_.lrc", LyricsExport.fileName("What?", "AC/DC"))
        assertEquals("lyrics.lrc", LyricsExport.fileName("", ""))
    }
}
