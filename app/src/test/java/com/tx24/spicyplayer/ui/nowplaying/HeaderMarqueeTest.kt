package com.tx24.spicyplayer.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderMarqueeTest {
    @Test
    fun `each pass holds for its first and last tenth`() {
        assertEquals(0f, HeaderMarquee.phase(0L), 0f)
        assertEquals(0f, HeaderMarquee.phase(2_500L), 0f)
        assertEquals(0.5f, HeaderMarquee.phase(12_500L), 0.0001f)
        assertEquals(1f, HeaderMarquee.phase(22_500L), 0f)
        assertEquals(1f, HeaderMarquee.phase(24_999L), 0f)
    }

    @Test
    fun `alternate passes run backwards`() {
        assertEquals(1f, HeaderMarquee.phase(25_000L), 0f)
        assertEquals(1f, HeaderMarquee.phase(27_500L), 0f)
        assertEquals(0.5f, HeaderMarquee.phase(37_500L), 0.0001f)
        assertEquals(0f, HeaderMarquee.phase(47_500L), 0f)
        assertEquals(0f, HeaderMarquee.phase(52_500L), 0f)
    }

    @Test
    fun `text that fits never moves and overflow travels exactly its excess`() {
        assertEquals(0f, HeaderMarquee.offsetPx(1f, containerWidthPx = 300f, spanWidthPx = 200f), 0f)
        assertEquals(-100f, HeaderMarquee.offsetPx(1f, containerWidthPx = 300f, spanWidthPx = 400f), 0f)
        assertEquals(-50f, HeaderMarquee.offsetPx(0.5f, containerWidthPx = 300f, spanWidthPx = 400f), 0f)
    }

    @Test
    fun `mask fades 2_5 and 3_75 percent of the text block`() {
        val (start, end) = HeaderMarquee.maskStops(containerWidthPx = 400f, elementWidthPx = 400f)
        assertEquals(0.025f, start, 0.0001f)
        assertEquals(1f - 0.0375f, end, 0.0001f)
        val (shortStart, _) = HeaderMarquee.maskStops(containerWidthPx = 400f, elementWidthPx = 200f)
        assertEquals(0.05f, shortStart, 0.0001f)
    }
}
