@file:Suppress("unused")

package android.view

/** A key press, made by the desktop window from the keyboard's. */
class KeyEvent(val action: Int, val keyCode: Int, val repeatCount: Int = 0, val eventTime: Long = android.os.SystemClock.uptimeMillis(), val metaState: Int = 0) {
    val downTime: Long get() = eventTime
    val isCtrlPressed: Boolean get() = metaState and META_CTRL_ON != 0
    val isShiftPressed: Boolean get() = metaState and META_SHIFT_ON != 0
    val isAltPressed: Boolean get() = metaState and META_ALT_ON != 0
    companion object {
        const val ACTION_DOWN = 0; const val ACTION_UP = 1; const val ACTION_MULTIPLE = 2
        const val META_SHIFT_ON = 1; const val META_ALT_ON = 2; const val META_CTRL_ON = 4096
        const val KEYCODE_UNKNOWN = 0; const val KEYCODE_BACK = 4; const val KEYCODE_DPAD_UP = 19; const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21; const val KEYCODE_DPAD_RIGHT = 22; const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_VOLUME_UP = 24; const val KEYCODE_VOLUME_DOWN = 25; const val KEYCODE_TAB = 61; const val KEYCODE_SPACE = 62
        const val KEYCODE_ENTER = 66; const val KEYCODE_DEL = 67; const val KEYCODE_HEADSETHOOK = 79
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85; const val KEYCODE_MEDIA_STOP = 86; const val KEYCODE_MEDIA_NEXT = 87
        const val KEYCODE_MEDIA_PREVIOUS = 88; const val KEYCODE_MEDIA_REWIND = 89; const val KEYCODE_MEDIA_FAST_FORWARD = 90
        const val KEYCODE_BUTTON_A = 96; const val KEYCODE_BUTTON_B = 97; const val KEYCODE_BUTTON_X = 99; const val KEYCODE_BUTTON_Y = 100
        const val KEYCODE_BUTTON_L1 = 102; const val KEYCODE_BUTTON_R1 = 103; const val KEYCODE_ESCAPE = 111
        const val KEYCODE_MEDIA_PLAY = 126; const val KEYCODE_MEDIA_PAUSE = 127; const val KEYCODE_VOLUME_MUTE = 164
        const val KEYCODE_NUMPAD_ENTER = 160; const val KEYCODE_F = 34; const val KEYCODE_S = 47; const val KEYCODE_R = 46
        const val KEYCODE_PAGE_UP = 92; const val KEYCODE_PAGE_DOWN = 93; const val KEYCODE_MOVE_HOME = 122; const val KEYCODE_MOVE_END = 123
    }
}
