package com.tx24.spicyplayer.spotify

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * The user's own Spotify account, through their own Spotify app (a Client ID they make at
 * developer.spotify.com, Settings → Device → Always-on display). It signs in with Spotify's
 * authorization code flow with PKCE: Spotify's own sign-in page, no client secret, and only the
 * permissions the always-on display's Add to playlist button needs: reading what's playing and
 * adding to playlists ([SpotifyPlaylist]).
 *
 * Its tokens are kept in their own private preferences, never in the settings export, and the
 * app is never backed up.
 */
internal object SpotifyAccount {
    const val REDIRECT_URI = "spicylyricsplus://callback"
    const val DEVELOPER_URL = "https://developer.spotify.com/dashboard"
    private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val SCOPE = "user-read-playback-state playlist-modify-public playlist-modify-private"
    private const val PREFS = "spotify_account"

    internal val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    private val refreshing = Mutex()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clientId(context: Context): String = prefs(context).getString("clientId", "").orEmpty()

    fun setClientId(context: Context, id: String) {
        val trimmed = id.trim()
        if (trimmed == clientId(context)) return
        // Tokens belong to the Spotify app that issued them.
        prefs(context).edit().clear().putString("clientId", trimmed).apply()
    }

    fun signedIn(context: Context): Boolean = prefs(context).getString("refreshToken", null) != null

    /** The signed-in account's name, when Spotify gave it. */
    fun accountName(context: Context): String? = prefs(context).getString("accountName", null)

    fun logOut(context: Context) {
        val id = clientId(context)
        prefs(context).edit().clear().putString("clientId", id).apply()
    }

    /** Spotify's sign-in page for [clientId], remembering what's needed to finish it. */
    fun loginUri(context: Context): Uri? {
        val id = clientId(context).takeIf { it.isNotBlank() } ?: return null
        val random = SecureRandom()
        val verifier = ByteArray(48).also(random::nextBytes).base64Url()
        val state = ByteArray(16).also(random::nextBytes).base64Url()
        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()).base64Url()
        prefs(context).edit().putString("verifier", verifier).putString("state", state).apply()
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", id)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .build()
    }

    /** Finishes signing in from Spotify's redirect; a message to show when it didn't work. */
    suspend fun finishLogin(context: Context, redirect: Uri): String? = withContext(Dispatchers.IO) {
        val prefs = prefs(context)
        redirect.getQueryParameter("error")?.let { return@withContext "Spotify said: $it" }
        val code = redirect.getQueryParameter("code") ?: return@withContext "Spotify sent no sign-in code."
        if (redirect.getQueryParameter("state") != prefs.getString("state", null)) {
            return@withContext "This sign-in wasn't started here. Try again from Settings."
        }
        val verifier = prefs.getString("verifier", null) ?: return@withContext "Start signing in from Settings."
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId(context))
            .add("code_verifier", verifier)
            .build()
        val error = runCatching { takeTokens(context, post(body)) }.exceptionOrNull()
        prefs.edit().remove("verifier").remove("state").apply()
        if (error != null) return@withContext error.message ?: "Couldn't reach Spotify."
        accessToken(context)?.let { token -> runCatching { fetchName(context, token) } }
        null
    }

    /** A current access token, refreshed when it has run out; null when signed out or refused. */
    suspend fun accessToken(context: Context): String? = refreshing.withLock {
        withContext(Dispatchers.IO) {
            val prefs = prefs(context)
            val refresh = prefs.getString("refreshToken", null) ?: return@withContext null
            val token = prefs.getString("accessToken", null)
            if (token != null && System.currentTimeMillis() < prefs.getLong("expiresAt", 0L) - 60_000L) return@withContext token
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", clientId(context))
                .build()
            runCatching { takeTokens(context, post(body)) }
                .onFailure { if (it is Refused) logOut(context) }
                .getOrNull()
        }
    }

    private class Refused(message: String) : Exception(message)

    private fun post(body: FormBody): JSONObject {
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()
            if (!response.isSuccessful) {
                val reason = json.optString("error_description").ifBlank { json.optString("error") }.ifBlank { "HTTP ${response.code}" }
                if (response.code in 400..499) throw Refused("Spotify refused: $reason")
                throw IllegalStateException("Spotify answered: $reason")
            }
            return json
        }
    }

    /** Stores the tokens from a token answer; returns the access token. */
    private fun takeTokens(context: Context, json: JSONObject): String {
        val token = json.getString("access_token")
        val edit = prefs(context).edit()
            .putString("accessToken", token)
            .putLong("expiresAt", System.currentTimeMillis() + json.optLong("expires_in", 3_600L) * 1_000L)
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { edit.putString("refreshToken", it) }
        edit.apply()
        return token
    }

    private fun fetchName(context: Context, token: String) {
        val request = Request.Builder().url("https://api.spotify.com/v1/me").header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val json = JSONObject(response.body?.string().orEmpty())
            json.optString("display_name").ifBlank { json.optString("id") }.takeIf { it.isNotBlank() }
                ?.let { prefs(context).edit().putString("accountName", it).apply() }
        }
    }

    private fun ByteArray.base64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

