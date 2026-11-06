package com.tx24.spicyplayer.ui.settings

import android.content.SharedPreferences
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppSettingsCorruptValuesTest {
    @Test fun wrongTypesReadAsDefaultsInsteadOfCrashing() {
        val settings = AppSettings(Prefs(mapOf("lowPerformance" to "yes", "backgroundBlur" to 999, "scrollLeadMs" to "fast", "stageArts" to 3)))

        assertFalse(settings.lowPerformance)
        assertEquals(MAX_BACKGROUND_BLUR, settings.backgroundBlur)
        assertEquals(250, settings.scrollLeadMs)
        assertEquals(StageArt.DEFAULT, settings.stageArt)
        assertEquals(emptySet<StageArt>(), AppSettings(Prefs(mapOf("stageArts" to "Hologram"))).stageArt)
        assertEquals(setOf(StageArt.Canvas), AppSettings(Prefs(mapOf("stageArts" to "Canvas"))).stageArt)
    }

    @Test fun aCustomBackgroundColorReadsOnlyAsAWholeHexColor() {
        assertEquals(0xFF1A1A2E.toInt(), parseHexColor("#1a1a2e"))
        assertEquals(0xFF000000.toInt(), parseHexColor(" 000000 "))
        assertEquals(null, parseHexColor("#abc"))
        assertEquals(null, parseHexColor("#12345G"))
        assertEquals(null, parseHexColor("#FF000000"))
        // Anything unreadable stored shows black.
        assertEquals(0xFF000000.toInt(), AppSettings(Prefs(mapOf("backgroundColor" to "teal"))).backgroundColorArgb)
        assertEquals(0xFF0B1426.toInt(), AppSettings(Prefs(mapOf("backgroundColor" to "#0B1426"))).backgroundColorArgb)
    }

    @Test fun theOldCoverStageSwitchBecomesThePickedArt() {
        assertEquals(StageArt.COVER_STAGE_ON, AppSettings(Prefs(mapOf("coverStage" to true))).stageArt)
        assertEquals(StageArt.DEFAULT, AppSettings(Prefs(mapOf("coverStage" to "yes"))).stageArt)
        assertEquals(StageArt.DEFAULT, AppSettings(Prefs(emptyMap())).stageArt)
        assertEquals(emptySet<StageArt>(), AppSettings(Prefs(mapOf("coverStage" to false))).stageArt)
        // Once picked, the switch no longer counts.
        assertEquals(setOf(StageArt.CoverArt), AppSettings(Prefs(mapOf("coverStage" to false, "stageArts" to "CoverArt"))).stageArt)
    }

    @Test fun lyricsSizesStartFromTheOneSizePickedBefore() {
        val upgraded = AppSettings(Prefs(mapOf("lyricsSize" to "Larger")))
        assertEquals(130 to 130, upgraded.lyricsSizePortrait to upgraded.lyricsSizeLandscape)
        assertEquals(1.3f, upgraded.lyricsScale(landscape = true), 0.0001f)
        assertEquals(100, AppSettings(Prefs(emptyMap())).lyricsSizePortrait)
        // Kept inside the slider's range, whatever was stored.
        assertEquals(250, AppSettings(Prefs(mapOf("lyricsSizeLandscape" to 900))).lyricsSizeLandscape)
    }

    @Test fun knowsWhatTypeEachSettingIsStoredAs() {
        val types = AppSettings(Prefs(emptyMap())).storedTypes
        assertEquals(Boolean::class, types["lowPerformance"])
        assertEquals(Int::class, types["backgroundBlur"])
        assertEquals(Int::class, types["lyricsSizePortrait"])
        assertEquals(String::class, types["stageArts"])
        assertEquals(Boolean::class, types["coverStage"])
    }

    /** Read-only, and casting like Android's: a value of another type throws. */
    private class Prefs(private val values: Map<String, Any>) : SharedPreferences {
        override fun getAll() = values
        override fun getString(key: String, defValue: String?) = values[key]?.let { it as String } ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) =
            @Suppress("UNCHECKED_CAST") (values[key] as Set<String>?)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int) = values[key]?.let { it as Int } ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key]?.let { it as Long } ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key]?.let { it as Float } ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key]?.let { it as Boolean } ?: defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = error("read-only")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
