package com.tx24.spicyplayer.ui.nowplaying

import kotlin.math.min

/**
 * The header's marquee for names too long to fit, run as `25s linear infinite alternate`.
 * Each pass holds still for the first and
 * last 10%, slides linearly in between, and every other pass runs backwards.
 */
object HeaderMarquee {
    const val PASS_MS = 25_000L

    /** Share of the travel covered at [elapsedMs] since the animation started, 0..1. */
    fun phase(elapsedMs: Long): Float {
        val t = elapsedMs.coerceAtLeast(0L)
        val pass = t / PASS_MS
        val local = (t % PASS_MS).toFloat() / PASS_MS
        val forward = when {
            local <= 0.1f -> 0f
            local >= 0.9f -> 1f
            else -> (local - 0.1f) / 0.8f
        }
        return if (pass % 2L == 0L) forward else 1f - forward
    }

    /**
     * Horizontal shift of the scrolling span: `translateX(min(-100% + 100cqw, 0px) * phase)`,
     * where 100% is the span's own width and 100cqw the text block's width.
     */
    fun offsetPx(phase: Float, containerWidthPx: Float, spanWidthPx: Float): Float =
        min(containerWidthPx - spanWidthPx, 0f) * phase

    /** `.SongName span { padding: 0 3cqw 0 2.5cqw }` against the text block. */
    fun spanStartPaddingPx(containerWidthPx: Float): Float = containerWidthPx * 0.025f
    fun spanEndPaddingPx(containerWidthPx: Float): Float = containerWidthPx * 0.03f

    /**
     * Stops of the edge mask (`mask-image: linear-gradient(90deg, transparent 0, #fff 2.5cqw,
     * #000 2.5cqw, #000 calc(100% - 3.75cqw), transparent 100%)`) as fractions of the masked
     * element's width, which is the span clamped to the text block (`max-width: 100cqw`).
     */
    fun maskStops(containerWidthPx: Float, elementWidthPx: Float): Pair<Float, Float> {
        if (elementWidthPx <= 0f) return 0f to 1f
        val start = (containerWidthPx * 0.025f / elementWidthPx).coerceIn(0f, 1f)
        val end = ((elementWidthPx - containerWidthPx * 0.0375f) / elementWidthPx).coerceIn(start, 1f)
        return start to end
    }
}
