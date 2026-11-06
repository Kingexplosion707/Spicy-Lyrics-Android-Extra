package com.tx24.spicyplayer.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientImageTest {
    private fun solid(rgb: Int, w: Int = 9, h: Int = 16) = IntArray(w * h) { (0xFF shl 24) or rgb }
    private fun channels(argb: Int) = Triple(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)

    @Test
    fun `grey stays grey, dimmed to 0,65`() {
        val out = AmbientImage(80, 36).draw(solid(0x808080), 9, 16)
        for (pixel in out) assertEquals(Triple(83, 83, 83), channels(pixel))
    }

    @Test
    fun `colour is saturated 2,5 times`() {
        val (r, g, b) = channels(AmbientImage(80, 36).draw(solid(0x996666), 9, 16)[0])
        // A muted red comes out a much stronger one, still dimmed.
        assertTrue("$r $g $b", r - g > (0x99 - 0x66) * 0.65f * 2f)
        assertEquals(g, b)
    }

    @Test
    fun `later frames blend in at 30 percent`() {
        val image = AmbientImage(80, 36)
        image.draw(solid(0x000000), 9, 16)
        val (r, _, _) = channels(image.draw(solid(0xFFFFFF), 9, 16)[0])
        assertEquals((255 * 0.65f * 0.3f).toInt().toDouble(), r.toDouble(), 1.0)
    }

    @Test
    fun `a portrait frame covers the wide image from its middle`() {
        // Top half white, bottom half black: the middle rows of the image are where they meet,
        // so the image's top is lighter than its bottom and nothing is left unpainted.
        val w = 9
        val h = 16
        val frame = IntArray(w * h) { i -> (0xFF shl 24) or if (i / w < h / 2) 0xFFFFFF else 0 }
        val out = AmbientImage(80, 36).draw(frame, w, h)
        val top = channels(out[40]).first
        val bottom = channels(out[35 * 80 + 40]).first
        assertTrue("$top $bottom", top > bottom)
    }
}
