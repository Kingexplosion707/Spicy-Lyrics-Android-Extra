package com.tx24.spicyplayer.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/*
 * Moving around with a TV remote's d-pad, a keyboard or a gamepad. Compose moves focus with the
 * arrow keys and clicks a focused `clickable` with the centre key, Enter or a gamepad's A (Android
 * falls back from A to the centre key, and from B to Back). These add what touch never needed:
 * a ring on whatever has focus, and a way into controls that read raw touches.
 */

/** The keys that press a focused control. */
private val CONFIRM_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA, Key.Spacebar)

/**
 * A ring around the control while it has focus, in [shape]. Goes before the `clickable` or
 * `toggleable` that makes it focusable, so it sees that one's focus.
 */
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val width = 2.dp.toPx()
                drawOutline(shape.createOutline(size, layoutDirection, this), FOCUS_RING, style = Stroke(width))
            }
        }
}

/**
 * Makes a control that reads raw touches reachable by keys: focusable, ringed while focused, and
 * pressed by the confirm keys (on release, as a tap is).
 */
fun Modifier.keyClickable(shape: Shape = CircleShape, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    this
        .focusRing(shape)
        .onKeyEvent { event ->
            if (!enabled || event.key !in CONFIRM_KEYS) return@onKeyEvent false
            if (event.type == KeyEventType.KeyUp) currentOnClick()
            true
        }
        .focusable(enabled)
}

/**
 * A layer of the screen (a page, a pop-up) that keys can move into only while it's [current], so
 * focus never wanders under what covers it. While a key was last used, it takes focus as soon as
 * it's current and [ready]: the first press lands in it, not on what was there before.
 */
fun Modifier.focusLayer(current: Boolean, ready: Boolean = true): Modifier = composed {
    val requester = remember { FocusRequester() }
    val keys = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(current, ready) {
        if (current && ready && keys) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    this
        .focusRequester(requester)
        .focusProperties { onEnter = { if (!current) cancelFocusChange() } }
        .focusGroup()
}

private val FOCUS_RING = Color.White.copy(alpha = 0.9f)
