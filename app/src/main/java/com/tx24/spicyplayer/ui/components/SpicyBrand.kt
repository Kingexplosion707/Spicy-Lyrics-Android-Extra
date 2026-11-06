package com.tx24.spicyplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.ui.theme.SpicyBrand
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyPalette
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * The themed parts' look: the brand's ramp, the logo's mark cropped big in a corner, and the
 * brand line over a heading. Settings lays them over its blurred backdrop, a pop-up over an opaque
 * plate.
 */

/** How a part of the chrome is dressed: the lavender brand, or the plain monochrome glass. */
enum class ChromeTheme(val label: String, val palette: SpicyPalette) {
    Purple("Purple", SpicyPalette.Purple),
    Classic("Classic", SpicyPalette.Neutral),
}

/** The pop-ups' palette, from Settings → Theme; [SpicyModal] dresses itself in it. */
val LocalPopupPalette = compositionLocalOf { ChromeTheme.Purple.palette }

/** The logo's note and sparkles, to tint. */
@Composable
fun rememberBrandGlyph(): VectorPainter = rememberVectorPainter(ImageVector.vectorResource(R.drawable.app_glyph))

/**
 * The ramp from [SpicyBrand.field] to [SpicyBrand.fieldDeep] at [fromAlpha] and [toAlpha], drawn
 * as CSS draws `linear-gradient(165deg, …)`: along a line through the centre at that angle,
 * long enough that both far corners sit on its ends.
 */
fun DrawScope.drawBrandRamp(brand: SpicyBrand, fromAlpha: Float = 1f, toAlpha: Float = 1f, degrees: Float = 165f) {
    val radians = Math.toRadians(degrees.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val c = center
    drawRect(
        Brush.linearGradient(
            listOf(brand.field.copy(alpha = fromAlpha), brand.fieldDeep.copy(alpha = toAlpha)),
            start = Offset(c.x - dx * half, c.y - dy * half),
            end = Offset(c.x + dx * half, c.y + dy * half),
        ),
    )
}

/**
 * The mark: [glyph] [width] across, its top-right corner at [right] and [top] from this one's
 * (negative runs it off the edge), turned [degrees] about its middle.
 */
fun DrawScope.drawBrandMark(
    glyph: VectorPainter,
    color: Color,
    alpha: Float,
    width: Float,
    right: Float,
    top: Float,
    degrees: Float,
) {
    val intrinsic = glyph.intrinsicSize
    val height = width * intrinsic.height / intrinsic.width
    val left = size.width - right - width
    translate(left, top) {
        rotate(degrees, Offset(width / 2f, height / 2f)) {
            with(glyph) { draw(androidx.compose.ui.geometry.Size(width, height), alpha, ColorFilter.tint(color)) }
        }
    }
}

/** "♪ Spicy Lyrics Mobile": the small brand line over a themed heading, in muted ink. */
@Composable
fun SpicyBrandLine(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ImageVector.vectorResource(R.drawable.app_glyph), null, Modifier.size(16.dp), tint = SpicyColors.TextSecondary)
        Text("Spicy Lyrics Mobile", style = SpicyType.Caption.copy(fontSize = 13.12.sp, fontWeight = FontWeight.SemiBold, color = SpicyColors.TextSecondary))
    }
}

/** A themed heading's title: 1.75rem bold, set tight. */
val BrandTitleSize = 28.sp
