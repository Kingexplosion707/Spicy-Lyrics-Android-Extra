package com.tx24.spicyplayer.network.data.spotify

import kotlin.math.ceil
import kotlin.math.floor

/**
 * How far through its lyrics a song is at any moment, for lyrics without timing that scroll
 * along with the song. The lyrics move while the song isn't quiet and hold while it is, so a long
 * instrumental intro or outro, or a break of a few seconds, doesn't eat the scroll the singing
 * needs.
 *
 * Built from the loudness of the song's audio analysis segments, so it needs no more than
 * [AudioAnalysis] already carries.
 */
class StaticPace private constructor(private val cumulative: DoubleArray) {

    /** How far through the lyrics [positionMs] is, 0 to 1. */
    fun progress(positionMs: Long): Float {
        val steps = cumulative.size - 1
        val x = positionMs / 1000.0 * RATE
        if (x <= 0.0) return 0f
        if (x >= steps) return 1f
        val k = x.toInt()
        val within = cumulative[k + 1] - cumulative[k]
        return ((cumulative[k] + within * (x - k)) / cumulative[steps]).toFloat()
    }

    companion object {
        /** Steps per second of the loud/quiet grid. */
        private const val RATE = 10
        /** A segment this quiet or quieter is silence, dB. */
        private const val SILENCE_DB = -35f
        /** Quiet stretches shorter than this are pauses inside the music, not breaks, seconds. */
        private const val MIN_BREAK_S = 3
        /** How long the last segment counts for: the analysis gives no end to it, seconds. */
        private const val LAST_SEGMENT_S = 0.5f

        /** The pace by [analysis]' loudness, or null when it has no segments or no sound in them. */
        fun of(analysis: AudioAnalysis): StaticPace? {
            val segments = analysis.segments
            if (segments.size < 2) return null
            val end = segments.last().start + LAST_SEGMENT_S
            val loud = BooleanArray(ceil(end * RATE).toInt())
            segments.forEachIndexed { i, segment ->
                if (segment.loudnessMax <= SILENCE_DB) return@forEachIndexed
                val to = if (i + 1 < segments.size) segments[i + 1].start else end
                val from = floor(segment.start * RATE).toInt().coerceAtLeast(0)
                val until = ceil(to * RATE).toInt().coerceAtMost(loud.size)
                for (k in from until until) loud[k] = true
            }
            val first = loud.indexOfFirst { it }
            val last = loud.indexOfLast { it }
            if (first < 0) return null
            // Short gaps between loud stretches are filled in; quiet ends never are.
            var gapStart = -1
            for (k in first..last) {
                if (!loud[k] && gapStart < 0) gapStart = k
                if (loud[k] && gapStart >= 0) {
                    if (k - gapStart < MIN_BREAK_S * RATE) for (g in gapStart until k) loud[g] = true
                    gapStart = -1
                }
            }
            val cumulative = DoubleArray(loud.size + 1)
            for (k in loud.indices) cumulative[k + 1] = cumulative[k] + if (loud[k]) 1.0 else 0.0
            return StaticPace(cumulative)
        }
    }
}
