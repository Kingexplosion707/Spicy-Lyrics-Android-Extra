package com.tx24.spicyplayer.wallpaper

import android.os.Handler
import android.os.Looper

/**
 * Whether the live wallpaper is on screen now (the home screen showing it), for what would
 * repeat its lyrics there: the overlay steps aside while it shows. Counts the wallpaper's
 * engines, since a preview and the home screen can each run one. Main thread.
 */
internal object WallpaperPresence {
    private var shown = 0
    private val listeners = mutableSetOf<(Boolean) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    val visible: Boolean get() = shown > 0

    fun engineVisible(visible: Boolean) {
        val before = this.visible
        shown = (shown + if (visible) 1 else -1).coerceAtLeast(0)
        if (before != this.visible) {
            val now = this.visible
            main.post { listeners.toList().forEach { it(now) } }
        }
    }

    fun addListener(listener: (Boolean) -> Unit) { listeners += listener }
    fun removeListener(listener: (Boolean) -> Unit) { listeners -= listener }
}
