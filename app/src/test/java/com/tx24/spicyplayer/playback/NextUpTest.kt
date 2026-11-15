package com.tx24.spicyplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextUpTest {
    private fun entry(id: Long, current: Boolean = false) = QueueEntry(id, "t$id", "", null, null, current)

    @Test fun `the entry after the playing one is next`() {
        assertEquals(3L, NextUp.nextEntry(listOf(entry(1), entry(2, current = true), entry(3), entry(4)))?.id)
    }

    @Test fun `nothing follows the last song or an unmarked queue`() {
        assertNull(NextUp.nextEntry(listOf(entry(1), entry(2, current = true))))
        assertNull(NextUp.nextEntry(listOf(entry(1), entry(2))))
        assertNull(NextUp.nextEntry(emptyList()))
    }

    @Test fun `due only in the last thirty seconds`() {
        assertFalse(NextUp.due(100_000, 200_000))
        assertFalse(NextUp.due(169_999, 200_000))
        assertTrue(NextUp.due(170_000, 200_000))
        assertTrue(NextUp.due(200_000, 200_000))
    }

    @Test fun `an unknown length or a position past it is not due`() {
        assertFalse(NextUp.due(5_000, 0))
        assertFalse(NextUp.due(210_000, 200_000))
        assertFalse(NextUp.due(-1, 20_000))
    }
}
