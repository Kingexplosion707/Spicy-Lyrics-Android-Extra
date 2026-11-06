package com.tx24.spicyplayer.ui.controls

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Keys reaching the lyrics screen from a TV remote, a keyboard or a gamepad, seen by the activity
 * before Compose (which only routes them through what has focus, and nothing does on launch).
 * Each brings the controls up, as a touch does. With the controls hidden, a navigation key's first
 * press only brings them back, its release held back too, so it never presses a button nobody can
 * see. A gamepad's shoulder and face buttons are shortcuts: L1 and R1 skip back and on, X plays or
 * pauses, Y resyncs. Media, volume and Back keys go on as they are.
 */
class KeyInput {
    /** When the last key went down (uptime), for the controls' idle timer; 0 before any. */
    val presses = MutableStateFlow(0L)
    /** Whether the controls (or a screen over them) are up; set by the lyrics screen. */
    var controlsShown: () -> Boolean = { true }
    var onPlayPause: () -> Unit = {}
    var onPrevious: () -> Unit = {}
    var onNext: () -> Unit = {}
    var onResync: () -> Unit = {}
    private var swallowing: Int? = null

    /** True when [event] was taken here and goes no further. */
    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code in PASSED_ON) return false
        val shortcut = when (code) {
            KeyEvent.KEYCODE_BUTTON_L1 -> onPrevious
            KeyEvent.KEYCODE_BUTTON_R1 -> onNext
            KeyEvent.KEYCODE_BUTTON_X -> onPlayPause
            KeyEvent.KEYCODE_BUTTON_Y -> onResync
            else -> null
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (swallowing == code) {
                swallowing = null
                return true
            }
            return shortcut != null
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val hidden = !controlsShown()
        presses.value = event.eventTime
        if (shortcut != null) {
            if (event.repeatCount == 0) shortcut()
            return true
        }
        if (hidden && code in NAVIGATION && event.repeatCount == 0) {
            swallowing = code
            return true
        }
        return swallowing == code
    }

    private companion object {
        val PASSED_ON = setOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_HEADSETHOOK,
        )
        val NAVIGATION = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SPACE,
        )
    }
}
