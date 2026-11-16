package com.tx24.spicyplayer.lyrics.spicy.models

/**
 * Word timing made up for lines that only have a line's timing, so they can light word by word:
 * the line's words are sung one after another from its start, each for a time that grows with
 * its letters, at a pace that fits the line's time but never drags (a line followed by a long
 * gap is sung at a natural pace and then rests).
 */
object GeneratedWordTiming {
    private const val PER_LETTER_MS = 65L
    private const val PER_WORD_MS = 120L
    /** Of the time up to the next line, the share the words may take at most: the rest is a breath. */
    private const val FILL = 0.92

    /** Whether [line] has a line's timing only: every word on the same span, or marked so. */
    fun needsTiming(line: Line): Boolean {
        if (line.untimed || line.words.isEmpty()) return false
        if (line.lineTimed) return true
        val first = line.words.first()
        return line.words.all { it.startMs == first.startMs && it.endMs == first.endMs }
    }

    /** Whether [lines] have any line that would get made-up timing. */
    fun applies(lines: List<Line>): Boolean = lines.any { it.role != LineRole.INTERLUDE && needsTiming(it) }

    /** [lines] with made-up word timing in every line that has only a line's timing; others as they are. */
    fun fillIn(lines: List<Line>): List<Line> {
        if (!applies(lines)) return lines
        return lines.mapIndexed { index, line ->
            if (line.role == LineRole.INTERLUDE || !needsTiming(line)) return@mapIndexed line
            val nextStart = lines.drop(index + 1).firstOrNull { it.role == LineRole.LEAD && it.startMs > line.startMs }?.startMs
            val room = maxOf(line.endMs, nextStart ?: line.endMs) - line.startMs
            line.copy(words = spread(line.words, line.startMs, room), lineTimed = false)
        }
    }

    private fun weight(word: Word): Long = PER_WORD_MS + PER_LETTER_MS * word.text.count { it.isLetterOrDigit() }.coerceAtLeast(1)

    private fun spread(words: List<Word>, startMs: Long, roomMs: Long): List<Word> {
        // A word glued to the one before it (a syllable) shares that word's pace, without its own pause.
        val weights = words.map { word -> if (word.isPartOfWord) weight(word) - PER_WORD_MS else weight(word) }
        val natural = weights.sum()
        val span = minOf(natural, (roomMs.coerceAtLeast(words.size * 80L) * FILL).toLong()).coerceAtLeast(words.size * 60L)
        var at = startMs.toDouble()
        val scale = span.toDouble() / natural
        return words.mapIndexed { i, word ->
            val start = at
            at += weights[i] * scale
            word.copy(startMs = start.toLong(), endMs = at.toLong().coerceAtLeast(start.toLong() + 1), syllables = emptyList(), letters = emptyList())
        }
    }
}
