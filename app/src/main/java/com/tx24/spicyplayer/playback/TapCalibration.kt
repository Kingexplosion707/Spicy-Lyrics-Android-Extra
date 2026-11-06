package com.tx24.spicyplayer.playback

import kotlin.math.abs

/**
 * Finding an output's delay from taps along to the song's beats. Each tap is where the player says
 * it was when the beat was heard, so how far the taps sit from the beats is how far the sound runs
 * behind the player's position: the delay the lyrics need.
 *
 * Each tap is matched to the nearest beat after taking off the delay already set, so only what is
 * still off has to be worked out: with no delay set, an output a good part of a beat behind (some
 * Bluetooth) could otherwise match the wrong beat. The first few taps, while the hand finds the
 * beat, and taps far from any beat don't count. The median of the rest is the answer, and their
 * spread says whether it can be trusted.
 *
 * Plain Kotlin, no Android.
 */
internal object TapCalibration {
    /** Taps that count before there is an answer. */
    const val MIN_TAPS = 12
    /** Taps left out at the start while the hand finds the beat. */
    const val WARMUP_TAPS = 4
    /** A tap further than this share of a beat from its nearest beat is a miss. */
    private const val MATCH_FRACTION = 0.45
    /** Taps spread wider than this (median distance from their median) are too uneven to trust. */
    const val MAX_SPREAD_MS = 40

    /**
     * [delayMs]: the output delay the taps call for. [spreadMs]: how unevenly they fell. [taps]:
     * how many counted.
     */
    data class Estimate(val delayMs: Int, val spreadMs: Int, val taps: Int) {
        val steady get() = spreadMs <= MAX_SPREAD_MS
    }

    /**
     * [tapPositionsMs]: the player's position at each tap, in order. [beatsMs]: the song's beats,
     * sorted. [currentDelayMs]: the output delay set now. Null until enough taps count.
     */
    fun estimate(tapPositionsMs: List<Long>, beatsMs: LongArray, currentDelayMs: Int): Estimate? {
        if (beatsMs.size < 2) return null
        val offsets = tapPositionsMs.drop(WARMUP_TAPS).mapNotNull { position ->
            val heard = position - currentDelayMs
            val i = nearest(beatsMs, heard)
            val beatLength = if (i + 1 < beatsMs.size) beatsMs[i + 1] - beatsMs[i] else beatsMs[i] - beatsMs[i - 1]
            val offset = heard - beatsMs[i]
            offset.takeIf { abs(it) <= beatLength * MATCH_FRACTION }
        }
        if (offsets.size < MIN_TAPS) return null
        val median = median(offsets)
        val spread = median(offsets.map { abs(it - median) })
        return Estimate((currentDelayMs + median).toInt(), spread.toInt(), offsets.size)
    }

    private fun nearest(sorted: LongArray, value: Long): Int {
        var lo = 0
        var hi = sorted.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < value) lo = mid + 1 else hi = mid
        }
        return if (lo > 0 && value - sorted[lo - 1] < sorted[lo] - value) lo - 1 else lo
    }

    private fun median(values: List<Long>): Long = values.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
