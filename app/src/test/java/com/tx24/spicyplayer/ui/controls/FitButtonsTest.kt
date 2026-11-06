package com.tx24.spicyplayer.ui.controls

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitButtonsTest {
    @Test
    fun `all fit with the widest gap`() {
        val fit = fitButtons(395.dp, 5, 48.dp, 48.dp, 8.dp, 16.dp)
        assertEquals(5, fit.slots)
        assertEquals(16.dp, fit.gap)
    }

    @Test
    fun `Spotify's ten buttons on a phone keep what fits and a More button`() {
        val fit = fitButtons(395.dp, 10, 48.dp, 48.dp, 8.dp, 16.dp)
        assertEquals(7, fit.slots)
        assertTrue(fit.size * fit.slots + fit.gap * (fit.slots - 1) <= 395.dp)
    }

    @Test
    fun `cover buttons shrink before anything goes in More`() {
        val fit = fitButtons(300.dp, 8, 32.dp, 40.dp, 6.dp, 12.dp)
        assertEquals(8, fit.slots)
        assertTrue(fit.size in 32.dp..40.dp)
    }

    @Test
    fun `cover buttons never go under the minimum`() {
        val fit = fitButtons(200.dp, 10, 32.dp, 40.dp, 6.dp, 12.dp)
        assertEquals(5, fit.slots)
        assertTrue(fit.size >= 32.dp)
        assertTrue(fit.size * fit.slots + fit.gap * (fit.slots - 1) <= 200.dp)
    }
}
