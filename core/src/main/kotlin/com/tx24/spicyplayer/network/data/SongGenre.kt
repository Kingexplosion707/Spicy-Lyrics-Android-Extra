package com.tx24.spicyplayer.network.data

import com.google.gson.JsonParser
import com.tx24.spicyplayer.network.motion.MotionCoverFinder
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** The broad kind of music a song is, as far as picking a look for it goes. */
enum class SongGenre {
    Pop, HipHop, RnB, Rock, Metal, Alternative, Electronic, Country, Latin, AsianPop,
    Jazz, Classical, Reggae, Gospel;

    companion object {
        /**
         * The family of a store genre name ("Hip-Hop/Rap", "Hard Rock", "K-Pop"); null for one
         * that says nothing about the sound (Soundtrack is classical-leaning, Children's Music
         * is pop).
         */
        fun of(name: String?): SongGenre? {
            val genre = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            fun has(vararg words: String) = words.any { it in genre }
            return when {
                has("k-pop", "j-pop", "kpop", "jpop", "c-pop", "mandopop", "cantopop", "anime", "j-rock", "k-rock", "vocaloid") -> AsianPop
                has("metal", "hardcore", "grindcore", "deathcore") -> Metal
                has("hip-hop", "hip hop", "rap", "trap", "drill", "grime") -> HipHop
                has("reggaeton", "latin", "urbano", "salsa", "bachata", "cumbia", "regional mexican", "sertanejo", "pop latino", "tropical", "flamenco", "brazilian", "mpb", "samba", "bossa") -> Latin
                has("r&b", "rnb", "soul", "funk", "neo-soul", "motown", "disco") -> RnB
                has("electronic", "dance", "house", "techno", "trance", "edm", "dubstep", "drum & bass", "drum and bass", "electronica", "garage", "hardstyle", "synthwave", "idm", "breakbeat") -> Electronic
                has("reggae", "dancehall", "ska", "dub", "afrobeat", "afrobeats", "afro-pop", "afropop", "amapiano", "soca", "calypso") -> Reggae
                has("gospel", "christian", "worship", "praise", "inspirational") -> Gospel
                has("jazz", "blues", "swing", "bebop", "big band", "lounge") -> Jazz
                has("classical", "soundtrack", "score", "opera", "orchestral", "instrumental", "new age", "ambient", "piano") -> Classical
                has("country", "folk", "americana", "bluegrass", "singer/songwriter", "singer-songwriter", "acoustic") -> Country
                has("alternative", "indie", "punk", "emo", "grunge", "shoegaze", "lo-fi", "lofi", "post-rock") -> Alternative
                has("rock") -> Rock
                has("pop", "children", "teen", "vocal", "holiday", "christmas") -> Pop
                else -> null
            }
        }
    }
}

/**
 * A song's genre from the iTunes Search API (its `primaryGenreName`), no key needed. Best
 * effort: any failure, or no matching song, is null.
 */
class ItunesGenre(private val client: OkHttpClient) {
    suspend fun find(title: String, artist: String): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val body = try {
            client.newCall(
                Request.Builder().url("https://itunes.apple.com/search?entity=song&limit=10&term=${encode("$artist $title")}").build(),
            ).awaitResponse().use { response ->
                if (!response.isSuccessful) return null
                withContext(Dispatchers.IO) { response.body.string() }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        return pick(parse(body), title, artist)
    }

    data class Song(val title: String, val artist: String, val genre: String)

    companion object {
        /** The genre of the same song by the same artist; else of the artist's songs found, the most common. */
        fun pick(songs: List<Song>, title: String, artist: String): String? {
            val byArtist = songs.filter { MotionCoverFinder.loose(artist, it.artist) && it.genre.isNotBlank() }
            byArtist.firstOrNull { MotionCoverFinder.loose(MotionCoverFinder.plain(title), MotionCoverFinder.plain(it.title)) }
                ?.let { return it.genre }
            return byArtist.groupingBy { it.genre }.eachCount().maxByOrNull { it.value }?.key
        }

        fun parse(json: String): List<Song> = runCatching {
            JsonParser.parseString(json).asJsonObject.getAsJsonArray("results").mapNotNull { e ->
                val o = e.asJsonObject
                Song(
                    title = o.get("trackName")?.asString.orEmpty(),
                    artist = o.get("artistName")?.asString.orEmpty(),
                    genre = o.get("primaryGenreName")?.asString ?: return@mapNotNull null,
                )
            }
        }.getOrDefault(emptyList())

        private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}
