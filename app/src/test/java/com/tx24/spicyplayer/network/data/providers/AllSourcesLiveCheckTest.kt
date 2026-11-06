package com.tx24.spicyplayer.network.data.providers

import com.google.gson.Gson
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.lyrics.NextLyricsBackend
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.measuredQuality
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Asks every source the app uses for a few well-known songs in different languages and prints
 * what each answered. Fails when a source answered none of them. Live network, so it skips
 * unless `RUN_ALL_SOURCES_TEST=1`; run it with `--info` to see the table. `ALL_SOURCES_ONLY` (ids,
 * comma-separated) narrows it to some sources.
 */
class AllSourcesLiveCheckTest {
    @Test
    fun `every source answers at least one well known song`() = runBlocking {
        assumeTrue(System.getenv("RUN_ALL_SOURCES_TEST") == "1")
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
        val key = System.getenv("SPICY_LYRICS_CLIENT_KEY") ?: BuildConfig.SPICY_LYRICS_CLIENT_KEY
        // ALL_SOURCES_ONLY=kuwo,lrc_red asks just those.
        val only = System.getenv("ALL_SOURCES_ONLY")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        val providers = NextLyricsBackend.createProviders(client, Gson(), key).filter { only.isEmpty() || it.descriptor.id in only }

        val rows = providers.map { provider ->
            async {
                provider.descriptor.id to SONGS.map { song ->
                    val started = System.nanoTime()
                    val result = runCatching { withTimeoutOrNull(30_000) { provider.fetch(song) } }
                        .getOrElse { ProviderResult.Unavailable(com.tx24.spicyplayer.network.data.ProviderFailureCategory.UNKNOWN, it.toString()) }
                        // As the app shows them: without the catalogues' title cards and credits.
                        .let { r ->
                            if (r is ProviderResult.Hit && provider.descriptor.id in com.tx24.spicyplayer.network.data.RemoteLyricsSource.FURNISHED)
                                r.copy(payload = com.tx24.spicyplayer.network.data.blend.LyricsFurniture.clean(r.payload, song.title, song.artist))
                            else r
                        }
                    val ms = (System.nanoTime() - started) / 1_000_000
                    result to ms
                }
            }
        }.awaitAll()

        val silent = mutableListOf<String>()
        rows.sortedBy { it.first }.forEach { (id, results) ->
            println("── $id")
            results.forEachIndexed { i, (result, ms) ->
                val text = when (result) {
                    is ProviderResult.Hit -> "HIT ${result.payload.measuredQuality()}" + (result.payload.attribution?.originName?.let { " from $it" } ?: "") +
                        // The first sung line, to tell another song's lyrics from these.
                        (com.tx24.spicyplayer.network.data.blend.BlendDocuments.from(result.payload)?.lines
                            ?.map(com.tx24.spicyplayer.network.data.blend.BlendText::lineText)
                            ?.firstOrNull { it.isNotBlank() && !com.tx24.spicyplayer.network.data.blend.BlendDonors.isCredit(it) }
                            ?.let { " | ${it.take(40)}" } ?: "") +
                        dotsOverWords(id, result, SONGS[i]).let { if (it > 0) "  <- $it interlude(s) over sung words" else "" }
                    null -> "TIMEOUT"
                    is ProviderResult.Unavailable -> "UNAVAILABLE ${result.category} ${result.message.orEmpty().take(120)}"
                    else -> result.toString()
                }
                println("   %-40s %6d ms  %s".format(SONGS[i].title.take(40), ms, text))
            }
            if (results.none { it.first is ProviderResult.Hit }) silent += id
        }
        printTiming(rows.toMap())
        assertTrue("No hits at all from: $silent", silent.isEmpty())
    }

    /**
     * Interlude dots the lyrics screen would show while words are sung: what a line whose own
     * times don't cover its words does, and the scroll waits on the dots meanwhile.
     */
    private fun dotsOverWords(id: String, hit: ProviderResult.Hit, song: LyricsLookupRequest): Int {
        val descriptor = NextLyricsBackend.createProviders(OkHttpClient(), Gson(), "").firstOrNull { it.descriptor.id == id }?.descriptor ?: return 0
        val ready = runCatching {
            com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter.render(
                com.tx24.spicyplayer.network.data.RemoteLyricsSelection(descriptor, hit.payload, hit.payload.measuredQuality()), song.durationSeconds * 1000L)
        }.getOrNull() ?: return 0
        val lines = ready.lines.map { l ->
            com.tx24.spicyplayer.lyrics.spicy.models.Line(
                words = l.words.map { com.tx24.spicyplayer.lyrics.spicy.models.Word(it.text, it.startMs, it.endMs, isPartOfWord = it.attached) },
                startMs = l.startMs, endMs = l.endMs, agent = l.agent, role = l.role, groupId = l.groupId,
                oppositeAligned = l.oppositeAligned, lineTimed = l.lineTimed)
        }
        return com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline(lines, minimalMode = false).filter { it.isInterlude }
            .count { dots -> lines.any { l -> l.words.any { w -> w.startMs < dots.endMs - 300 && w.endMs > dots.startMs + 300 } } }
    }

