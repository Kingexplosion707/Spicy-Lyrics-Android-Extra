package com.tx24.spicyplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsBackupTest {
    private val stores = mapOf(
        "ui" to mapOf("romanize" to true, "lyricsSize" to "Large", "scrollLeadMs" to 250, "speed" to 1.5f, "checkedAt" to 1_700_000_000_000L),
        "lyrics_sources" to mapOf("order" to "spicy,apple", "disabled" to setOf("netease", "qq")),
        "song_delays" to mapOf("song|artist" to -120),
    )

    @Test
    fun `values come back as the types they went in as`() {
        val decoded = SettingsBackup.decode(SettingsBackup.encode(stores, "0.5.1"))
        assertEquals(stores, decoded)
        assertTrue(decoded.getValue("ui")["scrollLeadMs"] is Int)
        assertTrue(decoded.getValue("ui")["checkedAt"] is Long)
        assertTrue(decoded.getValue("ui")["speed"] is Float)
    }

    @Test
    fun `stores outside the list are left out both ways`() {
        val encoded = SettingsBackup.encode(stores + ("spicy_lyrics_key" to mapOf("key" to "sl_pk_x")), "0.5.1")
        assertFalse("sl_pk_x" in encoded)
        val sneaky = """{"format":"spicy-player-settings","version":1,"stores":{"updates":{"skipped":{"type":"string","value":"v9"}}}}"""
        assertEquals(emptyMap<String, Any>(), SettingsBackup.decode(sneaky))
    }

    @Test
    fun `entries with a wrong or unknown type are skipped`() {
        val json = """{"format":"spicy-player-settings","version":1,"stores":{"ui":{
            "a":{"type":"int","value":"nope"},
            "b":{"type":"mystery","value":1},
            "c":{"type":"boolean","value":true}}}}"""
        assertEquals(mapOf("ui" to mapOf("c" to true)), SettingsBackup.decode(json))
    }

    @Test
    fun `backups from before the rename still restore`() {
        val old = """{"format":"spicy-player-settings","version":1,"stores":{"ui":{"c":{"type":"boolean","value":true}}}}"""
        assertEquals(mapOf("ui" to mapOf("c" to true)), SettingsBackup.decode(old))
        val format = com.google.gson.JsonParser.parseString(SettingsBackup.encode(emptyMap(), "1.0.0")).asJsonObject.get("format").asString
        assertEquals("spicy-lyrics-mobile-settings", format)
    }

    @Test
    fun `other files and newer backups are refused`() {
        assertRefused("not json at all")
        assertRefused("""{"hello":"world"}""")
        assertRefused("""{"format":"spicy-player-settings","version":2,"stores":{}}""")
    }

    private fun assertRefused(text: String) {
        try {
            SettingsBackup.decode(text)
            fail("Expected $text to be refused")
        } catch (_: SettingsBackup.InvalidBackup) {
        }
    }

    @Test
    fun `translation preferences and song language overrides travel without DeepL credentials`() {
        val translation = mapOf("targetLanguage" to "it", "provider" to "DeepL", "automatic" to true, "excludedLanguages" to setOf("en", "ru"), "humanTranslations" to true)
        val languages = mapOf("same-song-key-as-delay" to "ru")
        val encoded = SettingsBackup.encode(mapOf(
            "translation" to translation, "song_languages" to languages,
            "deepl_key" to mapOf("key" to "private-test-key:fx"),
            "ui" to mapOf("translationMode" to "Replace", "romanizationMode" to "UnderLine"),
        ), "test")
        assertFalse(encoded.contains("private-test-key"))
        assertEquals(translation, SettingsBackup.decode(encoded)["translation"])
        assertEquals(languages, SettingsBackup.decode(encoded)["song_languages"])
        assertEquals("Replace", SettingsBackup.decode(encoded)["ui"]?.get("translationMode"))
        assertEquals("UnderLine", SettingsBackup.decode(encoded)["ui"]?.get("romanizationMode"))
    }
}
