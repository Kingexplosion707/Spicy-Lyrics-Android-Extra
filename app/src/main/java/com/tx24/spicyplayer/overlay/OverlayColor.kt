package com.tx24.spicyplayer.overlay

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/** The overlay lyrics' colour: white as on the lyrics screen, a fixed colour, or the song's own. */
internal enum class OverlayColor(val label: String, val argb: Long?, /** A second colour, blended in toward the bottom. */ val argbEnd: Long? = null) {
    White("White", 0xFFFFFFFF),
    Song("From the cover", null),
    /** The cover's colour at the top, fading to white. */
    SongFade("Cover to white", null, 0xFFFFFFFF),
    /** One of the user's choosing (`AppSettings.customLyricsColor`). */
    Custom("Custom", null),
    Sunset("Sunset", 0xFFFF7EB6, 0xFFFFA94D),
    Ocean("Ocean", 0xFF8BE3D7, 0xFF74C0FC),
    Aurora("Aurora", 0xFF8CE99A, 0xFFB197FC),
    Candy("Candy", 0xFFFF7EB6, 0xFF74C0FC),
    Ember("Ember", 0xFFFFE066, 0xFFFF6B6B),
    Pink("Pink", 0xFFFF7EB6),
    Red("Red", 0xFFFF6B6B),
    Orange("Orange", 0xFFFFA94D),
    Yellow("Yellow", 0xFFFFE066),
    Green("Green", 0xFF8CE99A),
    Teal("Teal", 0xFF8BE3D7),
    Blue("Blue", 0xFF74C0FC),
    Purple("Purple", 0xFFB197FC);

    /** The colour to draw with: [cover]'s for [Song] (white without one). */
    fun resolve(cover: Bitmap?, custom: Int? = null): Color = when {
        argb != null -> Color(argb)
        this == Custom -> custom?.let { Color(it) } ?: Color.White
        else -> cover?.let(::songColor) ?: Color.White
    }

    /** Whether the colour comes from the song's cover. */
    val fromCover: Boolean get() = this == Song || this == SongFade

    companion object {
        const val PREF = "overlayColor"
        fun from(prefs: SharedPreferences): OverlayColor =
            runCatching { valueOf(prefs.getString(PREF, White.name)!!) }.getOrDefault(White)
    }
}

/**
 * The cover's most colourful tone, brightened so it reads on dark backgrounds: the average of
 * its saturated pixels, at a light, vivid lightness. White for a grey cover.
 */
internal fun songColor(cover: Bitmap): Color {
    val small = runCatching { Bitmap.createScaledBitmap(cover, 24, 24, true) }.getOrNull() ?: return Color.White
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var weight = 0.0
    val hsv = FloatArray(3)
    for (y in 0 until small.height) for (x in 0 until small.width) {
        val p = small.getPixel(x, y)
        android.graphics.Color.colorToHSV(p, hsv)
        val w = (hsv[1] * hsv[2]).toDouble()
        if (w < 0.15) continue
        r += android.graphics.Color.red(p) * w
        g += android.graphics.Color.green(p) * w
        b += android.graphics.Color.blue(p) * w
        weight += w
    }
    if (small !== cover) small.recycle()
    if (weight <= 0.0) return Color.White
    android.graphics.Color.RGBToHSV((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt(), hsv)
    hsv[1] = hsv[1].coerceIn(0.35f, 0.65f)
    hsv[2] = 1f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
