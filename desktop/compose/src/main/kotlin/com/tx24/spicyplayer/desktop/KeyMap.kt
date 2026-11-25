package com.tx24.spicyplayer.desktop

import android.view.KeyEvent as AndroidKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** The keyboard as Android's keys, for the lyrics screen's key handling (KeyInput). */
object KeyMap {
    private val keys = mapOf(
        Key.DirectionUp to AndroidKey.KEYCODE_DPAD_UP, Key.DirectionDown to AndroidKey.KEYCODE_DPAD_DOWN,
        Key.DirectionLeft to AndroidKey.KEYCODE_DPAD_LEFT, Key.DirectionRight to AndroidKey.KEYCODE_DPAD_RIGHT,
        Key.Enter to AndroidKey.KEYCODE_ENTER, Key.NumPadEnter to AndroidKey.KEYCODE_NUMPAD_ENTER,
        Key.Tab to AndroidKey.KEYCODE_TAB, Key.MediaPlayPause to AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE,
        Key.MediaNext to AndroidKey.KEYCODE_MEDIA_NEXT, Key.MediaPrevious to AndroidKey.KEYCODE_MEDIA_PREVIOUS,
    )
    private val held = HashMap<Key, Int>()

    fun toAndroid(event: KeyEvent): AndroidKey? {
        // Space plays and pauses, as in desktop players; the media keys reach the player itself.
        if (event.key == Key.Spacebar) return if (event.type == KeyEventType.KeyDown) AndroidKey(AndroidKey.ACTION_DOWN, AndroidKey.KEYCODE_BUTTON_X) else AndroidKey(AndroidKey.ACTION_UP, AndroidKey.KEYCODE_BUTTON_X)
        if (event.key == Key.PageUp) return if (event.type == KeyEventType.KeyDown) AndroidKey(AndroidKey.ACTION_DOWN, AndroidKey.KEYCODE_BUTTON_L1) else AndroidKey(AndroidKey.ACTION_UP, AndroidKey.KEYCODE_BUTTON_L1)
        if (event.key == Key.PageDown) return if (event.type == KeyEventType.KeyDown) AndroidKey(AndroidKey.ACTION_DOWN, AndroidKey.KEYCODE_BUTTON_R1) else AndroidKey(AndroidKey.ACTION_UP, AndroidKey.KEYCODE_BUTTON_R1)
        val code = keys[event.key] ?: return null
        return when (event.type) {
            KeyEventType.KeyDown -> { val n = held[event.key]?.plus(1) ?: 0; held[event.key] = n; AndroidKey(AndroidKey.ACTION_DOWN, code, n) }
            KeyEventType.KeyUp -> { held.remove(event.key); AndroidKey(AndroidKey.ACTION_UP, code) }
            else -> null
        }
    }
}
