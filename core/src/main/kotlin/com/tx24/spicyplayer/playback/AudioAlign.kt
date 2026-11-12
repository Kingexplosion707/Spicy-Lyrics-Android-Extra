package com.tx24.spicyplayer.playback

import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds where in a song the audio being played is, by ear: the times sounds start in what's
 * playing ([OnsetListener]) are laid over the times they start in the song's audio analysis
 * ([reference]), and the shift that fits best is how far the audio is from where the player says
 * it is ([match]).
 *
 * A player's reports are trusted for the fine timing; this is for when they're plainly wrong
 * (Spotify's Automix reports a song seconds behind its audio). So a fit near where the player
 * says wins unless another fits clearly better, and a fit that isn't clear is no answer at all.
 *
 * Plain Kotlin, no Android. Times are counted in steps of [STEP_MS].
 */
object AudioAlign {
    const val STEP_MS = 25

    /** A shift within this many steps of the player's own position is the player being right. */
    private const val NEAR_STEPS = 6
    /** Another fit has to beat the one near the player's position by this factor to replace it. */
    private const val BEAT_NEAR_BY = 1.3f
    /** The best fit's correlation has to reach this. */
    private const val MIN_SCORE = 0.25f
    /** How far the best fit stands above the others, in their standard deviations. */
    private const val MIN_Z = 5.2f
    /** How far above the next-best fit elsewhere (not its own shoulder) the best has to be. */
    private const val MIN_LEAD = 1.35f
    /** Fits this close to the best are its own shoulder, not rivals. */
    private const val SHOULDER_STEPS = 8
    /** A window with fewer sounds starting in it says too little. */
    private const val MIN_ONSETS = 10

    /**
     * How strongly a sound starts in each step of the song, from the analysis's segments (each a
     * note or a hit): the rise from where a segment starts to its peak, placed where it rises.
     */
    fun reference(analysis: AudioAnalysis): FloatArray {
        val segments = analysis.segments
        if (segments.isEmpty()) return FloatArray(0)
        val last = segments.last()
        val steps = ((max(last.start, last.peakAt) * 1000f) / STEP_MS).toInt() + 8
        val curve = FloatArray(steps)
        for (segment in segments) {
            val rise = (segment.loudnessMax - segment.loudnessStart).coerceIn(0f, 24f)
            if (rise < 1.5f) continue
            // The sound is heard starting between the segment's start and its peak.
            val at = segment.start + min((segment.peakAt - segment.start) / 2f, 0.04f)
            val step = (at * 1000f / STEP_MS).roundToInt()
            if (step in curve.indices) curve[step] = max(curve[step], rise)
        }
        return smoothed(curve)
    }

    /** [curve] spread a step either way, so a sound a step early or late still counts. */
    fun smoothed(curve: FloatArray): FloatArray {
        val out = FloatArray(curve.size)
        for (i in curve.indices) {
            val before = if (i > 0) curve[i - 1] else 0f
            val after = if (i < curve.size - 1) curve[i + 1] else 0f
            out[i] = 0.25f * before + 0.5f * curve[i] + 0.25f * after
        }
        return out
    }

    /** [offsetMs]: how far the audio is ahead of where the player says (negative: behind). */
    class Match(val offsetMs: Long, val score: Float)

    /**
     * The best fit before it's judged: its correlation ([score]), how many standard deviations it
     * stands above the other fits ([z]), and how many times the next-best fit elsewhere it is ([lead]).
     */
    class Fit(val offsetMs: Long, val score: Float, val z: Float, val lead: Float) {
        val clear get() = score >= MIN_SCORE && z >= MIN_Z && lead >= MIN_LEAD
    }

    /**
     * Lays [live] (onset strengths, a step each, already [smoothed]) over [reference].
     * [liveStartStep] is where the player says the song was when [live] began; the audio may be
     * from [minShiftSteps] to [maxShiftSteps] steps away from that. Null when nothing fits clearly.
     */
    fun match(
        reference: FloatArray,
        live: FloatArray,
        liveStartStep: Int,
        minShiftSteps: Int,
        maxShiftSteps: Int,
    ): Match? = fit(reference, live, liveStartStep, minShiftSteps, maxShiftSteps)
        ?.takeIf { it.clear }?.let { Match(it.offsetMs, it.score) }

    /** The best fit of [live] over [reference], however good; null when there's too little to lay over. */
    fun fit(
        reference: FloatArray,
        live: FloatArray,
        liveStartStep: Int,
        minShiftSteps: Int,
        maxShiftSteps: Int,
    ): Fit? {
        val n = live.size
        if (n < 80 || reference.size < n) return null
        var onsets = 0
        var liveSum = 0.0
        for (i in 0 until n) liveSum += live[i]
        val liveMean = (liveSum / n).toFloat()
        var liveVar = 0.0
        for (i in 1 until n - 1) {
            if (live[i] > liveMean * 2f && live[i] >= live[i - 1] && live[i] > live[i + 1]) onsets++
        }
        for (i in 0 until n) liveVar += (live[i] - liveMean) * (live[i] - liveMean)
        if (onsets < MIN_ONSETS || liveVar <= 0.0) return null
        val liveNorm = sqrt(liveVar)

        val first = max(minShiftSteps, -liveStartStep)
        val last = min(maxShiftSteps, reference.size - n - liveStartStep)
        if (last - first < 2 * SHOULDER_STEPS) return null
        val scores = FloatArray(last - first + 1)
        // Running sums of the reference, for each shift's mean and variance without a second pass.
        val prefix = DoubleArray(reference.size + 1)
        val prefixSq = DoubleArray(reference.size + 1)
        for (i in reference.indices) {
            prefix[i + 1] = prefix[i] + reference[i]
            prefixSq[i + 1] = prefixSq[i] + reference[i].toDouble() * reference[i]
        }
        for (shift in first..last) {
            val from = liveStartStep + shift
            val sum = prefix[from + n] - prefix[from]
            val sumSq = prefixSq[from + n] - prefixSq[from]
            val variance = sumSq - sum * sum / n
            if (variance <= 1e-9) continue
            var dot = 0.0
            for (i in 0 until n) dot += live[i] * reference[from + i]
            // Pearson: the means taken off both.
            scores[shift - first] = ((dot - liveMean * sum) / (liveNorm * sqrt(variance))).toFloat()
        }

        var best = 0
        for (i in scores.indices) if (scores[i] > scores[best]) best = i
        // The best fit near where the player says it is.
        var near = -1
        for (shift in max(first, -NEAR_STEPS)..min(last, NEAR_STEPS)) {
            val i = shift - first
            if (near < 0 || scores[i] > scores[near]) near = i
        }
        if (near >= 0 && scores[near] * BEAT_NEAR_BY >= scores[best]) best = near
        val score = scores[best]

        var sum = 0.0
        var sumSq = 0.0
        var rival = -1f
        var count = 0
        for (i in scores.indices) {
            if (abs(i - best) <= SHOULDER_STEPS) continue
            sum += scores[i]
            sumSq += scores[i].toDouble() * scores[i]
            count++
            if (scores[i] > rival) rival = scores[i]
        }
        if (count < 8) return null
        val mean = sum / count
        val deviation = sqrt(max(sumSq / count - mean * mean, 1e-9))
        val z = ((score - mean) / deviation).toFloat()
        val lead = if (rival > 1e-3f) score / rival else 99f

        // Between steps: the top of the curve through the best fit and its neighbours.
        var fraction = 0f
        if (best > 0 && best < scores.size - 1) {
            val a = scores[best - 1]
            val b = scores[best]
            val c = scores[best + 1]
            val curvature = a - 2 * b + c
            if (curvature < -1e-6f) fraction = (0.5f * (a - c) / curvature).coerceIn(-0.5f, 0.5f)
        }
        return Fit(((best + first + fraction) * STEP_MS).roundToInt().toLong(), score, z, lead)
    }
}

/**
 * Decides from one fit after another (windows a few seconds apart) when the audio's place is
 * known: three in a row that agree. Away from where the player says, two of the three have to be
 * clear on their own; that the player is right takes less (a song with a steady beat fits nearly
 * as well a beat either way, so its fits are seldom clear). A window with no fit starts the count
 * again.
 */
class AlignVotes {
    private val fits = ArrayList<AudioAlign.Fit>(3)

    fun reset() = fits.clear()

    /** Takes the next window's fit; the agreed offset in ms once there is one, else null. */
    fun add(fit: AudioAlign.Fit?): Long? {
        if (fit == null || fit.score < STEADY_MIN_SCORE || fit.z < STEADY_MIN_Z) {
            fits.clear()
            return null
        }
        if (fits.size == 3) fits.removeAt(0)
        fits += fit
        if (fits.size < 3) return null
        val low = fits.minOf { it.offsetMs }
        val high = fits.maxOf { it.offsetMs }
        val near = abs(low) <= NEAR_MS && abs(high) <= NEAR_MS
        // That the player is right: three in a row that agree near its position.
        if (near && high - low <= STEADY_AGREE_MS) return fits.sumOf { it.offsetMs } / 3
        // Anywhere else: three in a row that agree, at least two of them clear. (A steady beat
        // from another song, the one Automix is mixing out at the same tempo, can agree with
        // itself somewhere three times too, but not clearly.)
        if (high - low <= CLEAR_AGREE_MS && fits.count { it.clear } >= 2) return fits.sumOf { it.offsetMs } / 3
        return null
    }

    private companion object {
        const val CLEAR_AGREE_MS = 75L
        const val STEADY_AGREE_MS = 50L
        const val STEADY_MIN_SCORE = 0.3f
        const val STEADY_MIN_Z = 2.5f
        const val NEAR_MS = 150L
    }
}

/**
 * Hears where sounds start: fed short blocks of the audio being played as they come, it gives how
 * strongly a sound started in each [AudioAlign.STEP_MS] step of time (louder all at once in any
 * band of frequencies).
 *
 * The blocks may come unevenly and with gaps; each carries the time it ends at. Not thread-safe.
 */
class OnsetListener(private val sampleRate: Int, private val blockSize: Int = 1024) {
    private val window = FloatArray(blockSize) { (0.5 - 0.5 * cos(2.0 * PI * it / (blockSize - 1))).toFloat() }
    private val real = FloatArray(blockSize)
    private val imaginary = FloatArray(blockSize)
    private val bandEdges: IntArray
    private val bandLevel = FloatArray(BANDS)
    private val lastLevel = FloatArray(BANDS)
    private var hasLast = false

    /** Onset strength per step since [startMs]. */
    private var strengths = FloatArray(1024)
    private var counts = IntArray(1024)
    /** When step 0 began; set by the first block. */
    var startMs = Long.MIN_VALUE
        private set
    /** Steps heard so far. */
    var steps = 0
        private set

    init {
        require(blockSize and (blockSize - 1) == 0) { "block size must be a power of two" }
        // Bands evenly spaced by pitch from 60 Hz to 10 kHz (or as high as the audio goes).
        val top = min(10_000.0, sampleRate / 2.0 * 0.9)
        bandEdges = IntArray(BANDS + 1) { band ->
            val hz = 60.0 * Math.pow(top / 60.0, band / BANDS.toDouble())
            (hz * blockSize / sampleRate).roundToInt().coerceIn(1, blockSize / 2 - 1)
        }
    }

    fun reset() {
        startMs = Long.MIN_VALUE
        steps = 0
        hasLast = false
        strengths.fill(0f)
        counts.fill(0)
    }

    /**
     * Takes a block of unsigned 8-bit audio (as Android's Visualizer gives it) that ended at
     * [endMs].
     */
    fun add(block: ByteArray, endMs: Long) {
        if (block.size < blockSize) return
        var energy = 0f
        for (i in 0 until blockSize) {
            val sample = ((block[i].toInt() and 0xFF) - 128) / 128f
            energy += sample * sample
            real[i] = sample * window[i]
            imaginary[i] = 0f
        }
        addSpectrum(energy / blockSize, endMs)
    }

    /** Takes a block of audio in -1..1 that ended at [endMs]. */
    fun add(block: FloatArray, endMs: Long) {
        if (block.size < blockSize) return
        var energy = 0f
        for (i in 0 until blockSize) {
            energy += block[i] * block[i]
            real[i] = block[i] * window[i]
            imaginary[i] = 0f
        }
        addSpectrum(energy / blockSize, endMs)
    }

    private fun addSpectrum(meanSquare: Float, endMs: Long) {
        if (startMs == Long.MIN_VALUE) startMs = endMs
        val step = ((endMs - startMs) / AudioAlign.STEP_MS).toInt()
        if (step < 0) return
        grow(step + 1)
        // A gap (a late block): what came before it is no guide to what starts after.
        if (step - steps > MAX_GAP_STEPS) hasLast = false
        steps = max(steps, step + 1)
        if (meanSquare < SILENCE) {
            hasLast = false
            return
        }
        fft(real, imaginary)
        var flux = 0f
        for (band in 0 until BANDS) {
            var power = 0f
            val from = bandEdges[band]
            val to = max(bandEdges[band + 1], from + 1)
            for (bin in from until to) power += real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            val level = ln(power / (to - from) + FLOOR)
            bandLevel[band] = level
            if (hasLast) flux += max(0f, level - lastLevel[band])
        }
        if (hasLast) {
            // Two blocks in one step: the stronger start is the step's.
            strengths[step] = if (counts[step] == 0) flux else max(strengths[step], flux)
            counts[step]++
        }
        System.arraycopy(bandLevel, 0, lastLevel, 0, BANDS)
        hasLast = true
    }

    /** The last [count] steps' onset strengths, smoothed as [AudioAlign.match] wants; null if fewer were heard. */
    fun recent(count: Int): FloatArray? {
        if (steps < count) return null
        val out = FloatArray(count)
        System.arraycopy(strengths, steps - count, out, 0, count)
        return AudioAlign.smoothed(out)
    }

    /** How many of the last [count] steps a block actually landed in. */
    fun heard(count: Int): Int {
        var heard = 0
        for (i in max(0, steps - count) until steps) if (counts[i] > 0) heard++
        return heard
    }

    private fun grow(size: Int) {
        if (size <= strengths.size) return
        // Keeps the last few minutes: older steps are dropped and the start moved on.
        if (size > MAX_STEPS) {
            val drop = size - MAX_STEPS / 2
            val kept = steps - drop
            if (kept > 0) {
                System.arraycopy(strengths, drop, strengths, 0, kept)
                System.arraycopy(counts, drop, counts, 0, kept)
            }
            strengths.fill(0f, max(kept, 0), strengths.size)
            counts.fill(0, max(kept, 0), counts.size)
            steps = max(kept, 0)
            startMs += drop.toLong() * AudioAlign.STEP_MS
            return
        }
        val capacity = min(max(size, strengths.size * 2), MAX_STEPS)
        strengths = strengths.copyOf(capacity)
        counts = counts.copyOf(capacity)
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
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
                val r = re[i]; re[i] = re[j]; re[j] = r
                val m = im[i]; im[i] = im[j]; im[j] = m
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepRe = cos(angle).toFloat()
            val stepIm = sin(angle).toFloat()
            var start = 0
            while (start < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tRe = re[b] * wRe - im[b] * wIm
                    val tIm = re[b] * wIm + im[b] * wRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val next = wRe * stepRe - wIm * stepIm
                    wIm = wRe * stepIm + wIm * stepRe
                    wRe = next
                }
                start += length
            }
            length = length shl 1
        }
    }

    private companion object {
        const val BANDS = 8
        /** Quieter than this (mean square of -1..1 audio) is silence, or the 8-bit floor. */
        const val SILENCE = 2e-5f
        /** Keeps the log of an empty band finite and small rises in a quiet band small. */
        const val FLOOR = 0.05f
        const val MAX_GAP_STEPS = 4
        /** Ten minutes of steps. */
        const val MAX_STEPS = 24_000
    }
}
