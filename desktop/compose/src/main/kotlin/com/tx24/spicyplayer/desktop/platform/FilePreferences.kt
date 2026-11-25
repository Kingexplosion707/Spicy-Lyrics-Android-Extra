package com.tx24.spicyplayer.desktop.platform

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

/** SharedPreferences kept as one JSON file per name, written in the background. */
class FilePreferences private constructor(private val file: File) : SharedPreferences {
    private val values = ConcurrentHashMap<String, Any>()
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        values.putAll(read())
    }

    private fun read(): Map<String, Any> = runCatching {
        val o = JsonParser.parseString(file.readText()).asJsonObject
        o.entrySet().associate { (k, v) ->
            val obj = v.asJsonObject
            k to when (obj["t"].asString) {
                "s" -> obj["v"].asString
                "i" -> obj["v"].asInt
                "l" -> obj["v"].asLong
                "f" -> obj["v"].asFloat
                "b" -> obj["v"].asBoolean
                else -> obj["v"].asJsonArray.map { it.asString }.toSet()
            }
        }
    }.getOrDefault(emptyMap())

    /** Takes in what another process wrote to the file, telling the listeners what changed. */
    internal fun reload() {
        val fresh = read()
        if (fresh.isEmpty() && file.exists()) return // caught mid-write
        val changed = (values.keys + fresh.keys).filter { values[it] != fresh[it] }
        if (changed.isEmpty()) return
        changed.forEach { k -> fresh[k]?.let { values[k] = it } ?: values.remove(k) }
        SwingUtilities.invokeLater { changed.forEach { k -> listeners.forEach { it.onSharedPreferenceChanged(this, k) } } }
    }

    override val all: Map<String, *> get() = HashMap(values)
    override fun getString(key: String, def: String?): String? = values[key] as? String ?: def
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, def: Set<String>?): Set<String>? = (values[key] as? Set<String>)?.toSet() ?: def
    override fun getInt(key: String, def: Int): Int = (values[key] as? Number)?.toInt() ?: def
    override fun getLong(key: String, def: Long): Long = (values[key] as? Number)?.toLong() ?: def
    override fun getFloat(key: String, def: Float): Float = (values[key] as? Number)?.toFloat() ?: def
    override fun getBoolean(key: String, def: Boolean): Boolean = values[key] as? Boolean ?: def
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun putStringSet(key: String, value: Set<String>?) = apply { puts[key] = value?.toSet() }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
        override fun remove(key: String) = apply { puts[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            val changed = mutableListOf<String?>()
            if (clear) { values.clear(); changed += null }
            puts.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v; changed += k }
            save()
            val notify = Runnable { changed.forEach { k -> listeners.forEach { it.onSharedPreferenceChanged(this@FilePreferences, k) } } }
            if (SwingUtilities.isEventDispatchThread()) notify.run() else SwingUtilities.invokeLater(notify)
        }
    }

    private fun save() {
        if (readOnly) return
        val snapshot = HashMap(values)
        writer.execute {
            val o = JsonObject()
            snapshot.forEach { (k, v) ->
                o.add(k, JsonObject().apply {
                    when (v) {
                        is String -> { addProperty("t", "s"); addProperty("v", v) }
                        is Int -> { addProperty("t", "i"); addProperty("v", v) }
                        is Long -> { addProperty("t", "l"); addProperty("v", v) }
                        is Float -> { addProperty("t", "f"); addProperty("v", v) }
                        is Boolean -> { addProperty("t", "b"); addProperty("v", v) }
                        is Set<*> -> { addProperty("t", "set"); add("v", Gson().toJsonTree(v.map { it.toString() })) }
                    }
                })
            }
            val tmp = File(file.path + ".tmp")
            tmp.writeText(o.toString())
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }
    }

    companion object {
        /**
         * Set in the wallpaper: it shows the app's settings but leaves the files to the app, and
         * follows the app's changes to them ([watch]).
         */
        @Volatile var readOnly = false
        private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "prefs").apply { isDaemon = false } }
        private val open = ConcurrentHashMap<String, FilePreferences>()
        fun named(name: String): FilePreferences = open.getOrPut(name) { FilePreferences(File(DesktopPlatform.dataDir("prefs"), "$name.json")) }
        /** Re-reads a file whenever another process (the app, beside the wallpaper) rewrites it. */
        fun watch() {
            val dir = DesktopPlatform.dataDir("prefs").toPath()
            Thread({
                runCatching {
                    val watcher = dir.fileSystem.newWatchService()
                    dir.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY)
                    while (true) {
                        val key = watcher.take()
                        val names = key.pollEvents().mapNotNull { (it.context() as? java.nio.file.Path)?.toString() }
                            .filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.toSet()
                        key.reset()
                        Thread.sleep(150)
                        names.forEach { open[it]?.reload() }
                    }
                }
            }, "prefs-watch").apply { isDaemon = true }.start()
        }
        /** Waits for pending writes (on quit). */
        fun flush() { writer.submit {}.get() }
    }
}
