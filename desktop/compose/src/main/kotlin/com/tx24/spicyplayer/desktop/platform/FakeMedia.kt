package com.tx24.spicyplayer.desktop.platform

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * A pretend player for trying the app where Windows' media controls aren't (SPICY_FAKE_MEDIA set to
 * "Title|Artist|Album|durationMs|startMs").
 */
internal object FakeMedia {
    fun start(spec: String, apply: (JsonObject) -> Unit) {
        val p = spec.split('|')
        val started = System.currentTimeMillis() - (p.getOrNull(4)?.toLongOrNull() ?: 0L)
        Thread({
            var first = true
            while (true) {
                val o = JsonObject()
                val s = JsonObject().apply {
                    addProperty("id", "Spotify.exe"); addProperty("title", p.getOrElse(0) { "Song" }); addProperty("artist", p.getOrElse(1) { "Artist" })
                    addProperty("album", p.getOrElse(2) { "" }); addProperty("durationMs", p.getOrNull(3)?.toLongOrNull() ?: 200_000L)
                    addProperty("positionMs", 0L); addProperty("anchorMs", started); addProperty("isPlaying", true); addProperty("status", "Playing")
                    System.getenv("SPICY_FAKE_COVER")?.let { if (first) addProperty("cover", java.util.Base64.getEncoder().encodeToString(java.io.File(it).readBytes())) }
                }
                first = false
                o.add("sessions", JsonArray().apply { add(s) }); o.addProperty("current", "Spotify.exe")
                apply(o)
                Thread.sleep(2000)
            }
        }, "fake-media").apply { isDaemon = true }.start()
    }
}
