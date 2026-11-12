package com.tx24.spicyplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongLinksTest {
    @Test fun spotifyLinkOnlyForARealId() {
        assertEquals("https://open.spotify.com/track/0z93LiOLVx8thr9eD2r9uV", SongLinks.spotify("0z93LiOLVx8thr9eD2r9uV"))
        assertNull(SongLinks.spotify(null))
        assertNull(SongLinks.spotify("not an id"))
        assertNull(SongLinks.spotify("0z93LiOLVx8thr9eD2r9u/"))
    }

    @Test fun searchesAreEncoded() {
        assertEquals("https://genius.com/search?q=Dirty+%26+Clean+KSI", SongLinks.genius("Dirty & Clean", "KSI"))
        assertEquals("https://www.youtube.com/results?search_query=Dirty+KSI", SongLinks.youtube(" Dirty ", "KSI"))
    }

    @Test fun shareTextHasTheLinkWhenThereIsOne() {
        assertEquals("Dirty – KSI\nhttps://open.spotify.com/track/0z93LiOLVx8thr9eD2r9uV", SongLinks.shareText("Dirty", "KSI", "0z93LiOLVx8thr9eD2r9uV"))
        assertEquals("Dirty – KSI", SongLinks.shareText("Dirty", "KSI", null))
        assertEquals("Dirty", SongLinks.shareText("Dirty", "", null))
    }
}
