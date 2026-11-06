package com.tx24.spicyplayer.analytics

import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageReportTest {
    @Test
    fun `counts become plain numbers, top player and source, and per-source counts`() {
        val data = dailyUsageData(
            mapOf(
                UsageCounter.SONGS to 5,
                UsageCounter.lyrics("word") to 3,
                UsageCounter.lyrics("none") to 2,
                UsageCounter.page("Scroll & Sync") to 1,
                UsageCounter.RESYNC to 0,
                UsageCounter.player("com.spotify.music") to 4,
                UsageCounter.player("com.google.android.apps.youtube.music") to 1,
                UsageCounter.source("Spicy Lyrics") to 2,
                UsageCounter.source("Musixmatch") to 1,
            ),
        )
        assertEquals(5, data["songs"])
        assertEquals(3, data["lyrics_word"])
        assertEquals(1, data["page_scroll_sync"])
        assertFalse("zero counts are left out", "resync" in data)
        assertEquals("com.spotify.music", data["top_player"])
        assertEquals(2, data["players"])
        assertEquals("Spicy Lyrics", data["top_source"])
        assertEquals(2, data["source_spicy_lyrics"])
        assertEquals(1, data["source_musixmatch"])
        assertTrue("raw tallies don't leak through", data.keys.none { ':' in it })
    }

    @Test
    fun `lyrics outcome names`() {
        assertEquals("word", lyricsOutcome(LyricsType.Syllable))
        assertEquals("line", lyricsOutcome(LyricsType.Line))
        assertEquals("plain", lyricsOutcome(LyricsType.Static))
        assertEquals("none", lyricsOutcome(null))
    }

    @Test
    fun `daily is due a day later`() {
        val now = 10 * DAILY_INTERVAL_MS
        assertTrue(dailyDue(0L, now))
        assertFalse(dailyDue(now - DAILY_INTERVAL_MS + 1, now))
        assertTrue(dailyDue(now - DAILY_INTERVAL_MS, now))
    }
}
