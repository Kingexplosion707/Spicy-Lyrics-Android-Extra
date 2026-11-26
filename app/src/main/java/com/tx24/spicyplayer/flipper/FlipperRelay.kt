package com.tx24.spicyplayer.flipper

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** A way to a Flipper Zero running Spicy Lyrics: Bluetooth from the phone, USB from the computer. */
internal interface FlipperLink {
    /** Starts looking for the Flipper; [onLine] hears each line it sends, [onConnected] when it comes and goes. */
    fun start(onLine: (String) -> Unit, onConnected: (Boolean) -> Unit)
    /** Sends text to the Flipper, when connected. Any thread. */
    fun send(text: String)
    fun stop()
}

/**
 * Keeps a Flipper showing what plays: asks [snapshot] a few times a second while a Flipper is
 * connected, sends what changed ([FlipperFeed]), and passes its buttons to [controls].
 * Main thread only.
 */
internal class FlipperRelay(
    private val link: FlipperLink,
    private val snapshot: () -> FlipperFeed.Snapshot,
    private val controls: (FlipperProtocol.Command) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val feed = FlipperFeed()
    private var connected = false
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running || !connected) return
            send()
            main.postDelayed(this, TICK_MS)
        }
    }

    /** Whether a Flipper is connected now. */
    var onConnectedChanged: (Boolean) -> Unit = {}

    fun start() {
        if (running) return
        running = true
        link.start(
            onLine = { line -> main.post { heard(line) } },
            onConnected = { now -> main.post { connectedChanged(now) } },
        )
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
        link.stop()
        connected = false
    }

    /** Sends a change at once, without waiting for the next tick (a new song, new lyrics). */
    fun nudge() {
        if (running && connected) send()
    }

    private fun connectedChanged(now: Boolean) {
        if (!running || now == connected) return
        connected = now
        onConnectedChanged(now)
        main.removeCallbacks(tick)
        if (now) {
            feed.reset()
            link.send(FlipperProtocol.HELLO)
            main.post(tick)
        }
    }

    private fun heard(line: String) {
        if (!running) return
        if (FlipperProtocol.isAnswer(line)) {
            // A Flipper that opened the app again: it has nothing on screen.
            feed.reset()
            send()
            return
        }
        FlipperProtocol.command(line)?.let { command ->
            controls(command)
            // The player answers in a moment; show it once it has.
            main.postDelayed({ nudge() }, 350L)
        }
    }

    private fun send() {
        val messages = runCatching { feed.messages(snapshot(), SystemClock.elapsedRealtime()) }.getOrDefault(emptyList())
        messages.forEach(link::send)
    }

    private companion object {
        const val TICK_MS = 200L
    }
}
