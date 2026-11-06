package com.tx24.spicyplayer.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOffLookupTest {
    @Test
    fun `with the screen on it looks up at once and owes nothing`() {
        val lookup = ScreenOffLookup()
        assertTrue(lookup.request(screenOn = true))
        assertFalse(lookup.screenOn())
    }

    @Test
    fun `with the screen off it holds the lookup until the screen is on`() {
        val lookup = ScreenOffLookup()
        assertFalse(lookup.request(screenOn = false))
        assertTrue(lookup.held)
        assertTrue(lookup.screenOn())
        assertFalse(lookup.held)
    }

    @Test
    fun `several songs with the screen off make one lookup once it's on`() {
        val lookup = ScreenOffLookup()
        repeat(3) { assertFalse(lookup.request(screenOn = false)) }
        assertTrue(lookup.screenOn())
        assertFalse(lookup.screenOn())
    }

    @Test
    fun `lyrics that came another way cancel the held lookup`() {
        val lookup = ScreenOffLookup()
        lookup.request(screenOn = false)
        lookup.clear()
        assertFalse(lookup.screenOn())
    }

    @Test
    fun `a lookup made with the screen on settles one held before`() {
        val lookup = ScreenOffLookup()
        lookup.request(screenOn = false)
        assertTrue(lookup.request(screenOn = true))
        assertFalse(lookup.screenOn())
    }
}
