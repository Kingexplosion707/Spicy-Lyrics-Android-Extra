package com.tx24.spicyplayer.stats

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzTest {
    private val dirty = ListenBrainz.Listen("Dirty", "KSI", "Dirty", 1_800_000_000L, 180_000L)

    @Test fun aSongCountsAtHalfOrFourMinutes() {
        assertEquals(90_000L, ListenBrainz.listenAfterMs(180_000L))
        assertEquals(240_000L, ListenBrainz.listenAfterMs(900_000L))
        assertEquals(30_000L, ListenBrainz.listenAfterMs(40_000L))
        assertEquals(240_000L, ListenBrainz.listenAfterMs(0L))
    }

    @Test fun oneListenIsSingle() {
        val root = JsonParser.parseString(ListenBrainz.payload(listOf(dirty))).asJsonObject
        assertEquals("single", root.get("listen_type").asString)
        val listen = root.getAsJsonArray("payload").single().asJsonObject
        assertEquals(1_800_000_000L, listen.get("listened_at").asLong)
        val meta = listen.getAsJsonObject("track_metadata")
        assertEquals("KSI", meta.get("artist_name").asString)
        assertEquals("Dirty", meta.get("track_name").asString)
        assertEquals("Dirty", meta.get("release_name").asString)
        assertEquals(180_000L, meta.getAsJsonObject("additional_info").get("duration_ms").asLong)
    }

    @Test fun severalAreAnImportAndBlanksAreLeftOut() {
        val root = JsonParser.parseString(ListenBrainz.payload(listOf(dirty, dirty.copy(title = "Other", album = ""), dirty.copy(artist = " ")))).asJsonObject
        assertEquals("import", root.get("listen_type").asString)
        val listens = root.getAsJsonArray("payload")
        assertEquals(2, listens.size())
        assertFalse(listens[1].asJsonObject.getAsJsonObject("track_metadata").has("release_name"))
        assertNull(ListenBrainz.payload(emptyList()))
    }

    @Test fun theQueueSurvivesDisk() {
        val queue = listOf(dirty, dirty.copy(title = "Quote \" and \\ slash"))
        assertEquals(queue, ListenBrainz.queueFromJson(ListenBrainz.queueToJson(queue)))
        assertTrue(ListenBrainz.queueFromJson("not json").isEmpty())
        assertTrue(ListenBrainz.queueFromJson(null).isEmpty())
    }

    @Test fun aValidTokenNamesItsUser() {
        assertEquals("behnoud", ListenBrainz.validatedUser("""{"code":200,"message":"Token valid.","valid":true,"user_name":"behnoud"}"""))
        assertNull(ListenBrainz.validatedUser("""{"code":200,"message":"Token invalid.","valid":false}"""))
        assertNull(ListenBrainz.validatedUser(""))
    }
}
