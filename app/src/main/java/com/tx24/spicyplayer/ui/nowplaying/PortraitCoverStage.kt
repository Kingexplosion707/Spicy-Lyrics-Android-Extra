package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch

/**
 * Portrait, with the big cover up: the record's animated cover, or its still cover, filling the
 * screen's width from the top down to about the song's name, cropped at the sides, its bottom
 * melting into the background behind, as Apple Music shows it. The song is docked under it ([PortraitCanvasDock]). The
 * still cover stays beneath the loop, so a loop that's slow to start, or fails, shows the cover.
 */
@Composable
fun PortraitCoverStage(
    info: NowPlayingInfo,
    picture: StagePicture,
    modifier: Modifier = Modifier,
    /** Covered by something opaque: the animated cover holds its frame. */
    held: Boolean = false,
) {
    val artwork = rememberSessionArtwork(info.artwork, info.artworkUri, maxDimension = ARTWORK_MAX_PX, hasSession = info.hasSession)
    // The last cover stays up while the player has none to show.
    var resting by remember { mutableStateOf<SessionArtwork?>(null) }
    LaunchedEffect(artwork?.fingerprint) { if (artwork != null) resting = artwork }
    var failedMotionUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val motionQuery = MotionCoverQuery(info.artists, info.album, info.title)
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val height = (constraints.maxHeight * HEIGHT).toInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
                // Melts into the background at the bottom, on eased stops: a straight ramp leaves a
                // visible line where it reaches full strength.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Dimmed a little, like the stage, so the docked song and the controls read over
                    // it: only where there's picture, or the melted edges would show grey.
                    drawRect(Color.Black.copy(alpha = VEIL), blendMode = BlendMode.SrcAtop)
                    val stops = FEATHER_STOPS.reversed().map { (at, a) -> 1f - at * FEATHER to Color.Black.copy(alpha = a) }
                    drawRect(Brush.verticalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
                },
        ) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Crossfade(resting, animationSpec = tween(COVER_CHANGE_MS), label = "portraitStageCover") { cover ->
                    if (cover != null) {
                        val bitmap = remember(cover) { cover.bitmap.asImageBitmap() }
                        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                CrossfadingMotionVideo(
                    url = (picture as? StagePicture.Animated)?.url?.takeIf { it != failedMotionUrl },
                    onFailed = { failed ->
                        failedMotionUrl = failed
                        scope.launch { forgetMotionCover(context, motionQuery) }
                    },
                    modifier = Modifier.fillMaxSize(),
                    // Covers the box at its own shape, centred across.
                    cropFromTop = 0.5f,
                    held = held,
                )
            }
        }
    }
}

/** How much of the screen's height the picture covers, from the top. */
private const val HEIGHT = 0.68f
/** How much of the picture's height its bottom melts over. */
private const val FEATHER = 0.35f
/** Across the feather, from the edge in: (share of it, opacity). */
private val FEATHER_STOPS = listOf(0f to 0f, 0.18f to 0.10f, 0.38f to 0.36f, 0.72f to 0.85f, 1f to 1f)
private const val VEIL = 0.12f
private const val COVER_CHANGE_MS = 1000
private const val ARTWORK_MAX_PX = 1024
