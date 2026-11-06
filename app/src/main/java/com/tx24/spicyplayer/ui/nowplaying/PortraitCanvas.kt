package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import com.tx24.spicyplayer.ui.controls.DockTimeline
import com.tx24.spicyplayer.ui.controls.PlaybackControlsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Portrait, with the big cover up: the song's Spotify Canvas fills the screen in the cover's
 * place, as Spotify's own player shows it. A portrait loop on a portrait screen, so little of it is
 * cut. Dimmed a little, and more along the bottom, where the song is docked ([PortraitCanvasDock]).
 * The loop crossfades to the next song's. [held] (covered by something opaque) holds its frame.
 */
@Composable
fun PortraitCanvas(url: String, onFailed: (String) -> Unit, modifier: Modifier = Modifier, held: Boolean = false) {
    Box(modifier) {
        CrossfadingMotionVideo(
            url = url,
            onFailed = onFailed,
            modifier = Modifier.fillMaxSize(),
            // Faces sit in the upper half of most canvases, as on the stage.
            cropFromTop = 0.4f,
            held = held,
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black.copy(alpha = VEIL))
                // White text over bright frames: at least 4.5:1 behind the docked song.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.75f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Black.copy(alpha = 0.6f),
                    ),
                )
            },
        )
    }
}

/**
 * The song over [PortraitCanvas], in line with the controls' rows ([gutterPx] in from the left),
 * crossfading between two places in step with the controls ([controlsShown]): with them hidden, at
 * the bottom of the screen, name over artists over the timeline, like the cover stage's dock; with
 * them up, just above them ([controlsHeightPx]), the name and artists alone, since the controls
 * have their own timeline.
 */
@Composable
fun PortraitCanvasDock(
    info: NowPlayingInfo,
    sizes: ExpandedHeader,
    controls: PlaybackControlsState,
    controlsShown: () -> Float,
    controlsHeightPx: Int,
    widthPx: Float,
    gutterPx: Float,
    bottomPx: Float,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    releaseYear: ReleaseYear? = null,
) {
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        // The controls hidden: the song and the timeline, at the bottom.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = with(density) { gutterPx.toDp() }, bottom = with(density) { bottomPx.toDp() })
                .width(with(density) { widthPx.toDp() })
                .graphicsLayer { this.alpha = (1f - controlsShown()) * alpha() },
        ) {
            StageMetadata(info, sizes, Alignment.Start, bias = { -1f }, widthPx = widthPx, active = { (1f - controlsShown()) * alpha() > 0f }, releaseYear = releaseYear)
            // No spacer: the bar's touch height already stands it 11dp clear of the artists, as on the stage.
            DockTimeline(controls)
        }
        // The controls up: the song alone, just above them. Their top is the timeline's touch row,
        // which stands the bar as far from the song as the hidden dock's does: no margin on top.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = with(density) { gutterPx.toDp() }, bottom = with(density) { controlsHeightPx.toDp() })
                .graphicsLayer { this.alpha = controlsShown() * alpha() },
        ) {
            StageMetadata(info, sizes, Alignment.Start, bias = { -1f }, widthPx = widthPx, active = { controlsShown() * alpha() > 0f }, releaseYear = releaseYear)
        }
    }
}

/** The Canvas at brightness 0.8, so the docked song and the controls read over it. */
private const val VEIL = 0.2f
