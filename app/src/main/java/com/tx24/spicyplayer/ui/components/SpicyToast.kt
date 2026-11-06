package com.tx24.spicyplayer.ui.components

import com.tx24.spicyplayer.R
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import com.tx24.spicyplayer.ui.theme.SpicyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * A short note at the top of the screen on what an action did, one at a time: it slides and
 * fades in, holds [TOAST_MS], and goes. A new message replaces the one showing.
 */
@Composable
fun SpicyToastHost(messages: Flow<String>, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(messages) {
        messages.collectLatest { message ->
            if (text != null) shown.animateTo(0f, tween(120))
            text = message
            shown.animateTo(1f, tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Modal))
            // Long ones stay up long enough to be read.
            delay((TOAST_MS + (message.length - 60).coerceAtLeast(0) * MS_PER_CHAR).coerceAtMost(MAX_TOAST_MS))
            shown.animateTo(0f, tween(SpicyMotion.MODAL_MS, easing = SpicyMotion.Standard))
            text = null
        }
    }
    val message = text ?: return
    val palette = LocalPopupPalette.current
    val brand = palette.brand
    Box(modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S4), contentAlignment = Alignment.TopCenter) {
        val appear = Modifier
            .widthIn(max = 420.dp)
            .graphicsLayer {
                alpha = shown.value
                translationY = (1f - shown.value) * -12.dp.toPx()
            }
        if (brand != null) {
            // The brand's pill: the ramp, a hairline ring with a lit top edge, the logo before the text.
            Row(
                appear
                    // A pill while it's one line; wrapped, a rounded card rather than an oval.
                    .shadow(16.dp, PILL, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(PILL)
                    .drawBehind {
                        drawBrandRamp(brand)
                        val px = 1.dp.toPx()
                        val radius = CornerRadius(minOf(size.height / 2f, PILL_RADIUS.toPx()))
                        clipRect(bottom = px * 2f) {
                            drawRoundRect(brand.ink.copy(alpha = 0.22f), cornerRadius = radius, topLeft = Offset(0f, px), size = size)
                        }
                        drawRoundRect(
                            brand.ink.copy(alpha = 0.16f),
                            topLeft = Offset(px / 2f, px / 2f),
                            size = Size(size.width - px, size.height - px),
                            cornerRadius = CornerRadius(radius.x - px / 2f),
                            style = Stroke(px),
                        )
                    }
                    .heightIn(min = 44.dp)
                    .padding(start = SpicySpacing.S4, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ImageVector.vectorResource(R.drawable.app_glyph), null, Modifier.size(16.dp), tint = brand.inkMuted)
                Text(message, style = SpicyType.Caption.copy(color = brand.ink, fontWeight = FontWeight.SemiBold),
                    maxLines = MAX_LINES, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = SpicySpacing.S2))
            }
        } else {
            val shape = RoundedCornerShape(SpicyRadii.Md)
            Text(
                message,
                modifier = appear
                    .clip(shape)
                    .background(palette.BgElevated)
                    .border(1.dp, palette.HairlineStrong, shape)
                    .padding(horizontal = SpicySpacing.S4, vertical = SpicySpacing.S3),
                style = SpicyType.Caption.copy(color = palette.TextPrimary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                maxLines = MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val TOAST_MS = 3_000L
private const val MS_PER_CHAR = 40L
private const val MAX_TOAST_MS = 8_000L
private const val MAX_LINES = 4
private val PILL_RADIUS = 22.dp
private val PILL = RoundedCornerShape(PILL_RADIUS)
