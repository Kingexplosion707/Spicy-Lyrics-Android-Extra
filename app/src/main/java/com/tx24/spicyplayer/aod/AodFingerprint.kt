package com.tx24.spicyplayer.aod

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.Display

/**
 * Where a Pixel's under-display fingerprint sensor is, so holding it on the always-on display
 * leaves for the lock screen, whose sensor then unlocks. Only Pixels: others put the sensor
 * elsewhere or on the side.
 */
internal object AodFingerprint {
    const val PREF = "aodFingerprintUnlock"

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
        return context.packageManager.hasSystemFeature("android.hardware.fingerprint")
    }

    /**
     * The sensor, in this window's pixels ([width] by [height], upright), made larger than the
     * sensor itself to be easy to find blind. From the phone's own setting where it has one,
     * else where Pixels put it: centred, low on the screen.
     */
    fun area(display: Display?, width: Int, height: Int): Area {
        configured(display, width)?.let { return it }
        return Area(width / 2f, height * FALLBACK_Y, width * FALLBACK_RADIUS)
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
        Area(props[0] * scale, props[1] * scale, (props[2] * scale * GROW).coerceAtLeast(width * 0.08f))
    }.getOrNull()

    private const val FALLBACK_Y = 0.865f
    private const val FALLBACK_RADIUS = 0.15f
    /** The touch area against the sensor's own size. */
    private const val GROW = 1.8f
}
