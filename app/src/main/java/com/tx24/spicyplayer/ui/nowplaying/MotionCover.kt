package com.tx24.spicyplayer.ui.nowplaying

import android.content.Context
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tx24.spicyplayer.network.data.TrafficCounter
import com.tx24.spicyplayer.network.data.counted
import com.tx24.spicyplayer.network.motion.MotionCoverFinder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt
import okhttp3.OkHttpClient

/** What the animated cover is looked up by. */
data class MotionCoverQuery(val artist: String, val album: String, val title: String)

/**
 * The record's animated cover stream, or null while there is none (setting off, still searching,
 * or the album has no animation). Resets to null the moment the record changes.
 */
@Composable
fun rememberMotionCoverUrl(enabled: Boolean, query: MotionCoverQuery): String? =
    rememberMotionCover(enabled, query).let { if (it.settled) it.url else null }

/**
 * The animated cover's lookup for [query]. While a new song's is under way, it holds the last
 * answer, not [settled], so a loop on screen can fade out or give way to the next one instead
 * of vanishing the moment the song changes.
 */
data class MotionCoverLookup(val url: String?, val settled: Boolean)

@Composable
fun rememberMotionCover(
    enabled: Boolean,
    query: MotionCoverQuery,
    /** The record's tall stream where it has one, for a stage taller than it is wide. */
    tall: Boolean = false,
): MotionCoverLookup {
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

/**
 * [MotionCoverVideo] across changes of [url], the way a song change swaps one loop for the next:
 * the outgoing loop holds under the incoming one until that has drawn a frame and faded in, so
 * what's beneath never shows in between. A null [url] fades the last one out. [onFailed] gets
 * the loop that failed.
 */
@Composable
fun CrossfadingMotionVideo(
    url: String?,
    onFailed: (String) -> Unit,
    modifier: Modifier = Modifier,
    cropFromTop: Float? = null,
    /** Covered: the loops hold their frames ([MotionCoverVideo]'s `held`). */
    held: Boolean = false,
) {
    // Oldest first: at most the newest loop that has shown a frame and the one coming in.
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
        // Once the new loop has faded in, or the last one out, nothing beneath is needed.
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
 * Loops [url] muted, filling its box. Nothing shows until the first frame has been drawn, then it
 * fades in over whatever is beneath (the still cover); [visible] false fades it back out without
 * stopping it. A stream that fails to play calls [onFailed]. Every view of one [url] shows the
 * same decode ([MotionCoverStream]).
 *
 * With [cropFromTop] set, the video keeps its own shape instead, as big as it takes to cover the
 * box, centred across and with that share of the overflow cut from the top (square until the video
 * reports its size, while it's still hidden).
 *
 * A TextureView rather than a SurfaceView, so the cover's rounded clip and alpha apply to it.
 */
@Composable
fun MotionCoverVideo(
    url: String,
    visible: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn through this paint, e.g. a colour filter. */
    layerPaint: Paint? = null,
    cropFromTop: Float? = null,
    onFirstFrame: () -> Unit = {},
    /** Covered by something opaque: the loop holds its frame until it shows again. */
    held: Boolean = false,
) = key(url) {
    val latestOnFirstFrame by rememberUpdatedState(onFirstFrame)
    val stream = rememberMotionCoverStream(url, onFailed, held)
    var firstFrame by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (firstFrame && visible) 1f else 0f,
        animationSpec = tween(FADE_MS),
        label = "motionCoverAlpha",
    )
    AndroidView(
        factory = {
            TextureView(it).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        stream.addView(surface) {
                            firstFrame = true
                            latestOnFirstFrame()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        stream.resizeView(surface)
                    }

                    // The stream releases it, after it has stopped drawing into it.
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        stream.removeView(surface)
                        return false
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
        update = { it.setLayerPaint(layerPaint) },
        modifier = modifier
            .then(
                if (cropFromTop == null) Modifier else Modifier.layout { measurable, constraints ->
                    val aspect = stream.videoAspect.takeIf { it > 0f } ?: 1f
                    val (width, height) = coverSize(constraints.maxWidth, constraints.maxHeight, aspect)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (constraints.maxWidth - width) / 2,
                            ((constraints.maxHeight - height) * cropFromTop).roundToInt(),
                        )
                    }
                },
            )
            .graphicsLayer { this.alpha = alpha },
    )
}

/**
 * The shared stream of [url], held while this is composed: one player for every view and sampler
 * of it, looping only while the app is on screen. A stream that fails to play calls [onFailed].
 * [held] (covered by something opaque) stops this one keeping it looping, so a loop nobody can
 * see holds its frame instead of decoding; one that hasn't drawn yet still plays to its first.
 */
@Composable
internal fun rememberMotionCoverStream(url: String, onFailed: () -> Unit, held: Boolean = false): MotionCoverStream {
    val context = LocalContext.current
    val stream = remember(url) { MotionCoverLease(MotionCovers.acquire(context, url)) }.stream
    val latestOnFailed by rememberUpdatedState(onFailed)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(stream) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                latestOnFailed()
            }
        }
        stream.player.addListener(listener)
        onDispose { stream.player.removeListener(listener) }
    }
    DisposableEffect(stream, lifecycle) {
        // Only loops while the app is on screen, and not held with the music ([MotionCovers.pausedNow]).
        var started = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { started = true; stream.onScreen(true) }
                Lifecycle.Event.ON_STOP -> if (started) { started = false; stream.onScreen(false) }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (started) stream.onScreen(false)
        }
    }
    DisposableEffect(stream, held) {
        if (!held) return@DisposableEffect onDispose {}
        stream.cover(true)
        onDispose { stream.cover(false) }
    }
    return stream
}

