package android.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapTest {
    @Test
    fun `pixels go in and come out as Android's ARGB`() {
        val colors = IntArray(40 * 23) { if (it % 2 == 0) 0xFF102030.toInt() else 0x80FF8000.toInt() }
        val bitmap = Bitmap.createBitmap(colors, 40, 23, Bitmap.Config.ARGB_8888)
        assertEquals(0xFF102030.toInt(), bitmap.getPixel(0, 0))
        val out = IntArray(40 * 23)
        bitmap.getPixels(out, 0, 40, 0, 0, 40, 23)
        assertEquals(0xFF102030.toInt(), out[2])
        // As the Canvas background does, many times over.
        repeat(200) { Bitmap.createBitmap(colors, 40, 23, Bitmap.Config.ARGB_8888).image() }
    }

    @Test
    fun `too few pixels leave the rest clear instead of failing`() {
        val bitmap = Bitmap.createBitmap(IntArray(10) { -1 }, 8, 8, Bitmap.Config.ARGB_8888)
        assertEquals(-1, bitmap.getPixel(1, 0))
        assertEquals(0, bitmap.getPixel(7, 7))
    }
}
