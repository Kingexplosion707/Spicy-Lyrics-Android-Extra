package com.tx24.spicyplayer.lyrics

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Lines of lyrics the user kept (Share lyrics → Save): a few lines each, with the song they're
 * from and when they were saved. Newest first. Kept on the phone only. Safe to use from any thread.
 */
class SavedLines {
    data class Quote(val id: Long, val lines: List<String>, val title: String, val artist: String, val savedAtMs: Long) {
        /** The lines with the song under them, as copied or shared. */
        fun asText(): String = lines.joinToString("\n") + "\n\n$title — $artist"
    }

    private val quotes = ArrayList<Quote>()

    val all: List<Quote> @Synchronized get() = quotes.toList()

    val size: Int @Synchronized get() = quotes.size

    /**
     * Keeps [lines] of the song; the same lines of the same song again move to the top instead of
     * being kept twice. Over [MAX] quotes, the oldest go. Returns the quote kept.
     */
    @Synchronized
    fun add(lines: List<String>, title: String, artist: String, nowMs: Long): Quote? {
        val kept = lines.map(String::trim).filter(String::isNotEmpty)
        if (kept.isEmpty()) return null
        quotes.removeAll { it.lines == kept && it.title == title && it.artist == artist }
        val id = (quotes.maxOfOrNull { it.id } ?: 0L) + 1
        val quote = Quote(id, kept, title, artist, nowMs)
        quotes.add(0, quote)
        while (quotes.size > MAX) quotes.removeAt(quotes.size - 1)
        return quote
    }

    @Synchronized
    fun remove(id: Long): Boolean = quotes.removeAll { it.id == id }

    @Synchronized
    fun clear() = quotes.clear()

    /** Built by hand rather than through Gson's reflection, which R8's renaming would break. */
    @Synchronized
    fun toJson(): String = JsonObject().apply {
        add("quotes", JsonArray().apply {
            quotes.forEach { quote ->
                add(JsonObject().apply {
                    addProperty("id", quote.id)
                    add("lines", JsonArray().apply { quote.lines.forEach(::add) })
                    addProperty("title", quote.title)
                    addProperty("artist", quote.artist)
                    addProperty("savedAt", quote.savedAtMs)
                })
            }
        })
    }.toString()

    companion object {
        const val MAX = 500

        /** Reads what [toJson] wrote; anything unreadable is left out rather than failing the lot. */
        fun fromJson(json: String?): SavedLines {
            val saved = SavedLines()
            val array = runCatching { JsonParser.parseString(json.orEmpty()).asJsonObject.getAsJsonArray("quotes") }.getOrNull()
                ?: return saved
            for (element in array) {
                runCatching {
                    val o = element.asJsonObject
                    val lines = o.getAsJsonArray("lines").map { it.asString }
                    if (lines.isNotEmpty()) {
                        saved.quotes += Quote(o.get("id").asLong, lines, o.get("title").asString, o.get("artist").asString, o.get("savedAt").asLong)
                    }
                }
            }
            return saved
        }
    }
}
