package com.tx24.spicyplayer.lyrics.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class LineWrapperTest {
    private fun word(w: Float, text: String = "a") = LineWrapper.Piece(w, text, gluedToPrevious = false)
    private fun glued(w: Float, text: String = "a") = LineWrapper.Piece(w, text, gluedToPrevious = true)

    /** A word written without spaces: its first piece starts a group, the rest are glued on. */
    private fun group(text: String, charWidth: Float = 20f) =
        text.mapIndexed { i, c -> LineWrapper.Piece(charWidth, c.toString(), gluedToPrevious = i > 0) }

    private fun syllable(pieces: List<LineWrapper.Piece>, max: Float, trailing: Float = 5f) =
        LineWrapper.breaks(pieces, max, wordGap = 5f, trailingGap = trailing, textMode = false)

    private fun text(pieces: List<LineWrapper.Piece>, max: Float) =
        LineWrapper.breaks(pieces, max, wordGap = 5f, trailingGap = 0f, textMode = true)

    @Test fun aWordGroupMovesToTheNextRowWhole() {
        // Spicy Lyrics: 君の一番 / かわいいところな～んだ？ (not split inside the second group).
        val pieces = group("君の一番") + group("かわいいところな～んだ？")
        assertEquals(listOf(0, 4, 16), syllable(pieces, max = 300f))
    }

    @Test fun aGroupWiderThanARowBreaksWhereItFillsAndTheNextWordCarriesOn() {
        val pieces = listOf(word(40f)) + group("abcdef", charWidth = 40f) + listOf(word(20f))
        // "a" and the group's start | its middle | its end and the next word in the room left.
        assertEquals(listOf(0, 2, 4, 6, 8), syllable(pieces, max = 100f))
    }

    @Test fun aBrokenWordLeavesNoLonePieceOnTheLastRow() {
        // あいうえ fills the first row; お alone would follow, so え comes down with it.
        assertEquals(listOf(0, 3, 5), syllable(group("あいうえお"), max = 80f))
        assertEquals(listOf(0, 3, 5), text(group("あいうえお"), max = 80f))
    }

    @Test fun aWholeWordMayStillEndALineOnItsOwn() {
        val pieces = listOf(word(40f), word(40f), word(10f))
        assertEquals(listOf(0, 2, 3), syllable(pieces, max = 90f, trailing = 0f))
    }

    @Test fun aGroupBrokenInsideStillKeepsPunctuationWithItsCharacter() {
        // う、 fills past the row; 、 may not start the next one, so う moves down with it.
        val pieces = group("あいう、えおかきくけこ")
        assertEquals(2, syllable(pieces, max = 70f)[1])
    }

    @Test fun leftAlignedWordsNeedRoomForTheirTrailingSpace() {
        val pieces = listOf(word(45f), word(45f), word(10f))
        // 45 + 5 + 45 = 95 fits, but the second word's own trailing 0.32ch makes it 100.
        assertEquals(listOf(0, 1, 3), syllable(pieces, max = 95f))
    }

    @Test fun theLastWordNeedsNoTrailingSpace() {
        assertEquals(listOf(0, 2), syllable(listOf(word(45f), word(45f)), max = 95f))
    }

    @Test fun oppositeAlignedLinesOnlyCountGapsBetweenWords() {
        val pieces = listOf(word(45f), word(45f), word(10f))
        assertEquals(listOf(0, 2, 3), syllable(pieces, max = 95f, trailing = 0f))
    }

    @Test fun plainTextBreaksBetweenCjkCharacters() {
        assertEquals(listOf(0, 3, 5), text(group("あいうえお"), max = 70f))
    }

    @Test fun plainTextNeverStartsARowWithClosingPunctuation() {
        // あいう fills the row, but 、 can't start the next one, so う moves down with it.
        assertEquals(listOf(0, 2, 5), text(group("あいう、え"), max = 70f))
    }

    @Test fun plainTextNeverEndsARowWithAnOpeningBracket() {
        assertEquals(listOf(0, 2, 5), text(group("あい「うえ"), max = 70f))
    }

    @Test fun plainTextKeepsLatinWordsWhole() {
        val pieces = listOf(word(40f, "hello"), word(40f, "there"), word(40f, "you"))
        assertEquals(listOf(0, 2, 3), text(pieces, max = 90f))
    }

    @Test fun aWholeLineTimedAsOneWordIsCutWhereItMayWrap() {
        // Apple Music times some Japanese lines as one span; romanized, it's several words in one.
        val romaji = "Kasanatta kioku ni mayoi ga kieru"
        val cuts = LyricsLayoutCalculator.overflowFragments(romaji)
        assertEquals(listOf("Kasanatta", "kioku", "ni", "mayoi", "ga", "kieru"), cuts.map { (a, b) -> romaji.substring(a, b) })
        // After a space each starts a word of its own, so the row breaks between them.
        val pieces = cuts.map { (a, b) -> LineWrapper.Piece((b - a) * 10f, romaji.substring(a, b), gluedToPrevious = false) }
        // "Kasanatta kioku" | "ni mayoi ga" | "kieru": every break between words.
        assertEquals(listOf(0, 2, 5, 6), syllable(pieces, max = 150f))
        // Kanji and kana are cut between characters, glued, so they break anywhere.
        val japanese = "重なった記憶 に"
        assertEquals(listOf("重", "な", "っ", "た", "記", "憶", "に"),
            LyricsLayoutCalculator.overflowFragments(japanese).map { (a, b) -> japanese.substring(a, b) })
    }

    @Test fun aSingleWordWiderThanARowIsCutBetweenLetters() {
        // Issue #24: one timed word with no spaces used to stay one piece and run off-screen.
        val held = "Wenggggggg"
        val fits = { s: String -> s.length * 10f <= 50f }
        val cuts = LyricsLayoutCalculator.wrapFragments(held, fits)
        assertEquals(held.map { it.toString() }, cuts.map { (a, b) -> held.substring(a, b) })
        val pieces = cuts.map { (a, b) -> LineWrapper.Piece(10f, held.substring(a, b), gluedToPrevious = a > 0) }
        assertEquals(listOf(0, 5, 10), syllable(pieces, max = 50f))
        assertEquals(listOf(0, 5, 10), text(pieces, max = 50f))
        // Fragments that fit a row stay whole; only the over-wide one is cut.
        val mixed = "oh Wenggggggg"
        assertEquals(listOf("oh") + "Wenggggggg".map { it.toString() },
            LyricsLayoutCalculator.wrapFragments(mixed, fits).map { (a, b) -> mixed.substring(a, b) })
    }

    @Test fun anOverlongLatinWordStillBreaksRatherThanOverflow() {
        val pieces = listOf(word(40f, "a")) + listOf(glued(40f, "b"), glued(40f, "c"), glued(40f, "d"))
        assertEquals(listOf(0, 2, 4), text(pieces, max = 90f))
    }
}
