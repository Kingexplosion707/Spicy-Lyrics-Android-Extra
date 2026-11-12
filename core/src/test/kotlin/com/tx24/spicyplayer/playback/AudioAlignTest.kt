package com.tx24.spicyplayer.playback

import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class AudioAlignTest {
    private val sampleRate = 44_100

    /** A made-up song: where its sounds start (seconds) and how hard, as an analysis would list them. */
    private class Song(val onsets: FloatArray, val rises: FloatArray, val seconds: Float) {
        fun analysis() = AudioAnalysis(
            tempo = 120f, loudness = -8f, sections = emptyList(), beats = emptyList(),
            segments = onsets.indices.map { i ->
                AudioAnalysis.Segment(onsets[i], -30f, -30f + rises[i], onsets[i] + 0.03f)
            },
        )
    }

    /** Sounds at uneven times, a few a second, as sung and played music has. */
    private fun unevenSong(seed: Long, seconds: Float = 90f): Song {
        val random = Random(seed)
        val onsets = ArrayList<Float>()
        val rises = ArrayList<Float>()
        var t = 0.3f
        while (t < seconds - 1f) {
            onsets += t
            rises += 6f + random.nextFloat() * 14f
            t += 0.12f + random.nextFloat() * 0.5f
        }
        return Song(onsets.toFloatArray(), rises.toFloatArray(), seconds)
    }

    /** A steady four-on-the-floor beat: every half second the same hit. */
    private fun steadySong(seconds: Float = 90f): Song {
        val count = (seconds / 0.5f).toInt() - 2
        return Song(FloatArray(count) { 0.5f + it * 0.5f }, FloatArray(count) { 14f }, seconds)
    }

    /** The song as audio: each sound a short burst of a tone and noise, quieter for a smaller rise. */
    private fun render(song: Song, seed: Long, volume: Float = 0.6f): FloatArray {
        val random = Random(seed)
        val audio = FloatArray((song.seconds * sampleRate).toInt())
        for (i in song.onsets.indices) {
            val start = (song.onsets[i] * sampleRate).toInt()
            val gain = volume * (song.rises[i] / 20f)
            val hz = 80f + random.nextFloat() * 3000f
            val length = (0.12f * sampleRate).toInt()
            for (n in 0 until length) {
                val at = start + n
                if (at >= audio.size) break
                val envelope = exp(-n / (0.03f * sampleRate))
                audio[at] += gain * envelope * (0.6f * sin(2.0 * PI * hz * n / sampleRate).toFloat() + 0.4f * (random.nextFloat() * 2f - 1f))
            }
        }
        // A bed of quiet noise under it all, as a mix has.
        for (n in audio.indices) audio[n] = (audio[n] + 0.01f * (random.nextFloat() * 2f - 1f)).coerceIn(-1f, 1f)
        return audio
    }

    /**
     * Listens to [audio] from [fromSeconds] for [seconds], as a phone would: a block every 20 ms
     * or so, unevenly, some late, in 8 bits.
     */
    private fun listen(audio: FloatArray, fromSeconds: Float, seconds: Float, seed: Long): OnsetListener {
        val random = Random(seed)
        val listener = OnsetListener(sampleRate)
        val block = ByteArray(1024)
        var clockMs = 1_000L
        val endMs = clockMs + (seconds * 1000).toLong()
        while (clockMs < endMs) {
            val audioEnd = ((fromSeconds + (clockMs - 1_000L) / 1000f) * sampleRate).toInt()
            if (audioEnd - 1024 >= 0 && audioEnd <= audio.size) {
                for (i in 0 until 1024) {
                    block[i] = (audio[audioEnd - 1024 + i] * 127f + 128f).toInt().coerceIn(0, 255).toByte()
                }
                listener.add(block, clockMs)
            }
            clockMs += if (random.nextInt(40) == 0) 90L else 14L + random.nextInt(13)
        }
        return listener
    }

    /** The shift found when the player says [saysSeconds] and the audio is really at [reallySeconds]. */
    private fun shift(song: Song, audio: FloatArray, saysSeconds: Float, reallySeconds: Float, seed: Long): AudioAlign.Match? {
        val listener = listen(audio, reallySeconds, 11f, seed)
        val live = listener.recent(400) ?: return null
        // The player's position when the kept steps began.
        val keptFromMs = (listener.steps - 400) * AudioAlign.STEP_MS
        val startStep = ((saysSeconds * 1000 + keptFromMs) / AudioAlign.STEP_MS).toInt()
        return AudioAlign.match(AudioAlign.reference(song.analysis()), live, startStep, -160, 800)
    }

    @Test fun inSyncIsFoundInSync() {
        val song = unevenSong(1)
        val match = shift(song, render(song, 2), saysSeconds = 30f, reallySeconds = 30f, seed = 3)
        assertNotNull(match)
        assertTrue("was ${match!!.offsetMs}", abs(match.offsetMs) <= 60)
    }

    @Test fun audioAheadOfThePlayerIsMeasured() {
        val song = unevenSong(4)
        val audio = render(song, 5)
        for ((ahead, seed) in listOf(4.2f to 6L, 1.3f to 7L, 11.75f to 8L)) {
            val match = shift(song, audio, saysSeconds = 20f, reallySeconds = 20f + ahead, seed = seed)
            assertNotNull("nothing found for $ahead", match)
            assertTrue("for $ahead was ${match!!.offsetMs}", abs(match.offsetMs - (ahead * 1000).toLong()) <= 60)
        }
    }

    @Test fun audioBehindThePlayerIsMeasured() {
        val song = unevenSong(9)
        val match = shift(song, render(song, 10), saysSeconds = 40f, reallySeconds = 38.6f, seed = 11)
        assertNotNull(match)
        assertTrue("was ${match!!.offsetMs}", abs(match.offsetMs + 1_400) <= 60)
    }

    @Test fun quietAudioStillFits() {
        val song = unevenSong(12)
        val match = shift(song, render(song, 13, volume = 0.08f), saysSeconds = 25f, reallySeconds = 28f, seed = 14)
        assertNotNull(match)
        assertTrue("was ${match!!.offsetMs}", abs(match.offsetMs - 3_000) <= 60)
    }

    @Test fun anotherSongFitsNowhere() {
        val song = unevenSong(15)
        val other = unevenSong(16)
        for (seed in 20L..29L) {
            assertNull("seed $seed", shift(song, render(other, 17), saysSeconds = 30f, reallySeconds = 30f + (seed - 20), seed = seed))
        }
    }

    @Test fun silenceFitsNowhere() {
        val song = unevenSong(18)
        assertNull(shift(song, FloatArray(90 * sampleRate), saysSeconds = 30f, reallySeconds = 30f, seed = 19))
    }

    @Test fun aSteadyBeatNeverMovesThePlayer() {
        // Every half second fits as well as any other: the player's own position stands.
        val song = steadySong()
        val audio = render(song, 21)
        for (ahead in listOf(0f, 2f, 4.5f)) {
            val match = shift(song, audio, saysSeconds = 30f, reallySeconds = 30f + ahead, seed = 22)
            assertTrue("for $ahead was ${match?.offsetMs}", match == null || abs(match.offsetMs) <= 150)
        }
    }

    @Test fun theReferenceMarksWhereSoundsStart() {
        val song = Song(floatArrayOf(1f, 2.5f), floatArrayOf(12f, 12f), 4f)
        val reference = AudioAlign.reference(song.analysis())
        val peak = reference.indices.maxByOrNull { reference[it] }!!
        assertEquals(1_000f + 15f, peak * AudioAlign.STEP_MS.toFloat(), 26f)
    }

    private fun fit(offsetMs: Long, clear: Boolean) =
        if (clear) AudioAlign.Fit(offsetMs, 0.5f, 7f, 1.8f) else AudioAlign.Fit(offsetMs, 0.45f, 3.2f, 1.1f)

    @Test fun threeClearFitsThatAgreeDecide() {
        val votes = AlignVotes()
        assertNull(votes.add(fit(4_200, clear = true)))
        assertNull(votes.add(fit(4_230, clear = true)))
        assertEquals(4_210L, votes.add(fit(4_200, clear = false)))
    }

    @Test fun fitsThatDisagreeOrAreUnclearDoNotMoveThePlayer() {
        val votes = AlignVotes()
        // Far from the player and agreeing, but only one of them clear.
        votes.add(fit(4_200, clear = true)); votes.add(fit(4_210, clear = false))
        assertNull(votes.add(fit(4_190, clear = false)))
        // Clear, but each somewhere else.
        votes.reset()
        votes.add(fit(4_200, clear = true)); votes.add(fit(4_700, clear = true))
        assertNull(votes.add(fit(4_200, clear = true)))
        // A window with nothing in it starts the count again.
        votes.reset()
        votes.add(fit(4_200, clear = true)); votes.add(null); votes.add(fit(4_200, clear = true))
        assertNull(votes.add(fit(4_200, clear = true)))
    }

    @Test fun thePlayerBeingRightTakesLess() {
        val votes = AlignVotes()
        votes.add(fit(20, clear = false)); votes.add(fit(40, clear = false))
        assertEquals(30L, votes.add(fit(30, clear = false)))
    }
}