    /**
     * How each timed answer lines up with a trusted one for the same song (Spicy Lyrics, then AMLL,
     * then lrc.red): lines matched by their words, in order, then the median offset (early or late)
     * and the spread around it (drift, or lines out of place). Flags what's off by over half a second.
     */
    private fun printTiming(rows: Map<String, List<Pair<ProviderResult?, Long>>>) {
        fun timed(result: ProviderResult?): List<Pair<String, Double>>? =
            (result as? ProviderResult.Hit)?.let { com.tx24.spicyplayer.network.data.blend.BlendDocuments.from(it.payload) }?.lines
                ?.mapNotNull { line ->
                    val key = com.tx24.spicyplayer.network.data.spotify.SpotifyTrackMatcher
                        .normalize(com.tx24.spicyplayer.network.data.blend.BlendText.lineText(line)).replace(" ", "").take(12)
                    line.start?.takeIf { key.length >= 4 }?.let { key to it }
                }?.takeIf { it.size >= 4 }
        println("── timing against a trusted sync (median offset, spread, lines matched)")
        SONGS.forEachIndexed { i, song ->
            val refId = REFERENCES.firstOrNull { timed(rows[it]?.get(i)?.first) != null }
            val ref = refId?.let { timed(rows[it]!![i].first) }
            if (ref == null) { println("   ${song.title}: no trusted sync to compare with"); return@forEachIndexed }
            println("   ${song.title} (against $refId)")
            rows.keys.sorted().filter { it != refId }.forEach { id ->
                val theirs = timed(rows[id]?.get(i)?.first) ?: return@forEach
                val deltas = mutableListOf<Double>()
                var j = 0
                for ((key, at) in theirs) {
                    val k = (j until minOf(ref.size, j + 6)).firstOrNull { ref[it].first == key } ?: continue
                    deltas += at - ref[k].second
                    j = k + 1
                }
                if (deltas.size < 3) { println("      %-20s too few lines in common (%d)".format(id, deltas.size)); return@forEach }
                val median = deltas.sorted()[deltas.size / 2]
                val spread = deltas.map { kotlin.math.abs(it - median) }.sorted()[(deltas.size * 9) / 10]
                val flag = if (kotlin.math.abs(median) > 0.5 || spread > 0.5) "  <- off" else ""
                println("      %-20s %+6d ms  ±%5d ms  %d/%d lines%s".format(id, (median * 1000).toInt(), (spread * 1000).toInt(), deltas.size, ref.size, flag))
                if (flag.isNotEmpty() && System.getenv("ALL_SOURCES_DELTAS") == "1") println("         " + deltas.joinToString(" ") { "%+d".format((it * 1000).toInt()) })
            }
        }
    }

    @Test
    fun `NetEase has human romaji, lent by a sibling copy where needed`() = runBlocking {
        assumeTrue(System.getenv("RUN_ALL_SOURCES_TEST") == "1")
        val netease = NetEaseLyricsProvider(OkHttpClient(), Gson())
        val lines = netease.romanization(LyricsLookupRequest("YOASOBI", "怪物", "", 205))
        println("NetEase romaji for 怪物: ${lines?.size} lines, first: ${lines?.firstOrNull()}")
        assertTrue("no romaji", !lines.isNullOrEmpty())
    }

    private companion object {
        val REFERENCES = listOf("spicy_lyrics", "amll_ttml_db", "lrc_red")
        val SONGS = listOf(
            LyricsLookupRequest("Rick Astley", "Never Gonna Give You Up", "Whenever You Need Somebody", 213),
            LyricsLookupRequest("The Weeknd", "Blinding Lights", "After Hours", 200),
            LyricsLookupRequest("周杰伦", "晴天", "叶惠美", 269),
            LyricsLookupRequest("米津玄師", "Lemon", "STRAY SHEEP", 255),
            LyricsLookupRequest("BTS", "Dynamite", "BE", 199),
            // Unison is a small community catalogue; this is one it has.
            LyricsLookupRequest("Michael Jackson", "Love Never Felt So Good", "XSCAPE", 246),
        )
    }
}
