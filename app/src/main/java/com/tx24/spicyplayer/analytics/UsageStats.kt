package com.tx24.spicyplayer.analytics

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.ui.settings.AppSettings
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import com.tx24.spicyplayer.network.data.counted
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Anonymous usage stats, sent to the project's own Umami. Only release builds with a website id
 * send, only once the person has seen the notice, and only while the setting is on. Nothing about
 * what's playing is ever sent; README → Privacy lists every field.
 */
object UsageStats {
    /** The `ui` preferences, where the setting and the notice's answer live (`AppSettings`). */
    const val KEY_ENABLED = "usageStats"
    const val KEY_ASKED = "usageStatsAsked"

    private const val KEY_INSTALL_ID = "installId"
    private const val KEY_LAST_OPEN = "lastAppOpen"
    private const val KEY_LAST_DAILY = "lastDaily"
    private const val KEY_LAST_USAGE = "lastUsage"
    private const val COUNT_PREFIX = "count."
    private const val ENDPOINT = "https://umami.tx24.dev/api/send"

    /** Whether the notice and setting show: any build with a website id, so debug builds can test them. */
    val available: Boolean get() = BuildConfig.UMAMI_WEBSITE_ID.isNotBlank()

    /** Whether this build actually sends: release builds only. */
    private val sends: Boolean get() = available && BuildConfig.USAGE_STATS

    private val http by lazy {
        OkHttpClient.Builder().counted().callTimeout(15, TimeUnit.SECONDS).build()
    }

    /** The app's context, for counting from places that have none at hand. */
    private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /**
     * Reports an app open and the counts since the last report, each at most every few hours, and
     * once a day the settings in use ([lookup] adds the lookup settings the view model holds).
     */
    fun appOpened(context: Context, lookup: () -> Map<String, Any> = { emptyMap() }) {
        val app = context.applicationContext
        if (!allowed(app)) return
        val store = store(app)
        val now = System.currentTimeMillis()
        if (appOpenDue(store.getLong(KEY_LAST_OPEN, 0L), now)) {
            val prerelease = ui(app).getBoolean("includePrereleases", BuildConfig.VERSION_NAME.startsWith("0."))
            send(app, "app_open", mapOf("channel" to if (prerelease) "prerelease" else "stable")) {
                store.edit().putLong(KEY_LAST_OPEN, now).apply()
            }
        }
        if (dailyDue(store.getLong(KEY_LAST_DAILY, 0L), now)) {
            send(app, "daily_config", configSnapshot(AppSettings(ui(app))) + lookup()) {
                store.edit().putLong(KEY_LAST_DAILY, now).apply()
            }
        }
        // As often as app opens, so someone who uses the app once and leaves still gets counted.
        val counts = counts(store)
        if (counts.isNotEmpty() && appOpenDue(store.getLong(KEY_LAST_USAGE, 0L), now)) {
            send(app, "daily_usage", dailyUsageData(counts)) {
                subtract(store, counts)
                store.edit().putLong(KEY_LAST_USAGE, now).apply()
            }
        }
    }

    /** Counts one use of something ([UsageCounter]); kept on the phone until the next report. */
    fun count(name: String) {
        val app = app ?: return
        if (!allowed(app)) return
        val store = store(app)
        synchronized(this) {
            val key = COUNT_PREFIX + name
            store.edit().putInt(key, store.getInt(key, 0) + 1).apply()
        }
    }

    /** One song's lookup ending: which lyrics it got, from which source, in which player. */
    fun countSong(outcome: String, provider: String?, player: String?) {
        count(UsageCounter.SONGS)
        count(UsageCounter.lyrics(outcome))
        if (provider != null) count(UsageCounter.source(provider))
        if (player != null) count(UsageCounter.player(player))
    }

    /** Turning the setting off also forgets the install id and the counts; turning it back on starts a new visitor. */
    fun onSettingChanged(context: Context, enabled: Boolean) {
        if (!enabled) store(context.applicationContext).edit().clear().apply()
    }

    private fun counts(store: SharedPreferences): Map<String, Int> = synchronized(this) {
        store.all.filterKeys { it.startsWith(COUNT_PREFIX) }
            .mapNotNull { (key, value) -> (value as? Int)?.let { key.removePrefix(COUNT_PREFIX) to it } }
            .toMap()
    }

    /** Takes off what was sent, keeping anything counted while it was on its way. */
    private fun subtract(store: SharedPreferences, sent: Map<String, Int>) = synchronized(this) {
        store.edit().apply {
            sent.forEach { (name, value) ->
                val key = COUNT_PREFIX + name
                val left = store.getInt(key, 0) - value
                if (left > 0) putInt(key, left) else remove(key)
            }
        }.apply()
    }

    private fun allowed(context: Context): Boolean {
        if (!sends) return false
        val ui = ui(context)
        return ui.getBoolean(KEY_ASKED, false) && ui.getBoolean(KEY_ENABLED, true)
    }

    /** Events on their way, so an app reopened before an answer doesn't send them twice. */
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun send(context: Context, name: String, data: Map<String, Any>, onSent: () -> Unit) {
        if (!inFlight.add(name)) return
        val device = deviceInfo(context)
        val body = umamiEventBody(BuildConfig.UMAMI_WEBSITE_ID, installId(context), name, device, data)
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", umamiUserAgent(device))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                inFlight.remove(name)
                Timber.d(e, "Usage stats: %s not sent", name)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { if (it.isSuccessful) onSent() else Timber.d("Usage stats: %s got %d", name, it.code) }
                inFlight.remove(name)
            }
        })
    }

    private fun installId(context: Context): String {
        val store = store(context)
        store.getString(KEY_INSTALL_ID, null)?.let { return it }
        return UUID.randomUUID().toString().also { store.edit().putString(KEY_INSTALL_ID, it).apply() }
    }

    private fun deviceInfo(context: Context): DeviceInfo {
        val config = context.resources.configuration
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) }
        val model = Build.MODEL.orEmpty()
        return DeviceInfo(
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            androidSdk = Build.VERSION.SDK_INT,
            model = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim(),
            language = Locale.getDefault().toLanguageTag(),
            screenWidthDp = minOf(config.screenWidthDp, config.screenHeightDp),
            screenHeightDp = maxOf(config.screenWidthDp, config.screenHeightDp),
            tablet = config.smallestScreenWidthDp >= 600 ||
                config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE,
        )
    }

    private fun ui(context: Context): SharedPreferences = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private fun store(context: Context): SharedPreferences = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
}
