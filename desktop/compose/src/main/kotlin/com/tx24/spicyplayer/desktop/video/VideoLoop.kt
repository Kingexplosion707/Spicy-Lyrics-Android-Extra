package com.tx24.spicyplayer.desktop.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.FFmpegLogCallback
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * A muted video looping on a thread of its own: a Spotify Canvas MP4 or an Apple Music HLS
 * stream. The stream is saved to the disk cache once (an HLS stream remuxed to one MP4, its variant
 * no bigger than [MAX_SIDE]), then decoded from there with FFmpeg, frame by frame on its own
 * clock. [frame] is the latest frame (Compose state); samplers get small ARGB copies.
 */
class VideoLoop(private val url: String, private val cacheDir: File) {
    var frame by mutableStateOf<Image?>(null); private set
    var aspect by mutableStateOf(0f); private set
    @Volatile var playing = false
        set(v) { field = v; synchronized(lock) { (lock as Object).notifyAll() } }

    private val lock = Any()
    @Volatile private var released = false
    private val failures = CopyOnWriteArrayList<() -> Unit>()
    private class Sampler(val longSide: Int, val intervalMs: Long, val onFrame: (IntArray, Int, Int, Long) -> Unit) { @Volatile var last = 0L }
    private val samplers = CopyOnWriteArrayList<Sampler>()

    fun onFailure(cb: () -> Unit): () -> Unit { failures += cb; return { failures -= cb } }
    fun addSampler(longSide: Int, intervalMs: Long, onFrame: (IntArray, Int, Int, Long) -> Unit): () -> Unit {
        val s = Sampler(longSide, intervalMs, onFrame); samplers += s; return { samplers -= s }
    }
    fun release() { released = true; playing = false }

    private val thread = Thread(::run, "video-loop").apply { isDaemon = true; start() }

    private fun run() {
        val file = runCatching { local() }.getOrNull()
        if (file == null || released) { if (!released) fail(); return }
        try {
            while (!released) {
                FFmpegFrameGrabber(file).use { g -> if (!decode(g)) return }
            }
        } catch (t: Throwable) {
            if (!released) fail()
        }
    }

