package com.tx24.spicyplayer.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** The Lyrics Font setting. */
enum class LyricsFont(val label: String) {
    Default("Default"),
    System("System"),
    Custom("Custom"),
}

/**
 * A font file the user picked from their phone, copied into the app's own storage so it stays
 * readable. Each pick gets a new name: a font is cached by its path once loaded, so reusing one
 * would keep drawing the previous font.
 */
object LyricsFontFile {
    class Imported(val fileName: String, val displayName: String)

    private fun dir(context: Context) = File(context.filesDir, "fonts")

    /** Copies the font at [uri] in, or returns null when it isn't a font Android can read. */
    suspend fun import(context: Context, uri: Uri): Imported? = withContext(Dispatchers.IO) {
        // A provider can refuse the query outright (a revoked grant, say); the copy below then fails too.
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: "Custom font"
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, "lyrics-font-${System.currentTimeMillis()}")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        }.getOrNull() != null
        if (!copied || !isFont(file)) {
            file.delete()
            return@withContext null
        }
        // Only the newest pick is kept.
        dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
        Imported(file.name, displayName.substringBeforeLast('.').ifBlank { displayName })
    }

    /** The picked font as a family, or null when the file has gone. */
    fun family(context: Context, fileName: String): FontFamily? {
        if (fileName.isBlank()) return null
        val file = File(dir(context), fileName)
        if (!file.isFile) return null
        // A variable font has to be told each weight on its `wght` axis, or every weight draws
        // its default instance. A static one is declared once and bolded by the system.
        return if (isVariable(file)) {
            FontFamily(WEIGHTS.map { Font(file, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) })
        } else {
            FontFamily(Font(file, FontWeight.Normal))
        }
    }

    /** An OpenType/TrueType file (or collection) that Android loads as something of its own. */
    private fun isFont(file: File): Boolean {
        val tag = runCatching { RandomAccessFile(file, "r").use { it.readInt() } }.getOrNull() ?: return false
        if (tag !in FONT_TAGS) return false
        return runCatching { Typeface.createFromFile(file) }.getOrNull().let { it != null && it != Typeface.DEFAULT }
    }

    /** Whether the font's table directory lists an `fvar` table. Collections count as static. */
    private fun isVariable(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.readInt() == TAG_TTCF) return false
            val tables = raf.readUnsignedShort()
            raf.seek(12)
            repeat(tables) {
                if (raf.readInt() == TAG_FVAR) return true
                raf.skipBytes(12)
            }
            false
        }
    }.getOrDefault(false)

    private val WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black)
    private const val TAG_TTCF = 0x74746366 // "ttcf"
    private const val TAG_FVAR = 0x66766172 // "fvar"
    private val FONT_TAGS = setOf(0x00010000, 0x4F54544F /* OTTO */, 0x74727565 /* true */, TAG_TTCF)
}
