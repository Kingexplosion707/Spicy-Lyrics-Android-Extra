package com.tx24.spicyplayer.aod

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.ln
import kotlin.math.max

/**
 * How bright the always-on display is, as one level from 0 (darkest) to 100. The lower part goes
 * below the screen's own lowest brightness by dimming what's drawn (black stays black, so it
 * costs nothing); the upper part raises the screen's brightness. Settings → Device → Always-on
 * display: by the light sensor (dimmer in the dark, brighter in daylight, nudged by a bias) or a
 * level of your own.
 */
internal object AodBrightness {
    const val PREF_AUTO = "aodAutoBrightness"
    const val PREF_LEVEL = "aodLevel"
    const val PREF_BIAS = "aodBrightnessBias"
    const val DEFAULT_LEVEL = 15

    /** Up to here the screen sits at its lowest and only what's drawn dims. */
    private const val CONTENT_SPAN = 35f
    /** What's drawn at level 0: dim, but still readable in the dark. */
    private const val MIN_CONTENT_ALPHA = 0.28f
    /** The screen's own lowest (about 1/255) and the most it's raised to at 100. */
    private const val MIN_WINDOW = 0.004f
    private const val MAX_WINDOW = 0.6f

    /** The screen brightness for a level, for the window. */
    fun windowBrightness(level: Float): Float {
        if (level <= CONTENT_SPAN) return MIN_WINDOW
        val f = ((level - CONTENT_SPAN) / (100f - CONTENT_SPAN)).coerceIn(0f, 1f)
        // Squared, as the eye sees brightness: small steps at the dim end.
        return MIN_WINDOW + (MAX_WINDOW - MIN_WINDOW) * f * f
    }

    /** How much of what's drawn shows at a level (1 from [CONTENT_SPAN] up). */
    fun contentAlpha(level: Float): Float {
        if (level >= CONTENT_SPAN) return 1f
        val f = (level / CONTENT_SPAN).coerceIn(0f, 1f)
        return MIN_CONTENT_ALPHA + (1f - MIN_CONTENT_ALPHA) * f
    }

    /**
     * The level for the light around the phone, in lux, on a log scale: about 8 in a dark room,
     * 30 under a lamp, 50 in an office, 85 and up in daylight. [bias] (−40 to 40) shifts it.
     */
    fun levelForLux(lux: Float, bias: Int = 0): Float {
        val points = LUX_LEVELS
        val l = max(lux, 0f)
        val base = when {
            l <= points.first().first -> points.first().second
            l >= points.last().first -> points.last().second
            else -> {
                val i = points.indexOfLast { it.first <= l }
                val (x0, y0) = points[i]
                val (x1, y1) = points[i + 1]
                val f = ((ln(l + 1f) - ln(x0 + 1f)) / (ln(x1 + 1f) - ln(x0 + 1f))).coerceIn(0f, 1f)
                y0 + (y1 - y0) * f
            }
        }
        return (base + bias).coerceIn(0f, 100f)
    }

    private val LUX_LEVELS = listOf(
        0f to 4f,
        3f to 10f,
        15f to 22f,
        60f to 36f,
        250f to 50f,
        1_000f to 66f,
        5_000f to 84f,
        20_000f to 100f,
    )

    /**
     * Follows the light sensor while started, telling [onLux] the light now. Reports are
     * batched, and a change under a fifth either way is let go, so a sensor that jitters
     * doesn't keep the screen moving.
     */
    class LightFollower(context: Context, private val onLux: (Float) -> Unit) : SensorEventListener {
        private val sensors = context.getSystemService(SensorManager::class.java)
        private val light: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
        private var last = -1f

        val available: Boolean get() = light != null

        fun start() {
            val sensor = light ?: return
            last = -1f
            sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, BATCH_US)
        }

        fun stop() {
            sensors?.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {
            val lux = event.values.firstOrNull() ?: return
            if (last >= 0f && lux in last * 0.8f..last * 1.25f && kotlin.math.abs(lux - last) < 3f) return
            last = lux
            onLux(lux)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private companion object {
            const val BATCH_US = 2_000_000
        }
    }
}
