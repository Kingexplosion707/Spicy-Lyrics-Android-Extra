package com.tx24.spicyplayer.island

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import com.sameerasw.island.sdk.HostInfo
import com.sameerasw.island.sdk.IslandAction
import com.sameerasw.island.sdk.IslandActionType
import com.sameerasw.island.sdk.IslandCellPayload
import com.sameerasw.island.sdk.IslandExpandedPayload
import com.sameerasw.island.sdk.IslandHostHandle
import com.sameerasw.island.sdk.IslandInteractionsPayload
import com.sameerasw.island.sdk.IslandItemPayload
import com.sameerasw.island.sdk.IslandLinePayload
import com.sameerasw.island.sdk.IslandPayload
import com.sameerasw.island.sdk.IslandTone
import com.sameerasw.island.sdk.compose.ComposeIslandProviderService
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.playback.LiveLyrics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Spicy Lyrics on the Essentials island, as an island provider (Essentials asks once to allow it,
 * then lists it in Island settings with its other items). While Essentials is bound:
 *
 * - the compact pill shows the cover and the line being sung, scrolling a few words at a time with
 *   the singing so long lines aren't cut off, ahead of Essentials' own music player while playing;
 * - the line (when the island peeks) shows a longer part of it, scrolling the same way;
 * - the expanded island ([IslandLyricsView]) shows the lyrics word-synced in Spicy Lyrics' style,
 *   with background vocals, a second singer on the right and translations, plus the progress and
 *   the controls. A tapped line or a tap on the progress moves the song; the cover and song open
 *   the player.
 *
 * It costs little: it follows the player through the session the app shares, publishes only when
 * the line, the song or playing changes (timed to the next line, with a check at least every few
 * seconds while playing in case the song was moved), and draws per frame only while the expanded
 * view is open and the song plays (less often in power saving).
 *
 * Settings → Device → "Lyrics on the Essentials island" (on by default): off, it shows nothing.
 */
class SpicyIslandService : ComposeIslandProviderService() {
    private val main = Handler(Looper.getMainLooper())
    private var live: LiveLyrics? = null
    private var lastPublished: String? = null
    private var visualsFor: String? = null
    private var visualsJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var coverFor: Any? = null
    private var coverIcon: Bitmap? = null
    private val tick = Runnable { publish() }

