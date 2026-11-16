package com.tx24.spicyplayer.network.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongGenreTest {
    @Test
    fun `store genres fall into their family`() {
        assertEquals(SongGenre.HipHop, SongGenre.of("Hip-Hop/Rap"))
        assertEquals(SongGenre.Pop, SongGenre.of("Pop"))
        assertEquals(SongGenre.Rock, SongGenre.of("Hard Rock"))
        assertEquals(SongGenre.Metal, SongGenre.of("Heavy Metal"))
        assertEquals(SongGenre.Alternative, SongGenre.of("Alternative"))
        assertEquals(SongGenre.Alternative, SongGenre.of("Indie Pop"))
        assertEquals(SongGenre.RnB, SongGenre.of("R&B/Soul"))
        assertEquals(SongGenre.Electronic, SongGenre.of("Dance"))
        assertEquals(SongGenre.Electronic, SongGenre.of("Dubstep"))
        assertEquals(SongGenre.Country, SongGenre.of("Singer/Songwriter"))
        assertEquals(SongGenre.Latin, SongGenre.of("Urbano latino"))
        assertEquals(SongGenre.Latin, SongGenre.of("Pop Latino"))
        assertEquals(SongGenre.AsianPop, SongGenre.of("K-Pop"))
        assertEquals(SongGenre.AsianPop, SongGenre.of("Anime"))
        assertEquals(SongGenre.Jazz, SongGenre.of("Blues"))
        assertEquals(SongGenre.Classical, SongGenre.of("Soundtrack"))
        assertEquals(SongGenre.Reggae, SongGenre.of("Afrobeats"))
        assertEquals(SongGenre.Gospel, SongGenre.of("Christian & Gospel"))
    }

    @Test
    fun `an empty or unknown genre is none`() {
        assertNull(SongGenre.of(null))
        assertNull(SongGenre.of(" "))
        assertNull(SongGenre.of("Spoken Word"))
    }

    @Test
    fun `the same song's genre wins, else the artist's most common`() {
        val json = """{"results":[
            {"trackName":"Other","artistName":"XXXTENTACION","primaryGenreName":"Alternative"},
            {"trackName":"SAD!","artistName":"XXXTENTACION","primaryGenreName":"Hip-Hop/Rap"},
            {"trackName":"SAD!","artistName":"Someone Else","primaryGenreName":"Pop"}
        ]}"""
        val songs = ItunesGenre.parse(json)
        assertEquals("Hip-Hop/Rap", ItunesGenre.pick(songs, "SAD!", "XXXTENTACION"))
        assertEquals("Alternative", ItunesGenre.pick(songs, "Missing", "XXXTENTACION"))
        assertNull(ItunesGenre.pick(songs, "SAD!", "Nobody"))
        assertEquals(emptyList<ItunesGenre.Song>(), ItunesGenre.parse("not json"))
    }
}
