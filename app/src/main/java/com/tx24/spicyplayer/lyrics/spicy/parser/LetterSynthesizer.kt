package com.tx24.spicyplayer.lyrics.spicy.parser

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.models.Letter
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.Word

/**
 * Merges a word's syllables when [RenderConfig.syllableMerge] asks for it, then splits
 * sufficiently long syllables into per-letter timings for the "held word"
 * letter-by-letter emphasis.
 *
 * This runs at render time (not parse time) because letter capability depends on the
 * active [RenderConfig] (thresholds differ per quality mode) and on whether romanized
 * text is currently displayed (letters are split over the *displayed* string).
 */
object LetterSynthesizer {

    /**
     * @param romanized when true, letters are split over each word's romanized text (if present).
     */
    fun apply(lines: List<Line>, config: RenderConfig, romanized: Boolean): List<Line> {
        return lines.map { line ->
            if (line.isInterlude || line.isSongwriter || line.translationReplaces || line.lineTimed) {
                line
            } else {
                val words = mergeSyllables(line.words, config, romanized)
                line.copy(words = if (config.isBloom) bloomWords(words, config, romanized, line.isBackground)
                    else words.map { synthesize(it, config, romanized, line.isBackground) })
            }
        }
    }

    /**
     * Joins each run of syllables (a word, then the parts glued to it by [Word.isPartOfWord]) into
     * one word spanning the first syllable's start to the last one's end, so it fills evenly like
     * a word that was never split. [SyllableMerge.Held] merges a run only when the whole word
     * would get the held-word letter emphasis. See [startsNewWord] for glued syllables that stay apart.
     */
    internal fun mergeSyllables(words: List<Word>, config: RenderConfig, romanized: Boolean): List<Word> {
        if (config.syllableMerge == SyllableMerge.Off) return words
        return runs(words).flatMap { run ->
            if (run.size == 1 || config.syllableMerge == SyllableMerge.Steady && !joinsEvenly(run)) return@flatMap run
            val first = run.first()
            val merged = first.copy(
                text = run.joinToString("") { it.text },
                endMs = run.maxOf { it.endMs },
                isLetterGroup = false,
                letters = emptyList(),
                romanizedText = if (run.any { it.romanizedText != null }) run.joinToString("") { it.romanizedText ?: it.text } else null,
                syllables = run,
            )
            if (config.syllableMerge == SyllableMerge.Held && !isLetterCapable(merged, config, romanized, background = false)) run else listOf(merged)
        }
    }

    /** [words] in runs: a word, then the syllables glued to it ([Word.isPartOfWord]) that don't start one of their own. */
    private fun runs(words: List<Word>): List<MutableList<Word>> {
        val runs = mutableListOf<MutableList<Word>>()
        for (word in words) {
            if (word.isPartOfWord && runs.isNotEmpty() && !startsNewWord(runs.last().last(), word)) {
                runs.last() += word
            } else {
                runs += mutableListOf(word)
            }
        }
        return runs
    }

    /**
     * For each of [words], the index of the first word of the run it belongs to (see [runs]): a
     * word sung in several syllables shares one held-word swell, and its fill edge goes by the
     * whole word's length.
     */
    fun runStarts(words: List<Word>): IntArray {
        val starts = IntArray(words.size)
        var i = 0
        for (run in runs(words)) {
            val first = i
            repeat(run.size) { starts[i++] = first }
        }
        return starts
    }

    /**
     * Bloom judges a word sung in syllables as a whole: it's split into letters when the whole
     * word has two to seven of them and lasts a second or more from its first syllable's start to
     * its last one's end. Each syllable keeps its own letters and timing; the animator swells them
     * together. Background vocals are never split.
     */
    private fun bloomWords(words: List<Word>, config: RenderConfig, romanized: Boolean, background: Boolean): List<Word> =
        runs(words).flatMap { run ->
            if (run.size == 1) return@flatMap listOf(synthesize(run[0], config, romanized, background))
            val counts = run.map { GraphemeSegmenter.segment(display(it, romanized)).size }
            val whole = run.first().copy(
                text = run.joinToString("") { display(it, romanized) },
                romanizedText = null,
                endMs = run.maxOf { it.endMs },
            )
            val splits = isLetterCapable(whole, config, romanized = false, background = background) &&
                run.none { RtlDetector.isRtl(display(it, romanized)) }
            run.mapIndexed { i, part ->
                if (splits && counts[i] > 0) split(part, config, romanized)
                else if (part.isLetterGroup) part.copy(isLetterGroup = false, letters = emptyList()) else part
            }
        }

