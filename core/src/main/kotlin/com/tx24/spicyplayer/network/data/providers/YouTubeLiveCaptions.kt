package com.tx24.spicyplayer.network.data.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tx24.spicyplayer.network.data.LyricsAttribution
import com.tx24.spicyplayer.network.data.LyricsCapability
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderFailureCategory
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.SourceReleaseChannel
import com.tx24.spicyplayer.network.data.awaitResponse
import com.tx24.spicyplayer.network.data.spotify.SpotifyTrackMatcher
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Live captions: for a song no lyrics source has, the captions of the matching YouTube video,
 * timed word by word. They're YouTube's automatic captions, what its speech recognition heard,
 * so a word can be wrong; a video with only its own captions gives those, timed by line. Never
 * part of the sources' ranking: the backend asks it only after every source came back empty.
 */
class YouTubeLiveCaptions(
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    val descriptor = LyricsSourceDescriptor(
        ID, NAME, Int.MAX_VALUE / 4,
        setOf(LyricsCapability.WORD_SYNC, LyricsCapability.LINE_SYNC),
        upstreamFamily = "youtube",
        releaseChannel = SourceReleaseChannel.EXPERIMENTAL,
        defaultEnabled = false,
    )

    suspend fun fetch(request: LyricsLookupRequest): ProviderResult {
        if (request.title.isBlank()) return ProviderResult.Miss
        return try {
            val matches = mutableListOf<Candidate>()
            for (id in search("${request.artist} ${request.title}".trim())) {
                val candidate = describe(id, request) ?: continue
                matches += candidate
                // As long as the song to the second: no other upload can line up better.
                if (candidate.lengthGap <= 1) break
            }
            for (candidate in matches.sortedBy { it.lengthGap }) {
                val ttml = captions(candidate) ?: continue
                return ProviderResult.Hit(
                    RemoteLyricsPayload(
                        ttmlLyrics = ttml,
                        sourceId = ID,
                        attribution = LyricsAttribution(NAME, originName = "YouTube"),
                    ),
                )
            }
            ProviderResult.Miss
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            ProviderResult.Unavailable(ProviderFailureCategory.NETWORK, error.message, true)
        } catch (error: Exception) {
            ProviderResult.Unavailable(ProviderFailureCategory.MALFORMED_RESPONSE, error.message)
        }
    }

    private class Candidate(val id: String, val lengthGap: Int, val client: Client, val track: JsonObject)

    private suspend fun search(query: String): List<String> {
        val url = "https://www.youtube.com/results".toHttpUrl().newBuilder().addQueryParameter("search_query", query).build()
        val page = client.newCall(Request.Builder().url(url).header("Accept-Language", "en").get().build()).awaitResponse().use {
            throttled(it.code)
            if (!it.isSuccessful) throw IOException("YouTube search HTTP ${it.code}")
            it.body?.string().orEmpty()
        }
        return VIDEO_ID.findAll(page).map { it.groupValues[1] }.distinct().take(MAX_CANDIDATES).toList()
    }

    /** The video [id] if it is this song (or this video), with the captions to read. */
    private suspend fun describe(id: String, request: LyricsLookupRequest): Candidate? {
        for (client in CLIENTS) {
            val player = player(id, client) ?: continue
            val details = player.getAsJsonObject("videoDetails") ?: continue
            val seconds = details.get("lengthSeconds")?.asString?.toIntOrNull() ?: 0
            val gap = if (seconds > 0 && request.durationSeconds > 0) abs(seconds - request.durationSeconds) else MAX_LENGTH_GAP
            if (seconds > 0 && request.durationSeconds > 0 && gap > MAX_LENGTH_GAP) return null
            if (!sameSong(details.get("title")?.asString.orEmpty(), details.get("author")?.asString.orEmpty(), request)) return null
            val tracks = player.getAsJsonObject("captions")?.getAsJsonObject("playerCaptionsTracklistRenderer")
                ?.getAsJsonArray("captionTracks")?.mapNotNull { it as? JsonObject }.orEmpty()
            // Automatic captions carry each word's time; a video's own captions only each line's.
            val track = tracks.firstOrNull { it.get("kind")?.asString == "asr" } ?: tracks.firstOrNull() ?: return null
            return Candidate(id, gap, client, track)
        }
        return null
    }

    private suspend fun player(id: String, client: Client): JsonObject? {
        val context = JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", client.name)
                addProperty("clientVersion", client.version)
                addProperty("hl", "en")
                addProperty("gl", "US")
                client.extra.forEach { (key, value) -> if (value is Int) addProperty(key, value) else addProperty(key, value.toString()) }
            })
        }
        val body = JsonObject().apply { add("context", context); addProperty("videoId", id) }.toString()
        return this.client.newCall(
            Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", client.userAgent)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build(),
        ).awaitResponse().use {
            throttled(it.code)
            if (!it.isSuccessful) return@use null
            runCatching { gson.fromJson(it.body?.string(), JsonObject::class.java) }.getOrNull()
        }
    }

    private suspend fun captions(candidate: Candidate): String? {
        val baseUrl = candidate.track.get("baseUrl")?.asString ?: return null
        val url = baseUrl.toHttpUrl().newBuilder().setQueryParameter("fmt", "json3").build()
        val root = client.newCall(Request.Builder().url(url).header("User-Agent", candidate.client.userAgent).get().build())
            .awaitResponse().use {
                throttled(it.code)
                if (!it.isSuccessful) return null
                // YouTube now and then answers with an empty body; the next upload may answer.
                val text = it.body?.string().orEmpty()
                if (text.isBlank()) return null
                runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
            } ?: return null
        return toTtml(root)
    }

    private fun throttled(code: Int) {
        if (code == 429) throw IOException("YouTube HTTP 429")
    }

    internal class Client(val name: String, val version: String, val userAgent: String, val extra: Map<String, Any>)

    companion object {
        const val ID = "youtube_live_captions"
        const val NAME = "Live captions"

        /** The most a video's length may differ from the song's: past it the words would land at the wrong time. */
        private const val MAX_LENGTH_GAP = 4
        private const val MAX_CANDIDATES = 6
        /** A line's last word, with nothing timed after it, is held at most this long. */
        private const val LAST_WORD_MS = 1_200L
        /** A caption line without word times lasts until the next, at most this long. */
        private const val LAST_LINE_MS = 6_000L
        private const val MAX_LINE_WORDS = 10
        /** A gap this long between two words' starts ends the line. */
        private const val PAUSE_MS = 1_800L
        private val SENTENCE_ENDS = setOf('.', '?', '!', '…')

        private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
        /** Sound cues and music notes: "[Music]", "[Applause]", "(upbeat music)", "♪". */
        private val CUES = Regex("""\[[^\]]*]|\([^)]*\b(?:music|instrumental|applause|cheering|laughs?)\b[^)]*\)|[♪♫]""", RegexOption.IGNORE_CASE)
        private val SPACES = Regex("""\s+""")

        private val CLIENTS = listOf(
            Client("IOS", "20.10.4", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_0 like Mac OS X)", mapOf("deviceModel" to "iPhone16,2")),
            Client(
                "ANDROID_VR", "1.62.27",
                "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                mapOf("androidSdkVersion" to 32, "deviceMake" to "Oculus", "deviceModel" to "Quest 3"),
            ),
            Client("ANDROID", "20.10.38", "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip", mapOf("androidSdkVersion" to 30)),
        )

        /**
         * Whether a video titled [title] by [author] is [request]: its title holds the song's (or
         * the other way round, for a video named only by its song), and the artist is in the title
         * or is the channel.
         */
        internal fun sameSong(title: String, author: String, request: LyricsLookupRequest): Boolean {
            val video = SpotifyTrackMatcher.normalize(title)
            val song = SpotifyTrackMatcher.normalize(request.title)
            if (song.isBlank() || video.isBlank()) return false
            if (!video.contains(song) && !(song.contains(video) && video.length >= 4)) return false
            val artist = SpotifyTrackMatcher.normalize(request.artist)
            if (artist.isBlank()) return true
            val channel = SpotifyTrackMatcher.normalize(author.removeSuffix(" - Topic"))
            val firstArtist = SpotifyTrackMatcher.normalize(request.artist.split(',', '&', '•').first())
            return video.contains(artist) || channel.contains(artist) || (firstArtist.isNotBlank() &&
                (video.contains(firstArtist) || channel.contains(firstArtist) || firstArtist.contains(channel) && channel.length >= 3))
        }

        /**
         * YouTube's json3 captions as TTML: automatic captions (with each word's offset) word by
         * word, others line by line. Sound cues are left out, and lines left with no words go.
         */
        internal fun toTtml(root: JsonObject): String? {
            val events = root.getAsJsonArray("events")?.mapNotNull { it as? JsonObject }.orEmpty()
                .filter { event -> event.has("tStartMs") && event.getAsJsonArray("segs")?.any { seg -> seg.asJsonObject.text().isNotBlank() } == true }
                .sortedBy { it.get("tStartMs").asLong }
            if (events.isEmpty()) return null
            val wordTimed = events.any { event -> event.getAsJsonArray("segs").any { (it as? JsonObject)?.has("tOffsetMs") == true } }
            val paragraphs = StringBuilder()
            if (wordTimed) {
                val perEvent = events.mapIndexed { index, event ->
                    words(event, event.get("tStartMs").asLong, events.getOrNull(index + 1)?.get("tStartMs")?.asLong)
                }.filter { line -> line.any { word -> word.text.any(Char::isLetterOrDigit) } }
                val lines = if (punctuated(perEvent)) reflow(perEvent) else perEvent
                lines.forEach { words ->
                    paragraphs.append("""<p begin="${seconds(words.first().start)}" end="${seconds(words.last().end)}" ttm:agent="v1">""")
                    words.forEachIndexed { i, word ->
                        if (i > 0 && word.spaceBefore) paragraphs.append(' ')
                        paragraphs.append("""<span begin="${seconds(word.start)}" end="${seconds(word.end)}">${escape(word.text)}</span>""")
                    }
                    paragraphs.append("</p>")
                }
            } else events.forEachIndexed { index, event ->
                val start = event.get("tStartMs").asLong
                val nextStart = events.getOrNull(index + 1)?.get("tStartMs")?.asLong
                val text = event.getAsJsonArray("segs").joinToString("") { (it as? JsonObject)?.text().orEmpty() }
                    .replace(CUES, " ").replace(SPACES, " ").trim()
                if (text.none(Char::isLetterOrDigit)) return@forEachIndexed
                val duration = event.get("dDurationMs")?.asLong
                val end = minOf(nextStart ?: Long.MAX_VALUE, start + (duration ?: LAST_LINE_MS), start + LAST_LINE_MS)
                    .coerceAtLeast(start + 1)
                paragraphs.append("""<p begin="${seconds(start)}" end="${seconds(end)}" ttm:agent="v1">${escape(text)}</p>""")
            }
            if (paragraphs.isEmpty()) return null
            return """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" """ +
                """xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="${if (wordTimed) "Word" else "Line"}">""" +
                """<head><metadata><ttm:agent type="person" xml:id="v1"/></metadata></head><body><div>$paragraphs</div></body></tt>"""
        }

        private class Word(val text: String, val start: Long, var end: Long, val spaceBefore: Boolean)

        /** Automatic captions with sentence punctuation, which then marks where lines end better than the caption windows do. */
        private fun punctuated(lines: List<List<Word>>): Boolean {
            val words = lines.sumOf { it.size }
            val ends = lines.sumOf { line -> line.count { it.text.last() in SENTENCE_ENDS } }
            return ends >= 3 && ends * 40 >= words
        }

        /**
         * The words broken into lines at sentence ends, at pauses, and before a line grows past
         * [MAX_LINE_WORDS]; each line's last word then runs to the next line, held at most [LAST_WORD_MS].
         */
        private fun reflow(windows: List<List<Word>>): List<List<Word>> {
            // A caption window's first word follows the last one's after a space.
            val words = windows.flatMapIndexed { w, window ->
                window.mapIndexed { i, word -> if (w > 0 && i == 0 && !word.spaceBefore) Word(word.text, word.start, word.end, true) else word }
            }
            val lines = mutableListOf<List<Word>>()
            var line = mutableListOf<Word>()
            words.forEachIndexed { i, word ->
                line += word
                val next = words.getOrNull(i + 1)
                val sentenceEnd = word.text.last() in SENTENCE_ENDS && line.size >= 2
                val pause = next != null && next.start - word.start > PAUSE_MS
                if (next == null || sentenceEnd || pause || line.size >= MAX_LINE_WORDS) {
                    lines += line
                    line = mutableListOf()
                }
            }
            lines.forEachIndexed { l, current ->
                val nextLine = lines.getOrNull(l + 1)?.first()?.start
                current.forEachIndexed { i, word ->
                    val end = current.getOrNull(i + 1)?.start ?: minOf(nextLine ?: Long.MAX_VALUE, word.start + LAST_WORD_MS)
                    word.end = end.coerceAtLeast(word.start + 1)
                }
            }
            return lines
        }

        /** One caption line's words: each runs to the next word, the last to the next line (held at most [LAST_WORD_MS]). */
        private fun words(event: JsonObject, start: Long, nextStart: Long?): List<Word> {
            val words = mutableListOf<Word>()
            var spaceBefore = false
            for (raw in event.getAsJsonArray("segs")) {
                val seg = raw as? JsonObject ?: continue
                val text = seg.text().replace('\n', ' ')
                val cleaned = text.replace(CUES, " ")
                if (cleaned.isBlank()) {
                    if (text.isNotEmpty()) spaceBefore = true
                    continue
                }
                val at = start + (seg.get("tOffsetMs")?.asLong ?: 0L)
                words += Word(cleaned.replace(SPACES, " ").trim(), at, at, spaceBefore || cleaned.first().isWhitespace())
                spaceBefore = cleaned.last().isWhitespace()
            }
            words.forEachIndexed { i, word ->
                val following = words.getOrNull(i + 1)?.start
                val end = following ?: minOf(nextStart ?: Long.MAX_VALUE, word.start + LAST_WORD_MS)
                word.end = end.coerceAtLeast(word.start + 1)
            }
            return words
        }

        private fun JsonObject.text(): String = get("utf8")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

        private fun seconds(ms: Long) = String.format(Locale.ROOT, "%d.%03d", ms / 1_000, ms % 1_000)

        private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
