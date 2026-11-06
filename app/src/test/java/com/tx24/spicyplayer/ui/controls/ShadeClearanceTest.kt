package com.tx24.spicyplayer.ui.controls

import androidx.compose.animation.core.LinearEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeClearanceTest {
    @Test
    fun `a linear ramp blurs from where it crosses the intensity`() {
        // Span 200: 10% blur is reached 20px below the shade's top, so 80 of the 100 above the controls.
        assertEquals(80f, shadeBlurAbovePx(100f, 100f, LinearEasing, 0.1f), 0.01f)
    }

    @Test
    fun `taller controls leave less of the reach blurred`() {
        val short = shadeBlurAbovePx(180f, 100f, LinearEasing, 0.1f)
        val tall = shadeBlurAbovePx(180f, 300f, LinearEasing, 0.1f)
        assertTrue(tall < short)
    }

    @Test
    fun `never more than the reach, never less than nothing`() {
        assertEquals(180f, shadeBlurAbovePx(180f, 100f, LinearEasing, 0f), 0.01f)
        assertEquals(0f, shadeBlurAbovePx(180f, 100f, LinearEasing, 1f), 0.01f)
        assertEquals(0f, shadeBlurAbovePx(0f, 100f, LinearEasing, 0.1f), 0.01f)
    }

    @Test
    fun `phone controls keep a notice about 50dp above them`() {
        // The column of controls is about 278dp tall; at density 1, px are dp.
        val clear = controlsClearancePx(278, wide = false, density = 1f)
        assertTrue("was $clear", clear - 278 in 45f..56f)
    }

    @Test
    fun `the landscape bar keeps a notice about 20dp above it`() {
        val clear = controlsClearancePx(110, wide = true, density = 1f)
        assertTrue("was $clear", clear - 110 in 16f..25f)
    }

    @Test
    fun `scales with density`() {
        val dp = controlsClearancePx(278, wide = false, density = 1f) - 278
        val px = controlsClearancePx(278 * 3, wide = false, density = 3f) - 278 * 3
        assertEquals(dp * 3, px, 0.5f)
    }
}