/** Gives the stream back when it leaves the composition, or never made it in. */
private class MotionCoverLease(val stream: MotionCoverStream) : RememberObserver {
    override fun onRemembered() = Unit
    override fun onForgotten() = MotionCovers.release(stream)
    override fun onAbandoned() = MotionCovers.release(stream)
}

/**
 * The first credited artist: the session joins several with ", ", and Apple files the record
 * under its lead.
 */
internal fun leadArtist(artists: String): String =
    artists.split(", ").first().split(Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE))
        .first().trim()

internal const val FADE_MS = 600

/** The size, at [aspect] (width over height), that just covers a [width]×[height] box. */
internal fun coverSize(width: Int, height: Int, aspect: Float): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || aspect <= 0f) return width to height
    return if (width.toFloat() / height > aspect) {
        width to ceil(width / aspect).toInt()
    } else {
        ceil(height * aspect).toInt() to height
    }
}

/**
 * The process-wide finder and stream cache, and the shared streams built on them: animated covers
 * and Spotify Canvas videos alike.
 */
@OptIn(UnstableApi::class)
internal object MotionCovers {
    @Volatile private var finder: MotionCoverFinder? = null
    @Volatile private var cache: SimpleCache? = null

    fun finder(context: Context): MotionCoverFinder = finder ?: synchronized(this) {
        finder ?: MotionCoverFinder(
            client = OkHttpClient.Builder()
                .counted()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build(),
            memoFile = File(context.cacheDir, "motion/known.json"),
        ).also { finder = it }
    }

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "motion/streams"),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    /** Streams by URL, with how many hold each. Main thread. */
    private val streams = HashMap<String, MotionCoverStream>()

    /**
     * The kinds of loop that hold their frame while the music is paused (both, unless unpicked), and
     * whether it is. A stream pauses as a whole, so every view of it stays on the same frame.
     */
    var pausedKinds: Set<LoopKind> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            streams.values.forEach { it.sync() }
        }
    var musicPaused = false
        set(value) {
            if (field == value) return
            field = value
            streams.values.forEach { it.sync() }
        }

    /** Whether a loop at [url] is held still right now. */
    fun pausedNow(url: String): Boolean = musicPaused && LoopKind.of(url) in pausedKinds
    private val main = Handler(Looper.getMainLooper())

    fun acquire(context: Context, url: String): MotionCoverStream =
        streams.getOrPut(url) { MotionCoverStream(url, player(context.applicationContext, url)) }.also { it.users++ }

    /**
     * Lets go of [stream]; the last one out releases it a moment later, so a view handing over to
     * another (a background type swap) keeps the same player.
     */
    fun release(stream: MotionCoverStream) {
        stream.users--
        if (stream.users > 0) return
        main.post {
            if (stream.users > 0) return@post
            if (streams[stream.url] === stream) streams.remove(stream.url)
            stream.release()
        }
    }

    private fun player(context: Context, url: String): ExoPlayer {
        val upstream = DefaultHttpDataSource.Factory().setUserAgent(BROWSER_UA).setTransferListener(CountedTransfers)
        val source = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Apple's animated covers are HLS streams; a Spotify Canvas is a single MP4 file.
        val hls = isHls(url)
        val sources = if (hls) HlsMediaSource.Factory(source) else ProgressiveMediaSource.Factory(source)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(sources)
            .build()
            .apply {
                // Never asks for audio focus (the default), so the music app is left alone.
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .apply {
                        // Apple offers up to 1080² at 10 Mbit/s. One steady rendition this side of
                        // the cap keeps a loop to a few MB and caches cleanly.
                        if (hls) {
                            setMaxVideoSize(MAX_VIDEO_PX, MAX_VIDEO_PX)
                            setMaxVideoBitrate(MAX_VIDEO_BITRATE)
                            setForceHighestSupportedBitrate(true)
                        }
                    }
                    .build()
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                // Started once a view of it is on screen ([MotionCoverStream.sync]).
                playWhenReady = false
            }
    }

    internal fun isHls(url: String) = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    private const val CACHE_BYTES = 256L shl 20
    private const val MAX_VIDEO_PX = 1080
    private const val MAX_VIDEO_BITRATE = 3_600_000
    private const val BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
}

/** Counts what the cover videos download (cache hits aren't network, so don't count). */
@OptIn(UnstableApi::class)
private object CountedTransfers : TransferListener {
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (isNetwork) TrafficCounter.add(dataSpec.uri.host, bytesTransferred.toLong())
    }
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
}

/** Forgets a remembered stream that failed, so the next song change searches afresh. */
internal suspend fun forgetMotionCover(context: Context, query: MotionCoverQuery) {
    MotionCovers.finder(context).forget(leadArtist(query.artist), query.album, query.title)
}

/** The kinds of loop the app plays, which can each be set to hold still while the music is paused. */
enum class LoopKind(val label: String) {
    AnimatedCover("Animated covers"),
    Canvas("Spotify Canvas");

    companion object {
        /** Apple's animated covers are HLS streams; a Spotify Canvas is a single MP4. */
        fun of(url: String): LoopKind = if (MotionCovers.isHls(url)) AnimatedCover else Canvas

        /** Stored as names joined by commas; an unknown name is dropped. */
        fun parse(stored: String): Set<LoopKind> =
            stored.split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.toSet()

        fun store(kinds: Set<LoopKind>): String = entries.filter { it in kinds }.joinToString(",") { it.name }
    }
}