    /**
     * Whether [next], glued to [prev] with no space, is still a word of its own: across a hyphen,
     * dash or slash ("well-known" is two words), or next to a script written without spaces
     * between words (Chinese, Japanese, Thai...), where glued only means the same line.
     * Korean spaces its words, so its glued syllables do merge.
     */
    private fun startsNewWord(prev: Word, next: Word): Boolean {
        val before = prev.text.trimEnd()
        val after = next.text.trimStart()
        if (before.isEmpty() || after.isEmpty()) return true
        val last = before.codePointBefore(before.length)
        val first = after.codePointAt(0)
        return last in WORD_JOINERS || first in WORD_JOINERS || isUnspaced(last) || isUnspaced(first)
    }

    /** Silence between two parts of a word beyond which one fill would run through it. */
    private const val MAX_JOIN_GAP_MS = 100L
    /** A part sung this many times faster or slower per character than the word as a whole stays split. */
    private const val MAX_PACE_RATIO = 3.0

    /**
     * Whether a run of glued syllables reads as one word sung steadily: no gap between its
     * parts, no space inside one, no trailing dash on any but the last, and no part sung much
     * faster or slower per character than the whole.
     */
    private fun joinsEvenly(run: List<Word>): Boolean {
        // The space that ends the word belongs to its last part; one anywhere else splits it.
        if (run.any { part ->
                val last = part === run.last()
                fun spaced(text: String?) = text != null && (if (last) text.trimEnd() else text).any(Char::isWhitespace)
                spaced(part.text) || spaced(part.romanizedText)
            }) return false
        if (run.dropLast(1).any { it.text.isNotEmpty() && it.text.last() in DASHES }) return false
        for (i in 1 until run.size) if (run[i].startMs - run[i - 1].endMs > MAX_JOIN_GAP_MS) return false
        val total = (run.maxOf { it.endMs } - run.first().startMs).toDouble()
        val chars = run.sumOf { it.text.trimEnd().length }
        if (total <= 0 || chars == 0) return false
        return run.all { part ->
            val timeShare = part.duration / total
            val charShare = part.text.trimEnd().length.toDouble() / chars
            timeShare >= charShare / MAX_PACE_RATIO && timeShare <= charShare * MAX_PACE_RATIO
        }
    }

    private val DASHES = "-‐‑‒–—―".toSet()

    private val WORD_JOINERS = "-‐‑‒–—―/".map(Char::code).toSet()

    // Chinese and Japanese (Han, kana, "ー"), Thai, Lao, Myanmar and Khmer, by code point
    // (Character.UnicodeScript needs Android 7).
    private val UNSPACED_RANGES = listOf(
        0x0E00..0x0EFF, 0x1000..0x109F, 0x1780..0x17FF,
        0x2E80..0x2FDF, 0x3005..0x3007, 0x3021..0x3029, 0x3038..0x303B,
        0x3040..0x30FF, 0x31F0..0x31FF, 0x3400..0x4DBF, 0x4E00..0x9FFF,
        0xF900..0xFAFF, 0xFF66..0xFF9F, 0x20000..0x323AF,
    )

    private fun isUnspaced(codePoint: Int) = UNSPACED_RANGES.any { codePoint in it }

    // Romanized text is drawn trimmed (a leading space only marks a word of its own), so its
    // letters are split over the trimmed text too, one per drawn letter.
    private fun display(word: Word, romanized: Boolean) = if (romanized) (word.romanizedText ?: word.text).trim() else word.text

    // Spring has no lower length bound: even a single-character held word (e.g. "I", "oh") gets
    // the letter treatment if held long enough. Bloom splits only two to seven letters, and never
    // a background vocal.
    private fun isLetterCapable(word: Word, config: RenderConfig, romanized: Boolean, background: Boolean): Boolean {
        val display = display(word, romanized)
        return config.lettersEnabled &&
            !(config.isBloom && background) &&
            word.duration >= config.letterDurationThresholdMs &&
            GraphemeSegmenter.segment(display).size in config.letterMinLength..config.letterMaxLength &&
            !RtlDetector.isRtl(display)
    }

    private fun synthesize(word: Word, config: RenderConfig, romanized: Boolean, background: Boolean = false): Word {
        if (!isLetterCapable(word, config, romanized, background)) {
            return if (word.isLetterGroup) word.copy(isLetterGroup = false, letters = emptyList()) else word
        }
        return split(word, config, romanized)
    }

