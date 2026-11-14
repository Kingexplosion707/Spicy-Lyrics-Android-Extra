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

/**
 * Spicy Lyrics on the Essentials island, as an island provider (Essentials asks once to allow it,
 * then lists it in Island settings with its other items). While Essentials is bound:
 *
 * - the compact pill shows the cover and the line being sung;
 * - the line (when the island peeks, as it does for a new song) shows the line and the next one;
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
    private var peekedFor: String? = null
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
        val lineText = when {
            group == null || between -> null
            else -> group.lead.text()
        }
        val nextText = groups.getOrNull(index + 1)?.lead?.text()
        val compactText = lineText ?: if (synced && groups.isNotEmpty()) "♪" else track.title
        val lineStart = lineText ?: track.title
        val lineEnd = if (lineText != null) nextText.orEmpty() else track.artist
        val state = "${track.key}|$compactText|$lineStart|$lineEnd|${follower.playing}"
        if (state != lastPublished) {
            lastPublished = state
            val icon = coverIcon(track.cover)
            val payload = IslandPayload(
                items = listOf(
                    IslandItemPayload(
                        key = ITEM,
                        compact = listOfNotNull(
                            icon?.let { IslandCellPayload(key = "$ITEM.art", iconId = "art", tone = IslandTone.NONE) },
                            IslandCellPayload(key = "$ITEM.line", text = clip(compactText, 32), tone = IslandTone.NONE),
                        ),
                        line = IslandLinePayload(start = clip(lineStart, 120), end = clip(lineEnd, 120), iconId = icon?.let { "art" }, tone = IslandTone.NONE),
                        expanded = IslandExpandedPayload(heightDp = 300),
                        interactions = IslandInteractionsPayload(
                            onTap = IslandAction(IslandActionType.EXPAND_ITEM, ITEM),
                            onLongPress = IslandAction(IslandActionType.OPEN_APP),
                        ),
                    ),
                ),
            )
            runCatching { handle.publish(payload, icon?.let { mapOf("art" to it) }.orEmpty()) }
            // A new song peeks once, so its first line shows (Essentials allows one every 10 s).
            if (peekedFor != track.key && lineText != null) {
                peekedFor = track.key
                runCatching { handle.peek(ITEM, 4_000L) }
            }
        }
        // The next look: when the next line starts (or this one ends), and at least every few
        // seconds while playing, in case the song was moved in the player.
        if (follower.playing && synced && groups.isNotEmpty()) {
            val upcoming = groups.getOrNull(index + 1)?.startMs?.minus(LEAD_MS)
            val ends = group?.endMs?.plus(BREAK_MS)?.takeIf { it > now }
            val wait = listOfNotNull(upcoming, ends).filter { it > now }.minOrNull()?.minus(now) ?: RECHECK_MS
            main.postDelayed(tick, wait.coerceIn(60L, RECHECK_MS))
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
        private const val ICON_PX = 96
        /** A line counts as begun this long before its first word, so the change lands on time. */
        const val LEAD_MS = 150L
        /** This long after a line ends with nothing sung, it's a break. */
        private const val BREAK_MS = 1_500L
        private const val RECHECK_MS = 5_000L

        /** The follower the expanded view reads (the service's own). */
        @Volatile private var current: LiveLyrics? = null

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, true)

        private fun clip(text: String, max: Int): String =
            if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

        /** Opens the player's own now-playing screen, else the player app. */
        fun openPlayer(context: Context, live: LiveLyrics): Boolean {
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

    fun indexAt(groups: List<Group>, positionMs: Long): Int =
        groups.indexOfLast { it.startMs <= positionMs + SpicyIslandService.LEAD_MS }.coerceAtLeast(0)
}

internal fun TimedLine.text(romanized: Boolean = false): String = buildString {
    for (w in words) {
        if (isNotEmpty() && !w.attached) append(' ')
        append((if (romanized) w.romanized ?: w.text else w.text).trim())
    }
}
