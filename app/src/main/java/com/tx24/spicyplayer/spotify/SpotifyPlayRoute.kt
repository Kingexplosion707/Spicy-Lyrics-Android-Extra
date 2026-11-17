package com.tx24.spicyplayer.spotify

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import io.music_assistant.client.data.play.PlayInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Music Assistant's Spotify songs, played through the Spotify account signed in here instead of
 * Music Assistant's stream:
 *
 * - for **this device**, straight on this phone's Spotify app (started if it isn't running), so
 *   the sound doesn't go from Spotify to the server and back. Home Assistant sees it through its
 *   Spotify integration and the Companion app's media session, as any Spotify playback;
 * - for **another player**, on the Spotify Connect device Music Assistant made for it (its
 *   Spotify Connect plugin names the device after the player), so Music Assistant plays it and
 *   shows it as playing there.
 *
 * Anything it can't do (no sign-in, no Spotify version of a song, no such device) goes to Music
 * Assistant as usual. Settings → Sources → "Music Assistant plays Spotify through your account".
 */
internal class SpotifyPlayRoute(context: Context) : PlayInterceptor {
    private val app = context.applicationContext
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    override fun handles(local: Boolean): Boolean =
        enabled(app) && SpotifyAccount.signedIn(app) && SpotifyAccount.canControl(app)

    override suspend fun play(
        local: Boolean,
        playerName: String?,
        uris: List<String>,
        startUri: String?,
        shuffle: Boolean,
        option: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val token = SpotifyAccount.accessToken(app) ?: return@withContext false
        val device = if (local) phoneDevice(token) else playerName?.let { namedDevice(token, it) }
        device ?: return@withContext false
        val body = JSONObject()
        val context = uris.singleOrNull()?.takeIf { !it.startsWith("spotify:track:") }
        if (context != null) {
            body.put("context_uri", context)
            startUri?.let { body.put("offset", JSONObject().put("uri", it)) }
        } else {
            body.put("uris", JSONArray(uris))
            startUri?.takeIf { it in uris }?.let { body.put("offset", JSONObject().put("uri", it)) }
        }
        if (shuffle) put(token, "https://api.spotify.com/v1/me/player/shuffle?state=true&device_id=$device", null)
        put(token, "https://api.spotify.com/v1/me/player/play?device_id=$device", body.toString())
    }

    /** This phone's Spotify app as a Connect device, starting the app when it isn't one yet. */
    private suspend fun phoneDevice(token: String): String? {
        findPhone(token)?.let { return it }
        val launch = app.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE) ?: return null
        runCatching { app.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }
            .getOrElse { return null }
        repeat(WAKE_TRIES) {
            delay(WAKE_STEP_MS)
            findPhone(token)?.let { return it }
        }
        return null
    }

    private fun findPhone(token: String): String? {
        val devices = devices(token)
        val phones = devices.filter { it.optString("type").equals("Smartphone", ignoreCase = true) }
        val names = listOfNotNull(
            runCatching { Settings.Global.getString(app.contentResolver, "device_name") }.getOrNull(),
            Build.MODEL,
        ).map { it.trim().lowercase() }
        return (phones.firstOrNull { it.optString("name").trim().lowercase() in names } ?: phones.singleOrNull())
            ?.optString("id")?.takeIf { it.isNotBlank() }
    }

    /** The Spotify Connect device named after a Music Assistant player. */
    private fun namedDevice(token: String, name: String): String? =
        devices(token).firstOrNull { it.optString("name").equals(name.trim(), ignoreCase = true) }
            ?.optString("id")?.takeIf { it.isNotBlank() }

    private fun devices(token: String): List<JSONObject> = runCatching {
        http.newCall(Request.Builder().url("https://api.spotify.com/v1/me/player/devices").header("Authorization", "Bearer $token").build())
            .execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val list = JSONObject(response.body.string()).optJSONArray("devices") ?: return emptyList()
                (0 until list.length()).mapNotNull { list.optJSONObject(it) }
            }
    }.getOrDefault(emptyList())

    private fun put(token: String, url: String, json: String?): Boolean = runCatching {
        val body = (json ?: "").toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").put(body).build())
            .execute().use { it.isSuccessful }
    }.getOrDefault(false)

    companion object {
        const val PREF = "maSpotifyRoute"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val WAKE_TRIES = 12
        private const val WAKE_STEP_MS = 750L

        fun enabled(context: Context): Boolean =
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, true)
    }
}
