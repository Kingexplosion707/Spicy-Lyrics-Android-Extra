package com.tx24.spicyplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupClientTest {
    @Test fun aWholeCallHasADeadline() {
        val client = NextLyricsBackend.lookupClient()
        assertEquals(NextLyricsBackend.LOOKUP_CALL_TIMEOUT_S * 1_000L, client.callTimeoutMillis.toLong())
        // Never shorter than a slow but steady answer: connecting, then one read wait.
        assertTrue(client.callTimeoutMillis >= client.connectTimeoutMillis + client.readTimeoutMillis)
    }
}
