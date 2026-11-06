package com.tx24.spicyplayer.haptics

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.WordTiming
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType

/**
 * The lyric times the haptics fuse in ([MusicHapticScore.buildWithBeat]): when each sung word
 * (or syllable) starts and when each lead line starts.
 *
 * - Unsynced lyrics have no times: every line sits at 0, which would be a burst of pulses at the
 *   song's start. They give nothing.
 * - Untimed lines and interludes ("• • •") are not sung, and give nothing.
 * - A backing-vocal line gives its words but not a line thump: the lead line it sits under has
 *   that already, usually a moment apart.
 * - Lines timed only as a whole get their words laid out over the line ([WordTiming]) instead of
 *   every word sharing the line's start, which left them a single pulse.
 * - Blank pieces (a source romanization's own pieces) repeat their words' times and give nothing.
 *
 * [wordsEstimated] says the word times were laid out rather than given, so the score may move
 * them further onto the sung onsets it can hear.
 */
class LyricHapticTimes(val wordTimes: List<Long>, val lineTimes: List<Long>, val wordsEstimated: Boolean) {
    companion object {
        val NONE = LyricHapticTimes(emptyList(), emptyList(), false)

        private val lock = Any()
        private var lastLyrics: LyricsState.Ready? = null
        private var lastTimes: LyricHapticTimes = NONE

        /**
         * The times for [lyrics]. The same lyrics object gives the same result object, so a loop
         * that asks before every pulse doesn't rebuild them (or the score keyed on them).
         */
        fun of(lyrics: LyricsState.Ready?): LyricHapticTimes {
            if (lyrics == null) return NONE
            synchronized(lock) { if (lastLyrics === lyrics) return lastTimes }
            val times = build(lyrics)
            synchronized(lock) {
                lastLyrics = lyrics
                lastTimes = times
            }
            return times
        }

        fun build(lyrics: LyricsState.Ready): LyricHapticTimes {
            if (lyrics.lyricsType == LyricsType.Static) return NONE
            val sung = lyrics.lines.filter { !it.untimed && it.role != LineRole.INTERLUDE && it.words.isNotEmpty() }
            // Mostly laid out: the score may move the words further (see [wordsEstimated]).
            val estimated = sung.count { WordTiming.isWholeLine(it) } * 2 > sung.size
            val laidOut = WordTiming.spread(sung, lyrics.lyricsType)
            val words = laidOut.flatMap { line -> line.words.filter { it.text.isNotBlank() }.map { it.startMs } }
                .filter { it >= 0L }.distinct().sorted()
            val lines = sung.filter { it.role != LineRole.BACKGROUND }.map { it.startMs }
                .filter { it >= 0L }.distinct().sorted()
            return LyricHapticTimes(words, lines, estimated)
        }
    }
}
