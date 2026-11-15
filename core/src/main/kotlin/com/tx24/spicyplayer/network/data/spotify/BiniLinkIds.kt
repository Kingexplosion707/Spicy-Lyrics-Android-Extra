package com.tx24.spicyplayer.network.data.spotify

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tx24.spicyplayer.network.data.BackoffLadder
import com.tx24.spicyplayer.network.data.RetryAfterParser
import com.tx24.spicyplayer.network.data.awaitResponse
import com.tx24.spicyplayer.platform.CoreLog
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A track's ISRC from BiniLink's public ID lookup, which takes no key. It only maps exact IDs (it
 * has no title search), so it can't stand in for the match itself. Null for anything but a clean
 * answer: not knowing a track here doesn't mean it has no ISRC, so the caller asks elsewhere.
 */
class BiniLinkIds(client: OkHttpClient, private val gson: Gson) {
    private val deadlineClient = client.newBuilder().callTimeout(DEADLINE_S, TimeUnit.SECONDS).build()
    private val backoff = BackoffLadder()

    suspend fun isrc(spotifyId: String): String? {
        if (backoff.openUntil(System.currentTimeMillis()) != null) return null
        val url = URL.toHttpUrl().newBuilder().addPathSegment(spotifyId).build()
        return try {
            withContext(Dispatchers.IO) {
                deadlineClient.newCall(Request.Builder().url(url).get().build()).awaitResponse().use { response ->
                    when {
                        response.isSuccessful -> {
                            backoff.success()
                            gson.fromJson(response.body?.string(), JsonObject::class.java)
                                ?.get("isrc")?.takeIf { it.isJsonPrimitive }?.asString?.uppercase()
                        }
                        // Their answer about this one track, not about the service.
                        response.code == 400 || response.code == 404 || response.code == 422 -> {
                            backoff.success()
                            null
                        }
                        else -> {
                            backoff.failure(System.currentTimeMillis(), response.header("Retry-After")?.let(RetryAfterParser::deadline)?.toEpochMilli())
                            CoreLog.d("BiniLink") { "HTTP ${response.code}" }
                            null
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            backoff.failure(System.currentTimeMillis())
            CoreLog.d("BiniLink") { "failed: ${error.message}" }
            null
        } catch (error: Exception) {
            CoreLog.d("BiniLink") { "unreadable answer: ${error.message}" }
            null
        }
    }

    private companion object {
        const val URL = "https://link.binimum.org/api/v1/resolve/spotify"
        const val DEADLINE_S = 5L
    }
}
