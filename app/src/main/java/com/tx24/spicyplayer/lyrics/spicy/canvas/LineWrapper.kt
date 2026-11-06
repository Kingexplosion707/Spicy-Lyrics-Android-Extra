package com.tx24.spicyplayer.lyrics.spicy.canvas

/**
 * Where a lyric line wraps, matching how a browser lays out the same line.
 *
 * Word-synced lines are a row of word elements that wraps. Syllables written without a space
 * between them form a group that moves to the next row whole. Only a group wider than a whole
 * row breaks inside itself, where it fills a row; the words around it flow on in the room left,
 * as text does. A piece of a broken word isn't left alone on the last row.
 *
 * Line-synced and static lines are plain text ([textMode]): breaks at spaces and between CJK
 * characters, but not before closing punctuation or after opening brackets.
 */
internal object LineWrapper {

    /** One laid-out piece of a line: a word, syllable, or character. */
    data class Piece(val width: Float, val text: String, val gluedToPrevious: Boolean)

    /** Closing punctuation a line of CJK text never starts with (browser line-breaking rules). */
    private const val NO_BREAK_BEFORE = "、。，．・：；？！゛゜ヽヾゝゞ々）］｝」』】〕〉》〙〗〟’”｠»,.!?)]}…‥"
    /** Opening brackets a line never ends with. */
    private const val NO_BREAK_AFTER = "（［｛「『【〔〈《〘〖〝‘“｟«([{"

    /**
     * @param wordGap space between words on a row.
     * @param trailingGap space a word also needs after itself to fit (a left-aligned word
     *   element's `::after` margin; 0 for column-gap or plain text).
     * @return row start indices, then [pieces].size.
     */
    fun breaks(
        pieces: List<Piece>,
        maxWidth: Float,
        wordGap: Float,
        trailingGap: Float,
        textMode: Boolean,
    ): List<Int> {
        val n = pieces.size
        val lineBreaks = mutableListOf(0)
        if (n == 0) return lineBreaks.apply { add(0) }

        // Whether each piece's glued run (its word group) fits a row by itself.
        val runFits = BooleanArray(n)
        var runStart = 0
        var runWidth = 0f
        for (idx in 0 until n) {
            if (idx > 0 && !pieces[idx].gluedToPrevious) {
                val fits = runWidth + trailingGap <= maxWidth
                for (k in runStart until idx) runFits[k] = fits
                runStart = idx
                runWidth = 0f
            }
            runWidth += pieces[idx].width
        }
        for (k in runStart until n) runFits[k] = runWidth <= maxWidth

        fun endsRun(idx: Int) = idx == n - 1 || !pieces[idx + 1].gluedToPrevious

        /** Not before closing punctuation, nor after an opening bracket. */
        fun punctuationAllows(idx: Int): Boolean {
            val prev = pieces[idx - 1].text.lastOrNull() ?: return true
            val cur = pieces[idx].text.firstOrNull() ?: return true
            return cur !in NO_BREAK_BEFORE && prev !in NO_BREAK_AFTER
        }

        fun textBreakBefore(idx: Int): Boolean {
            val piece = pieces[idx]
            if (!piece.gluedToPrevious) return true
            if (!punctuationAllows(idx)) return false
            val prev = pieces[idx - 1].text.lastOrNull() ?: return true
            val cur = piece.text.firstOrNull() ?: return true
            // A Latin word longer than a row breaks anywhere rather than running off-screen.
            return LyricsLayoutCalculator.isCjk(prev) || LyricsLayoutCalculator.isCjk(cur) || !runFits[idx]
        }

        // Between words; inside a group only when the group is wider than a row, and then not
        // against punctuation.
        fun canBreakBefore(idx: Int): Boolean = when {
            idx == 0 -> true
            textMode -> textBreakBefore(idx)
            !pieces[idx].gluedToPrevious -> true
            else -> !runFits[idx] && punctuationAllows(idx)
        }

        // Greedy: as much on each row as fits. A group wider than a row breaks where it fills one,
        // and what follows carries on in the room left on its last row.
        var rowWidth = 0f
        var lastBreakCandidate = 0
        var i = 0
        while (i < n) {
            val piece = pieces[i]
            val gap = if (rowWidth > 0f && !piece.gluedToPrevious) wordGap else 0f
            if (canBreakBefore(i)) lastBreakCandidate = i
            val trailing = if (endsRun(i) && i < n - 1) trailingGap else 0f

            if (rowWidth + gap + piece.width + trailing > maxWidth && rowWidth > 0f) {
                val breakIdx = if (lastBreakCandidate > lineBreaks.last()) lastBreakCandidate else i
                lineBreaks.add(breakIdx)
                i = breakIdx
                rowWidth = 0f
            } else {
                rowWidth += gap + piece.width
                i++
            }
        }

        // A word broken so that one piece of it (a character, a syllable) is left alone on the
        // last row takes one more piece down with it, when the row above keeps two and it fits.
        if (lineBreaks.size >= 2) {
            val lastStart = lineBreaks.last()
            val prevStart = lineBreaks[lineBreaks.size - 2]
            val moved = lastStart - 1
            if (n - lastStart == 1 && pieces[lastStart].gluedToPrevious && moved - prevStart >= 2 && canBreakBefore(moved)) {
                var width = 0f
                for (k in moved until n) width += pieces[k].width + if (k > moved && !pieces[k].gluedToPrevious) wordGap else 0f
                if (width <= maxWidth) lineBreaks[lineBreaks.size - 1] = moved
            }
        }
        lineBreaks.add(n)
        return lineBreaks
    }
}
