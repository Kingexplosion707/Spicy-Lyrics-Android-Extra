package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.animation.LineSweepGeometry
import com.tx24.spicyplayer.lyrics.spicy.animation.BloomMotion
import com.tx24.spicyplayer.lyrics.spicy.parser.GraphemeSegmenter

/**
 * What Bloom needs to run one fill edge along a line: each word's width, the space after it, each
 * held word's letter widths, the wrapped row each word starts on and how many letters it draws.
 * Null where the line has no such edge (right-to-left, interludes), so the words fill one by one.
 */
internal fun sweepGeometry(layout: LineLayout): LineSweepGeometry? {
    if (layout.isRtl || layout.isInterlude || layout.isSongwriter) return null
    val words = layout.line.words
    return measureSweep(words.size, layout.words) { i -> words[i].letters.size.takeIf { words[i].isLetterGroup } ?: 0 }
}

/**
 * The same for the romanization row under a word-synced line, whose pieces stand in for words (a
 * piece cut into letters for a split one). Null with no animated row or in right-to-left text.
 */
internal fun rowSweepGeometry(layout: LineLayout): LineSweepGeometry? {
    val row = layout.supplements.firstOrNull { it.words.isNotEmpty() } ?: return null
    if (row.isRtl) return null
    val counts = HashMap<Int, Int>()
    row.words.forEach { if (it.charCount > 0) counts[it.sourceWordIndex] = it.charCount }
    val count = (row.words.maxOfOrNull { it.sourceWordIndex } ?: return null) + 1
    return measureSweep(count, row.words) { i -> counts[i] ?: 0 }
}

/** [splitLetters] says how many letters word `i` is cut into, 0 for a word drawn whole. */
private fun measureSweep(count: Int, pieces: List<WordLayout>, splitLetters: (Int) -> Int): LineSweepGeometry? {
    if (count <= 0) return null
    val widths = FloatArray(count)
    val letters = arrayOfNulls<FloatArray>(count)
    val tops = FloatArray(count) { Float.NaN }
    val counts = IntArray(count)
    var fontPx = 0f
    for (piece in pieces) {
        val i = piece.sourceWordIndex
        if (i !in 0 until count) continue
        if (tops[i].isNaN()) tops[i] = piece.relativeOffset.y
        counts[i] += GraphemeSegmenter.segment(piece.textLayoutResult.layoutInput.text.text.trim()).size
        val width = piece.textLayoutResult.size.width.toFloat()
        if (fontPx == 0f) with(piece.textLayoutResult.layoutInput) { fontPx = with(density) { style.fontSize.toPx() } }
        val split = splitLetters(i)
        if (split > 0) {
            val perLetter = letters[i] ?: FloatArray(split).also { letters[i] = it }
            if (piece.charIndex in perLetter.indices) perLetter[piece.charIndex] = width
            widths[i] += width
        } else {
            widths[i] = maxOf(widths[i], piece.fullWordWidth, width)
        }
    }
    if (fontPx <= 0f) return null
    // The space between two words on the same row, as laid out.
    var gap = BloomMotion.DEFAULT_GAP_EM * fontPx
    for (n in 1 until pieces.size) {
        val before = pieces[n - 1]
        val after = pieces[n]
        if (after.sourceWordIndex == before.sourceWordIndex || kotlin.math.abs(after.relativeOffset.y - before.relativeOffset.y) > fontPx / 2f) continue
        val between = after.relativeOffset.x - (before.relativeOffset.x + before.textLayoutResult.size.width)
        if (between > 0f) { gap = between; break }
    }
    // Rows: a word sitting lower than the one before it starts a new one.
    val rows = IntArray(count)
    var row = 0
    var lastTop = Float.NaN
    for (i in 0 until count) {
        val top = tops[i]
        if (!top.isNaN()) {
            if (!lastTop.isNaN() && top > lastTop + fontPx / 2f) row++
            lastTop = top
        }
        rows[i] = row
    }
    return LineSweepGeometry(widths, gap, letters, fontPx, rows, counts)
}
