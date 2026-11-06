package com.tx24.spicyplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicySpacing

/**
 * Stand-in rows while a settings page is still being built: grey bars where the label, the
 * description and the control will be, breathing slowly. [cards] puts each row in its own
 * outlined card (the source list); otherwise the rows sit loose, as in a page.
 */
@Composable
fun SettingsSkeleton(rows: Int, modifier: Modifier = Modifier, cards: Boolean = false) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    Column(
        modifier.fillMaxWidth().graphicsLayer { alpha = pulse },
        verticalArrangement = Arrangement.spacedBy(if (cards) SpicySpacing.S2 else 0.dp),
    ) {
        repeat(rows) { index ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (cards) Modifier.outlinedCard(tinted = true) else Modifier)
                    .padding(SpicySpacing.S3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S4),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bar(LABEL_WIDTHS[index % LABEL_WIDTHS.size], 14.dp)
                    Bar(DESCRIPTION_WIDTHS[index % DESCRIPTION_WIDTHS.size], 11.dp)
                }
                Box(Modifier.size(42.dp, 24.dp).background(SpicyColors.TintBgPressed, RoundedCornerShape(12.dp)))
            }
        }
    }
}

/**
 * Stand-in rows for a list of songs (the queue, the Lyrics Manager): a cover square, then the
 * title and artist as bars, breathing like [SettingsSkeleton].
 */
@Composable
fun TrackListSkeleton(rows: Int, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "trackSkeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "trackSkeletonPulse",
    )
    Column(modifier.fillMaxWidth().graphicsLayer { alpha = pulse }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { index ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S3, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
            ) {
                Box(Modifier.size(40.dp).background(SpicyColors.TintBgPressed, RoundedCornerShape(6.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bar(LABEL_WIDTHS[index % LABEL_WIDTHS.size] + 0.15f, 14.dp)
                    Bar(LABEL_WIDTHS[(index + 2) % LABEL_WIDTHS.size], 11.dp)
                }
            }
        }
    }
}

/** A stand-in for a text field or a row of buttons: one rounded block, [height] tall. */
@Composable
fun BlockSkeleton(modifier: Modifier = Modifier, height: Dp = 40.dp) {
    Box(modifier.fillMaxWidth().height(height).background(SpicyColors.TintBg, RoundedCornerShape(SpicyRadii.Sm)))
}

@Composable
private fun Bar(fraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .background(SpicyColors.TintBgPressed, RoundedCornerShape(height / 2)),
    )
}

// Varied so the rows don't read as one repeated shape.
private val LABEL_WIDTHS = floatArrayOf(0.45f, 0.6f, 0.35f, 0.5f)
private val DESCRIPTION_WIDTHS = floatArrayOf(0.8f, 0.65f, 0.9f, 0.7f)

private const val PULSE_MS = 900
