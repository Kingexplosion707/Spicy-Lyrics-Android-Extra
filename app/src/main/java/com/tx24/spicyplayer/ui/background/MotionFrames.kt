package com.tx24.spicyplayer.ui.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.KawarpVideoFrame
import com.tx24.spicyplayer.ui.nowplaying.rememberMotionCoverStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Small frames of the animated cover at [url] for the moving background to follow, from the same
 * player the header shows it with; null when there's no [url]. A frame not taken before the next
 * arrives is dropped. A stream that fails to play calls [onFailed].
 */
@Composable
fun rememberMotionFrames(url: String?, onFailed: () -> Unit): Flow<KawarpVideoFrame>? {
    if (url == null) return null
    val stream = rememberMotionCoverStream(url, onFailed)
    return remember(stream) {
        callbackFlow {
            val stop = stream.addSampler(SAMPLE_LONG_SIDE, SAMPLE_INTERVAL_MS) { sample ->
                trySend(KawarpVideoFrame(sample.pixels, sample.width, sample.height, sample.copyNanos))
            }
            awaitClose(stop)
        }.buffer(Channel.CONFLATED)
    }
}

/** Square covers come as 64×64, tall ones as 48×64: the blur makes anything finer pointless. */
private const val SAMPLE_LONG_SIDE = 64
/** About 12 frames a second. */
private const val SAMPLE_INTERVAL_MS = 83L
