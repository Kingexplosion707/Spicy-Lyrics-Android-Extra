package com.tx24.spicyplayer.network.data.providers

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.parser.TtmlLyricsParser
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.measuredQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLiveCaptionsTest {
    private fun json(text: String) = JsonParser.parseString(text) as JsonObject

    /** The shape of YouTube's automatic captions: a cue, line breaks, and each word's offset. */
    private val automatic = json(
        """
        {"wireMagic":"pb3","events":[
          {"tStartMs":0,"dDurationMs":200000,"id":1,"wpWinPosId":1,"wsWinStyleId":1},
          {"tStartMs":320,"dDurationMs":14260,"wWinId":1,"segs":[{"utf8":"[Music]"}]},
          {"tStartMs":18790,"wWinId":1,"aAppend":1,"segs":[{"utf8":"\n"}]},
          {"tStartMs":18800,"dDurationMs":7160,"wWinId":1,"segs":[{"utf8":"We're","acAsrConf":0},{"utf8":" no","tOffsetMs":239},{"utf8":" strangers","tOffsetMs":559},{"utf8":" to","tOffsetMs":1040}]},
          {"tStartMs":21790,"dDurationMs":4170,"wWinId":1,"aAppend":1,"segs":[{"utf8":"\n"}]},
          {"tStartMs":21800,"dDurationMs":7319,"wWinId":1,"segs":[{"utf8":"love.","acAsrConf":0},{"utf8":" You","tOffsetMs":1000},{"utf8":" know","tOffsetMs":1239},{"utf8":" the","tOffsetMs":1479},{"utf8":" rules","tOffsetMs":1720}]}
        ]}
        """,
    )

    @Test fun automaticCaptionsAreWordTimed() {
        val ttml = YouTubeLiveCaptions.toTtml(automatic)!!
        val parsed = TtmlLyricsParser.parse(ttml.byteInputStream())
        assertEquals(LyricsType.Syllable, parsed.type)
        // The "[Music]" cue isn't a line.
        assertEquals(2, parsed.lines.size)
        val first = parsed.lines[0].words
        assertEquals(listOf("We're", "no", "strangers", "to"), first.map { it.text.trim() })
        assertEquals(listOf(18_800L, 19_039L, 19_359L, 19_840L), first.map { it.startMs })
        // Each word runs to the next; the last to the next line.
        assertEquals(listOf(19_039L, 19_359L, 19_840L, 21_040L), first.map { it.endMs })
        assertEquals(21_800L, parsed.lines[1].startMs)
        assertEquals(RemoteLyricsQuality.WORD_SYNCED, RemoteLyricsPayload(ttmlLyrics = ttml).measuredQuality())
    }

    @Test fun punctuatedCaptionsBreakAtSentences() {
        // Caption windows cut "love." and "I." off their sentences; the punctuation puts them back.
        val ttml = YouTubeLiveCaptions.toTtml(
            json(
                """
                {"events":[
                  {"tStartMs":1000,"dDurationMs":4000,"segs":[{"utf8":"We're"},{"utf8":" no","tOffsetMs":200},{"utf8":" strangers","tOffsetMs":500},{"utf8":" to","tOffsetMs":900}]},
                  {"tStartMs":2000,"dDurationMs":4000,"segs":[{"utf8":"love."},{"utf8":" You","tOffsetMs":500},{"utf8":" know","tOffsetMs":700},{"utf8":" the","tOffsetMs":900},{"utf8":" rules","tOffsetMs":1100}]},
                  {"tStartMs":3500,"dDurationMs":4000,"segs":[{"utf8":"and"},{"utf8":" so","tOffsetMs":200},{"utf8":" do","tOffsetMs":400}]},
                  {"tStartMs":4100,"dDurationMs":4000,"segs":[{"utf8":"I."},{"utf8":" Never","tOffsetMs":600},{"utf8":" gonna","tOffsetMs":800},{"utf8":" stop.","tOffsetMs":1000}]}
                ]}
                """,
            ),
        )!!
        val lines = TtmlLyricsParser.parse(ttml.byteInputStream()).lines
            .map { line -> line.words.joinToString("") { word -> (if (word.isPartOfWord) "" else " ") + word.text.trim() }.trim() }
        assertEquals(listOf("We're no strangers to love.", "You know the rules and so do I.", "Never gonna stop."), lines)
    }

    @Test fun ownCaptionsAreLineTimed() {
        val ttml = YouTubeLiveCaptions.toTtml(
            json(
                """
                {"events":[
                  {"tStartMs":1000,"dDurationMs":2500,"segs":[{"utf8":"♪ First line ♪"}]},
                  {"tStartMs":4000,"dDurationMs":3000,"segs":[{"utf8":"Second\nline & more"}]}
                ]}
                """,
            ),
        )!!
        val parsed = TtmlLyricsParser.parse(ttml.byteInputStream())
        assertEquals(LyricsType.Line, parsed.type)
        assertEquals(listOf(1_000L, 4_000L), parsed.lines.map { it.startMs })
        assertEquals(listOf(3_500L, 7_000L), parsed.lines.map { it.endMs })
        assertEquals("Second line & more", parsed.lines[1].words.joinToString("") { it.text }.trim())
    }

    @Test fun onlyCuesAreNoCaptions() {
        assertNull(YouTubeLiveCaptions.toTtml(json("""{"events":[{"tStartMs":0,"dDurationMs":9000,"segs":[{"utf8":"[Music]"}]}]}""")))
        assertNull(YouTubeLiveCaptions.toTtml(json("""{"events":[]}""")))
    }

    @Test fun videoMatching() {
        val song = LyricsLookupRequest(artist = "Rick Astley", title = "Never Gonna Give You Up", album = "", durationSeconds = 213)
        assertTrue(YouTubeLiveCaptions.sameSong("Rick Astley - Never Gonna Give You Up (Official Video)", "Rick Astley", song))
        assertTrue(YouTubeLiveCaptions.sameSong("Never Gonna Give You Up", "Rick Astley - Topic", song))
        assertFalse(YouTubeLiveCaptions.sameSong("Together Forever", "Rick Astley", song))
        assertFalse(YouTubeLiveCaptions.sameSong("Never Gonna Give You Up (cover)", "Someone Else", song))
        // A video played in the YouTube app: its title and channel.
        val video = LyricsLookupRequest(artist = "Veritasium", title = "The Science of Thinking", album = "", durationSeconds = 720)
        assertTrue(YouTubeLiveCaptions.sameSong("The Science of Thinking", "Veritasium", video))
    }
}
