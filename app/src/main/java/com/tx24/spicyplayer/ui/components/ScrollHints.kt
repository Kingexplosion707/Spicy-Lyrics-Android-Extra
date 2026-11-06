package com.tx24.spicyplayer.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Shows that a scrolling body has more than fits: its top and bottom edges fade out where there's
 * more that way, and a thin bar on the right says how much and where. Goes before the
 * `verticalScroll` it describes; nothing shows while everything fits.
 */
fun Modifier.scrollHints(state: ScrollState): Modifier = this
    .drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
        val inset = 3.dp.toPx()
        val width = 3.dp.toPx()
        val track = size.height - inset * 2f
        val thumb = (track * size.height / (size.height + max)).coerceAtLeast(24.dp.toPx())
        val top = inset + (track - thumb) * state.value / max
        drawRoundRect(
            SCROLLBAR, Offset(size.width - inset - width, top), Size(width, thumb), CornerRadius(width / 2f),
        )
    }
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
        val edge = FADE.toPx().coerceAtMost(size.height / 4f)
        // Each edge fades in as there comes to be more past it, over the first edge's worth of scroll.
        val above = (state.value / edge).coerceIn(0f, 1f)
        val below = ((max - state.value) / edge).coerceIn(0f, 1f)
        val share = edge / size.height
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 1f - above),
                share to Color.Black,
                1f - share to Color.Black,
                1f to Color.Black.copy(alpha = 1f - below),
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private val FADE = 32.dp
private val SCROLLBAR = Color.White.copy(alpha = 0.35f)
