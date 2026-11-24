package com.tx24.spicyplayer.ivlyrics

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How much motion lyrics move: ivLyrics' motion intensity. */
enum class MotionIntensity(val label: String, internal val id: String) {
    Calm("Calm", "calm"),
    Normal("Normal", "normal"),
    Wild("Wild", "wild"),
}

/** How far motion lyrics' camera turns: ivLyrics' screen rotation. */
enum class MotionRotation(val label: String, internal val id: String) {
    Full("Full", "full"),
    Gentle("Gentle", "gentle"),
    Off("Off", "off"),
}

/**
 * The viewer's comfort for motion lyrics, read from the `ui` preferences ([KEYS], written by
 * [com.tx24.spicyplayer.ui.settings.AppSettings]) so every surface that shows them follows it.
 */
internal data class MotionComfort(
    val intensity: MotionIntensity = MotionIntensity.Normal,
    val rotation: MotionRotation = MotionRotation.Full,
    /** The photosensitivity-safe mode: no flashes or flicker. */
    val safe: Boolean = false,
) {
    companion object {
        const val INTENSITY = "motionIntensity"
        const val ROTATION = "motionRotation"
        const val SAFE = "motionSafe"
        val KEYS = setOf(INTENSITY, ROTATION, SAFE)

        fun read(prefs: SharedPreferences) = MotionComfort(
            intensity = runCatching { MotionIntensity.valueOf(prefs.getString(INTENSITY, null)!!) }.getOrDefault(MotionIntensity.Normal),
            rotation = runCatching { MotionRotation.valueOf(prefs.getString(ROTATION, null)!!) }.getOrDefault(MotionRotation.Full),
            safe = runCatching { prefs.getBoolean(SAFE, false) }.getOrDefault(false),
        )
    }
}

/**
 * "Direct again": each request has every motion lyrics page on screen (lyrics screen, wallpaper,
 * overlay, always-on display) compose the playing song anew, as another take.
 */
internal object MotionRedirect {
    private val count = MutableStateFlow(0)
    val requests: StateFlow<Int> = count

    fun request() {
        count.value++
    }
}
