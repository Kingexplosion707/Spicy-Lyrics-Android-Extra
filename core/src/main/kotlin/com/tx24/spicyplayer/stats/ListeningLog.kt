package com.tx24.spicyplayer.stats

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * What was listened to, kept on the phone only: per day and song, the time it played and how many
 * times it was played. A day is its number since 1970 in the phone's own time zone. Safe to use
 * from any thread.
 */
class ListeningLog {
    private class Entry(val title: String, val artist: String, var ms: Long, var plays: Int)

    /** One row of a chart: a song (with its artist) or an artist. */
    data class Row(val name: String, val detail: String, val ms: Long, val plays: Int)

    data class Summary(
        val ms: Long,
        val plays: Int,
        val songs: Int,
        val artists: Int,
        val topSongs: List<Row>,
        val topArtists: List<Row>,
    )

    private val days = sortedMapOf<Long, MutableMap<String, Entry>>()

    val isEmpty: Boolean @Synchronized get() = days.isEmpty()

    /** Adds [ms] of listening and [plays] plays of the song under [key] on [day]. */
    @Synchronized
    fun add(day: Long, key: String, title: String, artist: String, ms: Long, plays: Int) {
        if (ms <= 0 && plays <= 0) return
        val entry = days.getOrPut(day) { linkedMapOf() }.getOrPut(key) { Entry(title, artist, 0, 0) }
        entry.ms += ms.coerceAtLeast(0)
        entry.plays += plays.coerceAtLeast(0)
    }

    /** Drops the days before [firstDay]. */
    @Synchronized
    fun prune(firstDay: Long) {
        days.headMap(firstDay).clear()
    }

    @Synchronized
    fun clear() = days.clear()

    /** The totals and the charts for [fromDay]..[toDay], songs and artists ranked by time played. */
    @Synchronized
    fun summary(fromDay: Long, toDay: Long, top: Int = 10): Summary {
        val songs = LinkedHashMap<String, Entry>()
        for (day in days.subMap(fromDay, toDay + 1).values) {
            for ((key, entry) in day) {
                val sum = songs.getOrPut(key) { Entry(entry.title, entry.artist, 0, 0) }
                sum.ms += entry.ms
                sum.plays += entry.plays
            }
        }
        val artists = LinkedHashMap<String, Entry>()
        for (song in songs.values) {
            val name = leadArtist(song.artist)
            val sum = artists.getOrPut(name.lowercase()) { Entry(name, "", 0, 0) }
            sum.ms += song.ms
            sum.plays += song.plays
        }
        val byTime = compareByDescending<Entry> { it.ms }.thenByDescending { it.plays }
        return Summary(
            ms = songs.values.sumOf { it.ms },
            plays = songs.values.sumOf { it.plays },
            songs = songs.size,
            artists = artists.size,
            topSongs = songs.values.sortedWith(byTime).take(top).map { Row(it.title, it.artist, it.ms, it.plays) },
            topArtists = artists.values.sortedWith(byTime).take(top).map { Row(it.title, "", it.ms, it.plays) },
        )
    }

    @Synchronized
    fun toJson(): String {
        val root = JsonObject()
        root.addProperty("v", 1)
        val all = JsonObject()
        for ((day, songs) in days) {
            val one = JsonObject()
            for ((key, entry) in songs) {
                one.add(key, JsonObject().apply {
                    addProperty("t", entry.title)
                    addProperty("a", entry.artist)
                    addProperty("ms", entry.ms)
                    addProperty("n", entry.plays)
                })
            }
            all.add(day.toString(), one)
        }
        root.add("days", all)
        return root.toString()
    }

    companion object {
        /** Reads what [toJson] wrote; anything else gives an empty log. */
        fun fromJson(text: String): ListeningLog {
            val log = ListeningLog()
            runCatching {
                val all = JsonParser.parseString(text).asJsonObject.getAsJsonObject("days") ?: return log
                for ((day, songs) in all.entrySet()) {
                    val number = day.toLongOrNull() ?: continue
                    for ((key, value) in songs.asJsonObject.entrySet()) {
                        val entry = value.asJsonObject
                        log.add(number, key, entry.get("t").asString, entry.get("a").asString, entry.get("ms").asLong, entry.get("n").asInt)
                    }
                }
            }
            return log
        }

        /** The first artist of a credit like "A, B" or "A feat. B". */
        fun leadArtist(artist: String): String =
            artist.split(Regex("\\s*(,|;| feat\\.? | ft\\.? )\\s*", RegexOption.IGNORE_CASE)).firstOrNull()?.trim()
                .orEmpty().ifBlank { artist.trim().ifBlank { "Unknown artist" } }

        /** "3 h 5 min", "12 min" or "under a minute". */
        fun duration(ms: Long): String {
            val minutes = ms / 60_000
            return when {
                minutes < 1 -> "under a minute"
                minutes < 60 -> "$minutes min"
                else -> "${minutes / 60} h ${minutes % 60} min"
            }
        }
    }
}
