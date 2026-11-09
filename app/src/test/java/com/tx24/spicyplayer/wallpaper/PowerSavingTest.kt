package com.tx24.spicyplayer.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerSavingTest {
    /** When power saving (and with it Stop the haptics) applies: never, with the battery saver, or always. */
    @Test fun activeFollowsTheChoice() {
        assertEquals(listOf(false, false), listOf(false, true).map(PowerSaving.Off::active))
        assertEquals(listOf(false, true), listOf(false, true).map(PowerSaving.BatterySaver::active))
        assertEquals(listOf(true, true), listOf(false, true).map(PowerSaving.Always::active))
    }
}