/**
 * Adds the song playing to the playlist it's playing from (a song Smart Shuffle or a
 * recommendation slipped in, say), unless it's there already. Spotify lets an app change only
 * the playlists the account owns or collaborates on.
 */
internal object SpotifyPlaylist {
    sealed class Result(val message: String) {
        class Added(name: String) : Result("Added to $name")
        class Already(name: String) : Result("Already in $name")
        class Failed(message: String) : Result(message)
    }

    private const val API = "https://api.spotify.com/v1"
    private const val MAX_PAGES = 20

    suspend fun addPlayingToPlaylist(context: Context): Result = withContext(Dispatchers.IO) {
        val token = SpotifyAccount.accessToken(context) ?: return@withContext Result.Failed("Sign in to Spotify in Settings first")
        runCatching {
            val player = get(token, "$API/me/player") ?: return@runCatching Result.Failed("Spotify isn't playing anything")
            val track = (player.optJSONObject("item"))?.optString("uri")?.takeIf { it.startsWith("spotify:track:") }
                ?: return@runCatching Result.Failed("This isn't a song Spotify can add")
            val playing = player.optJSONObject("context")
            if (playing?.optString("type") != "playlist") return@runCatching Result.Failed("Not playing from a playlist")
            val id = playing.optString("uri").substringAfterLast(':').takeIf { it.isNotBlank() }
                ?: return@runCatching Result.Failed("Not playing from a playlist")
            val name = get(token, "$API/playlists/$id?fields=name")?.optString("name")?.takeIf { it.isNotBlank() } ?: "the playlist"
            if (contains(token, id, track)) return@runCatching Result.Already(name)
            val body = JSONObject().put("uris", org.json.JSONArray().put(track)).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url("$API/playlists/$id/items").header("Authorization", "Bearer $token").post(body).build()
            SpotifyAccount.client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.Added(name)
                    response.code == 403 -> Result.Failed("Spotify only lets you add to your own or shared playlists")
                    response.code == 401 -> Result.Failed("Sign in to Spotify again in Settings")
                    else -> Result.Failed("Spotify answered ${response.code}")
                }
            }
        }.getOrElse { Result.Failed("Couldn't reach Spotify") }
    }

    /** Whether [track] is in the playlist already, page by page; false when Spotify won't list it. */
    private fun contains(token: String, id: String, track: String): Boolean {
        var url: String? = "$API/playlists/$id/items?limit=100"
        var pages = 0
        while (url != null && pages++ < MAX_PAGES) {
            val page = get(token, url) ?: return false
            val items = page.optJSONArray("items") ?: return false
            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                val uri = (entry.optJSONObject("item") ?: entry.optJSONObject("track"))?.optString("uri")
                if (uri == track) return true
            }
            url = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        return false
    }

    private fun get(token: String, url: String): JSONObject? {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        SpotifyAccount.client.newCall(request).execute().use { response ->
            if (response.code != 200) return null
            return runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
        }
    }
}
