@file:Suppress("unused", "UNUSED_PARAMETER")

package android.view

import com.tx24.spicyplayer.desktop.platform.DesktopWindow

/** The window's root view, as far as the shared code reads it. */
open class View {
    open val rootView: View get() = this
    open val width: Int get() = DesktopWindow.widthPx
    open val height: Int get() = DesktopWindow.heightPx
    var keepScreenOn: Boolean
        get() = DesktopWindow.keepAwake
        set(v) { DesktopWindow.keepAwake = v }
    val display: Display? get() = Display()
    fun performHapticFeedback(c: Int): Boolean = false
    fun performHapticFeedback(c: Int, flags: Int): Boolean = false
    fun post(r: Runnable): Boolean { javax.swing.SwingUtilities.invokeLater(r); return true }
    fun postDelayed(r: Runnable, ms: Long): Boolean = android.os.Handler().postDelayed(r, ms)
    fun removeCallbacks(r: Runnable): Boolean = true
    val context: android.content.Context get() = com.tx24.spicyplayer.desktop.platform.DesktopPlatform.context
    var systemUiVisibility: Int = 0
    var isHapticFeedbackEnabled: Boolean = false
}

class Display {
    val refreshRate: Float get() = DesktopWindow.refreshRate
    val displayId: Int get() = 0
    val mode: Mode get() = Mode()
    val supportedModes: Array<Mode> get() = arrayOf(Mode())
    class Mode { val refreshRate: Float get() = DesktopWindow.refreshRate; val modeId: Int get() = 0; val physicalWidth: Int get() = DesktopWindow.widthPx; val physicalHeight: Int get() = DesktopWindow.heightPx }
}

object HapticFeedbackConstants {
    const val CLOCK_TICK = 4; const val CONTEXT_CLICK = 6; const val KEYBOARD_TAP = 3; const val LONG_PRESS = 0; const val VIRTUAL_KEY = 1
    const val CONFIRM = 16; const val REJECT = 17; const val GESTURE_START = 12; const val GESTURE_END = 13; const val SEGMENT_TICK = 26; const val SEGMENT_FREQUENT_TICK = 27
    const val TEXT_HANDLE_MOVE = 9; const val TOGGLE_ON = 21; const val TOGGLE_OFF = 22; const val DRAG_START = 25
}
