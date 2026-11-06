package com.tx24.spicyplayer.network.motion

import com.tx24.spicyplayer.network.motion.MotionCoverFinder.AlbumHit
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.albumIds
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.ambientVideo
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.key
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.loose
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.parseAlbums
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.plain
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.rankAlbums
import com.tx24.spicyplayer.network.motion.MotionCoverFinder.Companion.tallVideo
import com.tx24.spicyplayer.ui.nowplaying.leadArtist
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MotionCoverFinderTest {
    @Test
    fun `the tall stream comes from the page data, past its preview frame`() {
        val page = """"videoArtwork":{"dictionary":{"motionDetailSquare":{"previewFrame":{"height":3840},"video":"https://a/square.m3u8"}}},""" +
            """"tallVideoArtwork":{"dictionary":{"motionDetailTall":{"previewFrame":{"height":2732},"video":"https://a/tall.m3u8"}}}"""
        assertEquals("https://a/tall.m3u8", tallVideo(page))
        assertNull(tallVideo(""""videoArtwork":{"dictionary":{"motionDetailSquare":{"video":"https://a/square.m3u8"}}}"""))
    }

    @Test
    fun `edition suffixes come off`() {
        assertEquals("Midnights", plain("Midnights (The Til Dawn Edition)"))
        assertEquals("Blinding Lights", plain("Blinding Lights - Single"))
        assertEquals("Folklore", plain("Folklore (Deluxe Version)"))
        assertEquals("Hybrid Theory", plain("Hybrid Theory"))
    }

    @Test
    fun `loose names allow editions either way round`() {
        assertTrue(loose("Midnights", "Midnights (3am Edition)"))
        assertTrue(loose("Midnights (3am Edition)", "Midnights"))
        assertFalse(loose("NO NAME", "Something Else"))
        // Short names must match exactly, not by containment.
        assertFalse(loose("NF", "Jack White NF"))
    }

    @Test
    fun `another artist's album of the same name is never picked`() {
        val hits = listOf(
            AlbumHit("https://music.apple.com/us/album/no-name/1", "No Name", "Jack White"),
            AlbumHit("https://music.apple.com/us/album/no-name/2", "NO NAME", "NF"),
        )
        assertEquals(listOf("https://music.apple.com/us/album/no-name/2"), rankAlbums(hits, "NF", "NO NAME"))
    }

    @Test
    fun `exact name ranks above an edition`() {
        val hits = listOf(
            AlbumHit("u/deluxe", "Midnights (3am Edition)", "Taylor Swift"),
            AlbumHit("u/plain", "Midnights", "Taylor Swift"),
            AlbumHit("u/plain", "Midnights", "Taylor Swift"),
        )
        assertEquals(listOf("u/plain", "u/deluxe"), rankAlbums(hits, "Taylor Swift", "Midnights"))
    }

    @Test
    fun `search results parse and skip non-albums`() {
        val json = """{"results":[
            {"wrapperType":"collection","collectionName":"Midnights","artistName":"Taylor Swift",
             "collectionViewUrl":"https://music.apple.com/us/album/midnights/123?uo=4"},
            {"wrapperType":"artist","artistName":"Taylor Swift"}]}"""
        assertEquals(
            listOf(AlbumHit("https://music.apple.com/us/album/midnights/123", "Midnights", "Taylor Swift")),
            parseAlbums(json),
        )
        assertEquals(emptyList<AlbumHit>(), parseAlbums("not json"))
    }

    // Trimmed from a real song search. The album search for "Dua Lipa Radical Optimism" returns
    // only the clean edition (1745741774), whose page has no animation; the explicit one has it.
    private val radicalOptimismTracks = """{"results":[
        {"wrapperType":"track","trackName":"End Of An Era","collectionName":"Radical Optimism",
         "artistName":"Dua Lipa","collectionExplicitness":"explicit",
         "collectionViewUrl":"https://music.apple.com/us/album/end-of-an-era/1734980417?i=1734980418&uo=4"},
        {"wrapperType":"track","trackName":"End Of An Era (Extended)","collectionName":"Radical Optimism (Extended)",
         "artistName":"Dua Lipa","collectionExplicitness":"explicit",
         "collectionViewUrl":"https://music.apple.com/us/album/end-of-an-era-extended/1753352305?i=1&uo=4"},
        {"wrapperType":"track","trackName":"End Of An Era","collectionName":"Radical Optimism",
         "artistName":"Dua Lipa","collectionExplicitness":"cleaned",
         "collectionViewUrl":"https://music.apple.com/us/album/end-of-an-era/1745741774?i=2&uo=4"},
        {"wrapperType":"track","trackName":"End of an Era","collectionName":"Dinner Party",
         "artistName":"Niall Horan","collectionExplicitness":"explicit",
         "collectionViewUrl":"https://music.apple.com/us/album/end-of-an-era/1884418567?i=3&uo=4"}]}"""

    private val radicalOptimismAlbums = """{"results":[
        {"wrapperType":"collection","collectionName":"Radical Optimism","artistName":"Dua Lipa",
         "collectionExplicitness":"cleaned",
         "collectionViewUrl":"https://music.apple.com/us/album/radical-optimism/1745741774?uo=4"}]}"""

    @Test
    fun `the explicit edition a track sits on is tried before the clean one`() {
        val hits = parseAlbums(radicalOptimismTracks) + parseAlbums(radicalOptimismAlbums)
        assertEquals(
            listOf(
                "https://music.apple.com/us/album/end-of-an-era/1734980417",
                "https://music.apple.com/us/album/end-of-an-era/1745741774",
                "https://music.apple.com/us/album/end-of-an-era-extended/1753352305",
            ),
            rankAlbums(hits, "Dua Lipa", "Radical Optimism"),
        )
    }

    @Test
    fun `the explicit edition wins a tie however the results are ordered`() {
        // Zara Larsson's "Midnight Sun": the album search only knows the clean 1841486100.
        val hits = listOf(
            AlbumHit("https://music.apple.com/us/album/midnight-sun/1841486100", "Midnight Sun", "Zara Larsson"),
            AlbumHit("https://music.apple.com/us/album/midnight-sun/1817499239", "Midnight Sun", "Zara Larsson", explicit = true),
            AlbumHit("https://music.apple.com/us/album/midnight-sun-single/1852285641", "Midnight Sun - Single", "Zara Larsson & Muni Long"),
        )
        assertEquals(
            listOf(
                "https://music.apple.com/us/album/midnight-sun/1817499239",
                "https://music.apple.com/us/album/midnight-sun/1841486100",
                "https://music.apple.com/us/album/midnight-sun-single/1852285641",
            ),
            rankAlbums(hits, "Zara Larsson", "Midnight Sun"),
        )
    }

    @Test
    fun `misses remembered by an older search are retried, its hits kept`() {
        val memo = File.createTempFile("motion", ".json").apply { deleteOnExit() }
        memo.writeText(
            """{"dualipa|radicaloptimism":{"url":"","at":1},
               "taylorswift|midnights":{"url":"https://x/P1_default.m3u8","at":1}}""",
        )
        var requests = 0
        val offline = OkHttpClient.Builder()
            .addInterceptor { requests++; throw IOException("offline") }
            .build()
        val finder = MotionCoverFinder(offline, memo, now = { 2 })
        runBlocking {
            assertEquals("https://x/P1_default.m3u8", finder.find("Taylor Swift", "Midnights", "Anti-Hero"))
            assertEquals(0, requests)
            assertNull(finder.find("Dua Lipa", "Radical Optimism", "End Of An Era"))
            assertTrue(requests > 0)
        }
    }

    @Test
    fun `album ids and the ambient video come out of the pages`() {
        val search = """<a href="https://music.apple.com/us/album/from-zero/1766137049">
            <a href="https://music.apple.com/us/album/from-zero/1766137049"><a href="/us/album/x/5">"""
        assertEquals(listOf("1766137049"), albumIds(search))
        val page = """<amp-ambient-video class="x" src="https://mvod.itunes.apple.com/a/P1_default.m3u8" loop>"""
        assertEquals("https://mvod.itunes.apple.com/a/P1_default.m3u8", ambientVideo(page))
        assertNull(ambientVideo("<img src=\"cover.jpg\">"))
    }

    @Test
    fun `a single is keyed by its title`() {
        assertEquals(key("NF", "", "HOPE"), key("nf", "", "Hope"))
        assertNull(key("", "Album", "Title"))
    }

    @Test
    fun `lead artist drops the rest of the credit`() {
        assertEquals("Drake", leadArtist("Drake, 21 Savage"))
        assertEquals("Calvin Harris", leadArtist("Calvin Harris feat. Rihanna"))
        assertEquals("Simon & Garfunkel", leadArtist("Simon & Garfunkel"))
    }

    @Test
    fun `live lookup finds a known animated cover`() {
        assumeTrue(System.getenv("RUN_MOTION_COVER_TEST") == "1")
        val finder = MotionCoverFinder(OkHttpClient(), memoFile = null)
        // Midnights is animated on the edition the album search returns; the other two only on
        // the explicit edition, which the album search leaves out.
        listOf(
            Triple("Taylor Swift", "Midnights", "Anti-Hero"),
            Triple("Dua Lipa", "Radical Optimism", "End Of An Era"),
            Triple("Zara Larsson", "Midnight Sun", "Midnight Sun"),
        ).forEach { (artist, album, title) ->
            val url = runBlocking { finder.find(artist, album, title) }
            assertNotNull("$artist – $album", url)
            assertTrue(url!!.endsWith(".m3u8"))
        }
    }
}
