package com.tx24.spicyplayer.desktop.platform

/** What the shared code asks about the window: its size, refresh rate, keeping the screen awake. */
object DesktopWindow {
    @Volatile var widthPx: Int = 1280
    @Volatile var heightPx: Int = 800
    @Volatile var refreshRate: Float = runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate.toFloat().takeIf { it > 0 }
    }.getOrNull() ?: 60f
    @Volatile var keepAwake: Boolean = false
}
