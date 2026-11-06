package com.tx24.spicyplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectSearchTest {
    private val codes = listOf("tl", "fr", "pt", "es", "zh", "nb")
    private val names = listOf("Filipino", "French", "Portuguese (Brazil)", "Español", "Chinese", "Norwegian Bokmål")

    private fun find(query: String) = matchingOptions(query, codes, names).map { codes[it] }

    @Test fun `a blank query lists everything`() {
        assertEquals(codes, find(""))
        assertEquals(codes, find("   "))
    }

    @Test fun `names match from the start of a word, ignoring case`() {
        assertEquals(listOf("tl", "fr"), find("f"))
        assertEquals(listOf("tl"), find("FIL"))
        assertEquals(listOf("pt"), find("brazil"))
        assertEquals(emptyList<String>(), find("ilipino"))
    }

    @Test fun `codes match too`() {
        assertEquals(listOf("tl"), find("tl"))
        assertEquals(listOf("zh"), find("zh"))
    }

    @Test fun `accents don't matter either way`() {
        assertEquals(listOf("es"), find("espanol"))
        assertEquals(listOf("nb"), find("bokmal"))
        assertEquals(listOf("es"), find("Españ"))
    }

    @Test fun `every word has to match`() {
        assertEquals(listOf("pt"), find("port bra"))
        assertEquals(emptyList<String>(), find("port french"))
    }
}
