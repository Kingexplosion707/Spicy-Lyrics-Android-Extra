package com.tx24.spicyplayer.lyrics

import com.google.gson.Gson
import com.tx24.spicyplayer.network.data.LyricsOrigins
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.RemoteLyricsPolicy
import com.tx24.spicyplayer.network.data.RemoteLyricsProvider
import com.tx24.spicyplayer.network.data.SourcePair
import com.tx24.spicyplayer.network.data.SourceRanking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmUpFilterTest {
    private val providers = NextLyricsBackend.createProviders(OkHttpClient(), Gson(), clientKey = "")
    private val descriptors = providers.map(RemoteLyricsProvider::descriptor)

    private fun warmed(policy: RemoteLyricsPolicy): Set<String> {
        val ranking = SourceRanking(descriptors, LyricsSourceSettings.normalized(policy, descriptors))
        return NextLyricsBackend.warmable(providers, ranking).map { it.descriptor.id }.toSet()
    }

    @Test
    fun `only the providers that are asked are warmed`() {
        val ranking = SourceRanking(descriptors, LyricsSourceSettings.normalized(RemoteLyricsPolicy(), descriptors))
        val asked = descriptors.filter { ranking.asked(it.id) }.map { it.id }.toSet()
        assertEquals(asked, warmed(RemoteLyricsPolicy()))
        assertTrue(SPICY in asked)
        assertTrue(descriptors.size > asked.size)
    }

    @Test
    fun `Spicy Lyrics switched off everywhere isn't warmed`() {
        val off = LyricsOrigins.forProviders(descriptors)
            .filter { SPICY in it.providerIds }
            .associate { SourcePair(it.id, SPICY).key to false }
        val warmed = warmed(RemoteLyricsPolicy(pairs = off))
        assertFalse(SPICY in warmed)
        assertTrue(warmed.isNotEmpty())
    }

    @Test
    fun `Spicy Lyrics left on in one source is still warmed`() {
        val off = LyricsOrigins.forProviders(descriptors)
            .filter { SPICY in it.providerIds && it.id != LyricsOrigins.SPOTIFY }
            .associate { SourcePair(it.id, SPICY).key to false }
        assertTrue(SPICY in warmed(RemoteLyricsPolicy(pairs = off)))
    }

    private companion object {
        const val SPICY = "spicy_lyrics"
    }
}
