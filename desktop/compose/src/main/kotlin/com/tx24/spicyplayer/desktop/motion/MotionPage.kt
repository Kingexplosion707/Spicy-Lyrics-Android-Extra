package com.tx24.spicyplayer.desktop.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefBrowserPixels
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.json.JSONObject

/**
 * Chromium for motion lyrics' page (ivLyrics' MotionLyricsPage.html, the same file the Android app
 * runs in a WebView). Its engine is fetched the first time motion lyrics play (about 170 MB, once),
 * then kept in the app's folder.
 */
object MotionEngine {
    sealed interface Status {
        data object Idle : Status
        data class Preparing(val percent: Float) : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    /** Motion lyrics can play here (the engine fetched or fetchable). */
    @Volatile var available: Boolean = System.getProperty("os.name").let { it.startsWith("Windows") || it.startsWith("Linux") }

    private val mutableStatus = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = mutableStatus

    @Volatile private var client: CefClient? = null
    private val pages = ConcurrentHashMap<CefBrowser, MotionPage>()
    private val waiting = mutableListOf<(CefClient) -> Unit>()

    internal const val ORIGIN = "https://motion.ivlyrics.app/"
    private val page: String by lazy {
        val html = MotionEngine::class.java.getResourceAsStream("/motion/MotionLyricsPage.html")!!.readBytes().decodeToString()
        // The page's channel to the app: Android's addJavascriptInterface, here CEF's query router.
        val bridge = "<script>window.ivLyricsMotionNative={postMessage:function(m){window.cefQuery&&window.cefQuery({request:String(m),persistent:false,onSuccess:function(){},onFailure:function(){}});}};</script>"
        html.replaceFirst("<head>", "<head>$bridge")
    }
    val themeScript: String by lazy { MotionEngine::class.java.getResourceAsStream("/motion/SpicyThemes.js")?.readBytes()?.decodeToString() ?: "" }

    /** Calls [then] with the client once the engine is up (on the Swing thread). */
    fun withClient(then: (CefClient) -> Unit) {
        client?.let { then(it); return }
        synchronized(waiting) { waiting += then }
        if (mutableStatus.value is Status.Preparing) return
        mutableStatus.value = Status.Preparing(0f)
        Thread({
            val result = runCatching { start() }
            SwingUtilities.invokeLater {
                result.onSuccess { c ->
                    client = c
                    mutableStatus.value = Status.Ready
                    val list = synchronized(waiting) { waiting.toList().also { waiting.clear() } }
                    list.forEach { it(c) }
                }.onFailure { e ->
                    System.err.println("Motion engine: ${e.stackTraceToString()}")
                    mutableStatus.value = Status.Failed(e.message ?: "Couldn't start")
                    synchronized(waiting) { waiting.clear() }
                }
            }
        }, "motion-engine").apply { isDaemon = true }.start()
    }

    private fun start(): CefClient {
        val builder = me.friwi.jcefmaven.CefAppBuilder()
        builder.setInstallDir(File(DesktopPlatform.dataDir("chromium"), "jcef"))
        builder.setProgressHandler { state, percent ->
            if (state == me.friwi.jcefmaven.EnumProgress.DOWNLOADING || state == me.friwi.jcefmaven.EnumProgress.EXTRACTING) {
                mutableStatus.value = Status.Preparing(if (percent < 0) 0f else percent / 100f)
            }
        }
        builder.cefSettings.windowless_rendering_enabled = true
        builder.cefSettings.log_severity = org.cef.CefSettings.LogSeverity.LOGSEVERITY_DISABLE
        builder.cefSettings.cache_path = File(DesktopPlatform.dataDir("chromium"), if (DesktopPlatform.wallpaper) "cache-wallpaper" else "cache").path
        builder.addJcefArgs("--autoplay-policy=no-user-gesture-required", "--no-first-run", "--no-default-browser-check", "--disable-gpu-compositing")
        builder.setAppHandler(object : me.friwi.jcefmaven.MavenCefAppHandlerAdapter() {})
        val app: CefApp = builder.build()
        val c = app.createClient()
        // The page comes from memory at its https origin.
        c.addRequestHandler(object : org.cef.handler.CefRequestHandlerAdapter() {
            override fun getResourceRequestHandler(
                browser: CefBrowser?, frame: CefFrame?, request: CefRequest, isNavigation: Boolean, isDownload: Boolean,
                requestInitiator: String?, disableDefaultHandling: org.cef.misc.BoolRef?,
            ): org.cef.handler.CefResourceRequestHandler? =
                if (request.url == ORIGIN) object : org.cef.handler.CefResourceRequestHandlerAdapter() {
                    override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest) = PageHandler(page.toByteArray())
                } else null
        })
        val router = CefMessageRouter.create()
        router.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(browser: CefBrowser, frame: CefFrame, queryId: Long, request: String, persistent: Boolean, callback: CefQueryCallback): Boolean {
                val page = pages[browser]
                SwingUtilities.invokeLater { page?.receive(request) }
                callback.success("")
                return true
            }
        }, true)
        c.addMessageRouter(router)
        return c
    }

    internal fun attach(browser: CefBrowser, page: MotionPage) { pages[browser] = page }
    internal fun detach(browser: CefBrowser) { pages.remove(browser) }

    /** Serves the page from memory at its https origin (so its font requests come from an https page). */
    private class PageHandler(private val bytes: ByteArray) : CefResourceHandlerAdapter() {
        private var offset = 0
        override fun open(request: CefRequest, handleRequest: org.cef.misc.BoolRef, callback: CefCallback): Boolean { handleRequest.set(true); return true }
        override fun getResponseHeaders(response: CefResponse, length: IntRef, redirect: StringRef) {
            response.mimeType = "text/html"
            response.status = 200
            length.set(bytes.size)
        }
        override fun readResponse(out: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
            if (offset >= bytes.size) { bytesRead.set(0); return false }
            val n = minOf(bytesToRead, bytes.size - offset)
            System.arraycopy(bytes, offset, out, 0, n)
            offset += n
            bytesRead.set(n)
            return true
        }
    }
}

