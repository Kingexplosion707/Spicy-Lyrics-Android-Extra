package com.tx24.spicyplayer.lyrics.spicy.models

import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsFooterTest {
    private val spicy = LyricsProvenance("Spicy Lyrics", "Spicy Lyrics Community")

    @Test fun translationAndRomanizationGetARowEachOrShareOneRefined() {
        val footer = LyricsFooter(provenance = LyricsProvenance("LRCLIB"), translationCredit = "Genius + Google", romanizationCredit = "On-device")
        val lines = footer.lines()
        assertEquals(listOf("Provided by: LRCLIB", "Translation from: Genius + Google", "Romanization from: On-device"), lines.map { it.text })
        val refined = com.tx24.spicyplayer.lyrics.spicy.canvas.refinedFooterLines(lines)
        assertEquals(listOf("Provided by: LRCLIB", "Translation from: Genius + Google · Romanization from: On-device"), refined.map { it.text })
        // Pinned "No Writers" keeps them with the source.
        assertEquals(true, com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode.NoWriters.pins(FooterLine.Kind.TEXTS))
    }

    @Test fun communityCreditsFollowSpicyLyricsWording() {
        val footer = LyricsFooter(songwriters = listOf("Ada", "Ben"), provenance = spicy, uploader = LyricsCredit("TX26"))

        assertEquals(
            listOf("Written by: Ada, Ben", "Provided by: Spicy Lyrics", "These lyrics have been provided by our community", "Made by @TX26"),
            footer.lines().map(FooterLine::text),
        )
    }

    @Test fun makerAndUploaderAreBothCredited() {
        val lines = LyricsFooter(provenance = spicy, maker = LyricsCredit("maker"), uploader = LyricsCredit("up")).lines()

        assertEquals(
            listOf("Provided by: Spicy Lyrics", "These lyrics have been provided by our community", "Made by @maker", "Uploaded by @up"),
            lines.map(FooterLine::text),
        )
    }

    @Test fun otherSourcesNameThemselvesThenTheirCredits() {
        val amll = LyricsFooter(provenance = LyricsProvenance("AMLL TTML DB"), maker = LyricsCredit("someone")).lines()
        val unison = LyricsFooter(provenance = LyricsProvenance("Unison"), uploader = LyricsCredit("sub")).lines()

        assertEquals(listOf("Provided by: AMLL TTML DB", "Made by @someone"), amll.map(FooterLine::text))
        assertEquals(listOf("Provided by: Unison", "Submitted by @sub"), unison.map(FooterLine::text))
    }

    @Test fun catalogueLyricsNameTheirSource() {
        val lines = LyricsFooter(provenance = LyricsProvenance("Spicy Lyrics", "Apple Music")).lines()

        assertEquals(listOf("Provided by: Apple Music"), lines.map(FooterLine::text))
    }

    @Test fun onlyTrustedProfilesAreClickable() {
        fun url(profile: String) = LyricsFooter(maker = LyricsCredit("x", profile)).lines().single().profileUrl

        assertEquals("https://spicylyrics.org/uid/1", url("https://spicylyrics.org/uid/1"))
        assertEquals("https://github.com/someone", url("https://github.com/someone"))
        assertEquals("https://unison.boidu.dev/curator/ab12", url("https://unison.boidu.dev/curator/ab12"))
        assertNull(url("http://spicylyrics.org/uid/1"))
        assertNull(url("https://evil.example/spicylyrics.org"))
    }

    @Test fun doubleEscapedSongwritersAreDecoded() {
        assertEquals("Tom & Jerry", RemoteLyricsAdapter.decodeEntities("Tom &amp; Jerry"))
    }
}
