@file:Suppress("unused", "UNUSED_PARAMETER")

package android.view

import com.tx24.spicyplayer.desktop.platform.DesktopWindow

/** The app's window: its brightness and display mode are asked for and kept, nothing more. */
class Window {
    var attributes: WindowManager.LayoutParams = WindowManager.LayoutParams()
    val windowManager: WindowManager = WindowManager()
    val decorView: View = View()
    fun addFlags(f: Int) { if (f and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) DesktopWindow.keepAwake = true }
    fun clearFlags(f: Int) { if (f and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) DesktopWindow.keepAwake = false }
    fun setFlags(f: Int, mask: Int) = addFlags(f and mask)
    fun setFrameRate(hz: Float, compat: Int) {}
}

class WindowManager {
    @Suppress("DEPRECATION")
    val defaultDisplay: Display get() = Display()
    class LayoutParams {
        var screenBrightness: Float = BRIGHTNESS_OVERRIDE_NONE
        var preferredDisplayModeId: Int = 0
        var preferredRefreshRate: Float = 0f
        var flags: Int = 0
        var layoutInDisplayCutoutMode: Int = 0
        companion object {
            const val BRIGHTNESS_OVERRIDE_NONE = -1f
            const val BRIGHTNESS_OVERRIDE_FULL = 1f
            const val FLAG_KEEP_SCREEN_ON = 128
            const val FLAG_SHOW_WHEN_LOCKED = 524288
            const val FLAG_TURN_SCREEN_ON = 2097152
            const val LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1
            const val LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3
        }
    }
}
