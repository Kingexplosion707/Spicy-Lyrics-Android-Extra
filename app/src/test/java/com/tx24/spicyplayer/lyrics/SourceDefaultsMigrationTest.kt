package com.tx24.spicyplayer.lyrics

import android.content.SharedPreferences
import com.tx24.spicyplayer.network.data.LyricsOrigins
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.RemoteLyricsPolicy
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.SourceDisclosures
import com.tx24.spicyplayer.network.data.SourcePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDefaultsMigrationTest {
    private val version = NextLyricsBackend.SOURCE_DEFAULTS_VERSION
    private val switchedOff = NextLyricsBackend.SWITCHED_OFF
    private val nowOptIn = NextLyricsBackend.NOW_OPT_IN

    @Test
    fun `an older install keeps its order and choices but the now opt-in sources go off`() {
        val prefs = MemoryPrefs(mutableMapOf(
            "defaults" to 1,
            "order" to "lrclib,spicy_lyrics,musixmatch,kugou,qq_music",
            "disabled" to setOf("qq_music", "unison"),
            "humanRomanizations" to true,
            "blends" to setOf("blend_a"),
        ))

        NextLyricsBackend.migrateSourceDefaults(prefs)

        assertEquals("lrclib,spicy_lyrics,musixmatch,kugou,qq_music", prefs.values["order"])
        assertEquals(setOf("qq_music", "unison") + nowOptIn, prefs.values["disabled"])
        assertNull(prefs.values["humanRomanizations"])
        assertEquals(setOf("blend_a"), prefs.values["blends"])
        assertEquals(version, prefs.values["defaults"])
        // QQ Music was already off: nothing to tell about it.
        assertEquals(
            setOf("kugou", "netease", "kuwo", "genius", "musixmatch", SourceDisclosures.GENIUS_ROMANIZATION_ID),
            prefs.values[switchedOff],
        )
    }

    @Test
    fun `an install that never touched its sources had the old defaults on`() {
        val prefs = MemoryPrefs(mutableMapOf("defaults" to 1))

        NextLyricsBackend.migrateSourceDefaults(prefs)

        assertEquals(nowOptIn, prefs.values["disabled"])
        assertEquals(nowOptIn + "musixmatch" + SourceDisclosures.GENIUS_ROMANIZATION_ID, prefs.values[switchedOff])
    }

    @Test
    fun `romanizations switched off by hand aren't reported`() {
        val prefs = MemoryPrefs(mutableMapOf("defaults" to 1, "disabled" to nowOptIn + "musixmatch", "humanRomanizations" to false))

        NextLyricsBackend.migrateSourceDefaults(prefs)

        assertEquals(emptySet<String>(), prefs.values[switchedOff])
    }

    @Test
    fun `a fresh install has nothing switched off or to tell`() {
        val prefs = MemoryPrefs(mutableMapOf())

        NextLyricsBackend.migrateSourceDefaults(prefs)

        assertEquals(version, prefs.values["defaults"])
        assertNull(prefs.values["disabled"])
        assertNull(prefs.values[switchedOff])
    }

    @Test
    fun `runs once`() {
        val prefs = MemoryPrefs(mutableMapOf("defaults" to 1))
        NextLyricsBackend.migrateSourceDefaults(prefs)
        // The user switches Kugou back on and dismisses the notice.
        prefs.edit().putStringSet("disabled", nowOptIn - "kugou").remove(switchedOff).apply()

        NextLyricsBackend.migrateSourceDefaults(prefs)

        assertEquals(nowOptIn - "kugou", prefs.values["disabled"])
        assertNull(prefs.values[switchedOff])
    }

    @Test
    fun `a restored older backup is brought up to date too`() {
        val prefs = MemoryPrefs(mutableMapOf("defaults" to version))
        // What restoring a backup from before the change writes.
        prefs.values.clear()
        prefs.values += mapOf("defaults" to 1, "order" to "spicy_lyrics,netease", "disabled" to emptySet<String>())

        NextLyricsBackend.migrateSourceDefaults(prefs)

        assertEquals("spicy_lyrics,netease", prefs.values["order"])
        assertTrue("netease" in (prefs.values["disabled"] as Set<*>))
    }

    private val providers = NextLyricsBackend.createProviders(okhttp3.OkHttpClient(), com.google.gson.Gson(), clientKey = "")
        .map { it.descriptor }

    @Test
    fun `old source preferences move to sources once, under new keys`() {
        val prefs = MemoryPrefs(mutableMapOf(
            "defaults" to version,
            "order" to "lrclib,spicy_lyrics",
            "disabled" to setOf("spotify", "kugou", "qq_music", "kuwo", "netease", "genius", "youtube_transcript", "rmm_revival"),
            "blends" to setOf("blend_qq"),
        ))

        val policy = NextLyricsBackend.readPolicy(prefs, providers)

        assertEquals(LyricsOrigins.LRCLIB, policy.sourceOrder.first())
        assertEquals(LyricsOrigins.SPOTIFY, policy.sourceOrder.last())
        assertEquals(policy.sourceOrder.joinToString(","), prefs.values[NextLyricsBackend.SOURCE_ORDER])
        assertTrue(SourcePair(LyricsOrigins.QQ_MUSIC, "blend_qq").key in (prefs.values[NextLyricsBackend.PAIRS_ON] as Set<*>))
        assertTrue(SourcePair(LyricsOrigins.KUGOU, "kugou").key in (prefs.values[NextLyricsBackend.PAIRS_OFF] as Set<*>))
        // The old keys stay, but are no longer read: a change made since sticks.
        assertEquals("lrclib,spicy_lyrics", prefs.values["order"])
        val moved = LyricsSourceSettings.reorder(policy, providers, policy.sourceOrder.drop(1) + LyricsOrigins.LRCLIB)
        NextLyricsBackend.writePolicy(prefs, moved)
        assertEquals(moved, NextLyricsBackend.readPolicy(prefs, providers))
    }

    @Test
    fun `a restored older backup moves over too`() {
        val prefs = MemoryPrefs(mutableMapOf())
        NextLyricsBackend.writePolicy(prefs, NextLyricsBackend.readPolicy(prefs, providers))
        // What restoring a backup from before sources writes: the store cleared, the old keys back.
        prefs.values.clear()
        prefs.values += mapOf("defaults" to version, "order" to "unison,spicy_lyrics", "disabled" to setOf("amll_ttml_db"))

        val policy = NextLyricsBackend.readPolicy(prefs, providers)

        assertEquals(LyricsOrigins.UNISON, policy.sourceOrder.first())
        assertTrue(policy.sourceOrder.indexOf(LyricsOrigins.SPICY_COMMUNITY) < policy.sourceOrder.indexOf(LyricsOrigins.AMLL))
        assertEquals(false, policy.pairs[SourcePair(LyricsOrigins.AMLL, "amll_ttml_db").key])
    }

    @Test
    fun `a fresh install reads the defaults and stores nothing`() {
        val prefs = MemoryPrefs(mutableMapOf("defaults" to version))

        val policy = NextLyricsBackend.readPolicy(prefs, providers)

        assertEquals(LyricsSourceSettings.normalized(RemoteLyricsPolicy(), providers), policy)
        assertNull(prefs.values[NextLyricsBackend.SOURCE_ORDER])
    }

    @Test
    fun `stored sync types come back`() {
        val prefs = MemoryPrefs(mutableMapOf())
        val wordOnly = LyricsSourceSettings.setSyncTypes(
            NextLyricsBackend.readPolicy(prefs, providers), providers, LyricsOrigins.APPLE_MUSIC, setOf(RemoteLyricsQuality.WORD_SYNCED),
        )
        NextLyricsBackend.writePolicy(prefs, wordOnly)

        val read = NextLyricsBackend.readPolicy(prefs, providers)

        assertEquals(setOf(RemoteLyricsQuality.LINE_SYNCED, RemoteLyricsQuality.PLAIN), read.syncTypesOff[LyricsOrigins.APPLE_MUSIC])
    }

    @Test
    fun `the notice names what went off`() {
        val (title, text) = SourceDisclosures.switchedOffNotice(setOf("kugou", "genius", "musixmatch"))!!
        assertEquals("Some lyrics sources were switched off", title)
        assertTrue(text, text.startsWith("This update switched off Genius and Kugou. They're "))
        assertTrue(text, text.endsWith("Musixmatch is gone: the app could only reach it by posing as Musixmatch's own app."))
    }

    @Test
    fun `Musixmatch alone gets its own notice, and nothing gets none`() {
        assertEquals("Musixmatch is gone", SourceDisclosures.switchedOffNotice(setOf("musixmatch"))!!.first)
        assertNull(SourceDisclosures.switchedOffNotice(emptySet()))
    }

    /** Just enough of [SharedPreferences] for the migration. */
    private class MemoryPrefs(val values: MutableMap<String, Any>) : SharedPreferences {
        override fun getAll(): Map<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?) = values[key] as? Set<String> ?: defValues
        override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String) = key in values
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val puts = mutableMapOf<String, Any?>()
            private var clear = false
            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values }
            override fun putInt(key: String, value: Int) = apply { puts[key] = value }
            override fun putLong(key: String, value: Long) = apply { puts[key] = value }
            override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
            override fun remove(key: String) = apply { puts[key] = null }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) values.clear()
                puts.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
        }
    }
}
