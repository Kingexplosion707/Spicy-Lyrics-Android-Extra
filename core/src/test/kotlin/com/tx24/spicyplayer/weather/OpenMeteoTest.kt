package com.tx24.spicyplayer.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoTest {
    private val answer = """
        {"latitude":43.7,"longitude":-79.4,"current":{"time":"2026-11-12T15:45","interval":900,
        "temperature_2m":6.4,"weather_code":61,"is_day":1},
        "daily":{"time":["2026-11-12"],"temperature_2m_max":[8.1],"temperature_2m_min":[-1.6]}}
    """.trimIndent()

    @Test fun theUrlRoundsThePosition() {
        val url = OpenMeteo.url(43.653226, -79.383184)
        assertTrue(url, url.contains("latitude=43.65&longitude=-79.38"))
        assertTrue(url.contains("current=temperature_2m,weather_code,is_day"))
    }

    @Test fun anAnswerParses() {
        val weather = OpenMeteo.parse(answer, 5L)!!
        assertEquals(6.4f, weather.temperatureC, 0.01f)
        assertEquals(8.1f, weather.highC, 0.01f)
        assertEquals(-1.6f, weather.lowC, 0.01f)
        assertEquals(OpenMeteo.Sky.Rain, weather.sky)
        assertTrue(weather.isDay)
        assertEquals(weather, OpenMeteo.Weather.fromJson(weather.toJson()))
        assertNull(OpenMeteo.parse("{}", 0L))
        assertNull(OpenMeteo.parse(null, 0L))
        assertNull(OpenMeteo.Weather.fromJson("nope"))
    }

    @Test fun codesMapToSkies() {
        assertEquals(OpenMeteo.Sky.Clear, OpenMeteo.sky(0))
        assertEquals(OpenMeteo.Sky.PartlyCloudy, OpenMeteo.sky(2))
        assertEquals(OpenMeteo.Sky.Fog, OpenMeteo.sky(45))
        assertEquals(OpenMeteo.Sky.Snow, OpenMeteo.sky(75))
        assertEquals(OpenMeteo.Sky.Storm, OpenMeteo.sky(95))
        assertEquals(OpenMeteo.Sky.Cloudy, OpenMeteo.sky(1234))
    }

    @Test fun degreesRound() {
        assertEquals("6°", OpenMeteo.degrees(6.4f, false))
        assertEquals("44°", OpenMeteo.degrees(6.4f, true))
        assertEquals("0°", OpenMeteo.degrees(-0.4f, false))
        assertTrue(OpenMeteo.usesFahrenheit("us"))
        assertFalse(OpenMeteo.usesFahrenheit("CA"))
    }
}
