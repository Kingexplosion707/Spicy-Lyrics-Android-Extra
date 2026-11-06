package com.tx24.spicyplayer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugReportTest {
    @Test
    fun `blanks Spicy Lyrics keys`() {
        assertEquals("key sl_pk_… and sl_sk_…", redactSecrets("key sl_pk_abc123-XYZ and sl_sk_secret_9"))
    }

    @Test
    fun `blanks URL queries but keeps the address`() {
        assertEquals(
            "failed: https://apic.musixmatch.com/ws/1.1/macro.subtitles.get?… (timeout)",
            redactSecrets("failed: https://apic.musixmatch.com/ws/1.1/macro.subtitles.get?usertoken=abc&q=x (timeout)"),
        )
    }

    @Test
    fun `leaves ordinary text alone`() {
        val text = "LRCLIB: Not found (HTTP 404) · Song: What? · Artist"
        assertEquals(text, redactSecrets(text))
    }
}
