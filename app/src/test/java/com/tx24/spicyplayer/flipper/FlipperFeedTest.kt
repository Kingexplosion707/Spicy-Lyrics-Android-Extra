package com.tx24.spicyplayer.flipper

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipperFeedTest {
    private fun line(text: String, start: Long, end: Long, romanized: String? = null) = TimedLine(
        start, end, text.split(" ").mapIndexed { i, w ->
            TimedWord(w, start, end, attached = i == 0, romanized = romanized?.split(" ")?.getOrNull(i))
        },
    )

    private fun lyrics(vararg lines: TimedLine) = LyricsState.Ready(
        lines = lines.toList(), source = null, maker = null, uploader = null, songwriters = emptyList(),
        lyricsType = LyricsType.Line,
    )

    private fun snapshot(position: Long, playing: Boolean = true, lyrics: LyricsState.Ready? = null, settled: Boolean = true) =
        FlipperFeed.Snapshot("Café Song", "Beyoncé", 200_000, playing, position, lyrics, settled)

    @Test
    fun `text folds to what the Flipper can draw`() {
        assertEquals("Cafe - don't stop...", FlipperProtocol.ascii("Café — don’t stop…"))
        assertEquals("tab here", FlipperProtocol.ascii("tab\there"))
    }

    @Test
    fun `a line that won't fold goes by its romanization`() {
        val lead = com.tx24.spicyplayer.widget.WidgetLines.Lead(0, 1, "夜に駆ける", romanized = "yoru ni kakeru")
        assertEquals("yoru ni kakeru", FlipperProtocol.lineText(lead))
    }

    @Test
    fun `sends the song, the lines and the position, then only changes`() {
        val feed = FlipperFeed()
        val words = lyrics(line("First line here", 1_000, 3_000), line("Second line", 3_000, 5_000), line("Third", 5_000, 7_000))
        val first = feed.messages(snapshot(3_500, lyrics = words), 0)
        assertEquals("S\tCafe Song\tBeyonce\n", first[0])
        assertEquals("L\tFirst line here\tSecond line\tThird\n", first[1])
        assertTrue(first[2].startsWith("P\t1\t3500\t200000"))
        // Nothing new a moment later.
        assertTrue(feed.messages(snapshot(3_700, lyrics = words), 200).isEmpty())
        // The next line.
        // The next line, with the position for the Flipper to keep time by.
        val next = feed.messages(snapshot(5_100, lyrics = words), 1_800)
        assertEquals(listOf("L\tSecond line\tThird\t\n", "P\t1\t5100\t200000\n"), next)
    }

    @Test
    fun `tells the Flipper when it pauses, is moved, and every few seconds`() {
        val feed = FlipperFeed()
        feed.messages(snapshot(0), 0)
        assertTrue(feed.messages(snapshot(500, playing = false), 500).any { it.startsWith("P\t0\t500") })
        assertTrue(feed.messages(snapshot(60_000, playing = false), 600).any { it.startsWith("P\t0\t60000") })
        assertTrue(feed.messages(snapshot(60_000, playing = false), 700 + FlipperFeed.PLAYING_EVERY_MS).any { it.startsWith("P\t") })
    }

    @Test
    fun `says when there are no lyrics yet or at all`() {
        val feed = FlipperFeed()
        assertTrue(feed.messages(snapshot(0, settled = false), 0).contains("N\tLooking for lyrics...\n"))
        assertTrue(feed.messages(snapshot(0, settled = true), 10).contains("N\tNo lyrics for this song\n"))
    }

    @Test
    fun `word-synced lines carry their words and times`() {
        val feed = FlipperFeed()
        val sung = TimedLine(
            10_000, 12_000,
            listOf(
                TimedWord("Blind", 10_000, 10_400, attached = false),
                TimedWord("ed", 10_400, 10_700, attached = true),
                TimedWord("by", 10_800, 11_000, attached = false),
            ),
        )
        val ready = LyricsState.Ready(listOf(sung), null, null, null, emptyList(), lyricsType = LyricsType.Syllable)
        val out = feed.messages(snapshot(10_100, lyrics = ready), 0)
        val words = out.first { it.startsWith("W") }
        assertEquals("W\t10000\tBlind\u001f0\u001f400\u001f0\ted\u001f400\u001f700\u001f1\tby\u001f800\u001f1000\u001f0\n", words)
        // The position comes with the new line, for the wipe.
        assertTrue(out.any { it.startsWith("P\t1\t10100") })
    }

    @Test
    fun `a line too long for one message goes by whole words, then by none`() {
        val syllables = (0 until 40).map { FlipperProtocol.Word(if (it % 2 == 0) "la" else "la", it * 100L, it * 100L + 90, joined = it % 2 == 1) }
        val message = FlipperProtocol.words(0, syllables)!!
        assertEquals(20, message.trimEnd().split('\t').size - 2)
        val tooMany = (0 until 60).map { FlipperProtocol.Word("word$it", it * 100L, it * 100L + 90, joined = false) }
        assertNull(FlipperProtocol.words(0, tooMany))
    }

    @Test
    fun `reads the Flipper's buttons`() {
        assertEquals(FlipperProtocol.Command.PlayPause, FlipperProtocol.command("C\tplay"))
        assertEquals(FlipperProtocol.Command.Previous, FlipperProtocol.command("C\tprev\r"))
        assertNull(FlipperProtocol.command("C\tdance"))
        assertTrue(FlipperProtocol.isAnswer("SPICY\t1"))
    }
}
