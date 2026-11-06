package com.tx24.spicyplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/*
 * The design tokens: Apple HIG for the base, iOS glass for
 * overlays, monochrome white on a dark page. Lyric rendering never reads these; they are for the
 * app's own chrome (controls, settings). CSS px are taken as dp and rem as 16sp.
 */

/**
 * The chrome's colours. Most screens use [Neutral], monochrome white; a themed part (Settings, the
 * pop-ups) provides [Purple] through [LocalSpicyPalette], which re-tints its text, hairlines,
 * highlights and buttons the way the themed Settings re-points its CSS tokens, and gives its
 * plate the [brand] ramp.
 */
@Immutable
class SpicyPalette(
    /** `--color-text-*`. */
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextTertiary: Color,
    val TextQuaternary: Color,
    /** `--accent-on-fill`: text on an [Accent] fill. */
    val TextOnFill: Color,
    /** `--hairline`, `--hairline-strong`. */
    val Hairline: Color,
    val HairlineStrong: Color,
    /** `--accent-tint-bg(-hover)`: the soft highlight for selected rows and fields. */
    val TintBg: Color,
    val TintBgPressed: Color,
    /** The primary button's fill, and while held. */
    val Accent: Color,
    val AccentPressed: Color,
    /** The plate's ramp and mark; null for the plain glass. */
    val brand: SpicyBrand? = null,
) {
    /** `--color-bg-elevated`, `--color-bg-overlay`. */
    val BgElevated = Color(28, 28, 32).copy(alpha = 0.88f)
    val BgOverlay = Color.Black.copy(alpha = 0.35f)

    /** `--color-status-*`: muted; tints an icon or one line of text, never a surface. */
    val StatusDanger = Color(255, 110, 110).copy(alpha = 0.78f)
    val StatusInfo = Color(120, 180, 255).copy(alpha = 0.78f)
    val StatusSuccess = Color(140, 220, 170).copy(alpha = 0.78f)
    val StatusWarning = Color(255, 195, 120).copy(alpha = 0.82f)

    companion object {
        /** White at 92/60/35/18%; no colour accent, only brighter white. */
        val Neutral = SpicyPalette(
            TextPrimary = Color.White.copy(alpha = 0.92f),
            TextSecondary = Color.White.copy(alpha = 0.6f),
            TextTertiary = Color.White.copy(alpha = 0.35f),
            TextQuaternary = Color.White.copy(alpha = 0.18f),
            TextOnFill = Color.Black,
            Hairline = Color.White.copy(alpha = 0.08f),
            HairlineStrong = Color.White.copy(alpha = 0.14f),
            TintBg = Color.White.copy(alpha = 0.06f),
            TintBgPressed = Color.White.copy(alpha = 0.14f),
            Accent = Color.White.copy(alpha = 0.95f),
            AccentPressed = Color.White.copy(alpha = 0.8f),
        )

        /** Lavender ink on a deep purple ramp: text at 100/muted/50/26%, lines at 12/20%, highlights at 8/14%. */
        fun of(brand: SpicyBrand) = SpicyPalette(
            TextPrimary = brand.ink,
            TextSecondary = brand.inkMuted,
            TextTertiary = brand.ink.copy(alpha = 0.5f),
            TextQuaternary = brand.ink.copy(alpha = 0.26f),
            TextOnFill = brand.ctaInk,
            Hairline = brand.ink.copy(alpha = 0.12f),
            HairlineStrong = brand.ink.copy(alpha = 0.2f),
            TintBg = brand.ink.copy(alpha = 0.08f),
            TintBgPressed = brand.ink.copy(alpha = 0.14f),
            Accent = brand.cta,
            AccentPressed = Color.White,
            brand = brand,
        )

        val Purple = of(SpicyBrand.Purple)
    }
}

/**
 * A themed plate's colours, in sRGB: `oklch(0.30 0.095 297)` and so on (hue 297, lavender).
 */
@Immutable
class SpicyBrand(
    /** The ramp, top-left to bottom-right at 165°. */
    val field: Color,
    val fieldDeep: Color,
    /** The big mark in the corner. */
    val tint: Color,
    val ink: Color,
    val inkMuted: Color,
    /** The primary button, and the text on it. */
    val cta: Color,
    val ctaInk: Color,
) {
    companion object {
        val Purple = SpicyBrand(
            field = Color(0xFF342057), fieldDeep = Color(0xFF23133E), tint = Color(0xFF584383),
            ink = Color(0xFFD9CAFF), inkMuted = Color(0xFFB9AFD7), cta = Color(0xFFF1EDFE), ctaInk = Color(0xFF271446),
        )
    }
}

