package com.tx24.spicyplayer.quotes

import android.content.Context
import com.tx24.spicyplayer.lyrics.SavedLines
import java.io.File

/**
 * The saved lines ([SavedLines]) on disk: one small file in the app's own storage, read once and
 * written after every change.
 */
internal object SavedLinesStore {
    private const val FILE = "saved_lines.json"
    @Volatile private var loaded: SavedLines? = null

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    /** The saved lines; reads the file the first time (off the main thread, for the caller to see to). */
    fun get(context: Context): SavedLines = loaded ?: synchronized(this) {
        loaded ?: SavedLines.fromJson(runCatching { file(context).readText() }.getOrNull()).also { loaded = it }
    }

    fun add(context: Context, lines: List<String>, title: String, artist: String): SavedLines.Quote? {
        val quote = get(context).add(lines, title, artist, System.currentTimeMillis())
        if (quote != null) write(context)
        return quote
    }

    fun remove(context: Context, id: Long) {
        if (get(context).remove(id)) write(context)
    }

    fun clear(context: Context) {
        get(context).clear()
        write(context)
    }

    private fun write(context: Context) = synchronized(this) {
        val saved = loaded ?: return@synchronized
        runCatching {
            val target = file(context)
            val temp = File(target.parentFile, "$FILE.tmp")
            temp.writeText(saved.toJson())
            if (!temp.renameTo(target)) {
                target.writeText(saved.toJson())
                temp.delete()
            }
        }
    }
}
