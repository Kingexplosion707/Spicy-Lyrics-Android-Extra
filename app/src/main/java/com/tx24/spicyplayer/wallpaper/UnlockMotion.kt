package com.tx24.spicyplayer.wallpaper

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.settings.UnlockAnimation

/**
 * The wallpaper's unlock animation (Settings → Device → Live wallpaper), in two states as on
 * iPhone wallpapers: a locked look and the usual one. While locked (the lock screen, or the
 * screen off) it holds the locked look; once the phone unlocks it springs to the usual look. Android
 * doesn't tell a wallpaper how far along the unlock swipe is, so it plays when the unlock lands
 * instead of following the finger.
 *
 * - [UnlockAnimation.Zoom]: zoomed in and dimmed, settling back.
 * - [UnlockAnimation.Rise]: lowered and dimmed, rising into place.
 * - [UnlockAnimation.Focus]: blurred and dimmed, coming into focus (Android 12 on; else Zoom).
 */
@Composable
internal fun UnlockMotion(style: UnlockAnimation, locked: Boolean, content: @Composable () -> Unit) {
    if (style == UnlockAnimation.Off) {
        content()
        return
    }
    // 0 is the locked look, 1 the usual one.
    val shown = remember { Animatable(if (locked) 0f else 1f) }
    LaunchedEffect(locked) {
        if (locked) {
            // Locking happens with the screen off: no need to be seen going.
            shown.snapTo(0f)
        } else {
            shown.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        val focus = style == UnlockAnimation.Focus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val zoom = style == UnlockAnimation.Zoom || (style == UnlockAnimation.Focus && !focus)
        val moved = Modifier.graphicsLayer {
            val p = shown.value
            val rest = 1f - p
            when {
                zoom -> {
                    val s = 1f + LOCKED_ZOOM * rest
                    scaleX = s
                    scaleY = s
                }
                style == UnlockAnimation.Rise -> {
                    translationY = heightPx * LOCKED_DROP * rest
                    val s = 1f - 0.04f * rest
                    scaleX = s
                    scaleY = s
                }
                else -> Unit
            }
        }
        val blurred = if (focus) Modifier.blur((LOCKED_BLUR_DP * (1f - shown.value)).coerceAtLeast(0f).dp, BlurredEdgeTreatment.Rectangle) else Modifier
        Box(Modifier.fillMaxSize().then(moved).then(blurred)) { content() }
        // The dim of the locked look, lifting as it unlocks.
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = (1f - shown.value).coerceIn(0f, 1f) * LOCKED_DIM }
                .background(Color.Black),
        )
    }
}

/** The locked look: this much bigger, this share of the height lower, this blurred, and this dim. */
private const val LOCKED_ZOOM = 0.14f
private const val LOCKED_DROP = 0.08f
private const val LOCKED_BLUR_DP = 28f
private const val LOCKED_DIM = 0.45f
