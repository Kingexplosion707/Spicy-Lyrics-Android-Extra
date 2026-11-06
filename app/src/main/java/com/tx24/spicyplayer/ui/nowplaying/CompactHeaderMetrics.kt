package com.tx24.spicyplayer.ui.nowplaying

/**
 * Geometry of the compact song header (the bar across the top of the page), resolved for one
 * page size. The ratios are written as CSS container units: `cqh`/`cqw` are percentages of the
 * nearest container, which is the page for the bar itself and the bar for everything inside it.
 *
 * Tuned on a phone: the cover sits as far below the top as the lyrics are from the side, the
 * song text is centred on the cover, and there is generous room between cover and text.
 *
 * Pure arithmetic in pixels so it can move to a shared module later.
 *
 * @param pageWidthPx width of the whole lyrics page.
 * @param pageHeightPx height of the whole lyrics page.
 * @param density px per dp.
 * @param lyricFontSizeSp the renderer's base lyric size for this page (see `LyricsLayoutMetrics`).
 */
data class CompactHeaderMetrics(
    val pageWidthPx: Float,
    val pageHeightPx: Float,
    val density: Float,
    val lyricFontSizeSp: Float,
    /** The camera cutout's inset at the top of the screen, in px (the system bars are hidden). */
    val topInsetPx: Float = 0f,
    val size: HeaderSize = HeaderSize.Large,
) {
    private val pageWidthDp = pageWidthPx / density.coerceAtLeast(0.01f)

    /**
     * `.Header { margin-left: var(--LyricsLeftSidePadding) }`: the artwork lines up with the
     * lyrics' left edge, which in this renderer is the 5% content inset (`LyricsLayoutMetrics`).
     */
    val contentStartPx = pageWidthPx * LYRICS_SIDE_INSET
    val contentEndPx = pageWidthPx * (1f - LYRICS_SIDE_INSET)

    /**
     * The same gap above the cover as beside it, or 60% of the
     * cutout's inset if that's more, so it sits just under the camera's band.
     */
    val barTopPx = maxOf(contentStartPx, topInsetPx)

    /** `--Compact_NowBarHeight: 15cqh` against the page at [HeaderSize.Large]; less for the smaller sizes. */
    val barHeightPx = pageHeightPx * size.barFraction

    /** `.Header { --MediaBoxSize: 100cqh }` against the bar: the artwork is as tall as the bar. */
    val artSizePx = barHeightPx

    /** 13cqh between cover and text, so the text doesn't crowd the cover. */
    val gapPx = barHeightPx * 0.13f

    /** `.Metadata { left: calc(var(--MediaBoxSize) + var(--CompactNowBarHeaderGap)) }`. */
    val textStartPx = contentStartPx + artSizePx + gapPx
    val textWidthPx = (contentEndPx - textStartPx).coerceAtLeast(0f)

    val barBottomPx = barTopPx + barHeightPx

    /** The lyrics start where the bar ends. */
    val lyricsTopPx = barBottomPx

    /**
     * Type and effect sizes, written in CSS px next to compact lyrics of
     * `clamp(3rem, 7cqw, 4rem)`; scaling them by our lyric size over that keeps the ratio of
     * header text to lyrics at this page width (same rule as the renderer's blur and glow).
     */
    /** Our lyric size over the compact desktop one (`clamp(3rem, 7cqw, 4rem)`): CSS px to dp next to the lyrics. */
    val lyricsScale = lyricFontSizeSp / (pageWidthDp * 0.07f).coerceIn(COMPACT_LYRICS_MIN_PX, COMPACT_LYRICS_MAX_PX)
    val scale = lyricsScale * TEXT_BOOST * size.textScale

    /**
     * Where the active line's top sits below the top of the lyrics. Compact mode scrolls "Top",
     * not "Center" (`GetScrollType`): `scrollLyricsToIndex(i, "start", _, -85)` leaves the line's
     * top 85px down the lyrics viewport.
     */
    val activeLineTopPx = 85f * lyricsScale * density

    /** `.SongName { font-size: 2.5rem; line-height: 3rem }`, in sp. */
    val titleSizeSp = 40f * scale
    val titleLineHeightSp = 48f * scale

    /** `.Artists { font-size: 1.5rem; line-height: 2rem }`, in sp. */
    val artistsSizeSp = 24f * scale
    val artistsLineHeightSp = 32f * scale

    /** `.MediaImageContainer { box-shadow: 0 9px 20px 0 rgba(0,0,0,.271) }`, in dp. */
    val artShadowOffsetYDp = 9f * scale
    val artShadowBlurDp = 20f * scale

    /** `.ti_ToImage { box-shadow: -20px 0 21px 0 rgba(0,0,0,.14) }`, in dp. */
    val incomingShadowOffsetXDp = -20f * scale
    val incomingShadowBlurDp = 21f * scale

    /** `.fi_FromImage::before { backdrop-filter: blur(12px) }`, in dp. */
    val outgoingBlurDp = 12f * scale

    /**
     * The expanded header, the full now-playing view with the lyrics hidden: the cover centred with
     * the song text centred under it (`.Metadata { margin-top: 5cqw }`), in the space between the
     * header's top and the controls ([availableHeightPx] from the page top).
     *
     * For a phone: the cover spans the lyrics' own column (the 5% insets), shrinking only if the
     * block wouldn't fit; the base text is 4% of the cover, the name 2.25x that and the artists
     * 1.5x; and the block is centred in its space.
     */
    fun expanded(availableHeightPx: Float): ExpandedHeader {
        fun textFor(art: Float): Triple<Float, Float, Float> {
            val base = art / density * EXPANDED_TEXT_FRACTION
            val titleLine = base * 2.25f * 1.25f * density
            val artistsLine = base * 1.5f * 1.35f * density
            return Triple(base, titleLine + artistsLine, art * 0.05f)
        }
        val room = availableHeightPx - barTopPx
        var art = pageWidthPx * (1f - 2f * LYRICS_SIDE_INSET)
        val (_, textAtFull, gapAtFull) = textFor(art)
        if (art + gapAtFull + textAtFull > room) art *= room / (art + gapAtFull + textAtFull)
        art = art.coerceAtLeast(artSizePx)
        val (base, textHeight, gap) = textFor(art)
        val top = barTopPx + ((room - art - gap - textHeight) / 2f).coerceAtLeast(0f)
        return ExpandedHeader(
            artLeftPx = (pageWidthPx - art) / 2f,
            artTopPx = top,
            artSizePx = art,
            textHeightPx = textHeight,
            titleSizeSp = base * 2.25f,
            titleLineHeightSp = base * 2.25f * 1.25f,
            artistsSizeSp = base * 1.5f,
            artistsLineHeightSp = base * 1.5f * 1.35f,
        )
    }

    companion object {
        /** How much of the page width the expanded cover takes. */
        /** The expanded header's base text size against its cover. */
        const val EXPANDED_TEXT_FRACTION = 0.04f

        /** Expanded, `border-radius: 2cqh` against its square. */
        const val NOWBAR_CORNER_FRACTION = 0.02f

        /** Content inset of the (non-duet) lyric slot in `LyricsLayoutMetrics.contentSlot`. */
        const val LYRICS_SIDE_INSET = 0.05f

        /** Header type against the desktop sizes (1 = the same). */
        const val TEXT_BOOST = 1f

        /** `.MediaImageContainer { border-radius: 3cqh }`; the square `.MediaBox` is its container. */
        const val ART_CORNER_FRACTION = 0.03f
        private const val COMPACT_LYRICS_MIN_PX = 48f
        private const val COMPACT_LYRICS_MAX_PX = 64f
    }
}

/** The expanded header's cover and text (see [CompactHeaderMetrics.expanded]); px unless sp. */
data class ExpandedHeader(
    val artLeftPx: Float,
    val artTopPx: Float,
    val artSizePx: Float,
    val textHeightPx: Float,
    val titleSizeSp: Float,
    val titleLineHeightSp: Float,
    val artistsSizeSp: Float,
    val artistsLineHeightSp: Float,
) {
    /**
     * The song docked over a video (the cover stage, the portrait Canvas) is a caption: the name
     * 1.5x this text's base size on a 1.2 line, the artists 1.05x on 1.35.
     */
    val dockTitleSizeSp get() = titleSizeSp / 2.25f * 1.5f
    val dockArtistsSizeSp get() = artistsSizeSp / 1.275f * 1.05f
}

/**
 * How tall the compact song header is: [Large] is the desktop's 15cqh bar, [Small] a slim row
 * like Apple Music's, with the text shrunk less than the cover so it stays readable.
 */
enum class HeaderSize(val label: String, val barFraction: Float, val textScale: Float) {
    Small("Small", 0.08f, 0.8f),
    Medium("Medium", 0.115f, 0.9f),
    Large("Large", 0.15f, 1f),
}
