package com.tx24.spicyplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class WrappingParensTest {
    @Test fun dropsOnlyParenthesesThatWrapTheText() {
        assertEquals("oh yeah", WrappingParens.strip("(oh yeah)"))
        assertEquals("oh yeah", WrappingParens.strip("(oh yeah"))
        assertEquals("oh yeah", WrappingParens.strip("oh yeah)"))
        assertEquals("(oh) yeah (yeah)", WrappingParens.strip("(oh) yeah (yeah)"))
        assertEquals("oh (yeah)", WrappingParens.strip("oh (yeah)"))
        assertEquals("oh (yeah) oh", WrappingParens.strip("(oh (yeah) oh)"))
    }

    @Test fun romanizationLosesItsEdgesOnly() {
        fun word(text: String, roman: String?) = TimedWord(text, 0, 1, attached = false, romanized = roman)
        val words = listOf(word("ねえ", "(nee "), word("ねえ", "nee)"))
        assertEquals(listOf("nee ", "nee"), WrappingParens.stripRomanization(words).map { it.romanized })
        val plain = listOf(word("oh", null))
        assertEquals(plain, WrappingParens.stripRomanization(plain))
    }
}
