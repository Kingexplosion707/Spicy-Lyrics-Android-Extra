package com.tx24.spicyplayer.stats

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Scrobbling to ListenBrainz (Settings → Listening stats → Scrobble to ListenBrainz, off until
 * switched on with the user's own token). [ListeningTracker] hands over each song once enough of
 * it has been heard ([ListenBrainz.listenAfterMs]); it's sent then, and what couldn't be sent
 * (offline) waits in a small file and goes with the next one.
 *
 * The token is kept in its own private preferences, never in the settings export.
 */
internal object Scrobbler {
    /** The switch, in the "ui" preferences. */
    const val PREF = "scrobbleListenBrainz"
    private const val PREFS = "listenbrainz"
    private const val QUEUE_FILE = "listenbrainz-queue.json"
    /** The most listens kept waiting: older ones go first. */
    private const val MAX_QUEUED = 200

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(context: Context): String = prefs(context).getString("token", "").orEmpty()

    fun setToken(context: Context, token: String) {
        prefs(context).edit().putString("token", token.trim()).apply()
    }

    /** Switched on, with a token to send with. */
    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, false) &&
            token(context).isNotBlank()

    /** Sends [listen] (and anything still waiting); keeps it waiting when that can't be done now. */
    fun submit(context: Context, listen: ListenBrainz.Listen) {
        val app = context.applicationContext
        if (!enabled(app)) return
        worker.execute {
            val queue = (ListenBrainz.queueFromJson(runCatching { file(app).readText() }.getOrNull()) + listen).takeLast(MAX_QUEUED)
            val sent = send(app, queue.take(ListenBrainz.MAX_PER_REQUEST))
            val left = if (sent) queue.drop(ListenBrainz.MAX_PER_REQUEST) else queue
            runCatching {
                if (left.isEmpty()) file(app).delete() else file(app).writeText(ListenBrainz.queueToJson(left))
            }
        }
    }

    /** True when ListenBrainz took the listens, or refused them for good (a bad request isn't worth keeping). */
    private fun send(app: Context, listens: List<ListenBrainz.Listen>): Boolean {
        val body = ListenBrainz.payload(listens) ?: return true
        val request = Request.Builder().url(ListenBrainz.SUBMIT_URL)
            .header("Authorization", "Token ${token(app)}")
            .post(body.toRequestBody(json))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response -> response.isSuccessful || response.code == 400 }
        }.getOrDefault(false)
    }

    /** The account [token] belongs to, or null when ListenBrainz doesn't know it (or can't be reached). */
    suspend fun validate(token: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ListenBrainz.VALIDATE_URL).header("Authorization", "Token ${token.trim()}").build()
        runCatching {
            client.newCall(request).execute().use { response -> ListenBrainz.validatedUser(response.body?.string()) }
        }.getOrNull()
    }

    private fun file(context: Context) = File(context.filesDir, QUEUE_FILE)
}
