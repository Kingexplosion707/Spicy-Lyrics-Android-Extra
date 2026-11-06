package com.tx24.spicyplayer.haptics

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.WordTiming
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lyric times, word layout and onset snapping for the music haptics. */
class HapticAccuracyTest {
    /** A line as RemoteLyricsAdapter.wrappable leaves LRC: every piece stamped with the line's span. */
    private fun lrcLine(text: String, start: Long, end: Long, role: LineRole = LineRole.LEAD) = TimedLine(
        start, end, text.split(" ").mapIndexed { i, w -> TimedWord(w, start, end, attached = i == 0) }, role = role,
    )

    private fun ready(lines: List<TimedLine>, type: LyricsType) =
        LyricsState.Ready(lines, null, null, null, emptyList(), lyricsType = type)

    @Test fun wholeLineWordsFollowOneAnother() {
        val line = WordTiming.spreadLine(lrcLine("I want to break free", 10_000, 20_000))
        val starts = line.words.map { it.startMs }
        assertEquals(10_000L, starts.first())
        assertTrue("ascending: $starts", starts.zipWithNext().all { (a, b) -> b > a })
        line.words.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
        // Five syllables over a ten second gap: sung in about two seconds, not stretched over ten.
        val sung = line.words.last().endMs - 10_000
        assertEquals(5 * WordTiming.MS_PER_SYLLABLE + WordTiming.MS_PER_LINE, sung)
        // The held last word is the longest.
        val lengths = line.words.map { it.endMs - it.startMs }
        assertEquals(lengths.max(), lengths.last())
    }

    @Test fun denseLineUsesMostOfItsSpan() {
        val line = WordTiming.spreadLine(lrcLine("never gonna give you up never gonna let you down", 0, 2_000))
        assertEquals((2_000 * WordTiming.SUNG_SHARE).toLong(), line.words.last().endMs)
    }

    @Test fun shortSpanIsNeverExceeded() {
        val line = WordTiming.spreadLine(lrcLine("oh", 0, 300))
        assertEquals(300L, line.words.last().endMs)
    }

    @Test fun syllableLyricsAreLeftAlone() {
        val words = listOf(TimedWord("Hel", 0, 200, false), TimedWord("lo", 200, 500, true))
        val lines = listOf(TimedLine(0, 500, words))
        assertEquals(lines, WordTiming.spread(lines, LyricsType.Syllable))
        assertEquals(lines, WordTiming.spread(lines, LyricsType.Static))
    }

    @Test fun romanizationPiecesAreLaidOutOverTheSameSinging() {
        val line = TimedLine(1_000, 5_000, listOf(
            TimedWord("夜", 1_000, 5_000, false, romanized = ""),
            TimedWord("空", 1_000, 5_000, true, romanized = ""),
            TimedWord("", 1_000, 5_000, false, romanized = "yo"),
            TimedWord("", 1_000, 5_000, false, romanized = "zora"),
        ))
        val out = WordTiming.spreadLine(line)
        assertEquals(1_000L, out.words[0].startMs)
        assertEquals(1_000L, out.words[2].startMs)
        assertEquals(out.words[1].endMs, out.words[3].endMs)
        assertTrue(out.words[1].startMs > 1_000L && out.words[3].startMs > 1_000L)
    }

    @Test fun syllableCounts() {
        assertEquals(1f, WordTiming.syllables("break"))
        assertEquals(3f, WordTiming.syllables("beautiful"))
        assertEquals(2f, WordTiming.syllables("夜空"))
        assertEquals(2f, WordTiming.syllables("사랑"))
        assertEquals(0f, WordTiming.syllables("..."))
    }

