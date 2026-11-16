package com.tx24.spicyplayer.network.data.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IvLyricsDocumentTest {
    private fun line(start: Long, vararg words: String, background: Boolean = false, singer: String? = null): IvLine {
        var at = start
        val syllables = words.map { IvSyllable(it, at, at + 300).also { at += 400 } }
        return IvLine(words.joinToString("").trim(), start, at, syllables, background, singer)
    }

    @Test fun `word timing becomes TTML with spaces only between words`() {
        val ttml = IvLyricsDocument.ttml(listOf(line(1_000, "to", "geth", "er ", "now"), line(4_000, "Hi")))!!
        assertTrue(ttml.contains(">to</span><span begin=\"1.400s\" end=\"1.700s\">geth</span>"))
        assertTrue(ttml.contains(">er</span> <span"))
        assertTrue(ttml.contains("<p begin=\"1.000s\""))
        assertTrue(ttml.contains("itunes:timing=\"Word\""))
    }

    @Test fun `background vocals ride in the lead line and singers become agents`() {
        val ttml = IvLyricsDocument.ttml(listOf(
            line(0, "Hey ", "you", singer = "Ann"),
            line(500, "(hey)", background = true),
            line(3_000, "Yes", singer = "Bo"),
            line(5_000, "Again", singer = "Ann"),
        ))!!
        assertTrue(ttml.contains("<span ttm:role=\"x-bg\"><span begin=\"0.500s\""))
        assertEquals(3, Regex("<p ").findAll(ttml).count())
        assertTrue(ttml.contains("ttm:agent=\"v1\""))
        assertTrue(ttml.contains("ttm:agent=\"v2\""))
        assertEquals(2, Regex("ttm:agent=\"v1\"").findAll(ttml).count())
    }

    @Test fun `text is escaped`() {
        val ttml = IvLyricsDocument.ttml(listOf(line(0, "Rock ", "& ", "<roll>"), line(2_000, "Again")))!!
        assertTrue(ttml.contains(">&amp;</span>") && ttml.contains(">&lt;roll&gt;</span>"))
    }

    @Test fun `no word timing means no TTML, and LRC carries the lines`() {
        val lines = listOf(IvLine("Hello", 61_230, 63_000, emptyList(), false, null), IvLine("(bg)", 62_000, 63_000, emptyList(), true, null))
        assertNull(IvLyricsDocument.ttml(lines))
        assertEquals("[01:01.23]Hello", IvLyricsDocument.lrc(lines))
    }

    @Test fun `furigana readings land on the words that hold the kanji`() {
        val ruby = "<ruby>君<rt>きみ</rt></ruby>の<ruby>名前<rt>なまえ</rt></ruby>は"
        assertEquals(listOf("きみの", "なまえ", null), FuriganaReadings.forWords(ruby, listOf("君の", "名前", "は")))
    }

    @Test fun `a ruby across two words reads on the first`() {
        val ruby = "<ruby>東京<rt>とうきょう</rt></ruby>へ"
        assertEquals(listOf("とうきょう", null, null), FuriganaReadings.forWords(ruby, listOf("東", "京", "へ")))
    }

    @Test fun `markup that doesn't spell the words gives nothing`() {
        val ruby = "<ruby>君<rt>きみ</rt></ruby>の"
        assertEquals(listOf(null, null), FuriganaReadings.forWords(ruby, listOf("僕", "の")))
        assertEquals(listOf(null), FuriganaReadings.forWords(ruby, listOf("君")))
        assertEquals(listOf(null), FuriganaReadings.forWords("plain", listOf("plain")))
    }

    @Test fun `spaces in the words or the markup don't matter`() {
        val ruby = "<ruby>愛<rt>あい</rt></ruby> してる"
        assertEquals(listOf("あい", null), FuriganaReadings.forWords(ruby, listOf("愛 ", "してる")))
    }
}
