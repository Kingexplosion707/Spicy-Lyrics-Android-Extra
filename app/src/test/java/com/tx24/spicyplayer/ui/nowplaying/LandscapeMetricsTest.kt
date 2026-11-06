package com.tx24.spicyplayer.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeMetricsTest {
    // A phone on its side, in dp (density 1 keeps px = dp).
    private val phone = LandscapeMetrics(pageWidthPx = 914f, pageHeightPx = 411f, density = 1f)

    @Test
    fun `cover is the now bar width, from the shorter side on a phone`() {
        // min(30cqw, 52cqh) * 1.1: the height wins here.
        assertEquals(411f * 0.52f * 1.1f, phone.coverSizePx, 0.01f)
        assertEquals(411f / 2f - phone.coverSizePx * 0.6f, phone.coverTopPx, 0.01f)
    }

    @Test
    fun `panel and song text fit on the page`() {
        val panel = phone.panel
        val textBottom = panel.artTopPx + panel.artSizePx * 1.05f + panel.textHeightPx
        assertTrue("text runs off the bottom: $textBottom", textBottom < phone.pageHeightPx)
        assertTrue(panel.artTopPx > 0f)
    }

    @Test
    fun `sides mirror each other`() {
        val left = phone.coverLeftPx(0f)
        val right = phone.coverLeftPx(1f)
        assertEquals(phone.pageWidthPx - phone.coverSizePx - left, right, 0.01f)
        val lyricsLeft = phone.lyricsBox(withPanel = true, panelOnRight = false)
        val lyricsRight = phone.lyricsBox(withPanel = true, panelOnRight = true)
        assertEquals(lyricsLeft.widthPx, lyricsRight.widthPx, 0.01f)
        assertEquals(phone.pageWidthPx - lyricsLeft.leftPx - lyricsLeft.widthPx, lyricsRight.leftPx, 0.01f)
    }

    @Test
    fun `lyrics stay beside the cover and on the page`() {
        val box = phone.lyricsBox(withPanel = true, panelOnRight = false)
        val textStart = box.leftPx + box.widthPx * CompactHeaderMetrics.LYRICS_SIDE_INSET
        assertTrue("lyrics text overlaps the cover", textStart > phone.coverLeftPx(0f) + phone.coverSizePx)
        assertTrue(box.leftPx + box.widthPx <= phone.pageWidthPx + 0.01f)
    }

    @Test
    fun `big cover is centred at the panel's size`() {
        assertEquals(phone.coverSizePx, phone.centred.artSizePx, 0.01f)
        assertEquals((phone.pageWidthPx - phone.coverSizePx) / 2f, phone.centred.artLeftPx, 0.01f)
    }

    @Test
    fun `without the panel the lyrics span the page`() {
        val box = phone.lyricsBox(withPanel = false, panelOnRight = false)
        assertEquals(phone.pageWidthPx * 0.18f, box.leftPx + box.widthPx * CompactHeaderMetrics.LYRICS_SIDE_INSET, 0.01f)
    }
}
