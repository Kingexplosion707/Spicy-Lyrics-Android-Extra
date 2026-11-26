package com.tx24.spicyplayer.flipper

import com.fazecast.jSerialComm.SerialPort
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The computer's way to the Flipper: the second USB serial port the Spicy Lyrics app on it opens
 * (the first is the Flipper's own command line).
 *
 * The Flipper shows two ports only while that app (or another that opens the second port) runs,
 * so it looks only then, and says hello on each until one answers as Spicy Lyrics; the port that
 * answered is tried first the next time.
 */
internal class FlipperUsbLink : FlipperLink {
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "flipper-usb").apply { isDaemon = true } }
    @Volatile private var port: SerialPort? = null
    @Volatile private var running = false
    private var onLine: (String) -> Unit = {}
    private var onConnected: (Boolean) -> Unit = {}
    private var lastGood: String? = null

    override fun start(onLine: (String) -> Unit, onConnected: (Boolean) -> Unit) {
        this.onLine = onLine
        this.onConnected = onConnected
        running = true
        worker.scheduleWithFixedDelay({ runCatching { look() } }, 0, LOOK_EVERY_MS, TimeUnit.MILLISECONDS)
    }

    override fun stop() {
        running = false
        worker.shutdownNow()
        close()
    }

    override fun send(text: String) {
        val open = port ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        synchronized(this) {
            if (open.writeBytes(bytes, bytes.size) < 0) close()
        }
    }

    private fun flipperPorts(): List<SerialPort> = SerialPort.getCommPorts().filter {
        runCatching { it.vendorID == VENDOR && it.productID == PRODUCT }.getOrDefault(false)
    }

    private fun look() {
        if (!running) return
        val current = port
        if (current != null) {
            // Still plugged in and the app still open?
            if (flipperPorts().none { it.systemPortName == current.systemPortName }) close()
            return
        }
        val ports = flipperPorts()
        if (ports.size < 2) return
        val ordered = ports.sortedWith(compareByDescending<SerialPort> { it.systemPortName == lastGood }.thenByDescending { it.systemPortName })
        for (candidate in ordered) {
            if (!running) return
            if (hello(candidate)) return
        }
    }

    /** Opens [candidate] and says hello; keeps it when the Spicy Lyrics app answers. */
    private fun hello(candidate: SerialPort): Boolean {
        candidate.baudRate = 230_400
        candidate.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 0)
        if (!candidate.openPort()) return false
        candidate.setDTR()
        val greeting = FlipperProtocol.HELLO.toByteArray()
        candidate.writeBytes(greeting, greeting.size)
        val reader = LineReader(candidate)
        val deadline = System.currentTimeMillis() + ANSWER_MS
        while (System.currentTimeMillis() < deadline) {
            val line = reader.next() ?: continue
            if (FlipperProtocol.isAnswer(line)) {
                lastGood = candidate.systemPortName
                port = candidate
                onConnected(true)
                Thread({ listen(candidate, reader) }, "flipper-usb-read").apply { isDaemon = true }.start()
                return true
            }
        }
        candidate.closePort()
        return false
    }

    private fun listen(open: SerialPort, reader: LineReader) {
        while (running && port === open) {
            val line = try {
                reader.next()
            } catch (_: Exception) {
                break
            }
            if (line != null) onLine(line)
            if (!open.isOpen) break
        }
        if (port === open) close()
    }

    private fun close() {
        val open = port ?: return
        port = null
        runCatching { open.closePort() }
        onConnected(false)
    }

    /** Lines from [port], a read at a time. */
    private class LineReader(private val port: SerialPort) {
        private val buffer = StringBuilder()
        private val bytes = ByteArray(256)

        /** The next whole line, or null when none came within the port's timeout. */
        fun next(): String? {
            lineOut()?.let { return it }
            val n = port.readBytes(bytes, bytes.size)
            if (n < 0) throw java.io.IOException("Flipper port closed")
            if (n > 0) buffer.append(String(bytes, 0, n, Charsets.UTF_8))
            if (buffer.length > 4_000) buffer.setLength(0)
            return lineOut()
        }

        private fun lineOut(): String? {
            val end = buffer.indexOf("\n")
            if (end < 0) return null
            val line = buffer.substring(0, end).trimEnd('\r')
            buffer.delete(0, end + 1)
            return line.ifEmpty { null }
        }
    }

    private companion object {
        /** The Flipper Zero's USB serial ids. */
        const val VENDOR = 0x0483
        const val PRODUCT = 0x5740
        const val LOOK_EVERY_MS = 3_000L
        const val ANSWER_MS = 1_500L
    }
}
