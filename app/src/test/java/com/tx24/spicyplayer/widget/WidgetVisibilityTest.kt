package com.tx24.spicyplayer.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetVisibilityTest {
    @Test
    fun `seen, it sends as asked`() {
        val visibility = WidgetVisibility()
        assertEquals(false, visibility.send(full = false))
        assertEquals(true, visibility.send(full = true))
    }

    @Test
    fun `with the screen off nothing is sent and the next send is full`() {
        val visibility = WidgetVisibility()
        visibility.screenOn = false
        assertNull(visibility.send(full = false))
        assertNull(visibility.send(full = true))
        assertTrue(visibility.missed)
        visibility.screenOn = true
        assertEquals(true, visibility.send(full = false))
        assertFalse(visibility.missed)
        assertEquals(false, visibility.send(full = false))
    }

    @Test
    fun `under the lyrics screen nothing is sent until it leaves`() {
        val visibility = WidgetVisibility()
        visibility.appCovering = true
        assertFalse(visibility.visible)
        assertNull(visibility.send(full = false))
        visibility.appCovering = false
        assertEquals(true, visibility.send(full = false))
    }

    @Test
    fun `the screen coming on under the lyrics screen still sends nothing`() {
        val visibility = WidgetVisibility()
        visibility.screenOn = false
        visibility.appCovering = true
        assertNull(visibility.send(full = false))
        visibility.screenOn = true
        assertNull(visibility.send(full = false))
        visibility.appCovering = false
        assertEquals(true, visibility.send(full = false))
    }

    @Test
    fun `nothing missed, nothing owed`() {
        val visibility = WidgetVisibility()
        visibility.appCovering = true
        visibility.appCovering = false
        assertFalse(visibility.missed)
        assertEquals(false, visibility.send(full = false))
    }
}
