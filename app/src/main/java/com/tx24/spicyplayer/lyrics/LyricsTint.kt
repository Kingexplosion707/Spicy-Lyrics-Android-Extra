package com.tx24.spicyplayer.lyrics

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import com.tx24.spicyplayer.overlay.OverlayColor
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.parseHexColor

/** The lyrics' colour: one, or two blended from the top of the lyrics down to the bottom. */
@Immutable
internal data class LyricsInk(val top: Color, val bottom: Color = top) {
    val solid: Boolean get() = top == bottom

    companion object {
        val White = LyricsInk(Color.White)
    }
}

/**
 * The lyrics in [color], [brightness] times as bright, on the GPU (Android 12 and later; as drawn
 * before). The renderer draws them white at each word's own alpha, so they're multiplied by the
 * color, keeping each word's brightness, fill and glow; and their alpha by the brightness (capped
 * at solid), so dim words not yet sung grow as bright as the rest. White at 1 adds nothing.
 */
internal fun Modifier.lyricsTint(color: Color, brightness: Float = 1f): Modifier = lyricsTint(LyricsInk(color), brightness)

/** [lyricsTint] with an [ink] that may blend two colours down the lyrics. */
internal fun Modifier.lyricsTint(ink: LyricsInk, brightness: Float = 1f): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val bright = if (brightness <= 1f) null else android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(1f, 1f, 1f, brightness) }),
    )
    if (!ink.solid) {
        // The blend spans the view, so it's made for the view's height.
        return graphicsLayer {
            val gradient = android.graphics.LinearGradient(
                0f, 0f, 0f, size.height.coerceAtLeast(1f), ink.top.toArgb(), ink.bottom.toArgb(), android.graphics.Shader.TileMode.CLAMP,
            )
            val tinted = android.graphics.RenderEffect.createBlendModeEffect(
                android.graphics.RenderEffect.createOffsetEffect(0f, 0f),
                android.graphics.RenderEffect.createShaderEffect(gradient),
                android.graphics.BlendMode.MODULATE,
            )
            renderEffect = (if (bright != null) android.graphics.RenderEffect.createChainEffect(bright, tinted) else tinted).asComposeRenderEffect()
        }
    }
    val tint = if (ink.top == Color.White) null else android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.BlendModeColorFilter(ink.top.toArgb(), android.graphics.BlendMode.MODULATE),
    )
    val effect = when {
        tint != null && bright != null -> android.graphics.RenderEffect.createChainEffect(tint, bright)
        else -> tint ?: bright ?: return this
    }.asComposeRenderEffect()
    return graphicsLayer { renderEffect = effect }
}

/** The colours [choice] gives the lyrics of a song with [cover]: worked out once per song. */
@Composable
internal fun rememberLyricsTint(choice: OverlayColor, cover: Bitmap?, custom: String = "#FFFFFF"): LyricsInk =
    remember(choice, if (choice.fromCover) cover else null, custom) {
        val top = choice.resolve(cover, parseHexColor(custom))
        LyricsInk(top, choice.argbEnd?.let { Color(it) } ?: top)
    }

/** The Lyrics colour setting's ink for a song with [cover]. */
@Composable
internal fun rememberLyricsTint(settings: AppSettings, cover: Bitmap?): LyricsInk =
    rememberLyricsTint(settings.lyricsColor, cover, settings.customLyricsColor)
