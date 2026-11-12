package com.tx24.spicyplayer.weather

import com.google.gson.JsonParser
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The weather on the always-on display, from Open-Meteo (free, no key, no account): the current
 * temperature and conditions where the phone is, and today's high and low. Only a rounded
 * position (about 1 km) is sent.
 */
object OpenMeteo {
    /** What the weather is doing, as the always-on display draws it. */
    enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

    data class Weather(
        val temperatureC: Float,
        val highC: Float,
        val lowC: Float,
        val sky: Sky,
        val isDay: Boolean,
        /** When it was fetched, in wall-clock ms. */
        val fetchedAtMs: Long,
    ) {
        fun toJson(): String =
            """{"t":$temperatureC,"h":$highC,"l":$lowC,"s":"${sky.name}","d":$isDay,"at":$fetchedAtMs}"""

        companion object {
            fun fromJson(json: String?): Weather? = runCatching {
                val o = JsonParser.parseString(json.orEmpty()).asJsonObject
                Weather(
                    o.get("t").asFloat, o.get("h").asFloat, o.get("l").asFloat,
                    Sky.valueOf(o.get("s").asString), o.get("d").asBoolean, o.get("at").asLong,
                )
            }.getOrNull()
        }
    }

    /** The forecast URL for a position, rounded to two decimals (about a kilometre). */
    fun url(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${round2(latitude)}&longitude=${round2(longitude)}" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_days=1&timezone=auto"

    private fun round2(value: Double) = String.format(Locale.US, "%.2f", value)

    /** Open-Meteo's answer as [Weather], or null when it isn't one. */
    fun parse(json: String?, nowMs: Long): Weather? = runCatching {
        val root = JsonParser.parseString(json.orEmpty()).asJsonObject
        val current = root.getAsJsonObject("current")
        val daily = root.getAsJsonObject("daily")
        Weather(
            temperatureC = current.get("temperature_2m").asFloat,
            highC = daily.getAsJsonArray("temperature_2m_max")[0].asFloat,
            lowC = daily.getAsJsonArray("temperature_2m_min")[0].asFloat,
            sky = sky(current.get("weather_code").asInt),
            isDay = current.get("is_day").asInt == 1,
            fetchedAtMs = nowMs,
        )
    }.getOrNull()

    /** WMO weather interpretation codes, as Open-Meteo gives them. */
    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.Clear
        2 -> Sky.PartlyCloudy
        3 -> Sky.Cloudy
        45, 48 -> Sky.Fog
        51, 53, 55, 56, 57 -> Sky.Drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.Rain
        71, 73, 75, 77, 85, 86 -> Sky.Snow
        95, 96, 99 -> Sky.Storm
        else -> Sky.Cloudy
    }

    /** A temperature for showing: whole degrees, in Fahrenheit when [fahrenheit]. */
    fun degrees(celsius: Float, fahrenheit: Boolean): String {
        val value = if (fahrenheit) celsius * 9f / 5f + 32f else celsius
        // -0.4 rounds to 0, not "-0".
        val rounded = value.roundToInt().let { if (it == 0) 0 else it }
        return "$rounded°"
    }

    /** Whether a locale's country reads temperatures in Fahrenheit. */
    fun usesFahrenheit(country: String): Boolean = country.uppercase(Locale.US) in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
}
