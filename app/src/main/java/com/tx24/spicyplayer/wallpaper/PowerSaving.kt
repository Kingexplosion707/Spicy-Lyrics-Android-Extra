package com.tx24.spicyplayer.wallpaper

import android.content.SharedPreferences

/**
 * When power saving is on: never, while the phone's battery saver is on (the default), or always.
 * What it cuts then is chosen in Settings → Device → Live wallpaper: the wallpaper's extras
 * ([PowerCuts]) and, if chosen, the music haptics ([PREF_NO_HAPTICS]).
 */
internal enum class PowerSaving(val label: String) {
    Off("Off"),
    BatterySaver("With battery saver"),
    Always("Always");

    /** Whether power saving is on now, with the phone's battery saver [batterySaver] on or off. */
    fun active(batterySaver: Boolean): Boolean = this == Always || (this == BatterySaver && batterySaver)

    /** What to cut now, with the phone's battery saver [batterySaver] on or off. */
    fun cuts(prefs: SharedPreferences, batterySaver: Boolean): PowerCuts {
        if (!active(batterySaver)) return PowerCuts.NONE
        return PowerCuts(
            active = true,
            noCanvas = prefs.getBoolean(PREF_NO_CANVAS, true),
            noAnimatedCover = prefs.getBoolean(PREF_NO_ANIMATED_COVER, true),
            simplerAnimation = prefs.getBoolean(PREF_SIMPLER, true),
            lineByLine = prefs.getBoolean(PREF_LINE_BY_LINE, false),
            stopAll = prefs.getBoolean(PREF_STOP_ALL, false),
        )
    }

    companion object {
        const val PREF = "wallpaperPowerSaving"
        const val PREF_NO_CANVAS = "wallpaperSaveNoCanvas"
        const val PREF_NO_ANIMATED_COVER = "wallpaperSaveNoAnimatedCover"
        const val PREF_SIMPLER = "wallpaperSaveSimpler"
        const val PREF_LINE_BY_LINE = "wallpaperSaveLineByLine"
        const val PREF_STOP_ALL = "wallpaperSaveStopAll"
        /** Power saving also stops the music haptics, in the app and in the background. */
        const val PREF_NO_HAPTICS = "wallpaperSaveNoHaptics"

        fun from(prefs: SharedPreferences): PowerSaving =
            runCatching { valueOf(prefs.getString(PREF, BatterySaver.name)!!) }.getOrDefault(BatterySaver)
    }
}

/**
 * What the wallpaper leaves out while saving power: the Canvas video, the animated cover, the
 * lyrics' extra effects (glow, distance blur, word lift), word-by-word timing (lines light as a
 * whole), or everything ([stopAll]: the picture for when nothing plays, else a still frame).
 */
internal data class PowerCuts(
    val active: Boolean,
    val noCanvas: Boolean,
    val noAnimatedCover: Boolean,
    val simplerAnimation: Boolean,
    val lineByLine: Boolean,
    val stopAll: Boolean,
) {
    companion object {
        val NONE = PowerCuts(false, false, false, false, false, false)
    }
}
