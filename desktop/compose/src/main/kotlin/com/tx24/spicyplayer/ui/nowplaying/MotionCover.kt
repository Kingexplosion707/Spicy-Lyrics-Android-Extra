package com.tx24.spicyplayer.ui.nowplaying

// The desktop version of :app's MotionCover.kt (same API): animated covers and Spotify Canvas
// loops, decoded by FFmpeg instead of ExoPlayer and drawn by Compose instead of a TextureView.

import android.content.Context
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.desktop.video.VideoLoop
import com.tx24.spicyplayer.network.data.counted
import com.tx24.spicyplayer.network.motion.MotionCoverFinder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/** What the animated cover is looked up by. */
data class MotionCoverQuery(val artist: String, val album: String, val title: String)

@Composable
fun rememberMotionCoverUrl(enabled: Boolean, query: MotionCoverQuery): String? =
    rememberMotionCover(enabled, query).let { if (it.settled) it.url else null }

data class MotionCoverLookup(val url: String?, val settled: Boolean)

@Composable
fun rememberMotionCover(enabled: Boolean, query: MotionCoverQuery, tall: Boolean = false): MotionCoverLookup {
    val context = LocalContext.current
    val artist = leadArtist(query.artist)
    val key = MotionCoverFinder.key(artist, query.album, query.title)
    return produceState(MotionCoverLookup(null, settled = true), enabled, key, tall) {
        if (!enabled || key == null) {
            value = MotionCoverLookup(null, settled = true)
            return@produceState
        }
        value = value.copy(settled = false)
        val finder = MotionCovers.finder(context)
        val url = if (tall) finder.findTall(artist, query.album, query.title) else finder.find(artist, query.album, query.title)
        value = MotionCoverLookup(url, settled = true)
    }.value
}

@Composable
fun CrossfadingMotionVideo(
    url: String?,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    cropFromTop: Float? = null,
    held: Boolean = false,
) {
    val layers = remember { mutableStateListOf<String>() }
    val ready = remember { mutableStateMapOf<String, Boolean>() }
    if (url != null && layers.lastOrNull() != url) {
        val holding = layers.lastOrNull { ready[it] == true }
        layers.retainAll { it == holding }
        layers.remove(url)
        layers.add(url)
    }
    val current = url?.takeIf { ready[it] == true }
    LaunchedEffect(url, current) {
        if (url != null && current == null) return@LaunchedEffect
        delay(FADE_MS.toLong())
        layers.retainAll { it == url }
        ready.keys.retainAll(layers.toSet())
    }
    Box(modifier) {
        for (layer in layers) {
            key(layer) {
                MotionCoverVideo(
                    url = layer,
                    visible = url != null,
                    onFailed = { onFailed(layer) },
                    modifier = Modifier.matchParentSize(),
                    cropFromTop = cropFromTop,
                    onFirstFrame = { ready[layer] = true },
                    held = held,
                )
            }
        }
    }
}

/**
 * Loops [url] muted, covering its box (centred; with [cropFromTop], that share of the overflow is
 * cut from the top). Fades in once its first frame is up.
 */
@Composable
fun MotionCoverVideo(
    url: String,
    visible: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    layerPaint: Paint? = null,
    cropFromTop: Float? = null,
    onFirstFrame: () -> Unit = {},
    held: Boolean = false,
) = key(url) {
    val latestOnFirstFrame by rememberUpdatedState(onFirstFrame)
    val stream = rememberMotionCoverStream(url, onFailed, held)
    val frame = stream.frame
    LaunchedEffect(frame != null) { if (frame != null) latestOnFirstFrame() }
    val alpha by animateFloatAsState(if (frame != null && visible) 1f else 0f, tween(FADE_MS), label = "motionCoverAlpha")
    Canvas(modifier.graphicsLayer { this.alpha = alpha }) {
        val image = frame ?: return@Canvas
        val iw = image.width.toFloat(); val ih = image.height.toFloat()
        val scale = maxOf(size.width / iw, size.height / ih)
        val w = iw * scale; val h = ih * scale
        val left = (size.width - w) / 2f
        val top = (size.height - h) * (cropFromTop ?: 0.5f)
        drawIntoCanvas { canvas ->
            val paint = org.jetbrains.skia.Paint().apply { layerPaint?.colorFilter?.let { colorFilter = it.skia } }
            canvas.nativeCanvas.drawImageRect(
                image,
                org.jetbrains.skia.Rect.makeWH(iw, ih),
                org.jetbrains.skia.Rect.makeXYWH(left, top, w, h),
                org.jetbrains.skia.SamplingMode.LINEAR, paint, true,
            )
        }
    }
}

