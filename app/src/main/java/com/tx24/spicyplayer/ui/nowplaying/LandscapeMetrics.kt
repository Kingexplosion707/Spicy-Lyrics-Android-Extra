package com.tx24.spicyplayer.ui.nowplaying

import kotlin.math.min

/**
 * Geometry of the landscape lyrics page, resolved for one page size: the now-playing panel
 * (square cover with the song text centred under it) on one side, the lyrics on the other, like
 * the full-page (not compact) now bar. Written as CSS container units against the page
 * (`cqw`/`cqh` are percentages of its width and height).
 *
 * Everything keeps the desktop page's proportions rather than its pixels: there the cover is
 * 741px next to 56px lyrics, and a phone gets the same picture at its size (see [lyricFontSizeSp]).
 *
 * Positions are for the panel on the left; [coverLeftPx] and [lyricsBox] mirror them for the
 * right. Pure arithmetic in pixels so it can move to a shared module later.
 *
 * @param pageWidthPx width of the page, inside the cutout's insets.
 * @param pageHeightPx height of the page.
 * @param topPx where the page starts from the top of the screen.
 * @param timelineOutside the timeline sits under the cover, between it and the song text.
 */
data class LandscapeMetrics(
    val pageWidthPx: Float,
    val pageHeightPx: Float,
    val density: Float,
    val topPx: Float = 0f,
    val timelineOutside: Boolean = false,
) {
    /** `--NowBarWidth: calc(min(30cqw, 52cqh) * 1.1)`; the cover is as wide as the panel. */
    val coverSizePx = min(pageWidthPx * 0.30f, pageHeightPx * 0.52f) * 1.1f

    /** `--NowBarRightSpacing: calc(var(--NowBarWidth) / 4)`. */
    private val innerSpacingPx = coverSizePx / 4f

    /**
     * Page edge to panel: `--NowBarLeftSpacing: calc((50cqw - var(--NowBarWidth) -
     * var(--NowBarRightSpacing)) * .7)` on the normal (not cinema) page.
     */
    private val edgeSpacingPx = (pageWidthPx * 0.5f - coverSizePx - innerSpacingPx) * 0.7f

    /**
     * `.NowBar { padding-top: calc(50cqh - var(--NowBarWidth) * .625) }`, or `.6` with no timeline
     * under the cover.
     */
    val coverTopPx = topPx + pageHeightPx * 0.5f - coverSizePx * if (timelineOutside) 0.625f else 0.6f

    /**
     * The lyrics' size: 56px against the 741px cover, as on the desktop page, but never under the
     * lyrics' own floor (`clamp(1.85rem, ...)`), which a phone would otherwise sink well below.
     */
    val lyricFontSizeSp = maxOf(coverSizePx / density * (DESKTOP_LYRICS_PX / DESKTOP_COVER_PX), MIN_LYRICS_SP)

    /** How much the floor lifted the lyrics; the song text grows with them to keep its size next to them. */
    private val textBoost = lyricFontSizeSp / (coverSizePx / density * (DESKTOP_LYRICS_PX / DESKTOP_COVER_PX))

    /** `--default-font-size`, 3% of the cover (lifted with the lyrics): the timeline's times. */
    val baseFontSizeSp = coverSizePx / density * 0.03f * textBoost

    /** The cover's left edge at [side] (0 = panel on the left .. 1 = on the right). */
    fun coverLeftPx(side: Float): Float {
        val left = edgeSpacingPx
        val right = pageWidthPx - coverSizePx - edgeSpacingPx
        return left + (right - left) * side
    }

    /** The panel on the left, cover and text; see [coverLeftPx] for where it sits. */
    val panel = header(edgeSpacingPx)

    /** Without the lyrics (`.LyricsHidden`), the same panel moves to the page's centre. */
    val centred = header((pageWidthPx - coverSizePx) / 2f)

    /**
     * The song text under the cover, as measured on the desktop page: `--default-font-size` is
     * 3% of the cover, the name 2.25x that on a 1.12 line, the artists 1.275x on a 1.317 line;
     * all of it lifted with the lyrics where their floor holds them up ([textBoost]).
     */
    private fun header(left: Float): ExpandedHeader {
        val base = baseFontSizeSp
        val titleSize = base * 2.25f
        val artistsSize = base * 1.275f
        return ExpandedHeader(
            artLeftPx = left,
            artTopPx = coverTopPx,
            artSizePx = coverSizePx,
            textHeightPx = (titleSize * 1.12f + artistsSize * 1.317f) * density,
            titleSizeSp = titleSize,
            titleLineHeightSp = titleSize * 1.12f,
            artistsSizeSp = artistsSize,
            artistsLineHeightSp = artistsSize * 1.317f,
        )
    }

    /** The cover stage's soft edge toward the lyrics: 45% of the cover. */
    val stageFeatherPx = coverSizePx * 0.45f

    /** The panel's side of the page from its outer edge: the edge gap and the cover. The stage is solid this far. */
    val stageSolidPx = edgeSpacingPx + coverSizePx

    /** The stage, from the page's outer edge to where its feather has faded out over the lyrics. */
    val stageWidthPx = stageSolidPx + stageFeatherPx

    /** The stage's dock is as wide as the cover and sits 1% of it in from the page's outer edge. */
    val dockInsetPx = coverSizePx * 0.01f


    /**
     * Where the lyrics go beside the panel, [panelOnRight] or left (or across the page without it,
     * [withPanel] off), as a box whose own 5% column inset puts the text at the page's lyric
     * padding. Next to the panel the text runs from `(50cqw - NowBarRightSpacing * .3) * .9` to
     * `NowBarLeftSpacing * .35` from the far edge; without it, from 18cqw to 15.5cqw from the edges.
     */
    fun lyricsBox(withPanel: Boolean, panelOnRight: Boolean): LyricsBox {
        val textStart: Float
        val textEnd: Float
        if (withPanel) {
            textStart = (pageWidthPx * 0.5f - innerSpacingPx * 0.3f) * 0.9f
            textEnd = pageWidthPx - edgeSpacingPx * 0.35f
        } else {
            textStart = pageWidthPx * 0.18f
            textEnd = pageWidthPx * (1f - 0.155f)
        }
        val width = (textEnd - textStart) / (1f - 2f * CompactHeaderMetrics.LYRICS_SIDE_INSET)
        val left = textStart - width * CompactHeaderMetrics.LYRICS_SIDE_INSET
        return LyricsBox(if (panelOnRight) pageWidthPx - left - width else left, width)
    }

    private companion object {
        const val DESKTOP_LYRICS_PX = 56f
        const val DESKTOP_COVER_PX = 741f
        const val MIN_LYRICS_SP = 29.6f
    }
}

/** The lyrics' box across the page, in px from the page's left edge. */
data class LyricsBox(val leftPx: Float, val widthPx: Float)
