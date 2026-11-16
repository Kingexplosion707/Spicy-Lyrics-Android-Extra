package com.tx24.spicyplayer.lyrics.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionThemeTest {
    @Test fun `genres pick their theme`() {
        assertEquals(MotionTheme.Street, MotionTheme.forGenres(listOf("hip hop", "trap", "emo rap")))
        assertEquals(MotionTheme.Metal, MotionTheme.forGenres(listOf("Metalcore", "post-hardcore")))
        assertEquals(MotionTheme.Rave, MotionTheme.forGenres(listOf("Dance", "House", "EDM")))
        assertEquals(MotionTheme.Soul, MotionTheme.forGenres(listOf("contemporary r&b", "soul")))
        assertEquals(MotionTheme.Rock, MotionTheme.forGenres(listOf("indie rock", "garage rock", "rock")))
        assertEquals(MotionTheme.Latin, MotionTheme.forGenres(listOf("reggaeton", "latin pop")))
        assertEquals(MotionTheme.Jazz, MotionTheme.forGenres(listOf("jazz")))
        assertEquals(MotionTheme.Country, MotionTheme.forGenres(listOf("Country")))
    }

    @Test fun `the most used tag weighs most`() {
        assertEquals(MotionTheme.Pop, MotionTheme.forGenres(listOf("pop", "dance pop", "rock")))
        assertEquals(MotionTheme.Synthwave, MotionTheme.forGenres(listOf("synthwave", "pop")))
    }

    @Test fun `words match whole, not inside others`() {
        // "metal" is not in "metallic" alone; "rap" is not in "trapeze artist" as a word.
        assertNull(MotionTheme.forGenres(listOf("trapeze")))
        assertNull(MotionTheme.forGenres(emptyList()))
        assertNull(MotionTheme.forGenres(listOf("seen live", "favourites")))
    }

    @Test fun `ids round trip`() {
        MotionTheme.entries.forEach { assertEquals(it, MotionTheme.of(it.id)) }
        assertNull(MotionTheme.of(MotionTheme.AUTO))
    }
}
