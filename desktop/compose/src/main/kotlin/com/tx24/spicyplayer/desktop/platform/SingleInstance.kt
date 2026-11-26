package com.tx24.spicyplayer.desktop.platform

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * One app at a time: the first start listens on a local port (written to a file); a later start
 * (opening a spicylyricsplus:// link, or the shortcut again) sends its arguments there and ends.
 */
object SingleInstance {
    @Volatile var onArguments: (String) -> Unit = {}
    private val portFile get() = File(DesktopPlatform.dataDir("helper"), "instance.port")

    fun claim(args: Array<String>): Boolean {
        runCatching {
            val port = portFile.readText().trim().toInt()
            Socket(InetAddress.getLoopbackAddress(), port).use { s ->
                s.soTimeout = 1500
                s.getOutputStream().bufferedWriter().apply { write((args.toList() + "--show").joinToString("\n") + "\n\u0000\n"); flush() }
                if (s.getInputStream().read() == 'K'.code) return false
            }
        }
        val server = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return true
        portFile.writeText(server.localPort.toString())
        Thread({
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    s.use {
                        val lines = it.getInputStream().bufferedReader().lineSequence().takeWhile { l -> l != "\u0000" }.toList()
                        it.getOutputStream().write('K'.code); it.getOutputStream().flush()
                        javax.swing.SwingUtilities.invokeLater { lines.forEach(onArguments) }
                    }
                }
            }
        }, "single-instance").apply { isDaemon = true }.start()
        return true
    }
}
