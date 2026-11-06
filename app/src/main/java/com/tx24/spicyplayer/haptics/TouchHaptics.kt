package com.tx24.spicyplayer.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/*
 * Touch feedback: every control plays Android's named feedback for what it is (a toggle turning
 * on or off, a slider step, a gesture crossing its threshold), which the phone maps to its own
 * tuned effects and which follows the system's touch-feedback setting. The kinds used:
 *
 * - [HapticFeedbackType.VirtualKey]: the glass and playback buttons (a firm click);
 * - [HapticFeedbackType.ContextClick]: settings rows, pills and small buttons (a light tick);
 * - [HapticFeedbackType.ToggleOn] / [HapticFeedbackType.ToggleOff]: switches and play/pause;
 * - [HapticFeedbackType.SegmentTick]: picking an option in a list;
 * - [HapticFeedbackType.SegmentFrequentTick]: each step of a slider;
 * - [HapticFeedbackType.GestureThresholdActivate]: a drag that will now do something (the cover
 *   swipe passing the skip point, the timeline picked up);
 * - [HapticFeedbackType.GestureEnd]: a drag let go;
 * - [HapticFeedbackType.Confirm] / [HapticFeedbackType.Reject]: something done or refused.
 */

/** [content]'s touch feedback: the platform's, or none while the app's switch is off. */
@Composable
fun ProvideTouchHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val platform = LocalHapticFeedback.current
    val feedback = remember(enabled, platform) { if (enabled) platform else NoHaptics }
    CompositionLocalProvider(LocalHapticFeedback provides feedback, content = content)
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** [onClick], after [type]'s feedback. */
@Composable
fun withHaptic(type: HapticFeedbackType, onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { haptics.performHapticFeedback(type); onClick() }
}
