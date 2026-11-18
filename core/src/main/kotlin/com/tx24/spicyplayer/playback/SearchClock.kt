package com.tx24.spicyplayer.playback

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType

/**
 * The timer a searched song plays on: a song found by title and artist, with no player behind
 * it. It runs from [positionMs] at [atMs] while [playing], stops at [durationMs], and a play at
 * the end starts it again. Times are milliseconds on one monotonic clock.
 */
data class SearchClock(
    val durationMs: Long,
    val positionMs: Long = 0L,
    val playing: Boolean = false,
    val atMs: Long = 0L,
) {
    /** Only timed lyrics give a length; a song without one stays where it is. */
    val timed: Boolean get() = durationMs > 0L

    fun positionAt(nowMs: Long): Long {
        if (!playing) return positionMs
        val running = positionMs + (nowMs - atMs).coerceAtLeast(0L)
        return if (timed) running.coerceAtMost(durationMs) else running
    }

    fun play(nowMs: Long): SearchClock {
        if (!timed) return this
        val position = positionAt(nowMs).let { if (it >= durationMs) 0L else it }
        return copy(positionMs = position, playing = true, atMs = nowMs)
    }

    fun pause(nowMs: Long): SearchClock = copy(positionMs = positionAt(nowMs), playing = false, atMs = nowMs)

    fun toggle(nowMs: Long): SearchClock = if (playing) pause(nowMs) else play(nowMs)

    fun seek(toMs: Long, nowMs: Long): SearchClock {
        val position = if (timed) toMs.coerceIn(0L, durationMs) else toMs.coerceAtLeast(0L)
        return copy(positionMs = position, atMs = nowMs)
    }

    /** How long until it reaches its end while playing; null while it isn't running toward one. */
    fun remainingMs(nowMs: Long): Long? =
        if (playing && timed) (durationMs - positionAt(nowMs)).coerceAtLeast(0L) else null

    /** Stopped at its end. */
    fun ended(nowMs: Long): SearchClock = copy(positionMs = durationMs, playing = false, atMs = nowMs)

    companion object {
        /** A little time after the last line, so it isn't cut off as it ends. */
        const val TAIL_MS = 4_000L

        /**
         * How long a searched song with [lyrics] runs: to its last line's end and [TAIL_MS] more.
         * Unsynced lyrics have no timing, so they give none (0).
         */
        fun lengthOf(lyrics: LyricsState.Ready): Long {
            if (lyrics.lyricsType == LyricsType.Static) return 0L
            val last = lyrics.lines.asSequence().filterNot { it.untimed }.maxOfOrNull { maxOf(it.startMs, it.endMs) } ?: 0L
            return if (last > 0L) last + TAIL_MS else 0L
        }
    }
}
