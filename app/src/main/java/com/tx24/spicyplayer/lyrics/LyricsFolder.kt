package com.tx24.spicyplayer.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * A folder of lyrics files (.ttml, .lrc, subfolders too) the user picked, read where it is, so
 * files synced or dropped into it are picked up. Its listing is kept in [indexFile] with each
 * file's songs, so a rescan reads only the files that are new or changed.
 */
class LyricsFolder(private val context: Context, private val indexFile: File) {
    data class Status(val name: String, val files: Int)

    private val prefs = context.getSharedPreferences("lyrics_folder", Context.MODE_PRIVATE)
    private val lock = Any()
    @Volatile private var files: List<LyricsFolderIndex.File> = emptyList()
    private var loaded = false

    val tree: Uri? get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)

    fun status(): Status? {
        val tree = tree ?: return null
        ensureLoaded()
        return Status(folderName(tree), files.size)
    }

    /** Keeps reading [tree] across restarts and makes it the folder. */
    fun link(tree: Uri) = synchronized(lock) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val old = this.tree
        prefs.edit().putString(KEY_TREE, tree.toString()).apply()
        if (old != null && old != tree) release(old)
        files = emptyList()
        loaded = true
        indexFile.delete()
    }

    fun unlink() = synchronized(lock) {
        tree?.let(::release)
        prefs.edit().remove(KEY_TREE).apply()
        files = emptyList()
        indexFile.delete()
    }

    /**
     * Lists the folder again, reading the files that are new or changed since the last listing.
     * Returns whether anything changed; false too when the folder can't be read.
     */
    fun rescan(): Boolean {
        val tree = tree ?: return false
        ensureLoaded()
        val known = files.associateBy { it.id }
        val listed = runCatching { list(tree) }.getOrNull() ?: return false
        var changed = listed.size != known.size
        val next = listed.map { (id, name, modified) ->
            known[id]?.takeIf { it.modified == modified && it.name == name } ?: run {
                changed = true
                val content = read(tree, id).orEmpty()
                LyricsFolderIndex.File(id, name, modified, LyricsFolderIndex.songsOf(name, content))
            }
        }
        if (changed) synchronized(lock) {
            files = next
            save(tree, next)
        }
        return changed
    }

    /** Whether the folder has a file for [title] by [artist]; reads only the listing. */
    fun has(title: String, artist: String): Boolean {
        if (tree == null) return false
        ensureLoaded()
        return LyricsFolderIndex.find(files, title, artist) != null
    }

    /** The playing song's lyrics from the folder, as TTML, or null. */
    fun lyrics(title: String, artist: String): String? {
        val tree = tree ?: return null
        ensureLoaded()
        val file = LyricsFolderIndex.find(files, title, artist) ?: return null
        val text = read(tree, file.id) ?: return null
        return if (file.name.endsWith(".lrc", ignoreCase = true) || LrcConverter.isLrc(text)) LrcConverter.toTtml(text) else text
    }

    private fun ensureLoaded() = synchronized(lock) {
        if (loaded) return
        loaded = true
        files = load(tree ?: return)
    }

    /** Every lyrics file under [tree]: (document ID, name, last modified). */
    private fun list(tree: Uri): List<Triple<String, String, Long>> {
        val out = mutableListOf<Triple<String, String, Long>>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (pending.isNotEmpty()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeFirst())
            context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1).orEmpty()
                    when {
                        cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR -> pending += id
                        LyricsFolderIndex.isLyricsFile(name) -> out += Triple(id, name, cursor.getLong(3))
                    }
                }
            } ?: error("Folder can't be read")
        }
        return out
    }

    private fun read(tree: Uri, id: String): String? = runCatching {
        context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))
            ?.use { it.readBytes().decodeToString().removePrefix("\uFEFF") }
    }.getOrNull()

    private fun folderName(tree: Uri): String =
        DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }

    private fun release(tree: Uri) {
        runCatching { context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    // A JSON tree, not reflection: the release build renames fields.
    private fun save(tree: Uri, files: List<LyricsFolderIndex.File>) {
        val array = JsonArray()
        files.forEach { file ->
            array.add(JsonObject().apply {
                addProperty("id", file.id)
                addProperty("name", file.name)
                addProperty("modified", file.modified)
                add("songs", JsonArray().apply {
                    file.songs.forEach { song -> add(JsonObject().apply { addProperty("title", song.title); addProperty("artist", song.artist) }) }
                })
            })
        }
        val temp = File(indexFile.path + ".tmp")
        runCatching {
            indexFile.parentFile?.mkdirs()
            temp.writeText(JsonObject().apply { addProperty("tree", tree.toString()); add("files", array) }.toString())
            if (!temp.renameTo(indexFile)) { indexFile.delete(); temp.renameTo(indexFile) }
        }
    }

    private fun load(tree: Uri): List<LyricsFolderIndex.File> = runCatching {
        val root = JsonParser.parseString(indexFile.readText()).asJsonObject
        if (root.get("tree")?.asString != tree.toString()) return emptyList()
        root.getAsJsonArray("files").map { element ->
            val o = element.asJsonObject
            LyricsFolderIndex.File(
                id = o.get("id").asString,
                name = o.get("name").asString,
                modified = o.get("modified").asLong,
                songs = o.getAsJsonArray("songs").map { s ->
                    val song = s.asJsonObject
                    LyricsFolderIndex.Song(song.get("title").asString, song.get("artist").asString)
                },
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_TREE = "tree"
    }
}