    private val prefs: SharedPreferences by lazy { getSharedPreferences("ui", Context.MODE_PRIVATE) }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PREF) main.post { if (enabled(this)) follow() else stopFollowing(clear = true) }
    }

    override fun onAttached(host: IslandHostHandle, info: HostInfo) {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        lastPublished = null
        if (enabled(this)) follow() else host.publish(IslandPayload())
    }

    override fun onHostDetached() {
        super.onHostDetached()
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
        main.post { stopFollowing(clear = false) }
    }

    override fun onDestroy() {
        stopFollowing(clear = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun follow() {
        if (live != null) {
            publish()
            return
        }
        val created = LiveLyrics(applicationContext)
        created.onChange = { main.post { publish() } }
        created.start()
        live = created
        current = created
        publish()
    }

    private fun stopFollowing(clear: Boolean) {
        main.removeCallbacks(tick)
        live?.stop()
        live = null
        current = null
        visualsJob?.cancel()
        visualsFor = null
        canvasUrl = null
        motionUrl = null
        lastPublished = null
        if (clear) runCatching { host?.publish(IslandPayload()) }
    }

    /** Sends what the island shows now, when it changed, and times the next look. */
    private fun publish() {
        main.removeCallbacks(tick)
        val handle = host ?: return
        val follower = live ?: return
        val track = follower.track
        if (track == null) {
            if (lastPublished != "") runCatching { handle.publish(IslandPayload()) }
            lastPublished = ""
            return
        }
        val now = follower.positionMs()
        val lyrics = follower.lyrics
        val groups = lyrics?.let { IslandLyrics.groups(it) }.orEmpty()
        val synced = lyrics != null && lyrics.lyricsType != LyricsType.Static
        val index = if (synced) IslandLyrics.indexAt(groups, now) else -1
        val group = groups.getOrNull(index)
        // Between lines (an instrumental break) the pill shows a note instead of a finished line.
        val between = group != null && now > group.endMs + BREAK_MS && groups.getOrNull(index + 1)?.let { now < it.startMs - LEAD_MS } != false
        val sung = if (group == null || between) null else group.lead
        // Long lines scroll with the singing: the pill and the line show the part being sung,
        // moving on a few words at a time, so nothing is cut off.
        val compactWindow = sung?.let { IslandLyrics.window(it, now, COMPACT_CHARS) }
        val lineWindow = sung?.let { IslandLyrics.window(it, now, LINE_CHARS) }
        val compactText = compactWindow?.text ?: if (synced && groups.isNotEmpty()) "♪" else clip(track.title, COMPACT_CHARS)
        val lineStart = lineWindow?.text ?: track.title
        val lineEnd = if (sung != null) "" else track.artist
        val state = "${track.key}|$compactText|$lineStart|$lineEnd|${follower.playing}|$canvasUrl|$motionUrl"
        if (state != lastPublished) {
            lastPublished = state
            val icon = coverIcon(track.cover)
            val payload = IslandPayload(
                items = listOf(
                    IslandItemPayload(
                        key = ITEM,
                        compact = listOfNotNull(
                            icon?.let { IslandCellPayload(key = "$ITEM.art", iconId = "art", tone = IslandTone.NONE) },
                            IslandCellPayload(key = "$ITEM.line", text = compactText, tone = IslandTone.NONE),
                        ),
                        line = IslandLinePayload(start = clip(lineStart, 120), end = clip(lineEnd, 120), iconId = icon?.let { "art" }, tone = IslandTone.NONE),
                        expanded = IslandExpandedPayload(heightDp = 330),
                        // Ahead of Essentials' own music player (and everything but calls,
                        // notifications and alerts) while a song is on, so the lyrics stay in the pill.
                        priority = PRIORITY,
                        priorityOverride = if (follower.playing) PRIORITY_PLAYING else null,
                        interactions = IslandInteractionsPayload(
                            onTap = IslandAction(IslandActionType.EXPAND_ITEM, ITEM),
                            onLongPress = IslandAction(IslandActionType.OPEN_APP),
                        ),
                    ),
                ),
            )
            runCatching { handle.publish(payload, icon?.let { mapOf("art" to it) }.orEmpty()) }
        }
        if (visualsFor != track.key) lookUpVisuals(track)
        // The next look: when the next part of the line, the next line or a break begins, and at
        // least every few seconds while playing, in case the song was moved in the player.
        if (follower.playing && synced && groups.isNotEmpty()) {
            val upcoming = groups.getOrNull(index + 1)?.startMs?.minus(LEAD_MS)
            val ends = group?.endMs?.plus(BREAK_MS)?.takeIf { it > now }
            val wait = listOfNotNull(upcoming, ends, compactWindow?.nextMs, lineWindow?.nextMs)
                .filter { it > now }.minOrNull()?.minus(now) ?: RECHECK_MS
            main.postDelayed(tick, wait.coerceIn(60L, RECHECK_MS))
        }
    }

    /** The Canvas and the animated cover for the song, looked up once per song, for the expanded view. */
    private fun lookUpVisuals(track: com.tx24.spicyplayer.playback.BackgroundSession.Track) {
        visualsFor = track.key
        canvasUrl = null
        motionUrl = null
        visualsJob?.cancel()
        visualsJob = scope.launch {
            val canvas = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val meta = com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                    val spotify = com.tx24.spicyplayer.network.data.spotify.SharedSpotify
                    spotify.extras(cacheDir).trackIds(meta, track.request.spotifyTrackId)
                        .firstNotNullOfOrNull { spotify.canvas(cacheDir).find(it) }
                }.getOrNull()
            }
            val motion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    com.tx24.spicyplayer.ui.nowplaying.MotionCovers.finder(this@SpicyIslandService)
                        .find(com.tx24.spicyplayer.ui.nowplaying.leadArtist(track.artist), track.album, track.title)
                }.getOrNull()
            }
            if (visualsFor != track.key) return@launch
            canvasUrl = canvas
            motionUrl = motion
        }
    }

    /** The cover, small, for the pill (made once per cover). */
    private fun coverIcon(cover: Bitmap?): Bitmap? {
        if (cover == null) return null
        if (coverFor === cover) return coverIcon
        coverFor = cover
        coverIcon = runCatching { Bitmap.createScaledBitmap(cover, ICON_PX, ICON_PX, true) }.getOrNull()
        return coverIcon
    }

    override fun hasExpandedContent(itemKey: String): Boolean = itemKey == ITEM

    @Composable
    override fun ExpandedContent(itemKey: String) {
        val follower = current ?: return
        IslandLyricsView(
            live = follower,
            info = hostInfoState.value,
            onResize = { px -> host?.resize(itemKey, px) },
            onSnapshot = { bitmap -> host?.snapshot(itemKey, bitmap) },
            onKeepAlive = { host?.keepAlive(itemKey) },
            onOpenPlayer = {
                if (openPlayer(this, follower)) host?.collapse(itemKey)
            },
        )
    }

    companion object {
        const val PREF = "essentialsIsland"
        private const val ITEM = "lyrics"
        private const val ICON_PX = 64
        /** How much of a line the pill shows at once, and the line when the island peeks. */
        private const val COMPACT_CHARS = 16
        private const val LINE_CHARS = 38
        private const val PRIORITY = 29
        private const val PRIORITY_PLAYING = 12
        /** A line counts as begun this long before its first word, so the change lands on time. */
        const val LEAD_MS = 150L
        /** This long after a line ends with nothing sung, it's a break. */
        private const val BREAK_MS = 1_500L
        private const val RECHECK_MS = 5_000L

        /** The follower the expanded view reads (the service's own). */
        @Volatile private var current: LiveLyrics? = null
        /** The song's Canvas and animated cover, for the expanded view. */
        internal var canvasUrl by androidx.compose.runtime.mutableStateOf<String?>(null)
        internal var motionUrl by androidx.compose.runtime.mutableStateOf<String?>(null)

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, true)

        private fun clip(text: String, max: Int): String =
            if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

        /** Opens the player's own now-playing screen, else the player app. */
        internal fun openPlayer(context: Context, live: LiveLyrics): Boolean {
            live.sessionActivity?.let { session ->
                val sent = runCatching {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val options = android.app.ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        session.send(context, 0, null, null, null, null, options.toBundle())
                    } else {
                        session.send()
                    }
                }.isSuccess
                if (sent) return true
            }
            val pkg = live.playerPackage ?: return false
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            return runCatching {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }.isSuccess
        }
    }
}