val LocalSpicyPalette = staticCompositionLocalOf { SpicyPalette.Neutral }

/** The palette of the part being drawn; see [SpicyPalette]. */
val SpicyColors: SpicyPalette
    @Composable @ReadOnlyComposable get() = LocalSpicyPalette.current

/**
 * `--material-regular-*` plus the `.ViewControl` edge and cast shadow. The blur comes from Haze
 * (see `LocalBackdrop`); the rest is drawn.
 */
object SpicyGlass {
    val Fill = Color.Black.copy(alpha = 0.10f)

    /** `:hover`/pressed: `background: rgba(255,255,255,.12)`. */
    val FillPressed = Color.White.copy(alpha = 0.12f)

    /** `inset 0 1px 0 rgba(255,255,255,.36)`, `inset 0 0 0 1px (.16)`, `inset 0 -1px 0 (.18)`. */
    val EdgeTop = Color.White.copy(alpha = 0.36f)
    val EdgeRing = Color.White.copy(alpha = 0.16f)
    val EdgeBottom = Color.White.copy(alpha = 0.18f)

    /** `0 10px 24px -8px rgba(8,10,18,.42)`. */
    val CastColor = Color(8, 10, 18).copy(alpha = 0.42f)
    val CastOffsetY = 10.dp
    val CastBlur = 24.dp
    val CastSpread = (-8).dp

    /**
     * `--material-regular-blur: blur(12px)` on what's behind. The fill and edges are drawn on
     * top by the glass itself; the tint is only for phones that can't blur (before Android 12).
     */
    val material = HazeStyle(
        // Haze paints this under the blurred copy; the page is black.
        backgroundColor = Color.Black,
        tints = emptyList(),
        blurRadius = 12.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(Color.Black.copy(alpha = 0.3f)),
    )
}

/** `--space-1..6`: a 4dp grid. */
object SpicySpacing {
    val S1 = 4.dp
    val S2 = 8.dp
    val S3 = 12.dp
    val S4 = 16.dp
    val S5 = 20.dp
    val S6 = 24.dp
}

/** `--radius-*`. Pills use `CircleShape`. */
object SpicyRadii {
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
}

object SpicyMotion {
    /** `--ease-standard`. */
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** The springy overshoot the glass buttons scale with. */
    val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    const val FAST_MS = 150
    const val BASE_MS = 250
    const val SLOW_MS = 400

    /** `.ViewControls { transition: opacity 0.3s }`. */
    const val CONTROLS_FADE_MS = 300

    /**
     * The modal and its page slides, the toggle and the menus: `0.22s cubic-bezier(0.23, 1, 0.32, 1)`,
     * a quick ease-out.
     */
    val Modal = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    const val MODAL_MS = 220
}

/** The text ramp, in the lyrics font, in the current palette's primary text colour. */
object SpicyType {
    private val base = TextStyle(fontFamily = LyricsLayoutCalculator.spicyFontFamily)
    private val title = base.copy(fontSize = 17.6.sp, lineHeight = 1.25.em, fontWeight = FontWeight.Bold)
    private val headline = base.copy(fontSize = 15.32.sp, lineHeight = 1.3.em, fontWeight = FontWeight.SemiBold)
    private val body = base.copy(fontSize = 14.sp, lineHeight = 1.4.em, fontWeight = FontWeight.Normal)
    private val caption = base.copy(fontSize = 13.12.sp, lineHeight = 1.35.em, fontWeight = FontWeight.Normal)
    private val footnote = base.copy(fontSize = 12.16.sp, lineHeight = 1.3.em, fontWeight = FontWeight.Normal)

    val Title @Composable @ReadOnlyComposable get() = title.copy(color = SpicyColors.TextPrimary)
    val Headline @Composable @ReadOnlyComposable get() = headline.copy(color = SpicyColors.TextPrimary)
    val Body @Composable @ReadOnlyComposable get() = body.copy(color = SpicyColors.TextPrimary)
    val Caption @Composable @ReadOnlyComposable get() = caption.copy(color = SpicyColors.TextPrimary)
    val Footnote @Composable @ReadOnlyComposable get() = footnote.copy(color = SpicyColors.TextPrimary)
}
