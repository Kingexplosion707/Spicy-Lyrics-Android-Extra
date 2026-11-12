package com.tx24.spicyplayer.aod

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.tx24.spicyplayer.weather.OpenMeteo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The weather on the always-on display ([OpenMeteo]): fetched when the display comes up and the
 * last one is half an hour old, from the phone's last known rough position (no GPS is switched
 * on for it). Kept in its own preferences between showings.
 */
internal object AodWeather {
    const val PREF = "aodWeather"
    /** [com.tx24.spicyplayer.ui.settings.TemperatureUnit]'s name. */
    const val PREF_UNIT = "aodWeatherUnit"
    private const val STORE = "aod_weather"
    private const val FRESH_MS = 30 * 60_000L
    /** Older than this, it isn't shown at all. */
    private const val SHOW_MS = 3 * 60 * 60_000L

    val current = mutableStateOf<OpenMeteo.Weather?>(null)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean(PREF, false) && hasPermission(context)

    fun fahrenheit(context: Context): Boolean =
        when (context.getSharedPreferences("ui", Context.MODE_PRIVATE).getString(PREF_UNIT, null)) {
            "Fahrenheit" -> true
            "Celsius" -> false
            else -> OpenMeteo.usesFahrenheit(Locale.getDefault().country)
        }

    /** The last weather, from memory or disk, if it isn't too old to show. */
    fun load(context: Context): OpenMeteo.Weather? {
        val weather = current.value ?: OpenMeteo.Weather.fromJson(
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("last", null),
        )
        current.value = weather?.takeIf { System.currentTimeMillis() - it.fetchedAtMs < SHOW_MS }
        return current.value
    }

    /** Fetches it again when it's half an hour old (or never fetched). */
    suspend fun refreshIfStale(context: Context) {
        if (!enabled(context)) {
            current.value = null
            return
        }
        val last = load(context)
        if (last != null && System.currentTimeMillis() - last.fetchedAtMs < FRESH_MS) return
        val location = lastKnownLocation(context) ?: return
        val fetched = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(OpenMeteo.url(location.latitude, location.longitude)).build()).execute().use { response ->
                    if (!response.isSuccessful) null else OpenMeteo.parse(response.body?.string(), System.currentTimeMillis())
                }
            }.getOrNull()
        } ?: return
        current.value = fetched
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString("last", fetched.toJson()).apply()
    }

    /** The newest position any provider already has, without asking for a new one. */
    @Suppress("MissingPermission")
    private fun lastKnownLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }
}
