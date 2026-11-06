package com.tx24.spicyplayer.network.data

import com.google.gson.Gson
import com.tx24.spicyplayer.lyrics.NextLyricsBackend
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDefaultsTest {
    private val descriptors = NextLyricsBackend.createProviders(OkHttpClient(), Gson(), clientKey = "")
        .map(RemoteLyricsProvider::descriptor)

    @Test
    fun `a fresh install asks Spicy Lyrics and the lyrics APIs open to any app`() {
        val ranking = SourceRanking(descriptors, LyricsSourceSettings.normalized(RemoteLyricsPolicy(), descriptors))
        val asked = descriptors.filter { ranking.asked(it.id) }.map { it.id }.toSet()
        assertEquals(DEFAULT_ON, asked)
        assertEquals(emptyList<String>(), ranking.liveBlends)
    }

    @Test
    fun `Musixmatch is gone`() {
        assertTrue(descriptors.none { it.id == "musixmatch" })
    }

    @Test
    fun `a fresh install has the sources in the default order`() {
        val order = LyricsSourceSettings.normalized(RemoteLyricsPolicy(), descriptors).sourceOrder
        assertEquals(
            listOf(
                LyricsOrigins.SPICY_COMMUNITY, LyricsOrigins.AMLL, LyricsOrigins.APPLE_MUSIC, LyricsOrigins.UNISON,
                LyricsOrigins.SPOTIFY, LyricsOrigins.KUGOU, LyricsOrigins.NETEASE, LyricsOrigins.QQ_MUSIC, LyricsOrigins.KUWO,
                LyricsOrigins.LRCLIB, LyricsOrigins.LRC_RED_AI,
                LyricsOrigins.GENIUS, LyricsOrigins.YOUTUBE,
            ),
            order,
        )
    }

    @Test
    fun `each source is served by its providers in a fixed order`() {
        val providers = LyricsOrigins.forProviders(descriptors).associate { it.id to it.providerIds.filter { id -> LyricsBlends.byId(id) == null } }
        assertEquals(listOf("spicy_lyrics", "rmm_revival"), providers[LyricsOrigins.SPICY_COMMUNITY])
        assertEquals(listOf("amll_ttml_db"), providers[LyricsOrigins.AMLL])
        assertEquals(listOf("spicy_lyrics", "rmm_revival", "lrc_red"), providers[LyricsOrigins.APPLE_MUSIC])
        assertEquals(listOf("lrc_red"), providers[LyricsOrigins.LRC_RED_AI])
        assertEquals(listOf("unison", "spicy_unison"), providers[LyricsOrigins.UNISON])
        assertEquals(listOf("spicy_lyrics", "rmm_revival"), providers[LyricsOrigins.SPOTIFY])
        assertEquals(listOf("kugou", "lrcmux"), providers[LyricsOrigins.KUGOU])
        assertEquals(listOf("netease"), providers[LyricsOrigins.NETEASE])
        assertEquals(listOf("qq_music"), providers[LyricsOrigins.QQ_MUSIC])
        assertEquals(listOf("kuwo"), providers[LyricsOrigins.KUWO])
        assertEquals(listOf("lrclib", "lrcmux"), providers[LyricsOrigins.LRCLIB])
        assertEquals(listOf("genius", "lrcmux"), providers[LyricsOrigins.GENIUS])
        assertEquals(listOf("youtube_transcript", "lrcmux"), providers[LyricsOrigins.YOUTUBE])
        // Every provider serves at least one source; none gets a source of its own.
        assertEquals(descriptors.map { it.id }.toSet(), providers.values.flatten().toSet())
        assertEquals(LyricsOrigins.BUILT_IN.size, providers.size)
    }

    @Test
    fun `an answer finds its source by where the relay says it came from`() {
        val origins = LyricsOrigins.forProviders(descriptors)
        fun origin(provider: String, name: String?) = LyricsOrigins.originOf(origins, provider, name)?.id
        assertEquals(LyricsOrigins.SPICY_COMMUNITY, origin("spicy_lyrics", "Spicy Lyrics"))
        assertEquals(LyricsOrigins.SPICY_COMMUNITY, origin("rmm_revival", "Spicy Lyrics Community"))
        assertEquals(LyricsOrigins.APPLE_MUSIC, origin("spicy_lyrics", "Apple Music"))
        assertEquals(LyricsOrigins.SPOTIFY, origin("rmm_revival", "Spotify"))
        assertEquals(LyricsOrigins.APPLE_MUSIC, origin("lrc_red", "Apple Music"))
        assertEquals(LyricsOrigins.LRC_RED_AI, origin("lrc_red", LyricsOrigins.LRC_RED_AI_NAME))
        assertEquals(LyricsOrigins.KUGOU, origin("lrcmux", "KuGou"))
        assertEquals(LyricsOrigins.LRCLIB, origin("lrcmux", "LRCLIB"))
        assertEquals(LyricsOrigins.GENIUS, origin("lrcmux", "Genius"))
        assertEquals(LyricsOrigins.YOUTUBE, origin("lrcmux", "YouTube Music"))
        assertNull(origin("lrcmux", "Somewhere new"))
        // A provider serving one source needs no name.
        assertEquals(LyricsOrigins.NETEASE, origin("netease", null))
        assertEquals(LyricsOrigins.QQ_MUSIC, origin("blend_qq", "Spicy + QQ Music"))
    }

    @Test
    fun `every other source says what it sends before it's switched on`() {
        for (source in descriptors.filter { it.id !in DEFAULT_ON }) {
            // A named entry, not the generic fallback.
            assertNotNull(source.id, SourceDisclosures.forId(source.id))
            assertEquals(source.id, SourceDisclosures.forSource(source)?.id)
        }
    }

    @Test
    fun `default providers and blends need no disclosure`() {
        for (source in descriptors.filter { it.id in DEFAULT_ON } + LyricsBlends.ALL.map { it.descriptor }) {
            assertNull(source.id, SourceDisclosures.forSource(source))
        }
    }

    @Test
    fun `the user's own sources need no disclosure`() {
        val custom = LyricsSourceDescriptor("custom_1", "Mine", 1_000, emptySet(), upstreamFamily = "custom")
        assertNull(SourceDisclosures.forSource(custom))
    }

    @Test
    fun `spotify canvas says it sends the track ID to its lookup service`() {
        val text = SourceDisclosures.forId(SourceDisclosures.SPOTIFY_CANVAS_ID)!!.description
        assertTrue(text, text.contains("Spotify track ID") && text.contains("spicylyrics.org"))
    }

    @Test
    fun `a disclosure names where the song goes and what's sent`() {
        val text = SourceDisclosures.forId("kugou")!!.description
        assertTrue(text, text.contains("Kugou's servers in China"))
        assertTrue(text, text.contains("title, artist and length"))
        assertFalse(text, text.contains("  "))
    }

    private companion object {
        /** Spicy Lyrics, and the lyrics APIs that say any app may use them. */
        val DEFAULT_ON = setOf(RemoteLyricsSource.SPICY_ID, "lrclib", "amll_ttml_db", "unison", "spicy_unison", "lrcmux", "lrc_red")
    }
}
