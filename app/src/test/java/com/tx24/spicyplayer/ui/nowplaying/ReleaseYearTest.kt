package com.tx24.spicyplayer.ui.nowplaying

import com.tx24.spicyplayer.ui.settings.ReleaseYearPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseYearTest {
    @Test fun theYearSitsOnTheSideItIsSetTo() {
        assertEquals("Playboi Carti · 2020", ReleaseYear("2020", false, ReleaseYearPosition.Right).besideArtists("Playboi Carti"))
        assertEquals("2020 · Playboi Carti", ReleaseYear("2020", false, ReleaseYearPosition.Left).besideArtists("Playboi Carti"))
        assertEquals("Playboi Carti", ReleaseYear("2020", false, ReleaseYearPosition.Off).besideArtists("Playboi Carti"))
        // Still looked up: one line has no room to keep, so it shows once it's there.
        assertEquals("Playboi Carti", ReleaseYear(null, true, ReleaseYearPosition.Right).besideArtists("Playboi Carti"))
        assertEquals("Playboi Carti", (null as ReleaseYear?).besideArtists("Playboi Carti"))
    }

    @Test fun theArtistsRowKeepsRoomWhileTheYearIsLookedUp() {
        assertEquals(YearLabel("2020", before = true, shown = true), ReleaseYear("2020", false, ReleaseYearPosition.Left).label())
        assertEquals(false, ReleaseYear(null, true, ReleaseYearPosition.Right).label()?.shown)
        assertNull(ReleaseYear("2020", false, ReleaseYearPosition.Off).label())
        assertNull(ReleaseYear(null, false, ReleaseYearPosition.Right).label())
    }
}
