package com.tx24.spicyplayer.analytics

import com.google.gson.JsonObject

/** The device facts every usage event carries; none of them identify the person. */
data class DeviceInfo(
    val appVersion: String,
    val appBuild: Int,
    /** Android's version name ("14") and API level (34). */
    val androidRelease: String,
    val androidSdk: Int,
    /** Manufacturer and model, e.g. "Google Pixel 8". */
    val model: String,
    /** BCP 47, e.g. "en-GB". */
    val language: String,
    /** The screen in dp, width × height, the way a browser reports it in CSS pixels. */
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val tablet: Boolean,
)

/**
 * One event in the shape Umami's `/api/send` takes. [installId] is a random UUID made on the
 * phone; Umami turns it into one visitor per install, so a phone changing networks still counts once.
 */
fun umamiEventBody(
    websiteId: String,
    installId: String,
    name: String,
    device: DeviceInfo,
    data: Map<String, Any> = emptyMap(),
): String {
    val payload = JsonObject().apply {
        addProperty("website", websiteId)
        addProperty("id", installId)
        addProperty("hostname", "spicy-lyrics-mobile")
        addProperty("url", "/")
        addProperty("title", "Spicy Lyrics Mobile")
        addProperty("name", name)
        addProperty("language", device.language)
        addProperty("screen", "${device.screenWidthDp}x${device.screenHeightDp}")
        // Given outright, so the dashboard doesn't depend on Umami parsing the user agent.
        addProperty("os", "Android OS")
        addProperty("browser", "Spicy Lyrics Mobile")
        addProperty("device", if (device.tablet) "tablet" else "mobile")
        add("data", JsonObject().apply {
            addProperty("version", device.appVersion)
            addProperty("build", device.appBuild)
            addProperty("android", device.androidRelease)
            addProperty("sdk", device.androidSdk)
            addProperty("model", device.model)
            data.forEach { (key, value) ->
                when (value) {
                    is Number -> addProperty(key, value)
                    is Boolean -> addProperty(key, value)
                    else -> addProperty(key, value.toString())
                }
            }
        })
    }
    return JsonObject().apply {
        addProperty("type", "event")
        add("payload", payload)
    }.toString()
}

/**
 * Umami drops a request with no user agent (answering 200 anyway) and one that reads as a bot,
 * so this looks like a phone's browser with the app's name on the end.
 */
fun umamiUserAgent(device: DeviceInfo): String =
    "Mozilla/5.0 (Linux; Android ${device.androidRelease}; ${if (device.tablet) "Tablet" else "Mobile"}) " +
        "SpicyLyricsMobile/${device.appVersion}"

/** How often an app open is reported: often enough for daily counts, rarely enough to be cheap. */
const val APP_OPEN_INTERVAL_MS = 6 * 60 * 60 * 1000L

/** Whether an app open is due, [lastSentMs] being the last one Umami took (0 for never). */
fun appOpenDue(lastSentMs: Long, nowMs: Long): Boolean =
    lastSentMs <= 0L || nowMs - lastSentMs >= APP_OPEN_INTERVAL_MS || nowMs < lastSentMs
