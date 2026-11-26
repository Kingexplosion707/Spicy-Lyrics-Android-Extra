package com.tx24.spicyplayer.overlay

import android.content.Context

/** The floating overlay is a phone's (the desktop window can stay on top instead). */
class LyricsOverlayService {
    companion object { fun sync(context: Context): Boolean = false }
}

/** What the overlay looks like: the lyrics as on the lyrics screen, or one line in the status bar. */
internal enum class OverlayStyle(val label: String) {
    Full("Lyrics"),
    StatusBar("Status bar");

    companion object {
        const val PREF = "overlayStyle"
        fun from(prefs: android.content.SharedPreferences): OverlayStyle =
            runCatching { valueOf(prefs.getString(PREF, Full.name)!!) }.getOrDefault(Full)
    }
}

/** How big the overlay is: its height, and its lyrics' size against the lyrics screen's. */
internal enum class OverlaySize(val label: String, val heightDp: Int, val fontScale: Float) {
    Small("Small", 130, 0.62f),
    Medium("Medium", 180, 0.78f),
    Large("Large", 240, 0.95f);

    companion object {
        const val PREF = "overlaySize"
        fun from(prefs: android.content.SharedPreferences): OverlaySize =
            runCatching { valueOf(prefs.getString(PREF, Medium.name)!!) }.getOrDefault(Medium)
    }
}
