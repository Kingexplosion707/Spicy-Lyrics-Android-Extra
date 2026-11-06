package com.tx24.spicyplayer.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverColorsTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun greyCoverLandsNearSpotifysShades() {
        // Spotify gives a mid-grey cover 153, 83 and 53 grey.
        val colors = CoverColors.of(IntArray(100) { rgb(200, 200, 200) })
        assertEquals(153.0, ((colors.minContrast shr 16) and 0xFF).toDouble(), 6.0)
        assertEquals(83.0, ((colors.highContrast shr 16) and 0xFF).toDouble(), 6.0)
        assertEquals(53.0, ((colors.higherContrast shr 16) and 0xFF).toDouble(), 6.0)
    }

    @Test fun darkCoverKeepsItsColour() {
        val dark = rgb(20, 20, 40)
        val colors = CoverColors.of(IntArray(100) { dark })
        assertEquals(dark, colors.minContrast)
        assertEquals(dark, colors.higherContrast)
    }

    @Test fun shadesReachTheirContrast() {
        val colors = CoverColors.of(IntArray(100) { rgb(240, 120, 60) })
        assertTrue(CoverColors.contrastWithWhite(colors.minContrast) >= CoverColors.MIN_CONTRAST)
        assertTrue(CoverColors.contrastWithWhite(colors.highContrast) >= CoverColors.HIGH_CONTRAST)
        assertTrue(CoverColors.contrastWithWhite(colors.higherContrast) >= CoverColors.HIGHER_CONTRAST)
        // Still orange: red above green above blue.
        val r = (colors.highContrast shr 16) and 0xFF
        val g = (colors.highContrast shr 8) and 0xFF
        val b = colors.highContrast and 0xFF
        assertTrue(r > g && g > b)
    }

    @Test fun colourfulPoolBeatsABlackBorder() {
        val pixels = IntArray(100) { if (it < 55) rgb(0, 0, 0) else rgb(30, 90, 200) }
        val main = CoverColors.mainColor(pixels)
        assertEquals(rgb(30, 90, 200), main)
    }
}
