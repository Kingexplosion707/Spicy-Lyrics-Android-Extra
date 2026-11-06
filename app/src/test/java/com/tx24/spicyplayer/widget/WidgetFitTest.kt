package com.tx24.spicyplayer.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetFitTest {
    private val settings = WidgetSettings()

    @Test
    fun `a tall widget keeps everything`() {
        val fit = WidgetFit.of(settings, heightDp = 300)
        assertEquals(settings, fit.settings)
        assertEquals(3, fit.currentLines)
    }

    @Test
    fun `an unknown height changes nothing`() {
        assertEquals(WidgetFit(settings, 3), WidgetFit.of(settings, heightDp = null))
    }

    @Test
    fun `a tiny widget drops chrome and lets the painter fit the lyrics`() {
        val fit = WidgetFit.of(settings, heightDp = 60)
        assertEquals(settings.around, fit.settings.around)
        assertEquals(WidgetSettings.SongInfo.HIDDEN, fit.settings.songInfo)
        assertFalse(fit.settings.progress)
        assertEquals(settings.romanization, fit.settings.romanization)
        assertEquals(1, fit.currentLines)
    }

    @Test
    fun `a normal short widget keeps timeline and surrounding lines`() {
        val fit = WidgetFit.of(settings.copy(around = 2), heightDp = 200)
        assertEquals(WidgetSettings.SongInfo.BOTTOM, fit.settings.songInfo)
        assertEquals(2, fit.settings.around)
        assertEquals(true, fit.settings.progress)
    }

    @Test
    fun `changing the number of surrounding lines never hides the timeline`() {
        for (height in listOf(160, 180, 200, 240, 300)) {
            for (around in 0..2) {
                assertEquals(true, WidgetFit.of(settings.copy(around = around), height).settings.progress)
            }
        }
    }

    @Test
    fun `a size bigger than the screen is read as pixels`() {
        assertEquals(328 to 322, WidgetFit.reportedDp(328, 322, 392, 850, 2.75f))
        assertEquals(328 to 322, WidgetFit.reportedDp(902, 886, 392, 850, 2.75f))
    }

    @Test
    fun `a screen too small to be one never turns a size into pixels`() {
        assertEquals(380 to 190, WidgetFit.reportedDp(380, 190, 0, 0, 2.625f))
        assertEquals(380 to 190, WidgetFit.reportedDp(380, 190, 120, 60, 2.625f))
    }

    @Test
    fun `a launcher working at the default density is taken back to the app's dp`() {
        // A Galaxy Tab S9 FE+ in landscape at a smaller display size: 1593x415 reported, ~1154x311 on screen.
        val landscape = WidgetFit.launcherDp(1593, 415, 252, 340)
        assertEquals(1181 to 308, landscape)
        assertEquals(landscape, WidgetFit.reportedDp(landscape.first, landscape.second, 1625, 1015, 1.575f))
        // In portrait the reported width passed the screen and was read as pixels; corrected, it no longer is.
        val portrait = WidgetFit.launcherDp(1100, 594, 252, 340)
        assertEquals(portrait, WidgetFit.reportedDp(portrait.first, portrait.second, 1015, 1625, 1.575f))
    }

    @Test
    fun `a launcher size at the default density or without one is left alone`() {
        assertEquals(380 to 190, WidgetFit.launcherDp(380, 190, 420, 420))
        assertEquals(380 to 190, WidgetFit.launcherDp(380, 190, 420, 0))
        assertEquals(null to 141, WidgetFit.launcherDp(null, 190, 312, 420))
    }

    @Test
    fun `a widget a little past the screen at a larger display size stays the size it is`() {
        // An S25 Edge at its largest display size: 401x213 reported, drawn for 106x56 when read as pixels.
        val (width, height) = WidgetFit.launcherDp(401, 213, 600, 450)
        assertEquals(401 to 213, width to height)
        assertEquals(384 to 213, WidgetFit.reportedDp(width, height, 384, 832, 3.75f))
    }

    @Test
    fun `an empty display size falls back to the app's screen`() {
        assertEquals(411 to 891, WidgetFit.screenDp(0 to 0, 411 to 891))
        assertEquals(411 to 891, WidgetFit.screenDp(411 to 891, 300 to 200))
    }
}
