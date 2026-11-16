package com.tx24.spicyplayer.network.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectPlaybackTest {
    private val player = """
        {"device":{"id":"abc123","name":"Living room","is_active":true},
         "is_playing":true,"progress_ms":42000,"timestamp":1700000000000,
         "item":{"type":"track","id":"4uLU6hMCjMI75M1A2tKUQC","name":"Never Gonna Give You Up","duration_ms":213573,
           "artists":[{"name":"Rick Astley"},{"name":"Someone Else"}],
           "album":{"name":"Whenever You Need Somebody","images":[
             {"url":"https://i.scdn.co/big","width":2000},
             {"url":"https://i.scdn.co/mid","width":640},
             {"url":"https://i.scdn.co/small","width":64}]}}}
    """.trimIndent()

    @Test fun `reads the song, position and device`() {
        val state = ConnectPlayback.parse(player)!!
        assertEquals("4uLU6hMCjMI75M1A2tKUQC", state.trackId)
        assertEquals("Never Gonna Give You Up", state.title)
        assertEquals("Rick Astley, Someone Else", state.artist)
        assertEquals("Whenever You Need Somebody", state.album)
        assertEquals(213_573L, state.durationMs)
        assertEquals(42_000L, state.progressMs)
        assertTrue(state.playing)
        assertEquals("abc123", state.deviceId)
        assertEquals("Living room", state.deviceName)
    }

    @Test fun `takes the biggest cover that is still a sensible size`() {
        assertEquals("https://i.scdn.co/mid", ConnectPlayback.parse(player)!!.coverUrl)
    }

    @Test fun `episodes, ads and empty answers are nothing to follow`() {
        assertNull(ConnectPlayback.parse(player.replace("\"type\":\"track\"", "\"type\":\"episode\"")))
        assertNull(ConnectPlayback.parse("""{"is_playing":true,"item":null,"currently_playing_type":"ad"}"""))
        assertNull(ConnectPlayback.parse(""))
        assertNull(ConnectPlayback.parse("not json"))
    }

    @Test fun `a paused state keeps its position`() {
        val state = ConnectPlayback.parse(player.replace("\"is_playing\":true", "\"is_playing\":false"))!!
        assertFalse(state.playing)
        assertEquals(42_000L, state.progressMs)
    }

    private fun state(progress: Long, playing: Boolean = true, id: String = "4uLU6hMCjMI75M1A2tKUQC") =
        ConnectState(id, "t", "a", "", 200_000L, null, playing, progress, null, null)

    @Test fun `a late but agreeing answer does not move the clock`() {
        val clock = ConnectClock()
        assertTrue(clock.accept(state(10_000), atMs = 1_000))
        // 3 s later the API says 13.4 s: within tolerance of what the clock already ran to.
        assertFalse(clock.accept(state(13_400), atMs = 4_000))
        assertEquals(13_000L, clock.positionAt(4_000, 200_000))
    }

    @Test fun `a seek, a pause and a new song move the clock`() {
        val clock = ConnectClock()
        clock.accept(state(10_000), atMs = 0)
        assertTrue(clock.accept(state(90_000), atMs = 3_000))
        assertEquals(90_000L, clock.positionAt(3_000, 200_000))
        assertTrue(clock.accept(state(93_100, playing = false), atMs = 6_000))
        // Paused: time passing doesn't advance it.
        assertEquals(93_100L, clock.positionAt(20_000, 200_000))
        assertTrue(clock.accept(state(0, id = "0VjIjW4GlUZAMYd2vXMi3b"), atMs = 21_000))
    }

    @Test fun `our own commands move the clock before Spotify answers`() {
        val clock = ConnectClock()
        clock.accept(state(10_000), atMs = 0)
        clock.setPlaying(false, atMs = 2_000)
        assertEquals(12_000L, clock.positionAt(9_000, 200_000))
        clock.jump(50_000, atMs = 9_000)
        assertEquals(50_000L, clock.positionAt(9_000, 200_000))
    }

    @Test fun `the position never runs past the end`() {
        val clock = ConnectClock()
        clock.accept(state(199_000), atMs = 0)
        assertEquals(200_000L, clock.positionAt(60_000, 200_000))
    }

    @Test fun `polling is quick near a song's end and slow when idle`() {
        val playing = state(10_000)
        assertEquals(3_000L, ConnectPolling.intervalMs(playing, 10_000, visible = true))
        assertEquals(6_000L, ConnectPolling.intervalMs(playing, 10_000, visible = false))
        assertEquals(1_400L, ConnectPolling.intervalMs(playing, 199_000, visible = true))
        assertEquals(12_000L, ConnectPolling.intervalMs(state(10_000, playing = false), 10_000, visible = false))
        assertEquals(20_000L, ConnectPolling.intervalMs(null, 0, visible = false))
    }

    @Test fun `a rate limit wait is kept in bounds`() {
        assertEquals(5_000L, ConnectPolling.retryAfterMs(null))
        assertEquals(2_000L, ConnectPolling.retryAfterMs("0"))
        assertEquals(120_000L, ConnectPolling.retryAfterMs("99999"))
        assertEquals(30_000L, ConnectPolling.retryAfterMs("30"))
    }
}