    /** One pass through the file; false when it should stop for good. */
    private fun decode(g: FFmpegFrameGrabber): Boolean {
        g.pixelFormat = avutil.AV_PIX_FMT_BGRA
        g.start()
        val w0 = g.imageWidth; val h0 = g.imageHeight
        if (w0 <= 0 || h0 <= 0) return false
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w0, h0))
        val w = (w0 * scale).toInt() and 1.inv(); val h = (h0 * scale).toInt() and 1.inv()
        if (scale < 1f) { g.imageWidth = w; g.imageHeight = h; g.restart() }
        aspect = w0.toFloat() / h0
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        var startNanos = System.nanoTime()
        var pausedFor = 0L
        while (!released) {
            synchronized(lock) {
                if (!playing && frame != null) {
                    val t0 = System.nanoTime()
                    while (!playing && !released) (lock as Object).wait(500)
                    pausedFor += System.nanoTime() - t0
                }
            }
            if (released) return false
            val f = g.grabImage() ?: return true
            val buf = f.image?.get(0) as? java.nio.ByteBuffer ?: continue
            val stride = f.imageStride
            val bytes = ByteArray(stride * f.imageHeight)
            buf.rewind(); buf.get(bytes)
            val image = Image.makeRaster(info, bytes, stride)
            // Each frame at its own time.
            val due = startNanos + pausedFor + g.timestamp * 1000
            val wait = due - System.nanoTime()
            if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
            else if (wait < -500_000_000) { startNanos -= wait }
            frame = image
            sample(bytes, stride, f.imageWidth, f.imageHeight)
        }
        return false
    }

    private fun sample(bytes: ByteArray, stride: Int, w: Int, h: Int) {
        if (samplers.isEmpty()) return
        val now = System.currentTimeMillis()
        val due = samplers.filter { now - it.last >= it.intervalMs * 3 / 4 }
        if (due.isEmpty()) return
        val longSide = due.maxOf { it.longSide }
        val started = System.nanoTime()
        val s = longSide.toFloat() / maxOf(w, h)
        val sw = maxOf(1, (w * s).toInt()); val sh = maxOf(1, (h * s).toInt())
        // Box-averaged: each small pixel the mean of its block, as a GPU reduction would give.
        val out = IntArray(sw * sh)
        for (y in 0 until sh) {
            val y0 = y * h / sh; val y1 = maxOf(y0 + 1, (y + 1) * h / sh)
            for (x in 0 until sw) {
                val x0 = x * w / sw; val x1 = maxOf(x0 + 1, (x + 1) * w / sw)
                var r = 0L; var gg = 0L; var b = 0L; var n = 0
                var yy = y0
                while (yy < y1) {
                    var i = yy * stride + x0 * 4
                    for (xx in x0 until x1) { b += bytes[i].toInt() and 0xff; gg += bytes[i + 1].toInt() and 0xff; r += bytes[i + 2].toInt() and 0xff; i += 4; n++ }
                    yy += maxOf(1, (y1 - y0) / 4)
                }
                out[y * sw + x] = (0xff shl 24) or ((r / n).toInt() shl 16) or ((gg / n).toInt() shl 8) or (b / n).toInt()
            }
        }
        val nanos = System.nanoTime() - started
        due.forEach { it.last = now; it.onFrame(out, sw, sh, nanos) }
    }

    private fun fail() = javax.swing.SwingUtilities.invokeLater { failures.forEach { it() } }

    /** The stream in the cache, fetched (and for HLS, remuxed) the first time. */
    private fun local(): File {
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(cacheDir, "$name.mp4")
        if (file.length() > 0) { file.setLastModified(System.currentTimeMillis()); return file }
        val tmp = File(cacheDir, "$name.part")
        if (url.substringBefore('?').endsWith(".m3u8", true)) remux(variant(url), tmp)
        else client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            require(r.isSuccessful) { "HTTP ${r.code}" }
            tmp.outputStream().use { r.body.byteStream().copyTo(it) }
        }
        tmp.renameTo(file)
        trim()
        return file
    }

    /** The HLS variant whose longer side is closest to (not over) [MAX_SIDE]. */
    private fun variant(master: String): String {
        val text = client.newCall(Request.Builder().url(master).header("User-Agent", UA).build()).execute().use { it.body.string() }
        if (!text.contains("#EXT-X-STREAM-INF")) return master
        val lines = text.lines()
        data class V(val uri: String, val side: Int, val bw: Long)
        val variants = lines.mapIndexedNotNull { i, l ->
            if (!l.startsWith("#EXT-X-STREAM-INF")) return@mapIndexedNotNull null
            val res = Regex("RESOLUTION=(\\d+)x(\\d+)").find(l)
            val side = res?.let { maxOf(it.groupValues[1].toInt(), it.groupValues[2].toInt()) } ?: 0
            val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLong() ?: 0
            val codecs = Regex("CODECS=\"([^\"]+)\"").find(l)?.groupValues?.get(1) ?: ""
            if (codecs.contains("dvh") || codecs.contains("hvc1") && !HEVC) return@mapIndexedNotNull null
            lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.let { V(java.net.URI(master).resolve(it.trim()).toString(), side, bw) }
        }
        val pick = variants.filter { it.side in 1..MAX_SIDE }.maxByOrNull { it.side * 10_000_000L + it.bw }
            ?: variants.minByOrNull { if (it.side == 0) Int.MAX_VALUE else it.side }
        return pick?.uri ?: master
    }

    /** Copies the video stream into one MP4, no re-encoding. */
    private fun remux(source: String, out: File) {
        FFmpegFrameGrabber(source).use { g ->
            g.setOption("user_agent", UA)
            g.start()
            FFmpegFrameRecorder(out, g.imageWidth, g.imageHeight, 0).use { r ->
                r.format = "mp4"
                r.videoCodec = g.videoCodec
                r.frameRate = g.frameRate
                r.setOption("movflags", "+faststart")
                r.start(g.formatContext)
                while (true) {
                    val p = g.grabPacket() ?: break
                    if (p.stream_index() == g.videoStream) r.recordPacket(p)
                    org.bytedeco.ffmpeg.global.avcodec.av_packet_unref(p)
                }
            }
        }
    }

    private fun trim() {
        val files = cacheDir.listFiles { f -> f.extension == "mp4" }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEach { total += it.length(); if (total > CACHE_BYTES) it.delete() }
    }

    companion object {
        const val MAX_SIDE = 1080
        private const val CACHE_BYTES = 512L shl 20
        private const val HEVC = false
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
        private val client = OkHttpClient()
        init { runCatching { avutil.av_log_set_level(avutil.AV_LOG_ERROR) } }
    }
}
