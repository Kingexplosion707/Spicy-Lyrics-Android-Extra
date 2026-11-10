package com.tx24.spicyplayer.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningLogTest {
    private fun log() = ListeningLog().apply {
        add(100, "a", "Song A", "Artist One, Guest", 200_000, 1)
        add(101, "a", "Song A", "Artist One, Guest", 400_000, 2)
        add(101, "b", "Song B", "artist one", 100_000, 1)
        add(101, "c", "Song C", "Other", 500_000, 1)
    }

    @Test fun ranksSongsAndArtistsByTime() {
        val all = log().summary(0, 1_000)
        assertEquals(1_200_000, all.ms)
        assertEquals(5, all.plays)
        assertEquals(3, all.songs)
        assertEquals(2, all.artists)
        assertEquals(listOf("Song A", "Song C", "Song B"), all.topSongs.map { it.name })
        // The guest doesn't split the artist, nor does the spelling's case.
        assertEquals(listOf("Artist One" to 700_000L, "Other" to 500_000L), all.topArtists.map { it.name to it.ms })
    }

    @Test fun aPeriodCountsOnlyItsDays() {
        val day = log().summary(100, 100)
        assertEquals(200_000, day.ms)
        assertEquals(1, day.songs)
    }

    @Test fun survivesBeingSaved() {
        val read = ListeningLog.fromJson(log().toJson())
        assertEquals(log().summary(0, 1_000), read.summary(0, 1_000))
        assertTrue(ListeningLog.fromJson("not json").isEmpty)
    }

    @Test fun pruneDropsOldDays() {
        val log = log().apply { prune(101) }
        assertEquals(1_000_000, log.summary(0, 1_000).ms)
    }

    @Test fun readsDurationsAndArtists() {
        assertEquals("under a minute", ListeningLog.duration(59_000))
        assertEquals("12 min", ListeningLog.duration(12 * 60_000L))
        assertEquals("3 h 5 min", ListeningLog.duration(185 * 60_000L))
        assertEquals("Daft Punk", ListeningLog.leadArtist("Daft Punk feat. Pharrell Williams"))
        assertEquals("Unknown artist", ListeningLog.leadArtist(" "))
    }
}