/**
 * ivLyrics' motion lyrics page, as the Android app's MotionLyricsModeView drives it: the song, the
 * position, the viewer's comfort and Spicy's look go to the page; its frames come back as images.
 */
class MotionPage {
    var frame by mutableStateOf<Image?>(null); private set
    private var browser: CefBrowserPixels? = null
    private var released = false
    private var width = 640
    private var height = 480
    private var scale = 1.0

    private var pageReady = false
    private var wantedSong = ""
    private var sentSong: String? = null
    private var sentOptions: String? = null
    private var prelude = ""
    private var sentPrelude: String? = null
    private var positionMs = 0L
    private var playing = false
    private var active = true
    private var intensity = "normal"
    private var rotation = "full"
    private var safe = false
    private var look: String? = null

    init {
        MotionEngine.withClient { client ->
            if (released) return@withClient
            val b = CefBrowserPixels(client, MotionEngine.ORIGIN, true, null)
            b.resize(width, height, scale)
            MotionEngine.attach(b, this)
            b.addOnPaintListener { event ->
                val w = event.width; val h = event.height
                if (System.getenv("SPICY_DEBUG") != null) System.err.println("motion paint ${w}x$h")
                val buffer = event.renderedFrame
                val bytes = ByteArray(w * h * 4)
                buffer.rewind(); buffer.get(bytes)
                val image = Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL), bytes, w * 4)
                SwingUtilities.invokeLater { if (!released) frame = image }
            }
            b.createImmediately()
            b.setWindowlessFrameRate(60)
            browser = b
        }
    }

    fun resize(widthPx: Int, heightPx: Int, density: Float) {
        scale = density.toDouble()
        width = (widthPx / density).toInt().coerceAtLeast(1)
        height = (heightPx / density).toInt().coerceAtLeast(1)
        browser?.resize(width, height, scale)
    }

    fun setSong(uri: String, json: String, themeJson: String) {
        prelude = MotionEngine.themeScript + ";window.spicyMotionTheme=" + themeJson + ";"
        wantedSong = json
        flush()
    }

    fun setComfort(intensity: String, rotation: String, safe: Boolean) {
        this.intensity = intensity; this.rotation = rotation; this.safe = safe
        flush()
    }

    fun redirect() = run("window.ivLyricsMotionHost&&window.ivLyricsMotionHost.redirect&&window.ivLyricsMotionHost.redirect()")

    fun setActive(active: Boolean) {
        this.active = active
        browser?.setWindowlessFrameRate(if (active) 60 else 1)
        flush()
    }

    fun setPlayback(positionMs: Long, playing: Boolean) {
        this.positionMs = maxOf(0L, positionMs)
        this.playing = playing
        flush()
    }

    /** Spicy's look over the page (as the Android app's SpicyBridge.motionLook). */
    fun setLook(shade: Float, fadeBottom: Float) {
        val bottom = maxOf(EDGE_FADE, minOf(0.5f, fadeBottom))
        val edges = String.format(Locale.US,
            "linear-gradient(to bottom,transparent 0%%,#000 %.1f%%,#000 %.1f%%,transparent 100%%)," +
                "linear-gradient(to right,transparent 0%%,#000 %.1f%%,#000 %.1f%%,transparent 100%%)",
            EDGE_FADE * 100f, (1f - bottom) * 100f, SIDE_FADE * 100f, (1f - SIDE_FADE) * 100f)
        val mask = "-webkit-mask-image:$edges;mask-image:$edges;-webkit-mask-composite:source-in;mask-composite:intersect"
        val css = String.format(Locale.US,
            ".ivlyrics-motion-scrim{opacity:%.2f!important;%s}.ivlyrics-motion-hud{display:none!important}#ivlyrics-motion-root{%s}",
            maxOf(0f, minOf(1f, shade)), mask, mask)
        val script = "(function(){if(!document.head)return;var s=document.getElementById('spicy-look');" +
            "if(!s){s=document.createElement('style');s.id='spicy-look';document.head.appendChild(s);}s.textContent='$css';})()"
        look = script
        run(script)
    }

    fun release() {
        released = true
        browser?.let { MotionEngine.detach(it); it.close(true) }
        browser = null
    }

    internal fun receive(text: String) {
        if (System.getenv("SPICY_DEBUG") != null) System.err.println("motion says ${text.take(120)}")
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("type")) {
            "ready" -> {
                pageReady = true
                sentSong = null; sentOptions = null; sentPrelude = null
                look?.let(::run)
                flush()
            }
            "cacheGet" -> resolve(message.optInt("id"), cache.getString(message.optString("key"), "") ?: "", null)
            "cacheSet" -> cache.edit().putString(message.optString("key"), message.optString("value")).apply()
            "direct" -> resolve(message.optInt("id"), null, "AI direction is off")
            "error" -> System.err.println("motion lyrics page: ${message.optString("message")}")
        }
    }

    private val cache by lazy { DesktopPlatform.context.getSharedPreferences("ivlyrics_motion", android.content.Context.MODE_PRIVATE) }

    private fun flush() {
        if (!pageReady) return
        val options = optionsJson()
        if (options != sentOptions) { sentOptions = options; run("ivLyricsMotionHost.configure($options)") }
        if (prelude.isNotEmpty() && prelude != sentPrelude) { sentPrelude = prelude; run(prelude) }
        if (wantedSong != sentSong) {
            sentSong = wantedSong
            run("ivLyricsMotionHost.setTrack(" + (wantedSong.ifEmpty { "null" }) + ")")
        }
        run(String.format(Locale.US, "ivLyricsMotionHost.setPlayback({position:%d,playing:%s,rate:1})", positionMs, playing && active))
    }

    private fun optionsJson(): String =
        "{\"labels\":{\"directing\":\"\",\"failed\":\"\"},\"ai\":false,\"background\":true," +
            "\"motion\":{\"intensity\":\"$intensity\",\"rotation\":\"$rotation\",\"safe\":$safe}," +
            "\"insets\":{\"top\":0.0,\"right\":$RESERVE,\"bottom\":0.0,\"left\":0.0}}"

    private fun resolve(id: Int, value: String?, error: String?) {
        val body = if (error == null) "{\"ok\":true,\"value\":${JSONObject.quote(value ?: "")}}" else "{\"ok\":false,\"error\":${JSONObject.quote(error)}}"
        run("ivLyricsMotionHost.resolve($id,$body)")
    }

    private fun run(script: String) {
        browser?.let { b -> b.executeJavaScript(script, MotionEngine.ORIGIN, 0) }
    }

    private companion object {
        const val EDGE_FADE = 0.10f
        const val SIDE_FADE = 0.12f
        /** The Android stage keeps its small print clear of its buttons' corner; the same here. */
        const val RESERVE = 100.0
    }
}

/** Draws the page's latest frame, and keeps the page the size of its place. */
@Composable
fun MotionPageView(page: MotionPage, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val status by MotionEngine.status.collectAsState()
    Box(modifier.onSizeChanged { page.resize(it.width, it.height, density) }) {
        Canvas(Modifier.matchParentSize()) {
            val image = page.frame ?: return@Canvas
            drawIntoCanvas {
                it.nativeCanvas.drawImageRect(
                    image,
                    org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                    org.jetbrains.skia.Rect.makeWH(size.width, size.height),
                    org.jetbrains.skia.SamplingMode.LINEAR, null, true,
                )
            }
        }
        val s = status
        if (page.frame == null && s is MotionEngine.Status.Preparing) {
            Text(
                "Getting motion lyrics ready… ${(s.percent * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.align(Alignment.Center),
            )
        } else if (page.frame == null && s is MotionEngine.Status.Failed) {
            Text("Motion lyrics couldn't start: ${s.message}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}
