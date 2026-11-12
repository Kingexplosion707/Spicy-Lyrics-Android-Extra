package com.tx24.spicyplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedLinesTest {
    @Test fun keepsNewestFirstAndSurvivesSaving() {
        val saved = SavedLines()
        saved.add(listOf("first line", " second line "), "Song", "Artist", 1_000L)
        saved.add(listOf("another"), "Other", "Someone", 2_000L)
        val read = SavedLines.fromJson(saved.toJson()).all
        assertEquals(listOf("another", "first line"), read.map { it.lines.first() })
        assertEquals(listOf("first line", "second line"), read[1].lines)
        assertEquals("first line\nsecond line\n\nSong — Artist", read[1].asText())
    }

    @Test fun theSameLinesAgainMoveToTheTop() {
        val saved = SavedLines()
        saved.add(listOf("a"), "Song", "Artist", 1L)
        saved.add(listOf("b"), "Song", "Artist", 2L)
        saved.add(listOf("a"), "Song", "Artist", 3L)
        assertEquals(listOf("a", "b"), saved.all.map { it.lines.first() })
        assertEquals(2, saved.size)
    }

    @Test fun removesByIdAndIgnoresEmptyOrBrokenInput() {
        val saved = SavedLines()
        assertNull(saved.add(listOf("  ", ""), "Song", "Artist", 1L))
        val quote = saved.add(listOf("a"), "Song", "Artist", 1L)!!
        assertTrue(saved.remove(quote.id))
        assertEquals(0, saved.size)
        assertEquals(0, SavedLines.fromJson("not json").size)
        assertEquals(0, SavedLines.fromJson(null).size)
    }

    @Test fun keepsNoMoreThanItsLimit() {
        val saved = SavedLines()
        repeat(SavedLines.MAX + 20) { saved.add(listOf("line $it"), "Song", "Artist", it.toLong()) }
        assertEquals(SavedLines.MAX, saved.size)
        assertEquals("line ${SavedLines.MAX + 19}", saved.all.first().lines.first())
    }
}
