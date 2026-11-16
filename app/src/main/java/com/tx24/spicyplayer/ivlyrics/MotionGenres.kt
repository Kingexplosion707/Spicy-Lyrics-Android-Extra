package com.tx24.spicyplayer.ivlyrics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * An artist's genres, for motion lyrics' Auto theme: MusicBrainz's tags (most used first), else
 * the Deezer album genre of the song. Asked once per artist and kept, so a song costs nothing
 * the second time.
 */
internal object MotionGenres {
    private const val PREFS = "motion_genres"
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    }

    suspend fun of(context: Context, title: String, artist: String): List<String> = withContext(Dispatchers.IO) {
        val name = artist.split(",", "&", " feat", " ft.", " x ").first().trim()
        if (name.isEmpty()) return@withContext emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = name.lowercase()
        prefs.getString(key, null)?.let { saved -> return@withContext saved.split('|').filter { it.isNotBlank() } }
        val found = runCatching { musicBrainz(name) }.getOrNull().orEmpty()
            .ifEmpty { runCatching { deezer(title, name) }.getOrNull().orEmpty() }
        // An empty answer is kept too, so an artist nobody tags isn't asked about every song.
        prefs.edit().putString(key, found.joinToString("|")).apply()
        found
    }

    private fun get(url: String): String? = client.newCall(
        Request.Builder().url(url).header("User-Agent", "SpicyLyricsPlus/1.0 ( https://github.com/Kingexplosion707/Spicy-Lyrics-Android-Extra )").build(),
    ).execute().use { if (it.isSuccessful) it.body?.string() else null }

    private fun musicBrainz(artist: String): List<String> {
        val body = get("https://musicbrainz.org/ws/2/artist/?fmt=json&limit=1&query=" + Uri.encode("artist:\"$artist\"")) ?: return emptyList()
        val first = JSONObject(body).optJSONArray("artists")?.optJSONObject(0) ?: return emptyList()
        if (first.optInt("score", 0) < 80) return emptyList()
        val tags = first.optJSONArray("tags") ?: return emptyList()
        return (0 until tags.length()).mapNotNull { tags.optJSONObject(it) }
            .sortedByDescending { it.optInt("count", 0) }
            .mapNotNull { it.optString("name").takeIf(String::isNotBlank) }
    }

    private fun deezer(title: String, artist: String): List<String> {
        val search = get("https://api.deezer.com/search?limit=1&q=" + Uri.encode("artist:\"$artist\" track:\"$title\"")) ?: return emptyList()
        val albumId = JSONObject(search).optJSONArray("data")?.optJSONObject(0)?.optJSONObject("album")?.optLong("id") ?: return emptyList()
        val album = get("https://api.deezer.com/album/$albumId") ?: return emptyList()
        val genres = JSONObject(album).optJSONObject("genres")?.optJSONArray("data") ?: return emptyList()
        return (0 until genres.length()).mapNotNull { genres.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
    }
}
