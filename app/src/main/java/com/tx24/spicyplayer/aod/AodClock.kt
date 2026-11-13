package com.tx24.spicyplayer.aod

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.ui.settings.AodClockStyle
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The always-on display's clock, in the style chosen in Settings → Device → Always-on display:
 * thin, bold, hours over minutes, outlined, or liquid glass (a frosted body with a bright rim and
 * a highlight across its top, as if lit from above).
 */
@Composable
internal fun AodClock(
    now: Long,
    style: AodClockStyle,
    size: TextUnit,
    alpha: Float,
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    val context = LocalContext.current
    val h24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val short = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val textAlign = if (align == Alignment.Start) TextAlign.Start else if (align == Alignment.End) TextAlign.End else TextAlign.Center
    if (style == AodClockStyle.Stacked) {
        val hours = remember(h24) { SimpleDateFormat(if (h24) "HH" else "h", Locale.getDefault()) }
        val minutes = remember { SimpleDateFormat("mm", Locale.getDefault()) }
        val big = size * 1.45f
        Column(modifier, horizontalAlignment = align) {
            val line = TextStyle(fontSize = big, fontWeight = FontWeight.Medium, lineHeight = big * 0.92f, textAlign = textAlign)
            Text(hours.format(Date(now)), style = line.copy(color = Color.White.copy(alpha = alpha)))
            Text(minutes.format(Date(now)), style = line.copy(color = Color.White.copy(alpha = alpha * 0.6f)))
        }
        return
    }
    val text = short.format(Date(now))
    val base = TextStyle(fontSize = size, textAlign = textAlign, letterSpacing = if (style == AodClockStyle.Bold) (-0.02).em else 0.em)
    Box(modifier) {
        when (style) {
            AodClockStyle.Light -> Text(text, style = base.copy(color = Color.White.copy(alpha = alpha), fontWeight = FontWeight.Light))
            AodClockStyle.Bold -> Text(text, style = base.copy(color = Color.White.copy(alpha = alpha), fontWeight = FontWeight.Black))
            AodClockStyle.Outline -> {
                val stroke = with(LocalDensity.current) { (size.value * 0.035f).dp.toPx().coerceAtLeast(1.5f) }
                Text(text, style = base.copy(color = Color.White.copy(alpha = alpha), fontWeight = FontWeight.Bold, drawStyle = Stroke(stroke)))
            }
            AodClockStyle.Glass -> GlassText(text, base.copy(fontWeight = FontWeight.SemiBold), alpha)
            AodClockStyle.Stacked -> Unit
        }
    }
}

/**
 * Text as liquid glass, in four passes: a soft halo where light bends around it, a frosted body
 * that's clearer through the middle, a rim brightest at the top left, and a highlight on the top
 * of each figure. Over black it reads as lit glass; over the Canvas or cover it lets them through.
 */
@Composable
private fun GlassText(text: String, style: TextStyle, alpha: Float) {
    val px = with(LocalDensity.current) { (style.fontSize.value * 0.03f).dp.toPx().coerceAtLeast(1.2f) }
    val white = { a: Float -> Color.White.copy(alpha = (a * alpha).coerceIn(0f, 1f)) }
    Box {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                text,
                style = style.copy(color = white(0.22f)),
                modifier = Modifier.blur(14.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        Text(
            text,
            style = style.copy(brush = Brush.verticalGradient(listOf(white(0.40f), white(0.10f), white(0.24f)))),
        )
        Text(
            text,
            style = style.copy(
                brush = Brush.linearGradient(
                    listOf(white(1f), white(0.30f), white(0.12f), white(0.65f)),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
                drawStyle = Stroke(px),
            ),
        )
        Text(
            text,
            style = style.copy(brush = Brush.verticalGradient(0f to white(0.55f), 0.32f to white(0.06f), 0.4f to Color.Transparent)),
        )
    }
}

/** The clock's size for a percentage of [base]. */
internal fun clockSize(base: TextUnit, percent: Int): TextUnit = (base.value * percent / 100f).sp
