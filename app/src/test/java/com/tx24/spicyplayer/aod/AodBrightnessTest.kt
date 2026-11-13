package com.tx24.spicyplayer.aod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AodBrightnessTest {
    @Test
    fun `low levels dim what is drawn and keep the screen at its lowest`() {
        assertEquals(0.004f, AodBrightness.windowBrightness(0f), 1e-6f)
        assertEquals(0.004f, AodBrightness.windowBrightness(30f), 1e-6f)
        assertTrue(AodBrightness.contentAlpha(0f) < 0.5f)
        assertEquals(1f, AodBrightness.contentAlpha(40f), 1e-6f)
    }

    @Test
    fun `high levels raise the screen`() {
        assertTrue(AodBrightness.windowBrightness(70f) > AodBrightness.windowBrightness(50f))
        assertEquals(0.6f, AodBrightness.windowBrightness(100f), 1e-4f)
    }

    @Test
    fun `brighter light gives a higher level, and the bias shifts it`() {
        val dark = AodBrightness.levelForLux(0f)
        val room = AodBrightness.levelForLux(100f)
        val sun = AodBrightness.levelForLux(30_000f)
        assertTrue(dark < room && room < sun)
        assertEquals(100f, sun, 1e-4f)
        assertEquals(room + 10f, AodBrightness.levelForLux(100f, 10), 1e-4f)
        assertEquals(0f, AodBrightness.levelForLux(0f, -40), 1e-4f)
    }
}
