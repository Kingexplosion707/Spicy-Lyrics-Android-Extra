package com.tx24.spicyplayer.ui.share

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareLyricsTextTest {
    private fun word(text: String, attached: Boolean = false) = TimedWord(text, 0L, 0L, attached)

    @Test
    fun `words join with spaces, syllables of one word without`() {
        val line = TimedLine(0L, 1_000L, listOf(word("Hel"), word("lo", attached = true), word("there"), word("friend")))
        assertEquals("Hello there friend", lineText(line))
    }

    @Test
    fun `stray whitespace collapses`() {
        val line = TimedLine(0L, 1_000L, listOf(word("  so "), word(" far  "), word("away")))
        assertEquals("so far away", lineText(line))
    }
}
