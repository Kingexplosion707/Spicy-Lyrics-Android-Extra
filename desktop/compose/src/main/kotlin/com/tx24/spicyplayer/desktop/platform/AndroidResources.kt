package com.tx24.spicyplayer.desktop.platform

import java.io.InputStream

/** The :app resources copied into the jar by the desktop build (android-res/). */
object AndroidResources {
    private class Entry(val type: String, val name: String)
    private val entries: Map<Int, Entry> by lazy {
        text("index.tsv").lines().filter { it.isNotBlank() }.associate { l -> l.split('\t').let { it[0].toInt() to Entry(it[1], it[2]) } }
    }
    private val byName: Map<String, Int> by lazy { entries.entries.associate { "${it.value.type}/${it.value.name}" to it.key } }
    private val strings: Map<String, String> by lazy {
        text("strings.tsv").lines().filter { it.isNotBlank() }.associate { l -> l.substringBefore('\t') to l.substringAfter('\t').replace("\\n", "\n") }
    }
    private fun text(name: String) = javaClass.getResourceAsStream("/android-res/$name")?.bufferedReader()?.readText() ?: ""

    fun string(id: Int): String = entries[id]?.let { strings[it.name] } ?: ""
    fun stringArray(id: Int): Array<String> = emptyArray()
    fun plural(id: Int, n: Int): String = ""
    fun color(id: Int): Int = 0xFF000000.toInt()
    fun identifier(type: String, name: String): Int = byName["$type/$name"] ?: 0
    fun name(id: Int): String? = entries[id]?.name

    /** A drawable, font or raw file by id. */
    fun open(id: Int): InputStream {
        val e = entries[id] ?: throw android.content.res.Resources.NotFoundException("#$id")
        for (ext in listOf("png", "webp", "jpg", "xml", "ttf", "otf", "json")) {
            javaClass.getResourceAsStream("/android-res/${e.type}/${e.name}.$ext")?.let { return it }
        }
        throw android.content.res.Resources.NotFoundException("${e.type}/${e.name}")
    }
    fun path(id: Int): String? = entries[id]?.let { e ->
        listOf("png", "webp", "jpg", "xml", "ttf", "otf").map { "android-res/${e.type}/${e.name}.$it" }.firstOrNull { javaClass.getResource("/$it") != null }
    }
}
