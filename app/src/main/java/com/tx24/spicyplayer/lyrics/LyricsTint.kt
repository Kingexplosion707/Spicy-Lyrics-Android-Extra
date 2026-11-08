package com.tx24.spicyplayer.lyrics

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import com.tx24.spicyplayer.overlay.OverlayColor

/**
 * The lyrics in [color], [brightness] times as bright, on the GPU (Android 12 and later; as drawn
 * before). The renderer draws them white at each word's own alpha, so they're multiplied by the
 * color, keeping each word's brightness, fill and glow; and their alpha by the brightness (capped
 * at solid), so dim words not yet sung grow as bright as the rest. White at 1 adds nothing.
 */
internal fun Modifier.lyricsTint(color: Color, brightness: Float = 1f): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val tint = if (color == Color.White) null else android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.BlendModeColorFilter(color.toArgb(), android.graphics.BlendMode.MODULATE),
    )
    val bright = if (brightness <= 1f) null else android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(1f, 1f, 1f, brightness) }),
    )
    val effect = when {
        tint != null && bright != null -> android.graphics.RenderEffect.createChainEffect(tint, bright)
        else -> tint ?: bright ?: return this
    }.asComposeRenderEffect()
    return graphicsLayer { renderEffect = effect }
}

/** The colour [choice] gives the lyrics of a song with [cover]: worked out once per song. */
@Composable
internal fun rememberLyricsTint(choice: OverlayColor, cover: Bitmap?): Color =
    remember(choice, if (choice == OverlayColor.Song) cover else null) { choice.resolve(cover) }
