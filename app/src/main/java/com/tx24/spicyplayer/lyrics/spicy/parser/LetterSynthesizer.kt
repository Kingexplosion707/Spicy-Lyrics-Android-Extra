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
                line.copy(words = mergeSyllables(line.words, config, romanized).map { synthesize(it, config, romanized) })
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
        val runs = mutableListOf<MutableList<Word>>()
        for (word in words) {
            if (word.isPartOfWord && runs.isNotEmpty() && !startsNewWord(runs.last().last(), word)) {
                runs.last() += word
            } else {
                runs += mutableListOf(word)
            }
        }
        return runs.flatMap { run ->
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
            if (config.syllableMerge == SyllableMerge.Held && !isLetterCapable(merged, config, romanized)) run else listOf(merged)
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

    // No lower length bound: even a single-character
    // held word (e.g. "I", "oh") gets the letter treatment if held long enough.
    private fun isLetterCapable(word: Word, config: RenderConfig, romanized: Boolean): Boolean {
        val display = display(word, romanized)
        if (config.isAppleMusic) {
            return config.lettersEnabled && com.tx24.spicyplayer.lyrics.spicy.animation.AppleMusicMotion.emphasizes(
                display, GraphemeSegmenter.segment(display).size, word.duration,
            )
        }
        return config.lettersEnabled &&
            word.duration >= (if (config.isRefreshedSimple) 1000L else config.letterDurationThresholdMs) &&
            GraphemeSegmenter.segment(display).size in (if (config.isRefreshedSimple) 2..minOf(7, config.letterMaxLength) else 1..config.letterMaxLength) &&
            !RtlDetector.isRtl(display)
    }

    private fun synthesize(word: Word, config: RenderConfig, romanized: Boolean): Word {
        val display = display(word, romanized)
        val graphemes = GraphemeSegmenter.segment(display)
        val len = graphemes.size

        if (!isLetterCapable(word, config, romanized)) {
            return if (word.isLetterGroup) word.copy(isLetterGroup = false, letters = emptyList()) else word
        }

        // The word and its letters share one emphasis window: normal mode trims the tail by
        // 250ms; Simple shifts its start by 21ms and its end by 40ms. Refreshed keeps that full
        // window, with no additional trim.
        // The Apple Music style times its letters off the word's own window.
        val subStartMs = if (config.isSimple) -21L else 0L
        val subEndMs = when {
            config.isAppleMusic -> 0L
            config.isSimple -> -40L
            else -> 250L
        }
        val windowStart = word.startMs - subStartMs
        val windowEnd = (word.endMs - subEndMs).coerceAtLeast(windowStart + 1L)

        // Every character — including punctuation — is split into its own timed letter and
        // animated identically; punctuation isn't excluded.
        val letters = bySyllable(word, graphemes, windowStart, windowEnd, config, romanized)
            ?: evenly(graphemes, windowStart, windowEnd)
        return word.copy(startMs = windowStart, endMs = windowEnd, isLetterGroup = true, letters = letters)
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
