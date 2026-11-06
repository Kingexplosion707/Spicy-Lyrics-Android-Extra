package com.tx24.spicyplayer.ui.background

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.lyrics.spicy.canvas.DynamicBackgroundView
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpBackground
import com.tx24.spicyplayer.ui.nowplaying.SessionArtwork
import com.tx24.spicyplayer.ui.nowplaying.loadSessionArtwork
import com.tx24.spicyplayer.ui.nowplaying.rememberSessionArtwork
import com.tx24.spicyplayer.ui.settings.BackgroundType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The page's background, of the chosen [type], from the artwork the remote session publishes.
 *
 * The artist-header types show [artistHeaderUrl], or the cover when the artist has none; while
 * [artistHeaderPending] they keep what they show. [speed] is how fast the moving background
 * should go while playing (null: the normal speed).
 */
@Composable
fun SpicySessionBackground(
    artwork: Bitmap?,
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    type: BackgroundType = BackgroundType.Default,
    /** False holds the moving backgrounds still (low performance mode, or the setting). */
    animate: Boolean = true,
    /**
     * How blurred: for [BackgroundType.Default] a strength in percent (60 is the reference's),
     * for the others in dp. [BackgroundType.Color] and [BackgroundType.Custom] have nothing to blur.
     */
    blurDp: Int = 0,
    artistHeaderUrl: String? = null,
    artistHeaderPending: Boolean = false,
    speed: (() -> Float?)? = null,
    /** Cover Art only: the record's animated cover, played over the still one. */
    motionCoverUrl: String? = null,
    onMotionCoverFailed: () -> Unit = {},
    /** Covered by something opaque: the animated cover holds its frame. */
    motionCoverHeld: Boolean = false,
    /** False when no player is around: the logo then stands in at once. */
    hasSession: Boolean = true,
    /** Changes crossfade in place, a change of [type] too (Settings → Background → Refined Background). */
    refined: Boolean = false,
    /** Default only: the animated cover the moving background follows while it plays. */
    followMotionUrl: String? = null,
    onFollowMotionFailed: () -> Unit = {},
    /** [BackgroundType.Custom]'s colour. */
    customColor: Color = Color.Black,
) {
    @Composable
    fun background(type: BackgroundType, modifier: Modifier, retired: Boolean) = BackgroundOfType(
        artwork, artworkUri, isPlaying, modifier, type, animate && !retired, blurDp, artistHeaderUrl,
        artistHeaderPending, speed, motionCoverUrl, onMotionCoverFailed, hasSession, refined, retired,
        followMotionUrl, onFollowMotionFailed, motionCoverHeld, customColor,
    )
    if (!refined) {
        background(type, modifier, retired = false)
        return
    }
    // A new type fades in over the old one, which holds still beneath it until the new one has
    // settled and is then dropped, its renderer (and any engine or player in it) with it.
    val layers = remember { mutableStateListOf(TypeLayer(type, shown = true)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(type) {
        if (layers.last().type == type) return@LaunchedEffect
        val beneath = layers.toList()
        beneath.forEach { it.retired = true }
        val layer = TypeLayer(type, shown = false)
        layers += layer
        scope.launch { layer.alpha.animateTo(1f, tween(TYPE_FADE_MS, easing = TypeFadeEasing)) }
        scope.launch {
            delay(LAYER_SETTLE_MS.toLong())
            layers.removeAll(beneath)
        }
    }
    Box(modifier.background(Color.Black)) {
        for (layer in layers) {
            key(layer) {
                background(
                    layer.type,
                    Modifier.fillMaxSize().graphicsLayer { alpha = layer.alpha.value },
                    retired = layer.retired,
                )
            }
        }
    }
}

private class TypeLayer(val type: BackgroundType, shown: Boolean) {
    val alpha = Animatable(if (shown) 1f else 0f)
    var retired by mutableStateOf(false)
}

/** A type change fades in over `900ms cubic-bezier(0.45, 0, 0.25, 1)`, like a refined picture. */
private val TypeFadeEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)
private const val TYPE_FADE_MS = 900
/** A newly picked custom colour fades in, like a type change. */
private const val CUSTOM_COLOR_MS = TYPE_FADE_MS

/** One type's renderer. [retired] (on its way out) takes nothing new and holds still. */
@Composable
private fun BackgroundOfType(
    artwork: Bitmap?,
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier,
    type: BackgroundType,
    animate: Boolean,
    blurDp: Int,
    artistHeaderUrl: String?,
    artistHeaderPending: Boolean,
    speed: (() -> Float?)?,
    motionCoverUrl: String?,
    onMotionCoverFailed: () -> Unit,
    hasSession: Boolean,
    refined: Boolean,
    retired: Boolean,
    followMotionUrl: String?,
    onFollowMotionFailed: () -> Unit,
    motionCoverHeld: Boolean,
    customColor: Color,
) {
    when (type) {
        BackgroundType.Default, BackgroundType.Legacy -> {
            val softwareArtwork = rememberSessionArtwork(artwork, artworkUri, maxDimension = 256, hasSession = hasSession)?.bitmap
            if (type == BackgroundType.Legacy) {
                LegacyBackground(coverArtBitmap = softwareArtwork, modifier = modifier, animate = animate, blurDp = blurDp)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                KawarpBackground(
                    coverArtBitmap = softwareArtwork,
                    modifier = modifier,
                    isPlaying = isPlaying,
                    animate = animate,
                    blurIntensity = blurDp,
                    speed = speed,
                    // Held still (or on its way out), it shows the cover.
                    videoFrames = rememberMotionFrames(followMotionUrl?.takeIf { animate }, onFollowMotionFailed),
                )
            } else {
                DynamicBackgroundView(
                    coverArtBitmap = softwareArtwork,
                    modifier = modifier,
                    blurIntensity = blurDp,
                    animate = animate,
                )
            }
        }
        BackgroundType.Color -> {
            ColorBackground(
                rememberSessionArtwork(artwork, artworkUri, maxDimension = 256, hasSession = hasSession),
                modifier,
                refined = refined,
                retired = retired,
            )
        }
        BackgroundType.CoverArt -> {
            Box(modifier) {
                StillImageBackground(
                    rememberSessionArtwork(artwork, artworkUri, maxDimension = STILL_COVER_PX, hasSession = hasSession),
                    blurDp,
                    refined = refined,
                    retired = retired,
                )
                if (motionCoverUrl != null) MotionImageBackground(motionCoverUrl, blurDp, onMotionCoverFailed, refined = refined, held = motionCoverHeld)
            }
        }
        BackgroundType.Custom -> {
            val shown by animateColorAsState(customColor, tween(CUSTOM_COLOR_MS), label = "customColor")
            Box(modifier.background(shown))
        }
        BackgroundType.Auto, BackgroundType.ArtistHeader -> {
            val cover = rememberSessionArtwork(artwork, artworkUri, maxDimension = STILL_COVER_PX, hasSession = hasSession)
            val context = LocalContext.current
            // Which header URL has loaded, and what it gave (null: it failed, so the cover shows).
            // A change of type, or the background retiring, drops a load under way.
            val header by produceState<Pair<String, SessionArtwork?>?>(null, artistHeaderUrl, type, retired) {
                if (retired) return@produceState
                val url = artistHeaderUrl ?: return@produceState
                value = url to loadSessionArtwork(context, null, url, STILL_HEADER_PX)
            }
            val loaded = header?.takeIf { it.first == artistHeaderUrl }
            val image = when {
                artistHeaderPending -> null
                artistHeaderUrl != null && loaded == null -> null
                else -> loaded?.second ?: cover
            }
            StillImageBackground(image, blurDp, modifier, refined = refined, retired = retired)
        }
    }
}

private const val STILL_COVER_PX = 1024
private const val STILL_HEADER_PX = 1600