/** The shared loop of [url], held while this is composed. */
@Composable
internal fun rememberMotionCoverStream(url: String, onFailed: () -> Unit, held: Boolean = false): MotionCoverStream {
    val stream = remember(url) { MotionCoverLease(MotionCovers.acquire(url)) }.stream
    val latestOnFailed by rememberUpdatedState(onFailed)
    DisposableEffect(stream) {
        val stop = stream.onFailure { latestOnFailed() }
        stream.onScreen(true)
        onDispose { stop(); stream.onScreen(false) }
    }
    DisposableEffect(stream, held) {
        if (!held) return@DisposableEffect onDispose {}
        stream.cover(true)
        onDispose { stream.cover(false) }
    }
    return stream
}

private class MotionCoverLease(val stream: MotionCoverStream) : RememberObserver {
    override fun onRemembered() = Unit
    override fun onForgotten() = MotionCovers.release(stream)
    override fun onAbandoned() = MotionCovers.release(stream)
}

internal fun leadArtist(artists: String): String =
    artists.split(", ").first().split(Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE))
        .first().trim()

internal const val FADE_MS = 600

internal fun coverSize(width: Int, height: Int, aspect: Float): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || aspect <= 0f) return width to height
    return if (width.toFloat() / height > aspect) width to ceil(width / aspect).toInt() else ceil(height * aspect).toInt() to height
}

/** A small copy of one frame: [width]×[height] ARGB pixels, top row first. */
internal class MotionSample(val pixels: IntArray, val width: Int, val height: Int, val copyNanos: Long)

/** One loop, shared by every view and sampler of its URL. */
internal class MotionCoverStream(val url: String) {
    private val loop = VideoLoop(url, MotionCovers.cacheDir)
    private var screens = 0
    private var covered = 0
    val frame: org.jetbrains.skia.Image? get() = loop.frame
    val videoAspect: Float get() = loop.aspect

    fun onFailure(cb: () -> Unit): () -> Unit = loop.onFailure(cb)
    fun onScreen(started: Boolean) { screens += if (started) 1 else -1; sync() }
    fun cover(c: Boolean) { covered += if (c) 1 else -1; sync() }
    fun sync() {
        val drawn = loop.frame != null
        loop.playing = screens > 0 && !drawn || screens - covered > 0 && !MotionCovers.pausedNow(url)
    }
    fun addSampler(longSide: Int, intervalMs: Long, onFrame: (MotionSample) -> Unit): () -> Unit =
        loop.addSampler(longSide, intervalMs) { px, w, h, nanos -> onFrame(MotionSample(px, w, h, nanos)) }
    fun release() = loop.release()
}

internal object MotionCovers {
    @Volatile private var finder: MotionCoverFinder? = null
    val cacheDir: File by lazy { File(com.tx24.spicyplayer.desktop.platform.DesktopPlatform.dataDir("cache"), "motion/streams").apply { mkdirs() } }

    fun finder(context: Context): MotionCoverFinder = finder ?: synchronized(this) {
        finder ?: MotionCoverFinder(
            client = OkHttpClient.Builder().counted().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(),
            memoFile = File(context.cacheDir, "motion/known.json"),
        ).also { finder = it }
    }

    private val streams = HashMap<String, Pair<MotionCoverStream, Int>>()

    var pausedKinds: Set<LoopKind> = emptySet()
        set(value) { if (field != value) { field = value; streams.values.forEach { it.first.sync() } } }
    var musicPaused = false
        set(value) { if (field != value) { field = value; streams.values.forEach { it.first.sync() } } }

    fun pausedNow(url: String): Boolean = musicPaused && LoopKind.of(url) in pausedKinds

    @Synchronized fun acquire(url: String): MotionCoverStream {
        val (s, n) = streams[url] ?: (MotionCoverStream(url) to 0)
        streams[url] = s to n + 1
        return s
    }
    fun acquire(context: Context, url: String): MotionCoverStream = acquire(url)

    @Synchronized fun release(stream: MotionCoverStream) {
        val (s, n) = streams[stream.url] ?: return
        if (n <= 1) { streams.remove(stream.url); s.release() } else streams[stream.url] = s to n - 1
    }

    internal fun isHls(url: String) = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
}

internal suspend fun forgetMotionCover(context: Context, query: MotionCoverQuery) {
    MotionCovers.finder(context).forget(leadArtist(query.artist), query.album, query.title)
}

enum class LoopKind(val label: String) {
    AnimatedCover("Animated covers"),
    Canvas("Spotify Canvas");

    companion object {
        fun of(url: String): LoopKind = if (MotionCovers.isHls(url)) AnimatedCover else Canvas
        fun parse(stored: String): Set<LoopKind> =
            stored.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.toSet()
        fun store(kinds: Set<LoopKind>): String = entries.filter { it in kinds }.joinToString(",") { it.name }
    }
}
