package com.tx24.spicyplayer.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactHeaderMetricsTest {
    // A 411x800 dp page at 2.625x density, with the renderer's 7%-of-width lyric size.
    private val density = 2.625f
    private val width = 411f * density
    private val height = 800f * density
    private val metrics = CompactHeaderMetrics(width, height, density, lyricFontSizeSp = 411f * 0.07f)

    @Test
    fun `bar, artwork and gap follow the compact header ratios`() {
        assertEquals(width * 0.05f, metrics.barTopPx, 0.01f)
        assertEquals(height * 0.15f, metrics.barHeightPx, 0.01f)
        assertEquals(metrics.barHeightPx, metrics.artSizePx, 0.01f)
        assertEquals(metrics.barHeightPx * 0.13f, metrics.gapPx, 0.01f)
        assertEquals(metrics.barBottomPx, metrics.lyricsTopPx, 0.01f)
        assertEquals(metrics.barTopPx + metrics.barHeightPx, metrics.barBottomPx, 0.01f)
    }

    @Test
    fun `artwork lines up with the lyrics and text fills the rest of the lyric column`() {
        assertEquals(width * 0.05f, metrics.contentStartPx, 0.01f)
        assertEquals(metrics.contentStartPx + metrics.artSizePx + metrics.gapPx, metrics.textStartPx, 0.01f)
        assertEquals(width * 0.95f - metrics.textStartPx, metrics.textWidthPx, 0.01f)
    }

    @Test
    fun `text keeps its size relative to compact lyrics`() {
        // Compact desktop lyrics at 411dp are clamp(48, 28.77, 64) = 48px next to a 40px title.
        val lyricSp = 411f * 0.07f
        assertEquals(lyricSp / 48f, metrics.scale, 0.0001f)
        assertEquals(40f / 48f, metrics.titleSizeSp / lyricSp, 0.0001f)
        assertEquals(24f / 48f, metrics.artistsSizeSp / lyricSp, 0.0001f)
        assertEquals(1.2f, metrics.titleLineHeightSp / metrics.titleSizeSp, 0.0001f)
        assertEquals(32f / 24f, metrics.artistsLineHeightSp / metrics.artistsSizeSp, 0.0001f)
    }

    @Test
    fun `wide pages hit the 4rem lyric cap`() {
        val wide = CompactHeaderMetrics(1200f, 800f, 1f, lyricFontSizeSp = 56f)
        assertEquals(56f / 64f, wide.scale, 0.0001f)
    }
}
