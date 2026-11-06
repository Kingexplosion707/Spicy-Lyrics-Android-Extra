package com.tx24.spicyplayer.ui.background

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasBlurTest {
    @Test
    fun `the video blur just under 100 percent softens a phone as much as the glow`() {
        // A 1080px page: the glow's Gaussian is about 4.47 of its 80px, so 60px on the page, which
        // Android's blur reaches at a radius of about 104px.
        assertEquals(104f, glowBlurRadiusPx(1080), 1f)
        assertEquals(0f, glowBlurRadiusPx(0), 0f)
    }
}
