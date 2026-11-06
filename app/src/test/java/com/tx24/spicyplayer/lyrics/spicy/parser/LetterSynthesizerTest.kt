package com.tx24.spicyplayer.lyrics.spicy.parser

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterSynthesizerTest {
    // "to|night " (short, 600 ms) then "for|ev|er" (held, 1500 ms).
    private val words = listOf(
        Word("to", 0, 200),
        Word("night ", 200, 600, isPartOfWord = true),
        Word("for", 1000, 1500),
        Word("ev", 1500, 1800, isPartOfWord = true, romanizedText = "EV"),
        Word("er", 1800, 2500, isPartOfWord = true),
    )

    private fun merge(mode: SyllableMerge, romanized: Boolean = false) =
        LetterSynthesizer.mergeSyllables(words, RenderConfig.FULL.copy(syllableMerge = mode), romanized)

    @Test
    fun `off keeps the syllables`() {
        assertEquals(words, merge(SyllableMerge.Off))
    }

    @Test
    fun `full merges every split word into one spanning its syllables`() {
        val merged = merge(SyllableMerge.Full)
        assertEquals(listOf("tonight ", "forever"), merged.map { it.text })
        assertEquals(listOf(0L to 600L, 1000L to 2500L), merged.map { it.startMs to it.endMs })
        assertEquals(listOf(false, false), merged.map { it.isPartOfWord })
        assertNull(merged[0].romanizedText)
        assertEquals("forEVer", merged[1].romanizedText)
    }

    @Test
    fun `held merges only words long enough for the letter effect`() {
        val merged = merge(SyllableMerge.Held)
        assertEquals(listOf("to", "night ", "forever"), merged.map { it.text })
    }

    private fun mergeTexts(vararg syllables: String): List<String> {
        val glued = syllables.mapIndexed { i, s -> Word(s, i * 300L, i * 300L + 300, isPartOfWord = i > 0) }
        return LetterSynthesizer.mergeSyllables(glued, RenderConfig.FULL.copy(syllableMerge = SyllableMerge.Full), false)
            .map { it.text }
    }

    @Test
    fun `hyphens and dashes keep glued words apart`() {
        assertEquals(listOf("well-", "known"), mergeTexts("well-", "known"))
        assertEquals(listOf("yeah", "—", "no"), mergeTexts("yeah", "—", "no"))
        assertEquals(listOf("to", "-night"), mergeTexts("to", "-night"))
        assertEquals(listOf("don't,"), mergeTexts("don", "'t", ","))
    }

    @Test
    fun `scripts without word spaces never merge`() {
        assertEquals(listOf("愛", "し", "て", "る"), mergeTexts("愛", "し", "て", "る"))
        assertEquals(listOf("ラー", "メン"), mergeTexts("ラー", "メン"))
        assertEquals(listOf("รัก", "เธอ"), mergeTexts("รัก", "เธอ"))
        assertEquals(listOf("I", "愛"), mergeTexts("I", "愛"))
    }

    @Test
    fun `korean spaces its words so its syllables merge`() {
        assertEquals(listOf("사랑해"), mergeTexts("사", "랑", "해"))
    }

    @Test
    fun `a split-off word keeps its glue to the one before`() {
        val merged = LetterSynthesizer.mergeSyllables(
            listOf(Word("well-", 0, 300), Word("kn", 300, 600, isPartOfWord = true), Word("own", 600, 900, isPartOfWord = true)),
            RenderConfig.FULL.copy(syllableMerge = SyllableMerge.Full), false,
        )
        assertEquals(listOf("well-", "known"), merged.map { it.text })
        assertEquals(listOf(false, true), merged.map { it.isPartOfWord })
    }

    @Test
    fun `a merged held word gets letters across the whole word`() {
        val config = RenderConfig.FULL.copy(syllableMerge = SyllableMerge.Held)
        val line = com.tx24.spicyplayer.lyrics.spicy.models.Line(words, 0)
        val forever = LetterSynthesizer.apply(listOf(line), config, romanized = false).single().words.last()
        assertEquals(true, forever.isLetterGroup)
        assertEquals("forever", forever.letters.joinToString("") { it.char })
    }

    @Test
    fun `a merged held word's letters keep each syllable's pace`() {
        val perfect = com.tx24.spicyplayer.lyrics.spicy.models.Line(
            listOf(Word("per", 0, 1500), Word("fect", 1500, 1800, isPartOfWord = true)), 0,
        )
        val apple = RenderConfig.FULL.copy(syllableMerge = SyllableMerge.Full, syllablePace = true, appleMusic = true)
        val word = LetterSynthesizer.apply(listOf(perfect), apple, romanized = false).single().words.single()
        assertEquals("perfect", word.letters.joinToString("") { it.char })
        // "per" takes 1.5s over 3 letters, "fect" 0.3s over 4.
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 1575L, 1650L, 1725L), word.letters.map { it.startMs })
        assertEquals(1800L, word.letters.last().endMs)

        // A trimmed window is shared out, not taken off the last syllable.
        val paced = RenderConfig.FULL.copy(syllableMerge = SyllableMerge.Full, syllablePace = true)
        val trimmed = LetterSynthesizer.apply(listOf(perfect), paced, romanized = false).single().words.single()
        val fect = trimmed.letters.drop(3)
        assertEquals(trimmed.endMs, fect.last().endMs)
        assertEquals(true, fect.last().endMs - fect.first().startMs > 200L)

        // Off, the letters share the word evenly, as before.
        val even = LetterSynthesizer.apply(listOf(perfect), apple.copy(syllablePace = false), romanized = false).single().words.single()
        assertEquals(listOf(0L, 257L, 514L, 771L, 1028L, 1285L, 1542L), even.letters.map { it.startMs })
    }

    // A Chinese syllable after the first romanizes with a leading space (it starts a word of its
    // own); the layout draws the romanization trimmed, so the letters must cover only "hao".
    private val heldChinese = com.tx24.spicyplayer.lyrics.spicy.models.Line(
        listOf(Word("你", 0, 300, romanizedText = "ni"), Word("好", 300, 2300, isPartOfWord = true, romanizedText = " hao")), 0,
    )

    @Test
    fun `a romanized held syllable splits its letters over the visible text`() {
        val hao = LetterSynthesizer.apply(listOf(heldChinese), RenderConfig.FULL, romanized = true).single().words.last()
        assertEquals(true, hao.isLetterGroup)
        assertEquals(listOf("h", "a", "o"), hao.letters.map { it.char })
        assertEquals(hao.startMs, hao.letters.first().startMs)
        assertEquals(hao.endMs, hao.letters.last().endMs)
    }

    @Test
    fun `the original text of a romanized syllable is split as written`() {
        val hao = LetterSynthesizer.apply(listOf(heldChinese), RenderConfig.FULL, romanized = false).single().words.last()
        assertEquals(listOf("好"), hao.letters.map { it.char })
    }

    private fun mergeRun(vararg parts: Word) = LetterSynthesizer.mergeSyllables(
        parts.toList(), RenderConfig.FULL.copy(syllableMerge = SyllableMerge.Steady), false,
    ).map { it.text }

    @Test
    fun `a gap inside the word keeps it split`() {
        assertEquals(listOf("sun", "set"), mergeRun(Word("sun", 0, 300), Word("set", 450, 750, isPartOfWord = true)))
        assertEquals(listOf("sunset"), mergeRun(Word("sun", 0, 300), Word("set", 380, 680, isPartOfWord = true)))
    }

    @Test
    fun `a part sung far faster than the rest keeps the word split`() {
        // "a" holds 60% of the time for 1 of 8 characters.
        assertEquals(listOf("a", "bcdefgh"), mergeRun(Word("a", 0, 600), Word("bcdefgh", 600, 1000, isPartOfWord = true)))
    }

    @Test
    fun `a space inside a part keeps the word split`() {
        assertEquals(listOf("a b", "c"), mergeRun(Word("a b", 0, 300), Word("c", 300, 600, isPartOfWord = true)))
        assertEquals(listOf("ab", "c"), mergeRun(Word("ab", 0, 300, romanizedText = "a b"), Word("c", 300, 600, isPartOfWord = true)))
    }

    @Test
    fun `full still merges across a gap`() {
        val merged = LetterSynthesizer.mergeSyllables(
            listOf(Word("sun", 0, 300), Word("set", 450, 750, isPartOfWord = true)),
            RenderConfig.FULL.copy(syllableMerge = SyllableMerge.Full), false,
        )
        assertEquals(listOf("sunset"), merged.map { it.text })
    }
}
