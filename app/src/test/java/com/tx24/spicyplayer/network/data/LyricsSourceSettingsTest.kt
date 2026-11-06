package com.tx24.spicyplayer.network.data

import com.google.gson.Gson
import com.tx24.spicyplayer.lyrics.NextLyricsBackend
import com.tx24.spicyplayer.network.data.LyricsOrigins.AMLL
import com.tx24.spicyplayer.network.data.LyricsOrigins.APPLE_MUSIC
import com.tx24.spicyplayer.network.data.LyricsOrigins.KUGOU
import com.tx24.spicyplayer.network.data.LyricsOrigins.KUWO
import com.tx24.spicyplayer.network.data.LyricsOrigins.NETEASE
import com.tx24.spicyplayer.network.data.LyricsOrigins.QQ_MUSIC
import com.tx24.spicyplayer.network.data.LyricsOrigins.GENIUS
import com.tx24.spicyplayer.network.data.LyricsOrigins.LRCLIB
import com.tx24.spicyplayer.network.data.LyricsOrigins.LRC_RED_AI
import com.tx24.spicyplayer.network.data.LyricsOrigins.SPICY_COMMUNITY
import com.tx24.spicyplayer.network.data.LyricsOrigins.SPOTIFY
import com.tx24.spicyplayer.network.data.LyricsOrigins.UNISON
import com.tx24.spicyplayer.network.data.LyricsOrigins.YOUTUBE
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSourceSettingsTest {
    private val providers = NextLyricsBackend.createProviders(OkHttpClient(), Gson(), clientKey = "")
        .map(RemoteLyricsProvider::descriptor)

    @Test
    fun `old order, switches and blends move over to sources`() {
        val policy = LyricsSourceSettings.migrate(
            order = listOf("lrclib", "spicy_lyrics", "amll_ttml_db", "apple_music", "unison", "netease", "spotify"),
            disabled = setOf("rmm_revival", "kugou", "qq_music", "kuwo", "genius", "youtube_transcript"),
            blends = setOf("blend_netease"),
            providers = providers,
        )

        // Each source at the earliest place its lyrics ranked at; lrc.red AI syncs where lrc.red stood.
        assertEquals(
            listOf(LRCLIB, SPICY_COMMUNITY, AMLL, APPLE_MUSIC, LRC_RED_AI, UNISON, KUGOU, QQ_MUSIC, NETEASE, SPOTIFY, KUWO, GENIUS, YOUTUBE),
            policy.sourceOrder,
        )
        assertEquals(emptySet<String>(), policy.disabledSourceIds)
        val ranking = SourceRanking(providers, policy)
        // A provider's switch is its old one in every source it serves.
        assertTrue(ranking.pairOn(APPLE_MUSIC, "spicy_lyrics") && ranking.pairOn(SPOTIFY, "spicy_lyrics"))
        assertFalse(ranking.pairOn(APPLE_MUSIC, "rmm_revival") || ranking.pairOn(SPICY_COMMUNITY, "rmm_revival"))
        assertTrue(ranking.pairOn(NETEASE, "netease") && ranking.pairOn(KUGOU, "lrcmux") && ranking.pairOn(GENIUS, "lrcmux"))
        assertFalse(ranking.pairOn(KUGOU, "kugou") || ranking.pairOn(GENIUS, "genius"))
        assertTrue(ranking.pairOn(NETEASE, "blend_netease"))
        assertFalse(ranking.pairOn(KUGOU, "blend_kugou"))
        assertEquals(listOf("blend_netease"), ranking.liveBlends.map { it.id })
    }

    @Test
    fun `a switched-off Apple Music or Spotify slot puts that source last, still on`() {
        val policy = LyricsSourceSettings.migrate(null, setOf("apple_music", "spotify"), emptySet(), providers)

        assertEquals(listOf(APPLE_MUSIC, SPOTIFY), policy.sourceOrder.takeLast(2))
        val ranking = SourceRanking(providers, policy)
        assertTrue(LyricsSourceSettings.isOn(ranking, APPLE_MUSIC))
        assertTrue(LyricsSourceSettings.isOn(ranking, SPOTIFY))
    }

    @Test
    fun `untouched old defaults keep the old ranking`() {
        val policy = LyricsSourceSettings.migrate(null, emptySet(), emptySet(), providers)

        assertEquals(
            listOf(SPICY_COMMUNITY, AMLL, APPLE_MUSIC, LRC_RED_AI, UNISON, KUGOU, QQ_MUSIC, NETEASE, LRCLIB, SPOTIFY, KUWO, GENIUS, YOUTUBE),
            policy.sourceOrder,
        )
        // Opt-in providers stay off; the old defaults stay on.
        val ranking = SourceRanking(providers, policy)
        assertFalse(ranking.asked("kugou"))
        assertTrue(ranking.asked("lrc_red"))
    }

    @Test
    fun `custom sources keep their place`() {
        val custom = LyricsSourceDescriptor("custom_1", "Mine", 1_000, setOf(LyricsCapability.LINE_SYNC), upstreamFamily = "custom")
        val policy = LyricsSourceSettings.migrate(listOf("custom_1", "spicy_lyrics"), emptySet(), emptySet(), providers + custom)

        assertEquals(listOf("custom_1", SPICY_COMMUNITY), policy.sourceOrder.take(2))
        assertTrue(SourceRanking(providers + custom, policy).asked("custom_1"))
    }

    @Test
    fun `a source added later slots in by the default order`() {
        val stored = RemoteLyricsPolicy(sourceOrder = listOf(LRCLIB, SPICY_COMMUNITY, AMLL, APPLE_MUSIC, UNISON, SPOTIFY, KUGOU, NETEASE, QQ_MUSIC, KUWO, GENIUS, YOUTUBE))

        val order = LyricsSourceSettings.normalized(stored, providers).sourceOrder

        // lrc.red AI syncs follows LRCLIB, as it does by default.
        assertEquals(listOf(LRCLIB, LRC_RED_AI, SPICY_COMMUNITY), order.take(3))
    }

    @Test
    fun `switching on a source with no provider on switches its first provider on`() {
        val fresh = LyricsSourceSettings.normalized(RemoteLyricsPolicy(), providers)
        val noLrcMux = LyricsSourceSettings.setPairEnabled(fresh, providers, SourcePair(KUGOU, "lrcmux"), false)
        assertFalse(LyricsSourceSettings.isOn(SourceRanking(providers, noLrcMux), KUGOU))
        // The first provider, not a blend.
        assertEquals("kugou", LyricsSourceSettings.providerToSwitchOn(SourceRanking(providers, noLrcMux), KUGOU))

        val on = LyricsSourceSettings.setSourceEnabled(noLrcMux, providers, KUGOU, true)

        assertTrue(SourceRanking(providers, on).asked("kugou"))
        assertNull(LyricsSourceSettings.providerToSwitchOn(SourceRanking(providers, on), KUGOU))
    }

    @Test
    fun `switching a provider on switches its source back on`() {
        val off = LyricsSourceSettings.setSourceEnabled(LyricsSourceSettings.normalized(RemoteLyricsPolicy(), providers), providers, GENIUS, false)
        assertFalse(SourceRanking(providers, off).live(SourceRanking(providers, off).origins.single { it.id == GENIUS }, "lrcmux"))

        val on = LyricsSourceSettings.setPairEnabled(off, providers, SourcePair(GENIUS, "genius"), true)

        assertTrue(LyricsSourceSettings.isOn(SourceRanking(providers, on), GENIUS))
        assertTrue(SourceRanking(providers, on).asked("genius"))
    }

    @Test
    fun `a dragged order and sync types are kept`() {
        val fresh = LyricsSourceSettings.normalized(RemoteLyricsPolicy(), providers)
        val dragged = listOf(AMLL) + (fresh.sourceOrder - AMLL)
        val moved = LyricsSourceSettings.reorder(fresh, providers, dragged)
        assertEquals(dragged, moved.sourceOrder)

        val wordOnly = LyricsSourceSettings.setSyncTypes(moved, providers, APPLE_MUSIC, setOf(RemoteLyricsQuality.WORD_SYNCED))
        assertEquals(setOf(RemoteLyricsQuality.LINE_SYNCED, RemoteLyricsQuality.PLAIN), wordOnly.syncTypesOff[APPLE_MUSIC])
    }
}
