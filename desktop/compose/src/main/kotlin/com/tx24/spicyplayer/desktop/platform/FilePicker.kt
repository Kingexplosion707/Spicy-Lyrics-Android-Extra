package com.tx24.spicyplayer.desktop.platform

import android.net.Uri
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/** The system's file dialogs, for the shared screens' "pick a file". */
object FilePicker {
    @Volatile var window: Frame? = null

    private val extensions = mapOf(
        "image" to listOf("png", "jpg", "jpeg", "webp", "gif", "bmp"),
        "font" to listOf("ttf", "otf"),
        "json" to listOf("json"), "text" to listOf("txt", "lrc", "ttml", "xml", "json"),
        "zip" to listOf("zip"),
    )

    fun open(mimes: Array<String>, done: (Uri?) -> Unit) = SwingUtilities.invokeLater {
        val d = FileDialog(window, "Open", FileDialog.LOAD)
        val exts = mimes.flatMap { m -> extensions.entries.filter { m.contains(it.key) }.flatMap { it.value } }
        if (exts.isNotEmpty() && "*/*" !in mimes) {
            d.setFilenameFilter { _, name -> exts.any { name.lowercase().endsWith(".$it") } }
            if (System.getProperty("os.name").startsWith("Windows")) d.file = exts.joinToString(";") { "*.$it" }
        }
        d.isVisible = true
        done(d.file?.let { Uri.fromFile(File(d.directory, it)) })
    }

    fun save(name: String, done: (Uri?) -> Unit) = SwingUtilities.invokeLater {
        val d = FileDialog(window, "Save", FileDialog.SAVE)
        d.file = name
        d.isVisible = true
        done(d.file?.let { Uri.fromFile(File(d.directory, it)) })
    }

    fun folder(done: (Uri?) -> Unit) = SwingUtilities.invokeLater {
        val c = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        done(if (c.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) Uri.fromFile(c.selectedFile) else null)
    }
}