    /** How far a held word's emphasis window starts and ends after the word itself. */
    private fun windowShift(config: RenderConfig): Pair<Long, Long> =
        // Spring trims the tail by 250ms; Bloom starts 21ms and ends 40ms later.
        if (config.isBloom) 21L to 40L else 0L to -250L

    /** [word] split into timed letters over its emphasis window. */
    private fun split(word: Word, config: RenderConfig, romanized: Boolean): Word {
        val graphemes = GraphemeSegmenter.segment(display(word, romanized))
        val (shiftStart, shiftEnd) = windowShift(config)
        val windowStart = word.startMs + shiftStart
        val windowEnd = (word.endMs + shiftEnd).coerceAtLeast(windowStart + 1L)

        // Every character — including punctuation — is split into its own timed letter and
        // animated identically; punctuation isn't excluded.
        val letters = bySyllable(word, graphemes, windowStart, windowEnd, config, romanized)
            ?: evenly(graphemes, windowStart, windowEnd)
        return word.copy(startMs = windowStart, endMs = windowEnd, isLetterGroup = true, letters = letters)
    }

    /**
     * The word an under-line row draws for [word]: its romanization [text] as a word of its own,
     * timed as the word is and split into letters by the same rules, so the row animates exactly
     * as a line of that text would. [word] has already been through [synthesize], which moved a
     * held word's times to its letters' window; they're put back first.
     */
    fun rowWord(word: Word, text: String, config: RenderConfig): Word {
        val (start, end) = sungSpan(word, config)
        return synthesize(
            word.copy(text = text, romanizedText = text, startMs = start, endMs = end, isLetterGroup = false, letters = emptyList()),
            config, romanized = true,
        )
    }

    /** When [word] is sung: its times, with a held word's moved window put back. */
    fun sungSpan(word: Word, config: RenderConfig): Pair<Long, Long> {
        if (!word.isLetterGroup) return word.startMs to word.endMs
        val (shiftStart, shiftEnd) = windowShift(config)
        return (word.startMs - shiftStart) to (word.endMs - shiftEnd)
    }

    private fun evenly(graphemes: List<String>, from: Long, to: Long): List<Letter> {
        val span = (to - from).toFloat() / graphemes.size
        return graphemes.mapIndexed { i, grapheme ->
            Letter(char = grapheme, startMs = from + (i * span).toLong(), endMs = from + ((i + 1) * span).toLong())
        }
    }

    /**
     * A merged word's letters, each syllable's spread over that syllable's own time, so a long
     * "per" and a quick "fect" keep their pace inside one word. Each syllable runs until the next
     * starts. Null with [RenderConfig.syllablePace] off, when the word wasn't merged, or when its
     * syllables don't split into the same letters.
     */
    private fun bySyllable(word: Word, graphemes: List<String>, windowStart: Long, windowEnd: Long, config: RenderConfig, romanized: Boolean): List<Letter>? {
        if (!config.syllablePace) return null
        val parts = word.syllables.takeIf { it.size > 1 } ?: return null
        // The displayed text is the parts joined; romanized, it's drawn trimmed at both ends.
        val texts = parts.mapIndexed { i, part ->
            val text = if (romanized) part.romanizedText ?: part.text else part.text
            when {
                !romanized -> text
                i == 0 -> text.trimStart()
                i == parts.lastIndex -> text.trimEnd()
                else -> text
            }
        }
        val counts = texts.map { GraphemeSegmenter.segment(it).size }
        if (counts.sum() != graphemes.size) return null
        // The window may be trimmed or shifted from the word's own times: syllables keep their
        // share of it, so a trim doesn't all come off the last one.
        val scale = (windowEnd - windowStart).toDouble() / (word.endMs - word.startMs).coerceAtLeast(1L)
        fun placed(ms: Long) = windowStart + ((ms - word.startMs) * scale).toLong()
        val letters = ArrayList<Letter>(graphemes.size)
        var cursor = windowStart
        parts.forEachIndexed { i, _ ->
            val end = if (i == parts.lastIndex) windowEnd else placed(parts[i + 1].startMs).coerceIn(cursor, windowEnd)
            if (counts[i] > 0) letters += evenly(graphemes.subList(letters.size, letters.size + counts[i]), cursor, end)
            cursor = end
        }
        return letters
    }
}
