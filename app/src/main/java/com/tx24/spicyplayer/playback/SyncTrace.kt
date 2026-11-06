package com.tx24.spicyplayer.playback

/**
 * The last few things something did, for the debug report. Release builds drop the log, and a
 * problem on someone else's phone can't be read any other way.
 *
 * Plain Kotlin, no Android: the times are `elapsedRealtime`, passed in.
 */
internal class EventTrace(private val maxLines: Int) {
    private val lines = ArrayDeque<Pair<Long, String>>()

    fun add(nowMs: Long, line: String) = synchronized(lines) {
        if (lines.size >= maxLines) lines.removeFirst()
        lines.addLast(nowMs to line)
    }

    /** Oldest first, each stamped with how long before [nowMs] it happened. */
    fun lines(nowMs: Long): List<String> = synchronized(lines) {
        lines.map { (at, line) -> "-%.1fs %s".format(java.util.Locale.ROOT, (nowMs - at) / 1000.0, line) }
    }
}

/** What the lyric clock did. */
internal val SyncTrace = EventTrace(40)

/** What translation did: started, shown, skipped, dropped as stale, failed. */
internal val TranslationTrace = EventTrace(40)
