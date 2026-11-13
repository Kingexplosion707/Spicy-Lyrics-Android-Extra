package com.tx24.spicyplayer.haptics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Finds the hits in live audio, for haptics without a song's analysis: what the phone plays (a
 * film, a video) or what its microphone hears (music on someone else's speaker). Each block of
 * samples is split by a small FFT into the lows (kicks, bass, explosions) and the highs (snares,
 * claps); a band hits when its energy jumps well above its own recent level, so it follows loud
 * and quiet passages alike.
 */
class LiveBeatDetector(
    /** 0..1: higher lets softer hits through. */
    sensitivity: Float = 0.5f,
) {
    enum class Kind { Low, High }

    /** A hit and how strong it is, 0..1. */
    class Hit(val kind: Kind, val strength: Float)

    /** How far over its floor (in log energy, about 2.7 times per step) a band must jump to hit. */
    private val threshold = 3.2f - 2.2f * sensitivity.coerceIn(0f, 1f)
    private val low = Band(LOW_FROM_HZ, LOW_TO_HZ, refractoryMs = 110L)
    private val high = Band(HIGH_FROM_HZ, HIGH_TO_HZ, refractoryMs = 90L)
    private var re = FloatArray(0)
    private var im = FloatArray(0)
    private var window = FloatArray(0)

    /** Forgets what it has heard (a new source, or after a long silence). */
    fun reset() {
        low.reset()
        high.reset()
    }

    /**
     * Takes one block of mono samples (-1..1) at [sampleRate], heard at [atMs]; returns the
     * strongest hit in it, if any. Blocks may overlap.
     */
    fun frame(samples: FloatArray, count: Int, sampleRate: Int, atMs: Long): Hit? {
        if (count < 64 || sampleRate <= 0) return null
        var n = 1
        while (n * 2 <= count && n < MAX_FFT) n *= 2
        if (re.size != n) {
            re = FloatArray(n)
            im = FloatArray(n)
            window = FloatArray(n) { i -> (0.5 - 0.5 * cos(2 * PI * i / (n - 1))).toFloat() }
        }
        val start = count - n
        for (i in 0 until n) {
            re[i] = samples[start + i] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val binHz = sampleRate.toFloat() / n
        val lowHit = low.take(energy(low, binHz, n), atMs, threshold)
        val highHit = high.take(energy(high, binHz, n), atMs, threshold)
        return when {
            lowHit != null && (highHit == null || lowHit >= highHit) -> Hit(Kind.Low, lowHit)
            highHit != null -> Hit(Kind.High, highHit)
            else -> null
        }
    }

    private fun energy(band: Band, binHz: Float, n: Int): Float {
        val from = (band.fromHz / binHz).toInt().coerceIn(1, n / 2 - 1)
        val to = (band.toHz / binHz).toInt().coerceIn(from, n / 2 - 1)
        var sum = 0f
        for (k in from..to) sum += re[k] * re[k] + im[k] * im[k]
        return sum / (to - from + 1)
    }

    /**
     * One band's floor (its level between hits, on a log scale: quick to fall, slow to rise, so
     * hits don't lift it) and when it last hit.
     */
    private class Band(val fromHz: Float, val toHz: Float, val refractoryMs: Long) {
        private var floor = Float.NaN
        private var previous = Float.NaN
        private var lastHitMs = Long.MIN_VALUE / 2

        fun reset() {
            floor = Float.NaN
            previous = Float.NaN
            lastHitMs = Long.MIN_VALUE / 2
        }

        /** The hit's strength when [energy] jumps out of the band's floor, else null. */
        fun take(energy: Float, atMs: Long, threshold: Float): Float? {
            val level = ln(energy + SILENCE)
            if (floor.isNaN()) {
                floor = level
                previous = level
                return null
            }
            val above = level - floor
            val rise = level - previous
            previous = level
            floor += if (level < floor) (level - floor) * FALL else (level - floor) * RISE_RATE
            if (level < ln(SILENCE) + QUIET || above < threshold || rise < MIN_RISE || atMs - lastHitMs < refractoryMs) return null
            lastHitMs = atMs
            return ((above - threshold) / STRONG_ABOVE + 0.35f).coerceIn(0.2f, 1f)
        }
    }

    private companion object {
        const val LOW_FROM_HZ = 35f
        const val LOW_TO_HZ = 200f
        const val HIGH_FROM_HZ = 1_800f
        const val HIGH_TO_HZ = 7_000f
        const val MAX_FFT = 2048
        const val SILENCE = 1e-9f
        /** A band this far (in log energy) above silence counts as sound at all. */
        const val QUIET = 4f
        /** How fast the floor follows the level down, and up. */
        const val FALL = 0.06f
        const val RISE_RATE = 0.015f
        /** The jump from the block before that a hit needs, in log energy. */
        const val MIN_RISE = 0.4f
        /** How far over the threshold a hit is at full strength. */
        const val STRONG_ABOVE = 3f

        /** In-place radix-2 FFT; [re].size is a power of two. */
        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wr = cos(angle).toFloat()
                val wi = sin(angle).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = a + len / 2
                        val xr = re[b] * cr - im[b] * ci
                        val xi = re[b] * ci + im[b] * cr
                        re[b] = re[a] - xr
                        im[b] = im[a] - xi
                        re[a] += xr
                        im[a] += xi
                        val nr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = nr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
