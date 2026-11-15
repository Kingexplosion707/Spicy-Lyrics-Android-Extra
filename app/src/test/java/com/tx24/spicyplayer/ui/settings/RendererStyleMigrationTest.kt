package com.tx24.spicyplayer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererStyleMigrationTest {
    private fun migrated(vararg stored: Pair<String, Any>): Map<String, Any> {
        val values = stored.toMap().toMutableMap()
        val edit = RendererStyleMigration.migrate(values)
        edit.remove.forEach(values::remove)
        values.putAll(edit.set)
        return values
    }

    @Test fun theAppleMusicStyleAndSimpleBecomeBloom() {
        assertEquals("Bloom", migrated("lyricsStyle" to "AppleMusic")["rendererStyle"])
        assertEquals("Bloom", migrated("lyricsStyle" to "Spicy", "simpleLyricsMode" to true)["rendererStyle"])
        assertEquals("Bloom", migrated("simpleLyricsMode" to true, "refreshedSimpleLyrics" to true)["rendererStyle"])
    }

    @Test fun everyoneElseStaysOnSpring() {
        assertEquals("Spring", migrated("lyricsStyle" to "Spicy")["rendererStyle"])
        assertEquals("Spring", migrated("simpleLyricsMode" to false)["rendererStyle"])
        // The Refreshed look did nothing without Simple Lyrics Mode.
        assertEquals("Spring", migrated("refreshedSimpleLyrics" to true)["rendererStyle"])
    }

    @Test fun theOldKeysGo() {
        val after = migrated("lyricsStyle" to "AppleMusic", "simpleLyricsMode" to true, "refreshedSimpleLyrics" to true, "glow" to true)
        assertEquals(setOf("rendererStyle", "glow"), after.keys)
    }

    @Test fun fadeAwayPreviousLinesStartsOffForTheAppleMusicStyleItNeverAppliedTo() {
        assertEquals(false, migrated("lyricsStyle" to "AppleMusic", "minimalLyricsMode" to true)["minimalLyricsMode"])
        assertEquals(true, migrated("simpleLyricsMode" to true, "minimalLyricsMode" to true)["minimalLyricsMode"])
    }

    @Test fun aChosenStyleIsKeptAndNothingIsDoneTwice() {
        // Restored from a backup made after the change, with stray old keys: the choice stands.
        assertEquals("Spring", migrated("rendererStyle" to "Spring", "simpleLyricsMode" to true)["rendererStyle"])
        val once = migrated("lyricsStyle" to "AppleMusic")
        assertTrue(RendererStyleMigration.migrate(once).isEmpty)
        assertTrue(RendererStyleMigration.migrate(mapOf("glow" to true)).isEmpty)
    }
}
