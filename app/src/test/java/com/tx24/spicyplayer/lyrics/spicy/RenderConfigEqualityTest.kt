package com.tx24.spicyplayer.lyrics.spicy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The lyrics view keys its layout cache on a config copied with a fresh `ScrollConfig()` on every
 * recomposition. If two default scroll configs ever differ, every recomposition measures the whole
 * lyrics again and keeps the result.
 */
class RenderConfigEqualityTest {
    @Test
    fun `two default scroll configs are equal`() {
        assertEquals(ScrollConfig(), ScrollConfig())
        assertEquals(ScrollConfig().hashCode(), ScrollConfig().hashCode())
    }

    @Test
    fun `the default pace is one shared instance`() {
        assertSame(ScrollConfig().staticPace, ScrollConfig().staticPace)
    }

    @Test
    fun `a config copied with a fresh default scroll equals the last one`() {
        val config = RenderConfig(bloom = true)
        assertEquals(config.copy(scroll = ScrollConfig()), config.copy(scroll = ScrollConfig()))
    }
}