/** Lyrics as the island shows them: each lead line with its background vocals. */
internal object IslandLyrics {
    class Group(val index: Int, val lead: TimedLine, val backing: List<Pair<Int, TimedLine>>) {
        val startMs: Long get() = minOf(lead.startMs, backing.minOfOrNull { it.second.startMs } ?: lead.startMs)
        val endMs: Long get() = maxOf(lead.endMs, backing.maxOfOrNull { it.second.endMs } ?: lead.endMs)
    }

    fun groups(lyrics: LyricsState.Ready): List<Group> {
        val out = ArrayList<Group>()
        val static = lyrics.lyricsType == LyricsType.Static
        for ((i, line) in lyrics.lines.withIndex()) {
            if (line.untimed && !static) continue
            if (line.words.none { it.text.isNotBlank() }) continue
            if (line.role != LineRole.LEAD && out.isNotEmpty()) {
                val last = out.last()
                out[out.lastIndex] = Group(last.index, last.lead, last.backing + (i to line))
            } else {
                out += Group(i, line, emptyList())
            }
        }
        return out
    }

    /** The part of a line shown at once: its text, and when the next part starts (null: the last). */
    class Window(val text: String, val nextMs: Long?)

    /**
     * The part of [line] being sung at [positionMs], at most [maxChars] long: the line split at
     * word boundaries into parts that fit, each shown from its first word's start (spread over the
     * line's time when only the line is timed). A short line is shown whole.
     */
    fun window(line: TimedLine, positionMs: Long, maxChars: Int): Window {
        val full = line.text()
        if (full.length <= maxChars || line.words.isEmpty()) return Window(clipTo(full, maxChars), null)
        class Part(val text: String, val startMs: Long)
        val parts = ArrayList<Part>()
        val chars = full.length.coerceAtLeast(1)
        var builder = StringBuilder()
        var partStart = line.startMs
        var offset = 0
        val wordTimed = line.words.any { it.endMs > it.startMs } && !line.lineTimed
        for ((n, w) in line.words.withIndex()) {
            val word = w.text.trim()
            val joiner = if (builder.isEmpty() || w.attached) "" else " "
            val at = if (wordTimed) w.startMs else line.startMs + (line.endMs - line.startMs) * offset / chars
            if (builder.isNotEmpty() && builder.length + joiner.length + word.length > maxChars && !w.attached) {
                parts += Part(builder.toString(), partStart)
                builder = StringBuilder()
                partStart = at
                builder.append(word)
            } else {
                if (builder.isEmpty()) partStart = if (n == 0) line.startMs else at
                builder.append(joiner).append(word)
            }
            offset += joiner.length + word.length
        }
        if (builder.isNotEmpty()) parts += Part(builder.toString(), partStart)
        val i = parts.indexOfLast { it.startMs <= positionMs + SpicyIslandService.LEAD_MS }.coerceAtLeast(0)
        return Window(clipTo(parts[i].text, maxChars), parts.getOrNull(i + 1)?.startMs?.minus(SpicyIslandService.LEAD_MS))
    }

    private fun clipTo(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

    fun indexAt(groups: List<Group>, positionMs: Long): Int =
        groups.indexOfLast { it.startMs <= positionMs + SpicyIslandService.LEAD_MS }.coerceAtLeast(0)
}

internal fun TimedLine.text(romanized: Boolean = false): String = buildString {
    for (w in words) {
        if (isNotEmpty() && !w.attached) append(' ')
        append((if (romanized) w.romanized ?: w.text else w.text).trim())
    }
}
