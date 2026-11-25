package com.tx24.spicyplayer.desktop.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Whether motion lyrics' web engine is there. */
object MotionEngine {
    @Volatile var available: Boolean = false
}

/** ivLyrics' motion lyrics page, in the desktop's web engine. */
class MotionPage {
    fun setSong(uri: String, json: String, themeJson: String) {}
    fun setComfort(intensity: String, rotation: String, safe: Boolean) {}
    fun redirect() {}
    fun setActive(active: Boolean) {}
    fun setPlayback(positionMs: Long, playing: Boolean) {}
    fun setLook(shade: Float, fadeBottom: Float) {}
    fun release() {}
}

@Composable
fun MotionPageView(page: MotionPage, modifier: Modifier = Modifier) {
    Box(modifier)
}
