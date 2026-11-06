package com.tx24.spicyplayer.analytics

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class UmamiEventTest {
    private val device = DeviceInfo(
        appVersion = "0.7.0",
        appBuild = 8,
        androidRelease = "14",
        androidSdk = 34,
        model = "Google Pixel 8",
        language = "en-GB",
        screenWidthDp = 412,
        screenHeightDp = 915,
        tablet = false,
    )

    @Test
    fun `body has the event and the device facts, and nothing else about the person`() {
        val body = JsonParser.parseString(
            umamiEventBody("site", "install", "app_open", device, mapOf("channel" to "stable", "count" to 3)),
        ).asJsonObject
        assertEquals("event", body["type"].asString)
        val payload = body["payload"].asJsonObject
        assertEquals("site", payload["website"].asString)
        assertEquals("install", payload["id"].asString)
        assertEquals("app_open", payload["name"].asString)
        assertEquals("412x915", payload["screen"].asString)
        assertEquals("mobile", payload["device"].asString)
        val data = payload["data"].asJsonObject
        assertEquals("0.7.0", data["version"].asString)
        assertEquals(34, data["sdk"].asInt)
        assertEquals("stable", data["channel"].asString)
        assertEquals(3, data["count"].asInt)
        assertEquals(
            setOf("website", "id", "hostname", "url", "title", "name", "language", "screen", "os", "browser", "device", "data"),
            payload.keySet(),
        )
    }

    @Test
    fun `user agent reads as a phone browser`() {
        val agent = umamiUserAgent(device)
        assertTrue(agent.startsWith("Mozilla/5.0 (Linux; Android 14; Mobile)"))
        assertFalse(agent.contains("bot", ignoreCase = true))
    }

    @Test
    fun `app open is due after the interval, on first run, and after the clock goes back`() {
        val now = 10 * APP_OPEN_INTERVAL_MS
        assertTrue(appOpenDue(0L, now))
        assertFalse(appOpenDue(now - APP_OPEN_INTERVAL_MS + 1, now))
        assertTrue(appOpenDue(now - APP_OPEN_INTERVAL_MS, now))
        assertTrue(appOpenDue(now + 1_000L, now))
    }

    /** Sends one test-tagged event to the real Umami: `RUN_UMAMI_TEST=1` and `UMAMI_WEBSITE_ID`. */
    @Test
    fun `live Umami takes the event`() {
        assumeTrue(System.getenv("RUN_UMAMI_TEST") == "1")
        val website = System.getenv("UMAMI_WEBSITE_ID").orEmpty()
        assumeTrue(website.isNotBlank())
        val body = umamiEventBody(website, "test-${UUID.randomUUID()}", "umami_test", device)
        val connection = URL("https://umami.tx24.dev/api/send").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("User-Agent", umamiUserAgent(device))
        connection.outputStream.use { it.write(body.toByteArray()) }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        println("Umami: ${connection.responseCode} $response")
        assertEquals(200, connection.responseCode)
        // A bot verdict also answers 200, with this instead of a cache token.
        assertFalse(response.contains("beep"))
    }
}
