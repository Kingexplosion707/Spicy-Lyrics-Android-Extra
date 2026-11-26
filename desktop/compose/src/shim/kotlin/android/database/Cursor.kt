package android.database

import java.io.File

/** Just enough of a cursor for "what is this file called and how big is it". */
open class Cursor private constructor(private val file: File) : java.io.Closeable {
    private var pos = -1
    fun moveToFirst(): Boolean { pos = 0; return true }
    fun moveToNext(): Boolean = ++pos == 0
    val count: Int get() = 1
    fun getColumnIndex(name: String): Int = when (name) { "_display_name" -> 0; "_size" -> 1; else -> -1 }
    fun getColumnIndexOrThrow(name: String): Int = getColumnIndex(name).also { require(it >= 0) }
    fun getString(i: Int): String? = when (i) { 0 -> file.name; 1 -> file.length().toString(); else -> null }
    fun getLong(i: Int): Long = if (i == 1) file.length() else 0
    fun getInt(i: Int): Int = getLong(i).toInt()
    fun isNull(i: Int) = i !in 0..1
    override fun close() {}
    companion object { fun forFile(f: File): Cursor = Cursor(f) }
}
