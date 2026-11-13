package com.tx24.spicyplayer.aod

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Where a Pixel's under-display fingerprint sensor is, so holding it on the always-on display
 * asks to unlock. Only Pixels: others put the sensor elsewhere or on the side.
 *
 * Android doesn't tell apps where the sensor is. It's found, best first: read from the phone
 * through Shizuku (`dumpsys fingerprint`, [findWithShizuku]); set by touching it once
 * ([saveTouched]); the framework's own setting, where a phone has one; or where Pixels put it.
 * A found spot is kept as fractions of the screen, so it holds at any resolution.
 */
internal object AodFingerprint {
    const val PREF = "aodFingerprintUnlock"
    private const val PREF_X = "aodFingerprintX"
    private const val PREF_Y = "aodFingerprintY"
    private const val PREF_R = "aodFingerprintR"

    class Area(val x: Float, val y: Float, val radius: Float) {
        fun contains(px: Float, py: Float): Boolean {
            val dx = px - x
            val dy = py - y
            return dx * dx + dy * dy <= radius * radius
        }
    }

    /** A Pixel with a fingerprint sensor under the screen (the 6 and later; not the Fold's side one). */
    fun supported(context: Context): Boolean {
        if (!Build.MANUFACTURER.equals("Google", ignoreCase = true) || !Build.MODEL.startsWith("Pixel", ignoreCase = true)) return false
        if (Build.MODEL.contains("Fold", ignoreCase = true) || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }

    /** Whether the spot has been found (through Shizuku or by touch), not guessed. */
    fun found(context: Context): Boolean = prefs(context).contains(PREF_X)

    /**
     * The sensor, in this window's pixels ([width] by [height], upright), made larger than the
     * sensor itself to be easy to find blind.
     */
    fun area(context: Context, display: Display?, width: Int, height: Int): Area {
        val prefs = prefs(context)
        if (prefs.contains(PREF_X)) {
            val r = prefs.getFloat(PREF_R, FALLBACK_RADIUS / GROW) * width * GROW
            return Area(prefs.getFloat(PREF_X, 0.5f) * width, prefs.getFloat(PREF_Y, FALLBACK_Y) * height, r.coerceAtLeast(width * MIN_RADIUS))
        }
        configured(display, width)?.let { return it }
        return Area(width / 2f, height * FALLBACK_Y, width * FALLBACK_RADIUS)
    }

    /** Keeps a spot touched on screen: [x] and [y] as fractions of the screen's width and height. */
    fun saveTouched(context: Context, x: Float, y: Float) {
        prefs(context).edit().putFloat(PREF_X, x).putFloat(PREF_Y, y).putFloat(PREF_R, TOUCHED_RADIUS).apply()
    }

    fun forget(context: Context) {
        prefs(context).edit().remove(PREF_X).remove(PREF_Y).remove(PREF_R).apply()
    }

    enum class Find(val message: String) {
        Ok("Found the fingerprint sensor"),
        NotRunning("Shizuku isn't running. Start it in the Shizuku app, then try again"),
        Denied("Shizuku permission was denied"),
        NotFound("The phone didn't say where its sensor is. Set it by touch instead"),
    }

    /**
     * Asks the phone where its sensor is, through Shizuku: `dumpsys fingerprint` lists each
     * sensor's location in the screen's full-resolution pixels.
     */
    suspend fun findWithShizuku(context: Context): Find {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return Find.NotRunning
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return Find.NotRunning
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) return Find.NotFound
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED &&
            !com.tx24.spicyplayer.overlay.StatusBarClock.requestShizukuPermission()
        ) return Find.Denied
        val dump = withContext(Dispatchers.IO) {
            runCatching {
                val newProcess = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
                ).apply { isAccessible = true }
                val process = newProcess.invoke(null, arrayOf("dumpsys", "fingerprint"), null, null) as Process
                process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
            }.getOrNull()
        } ?: return Find.NotFound
        val spot = parseLocation(dump) ?: return Find.NotFound
        val (nativeW, nativeH) = nativeSize(context) ?: return Find.NotFound
        prefs(context).edit()
            .putFloat(PREF_X, spot.first / nativeW)
            .putFloat(PREF_Y, spot.second / nativeH)
            .putFloat(PREF_R, (spot.third / nativeW).coerceAtLeast(0.03f))
            .apply()
        return Find.Ok
    }

    /**
     * The first sensor location in a `dumpsys fingerprint`: x, y and radius. Android words it
     * as `sensorLocationX=540, sensorLocationY=2160, sensorRadius=130` or, in a list of
     * locations, `SensorLocationInternal{display=..., x=540, y=2160, r=130}`.
     */
    internal fun parseLocation(dump: String): Triple<Float, Float, Float>? {
        val named = Regex("""sensorLocationX\s*[=:]\s*(\d+).*?sensorLocationY\s*[=:]\s*(\d+).*?sensorRadius\s*[=:]\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
        named.find(dump)?.let { m ->
            val (x, y, r) = m.destructured
            if (y.toInt() > 0) return Triple(x.toFloat(), y.toFloat(), r.toFloat())
        }
        val located = Regex("""SensorLocation\w*\s*[{\[(][^}\])]*?\bx\s*[=:]\s*(\d+)[^}\])]*?\by\s*[=:]\s*(\d+)[^}\])]*?\br\s*[=:]\s*(\d+)""")
        located.findAll(dump).forEach { m ->
            val (x, y, r) = m.destructured
            if (y.toInt() > 0) return Triple(x.toFloat(), y.toFloat(), r.toFloat())
        }
        return null
    }

    /** The screen's full resolution, upright: what the sensor's location is given in. */
    private fun nativeSize(context: Context): Pair<Float, Float>? {
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return null
        val mode = display.supportedModes.maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight } ?: return null
        val w = minOf(mode.physicalWidth, mode.physicalHeight).toFloat()
        val h = maxOf(mode.physicalWidth, mode.physicalHeight).toFloat()
        return w to h
    }

    /** The framework's own sensor location, `[x, y, radius]` at the screen's full resolution, scaled to [width]. */
    private fun configured(display: Display?, width: Int): Area? = runCatching {
        val res = Resources.getSystem()
        val id = res.getIdentifier("config_udfps_sensor_props", "array", "android")
        if (id == 0) return null
        val props = res.getIntArray(id)
        if (props.size < 3 || props[2] <= 0) return null
        val native = display?.supportedModes?.maxOfOrNull { minOf(it.physicalWidth, it.physicalHeight) } ?: width
        val scale = width.toFloat() / native.coerceAtLeast(1)
        Area(props[0] * scale, props[1] * scale, (props[2] * scale * GROW).coerceAtLeast(width * MIN_RADIUS))
    }.getOrNull()

    private fun prefs(context: Context) = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private const val FALLBACK_Y = 0.865f
    private const val FALLBACK_RADIUS = 0.15f
    /** A spot set by touch is where a fingertip landed, not the sensor's middle: a little wider. */
    private const val TOUCHED_RADIUS = 0.07f
    private const val MIN_RADIUS = 0.1f
    /** The touch area against the sensor's own size. */
    private const val GROW = 1.8f
}