    @Test fun hapticTimesSkipWhatIsNotSung() {
        val lines = listOf(
            lrcLine("first line here", 1_000, 4_000),
            TimedLine(4_000, 9_000, emptyList(), role = LineRole.INTERLUDE),
            lrcLine("second line", 9_000, 12_000),
            lrcLine("echo", 9_500, 12_000, role = LineRole.BACKGROUND),
        )
        val times = LyricHapticTimes.build(ready(lines, LyricsType.Line))
        assertEquals(listOf(1_000L, 9_000L), times.lineTimes)
        assertTrue(times.wordsEstimated)
        assertEquals(6, times.wordTimes.size)
        assertTrue(times.wordTimes.contains(9_500L))
        val static = LyricHapticTimes.build(ready(listOf(TimedLine(0, 0, listOf(TimedWord("a b", 0, 0, false)))), LyricsType.Static))
        assertTrue(static.wordTimes.isEmpty() && static.lineTimes.isEmpty())
        // The same lyrics object gives the same lists back, so the score cache isn't rebuilt.
        val r = ready(lines, LyricsType.Line)
        assertTrue(LyricHapticTimes.of(r) === LyricHapticTimes.of(r))
    }

    private fun analysisWithLateHits(): AudioAnalysis {
        val rhythm = rhythm(listOf((0 until 16).map { it * 0.5f + 0.03f }) + List(7) { emptyList() })
        return AudioAnalysis(
            tempo = 120f, loudness = -10f,
            sections = listOf(AudioAnalysis.Section(0f, 8f, -8f, 120f)),
            beats = (0 until 16).map { AudioAnalysis.Beat(it * 0.5f, 0.5f, 0.9f) },
            bars = listOf(0f, 2f, 4f, 6f),
            // Sung onsets 60 ms after each half beat's quarter, rising 10 dB.
            segments = (0 until 16).map { AudioAnalysis.Segment(it * 0.5f + 0.31f, -20f, -8f, it * 0.5f + 0.33f) },
            rhythm = com.tx24.spicyplayer.network.data.spotify.Rhythm.parse(rhythm),
        )
    }

    @Test fun beatGridLandsOnTheHits() {
        val score = MusicHapticScore.buildWithBeat(
            analysisWithLateHits(), MusicHapticsStyle.Beat,
            HapticBeatOptions(mode = HapticBeatMode.Beat, detection = HapticBeatDetection.AllInOne, timing = HapticBeatTiming.Spotify),
        )
        val beats = score.filter { it.pulse == MusicPulse.Beat || it.pulse == MusicPulse.Downbeat }
        assertTrue(beats.isNotEmpty())
        // Every beat moved onto its kick, 30 ms after the tracker's grid.
        assertTrue(beats.map { it.atMs }.toString(), beats.all { kotlin.math.abs((it.atMs - 30 + 250) % 500 - 250) <= 2 })
    }

    @Test fun wordsLandOnSungOnsets() {
        val analysis = analysisWithLateHits()
        val options = HapticBeatOptions(mode = HapticBeatMode.Apple, vocalsOnly = true, timing = HapticBeatTiming.Lyrics)
        // An estimated word 100 ms off the onset moves; a source-timed one that far does not.
        val est = MusicHapticScore.buildWithBeat(analysis, options = options, wordTimes = listOf(2_210L), wordsEstimated = true)
        assertEquals(listOf(2_310L), est.map { it.atMs })
        val real = MusicHapticScore.buildWithBeat(analysis, options = options, wordTimes = listOf(2_210L), wordsEstimated = false)
        assertEquals(listOf(2_210L), real.map { it.atMs })
        val near = MusicHapticScore.buildWithBeat(analysis, options = options, wordTimes = listOf(2_290L))
        assertEquals(listOf(2_310L), near.map { it.atMs })
    }

    private fun rhythm(bands: List<List<Float>>): String {
        val text = StringBuilder("11025 32 ${bands.size}")
        bands.forEach { onsets ->
            text.append(' ').append(onsets.size)
            var last = 0L
            onsets.forEach { t -> val step = Math.round(t * 11025.0 / 32); text.append(' ').append(step - last); last = step }
        }
        val deflater = java.util.zip.Deflater().apply { setInput(text.toString().toByteArray()); finish() }
        val buffer = ByteArray(64 * 1024)
        return java.util.Base64.getUrlEncoder().encodeToString(buffer.copyOf(deflater.deflate(buffer)))
    }
}
