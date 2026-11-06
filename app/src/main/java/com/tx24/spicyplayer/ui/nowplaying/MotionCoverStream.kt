package com.tx24.spicyplayer.ui.nowplaying

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * One animated cover playing, shared by everything that shows or samples it. The player decodes
 * once, into a texture of this stream's own; each new frame is then drawn from there into every
 * view showing it ([addView]) and, a few times a second, into a small copy for whoever samples it
 * ([addSampler]). A player can only feed one surface, so this is what lets the header and the
 * background show one decode.
 *
 * Main thread, apart from the sampler callbacks.
 */
@OptIn(UnstableApi::class)
internal class MotionCoverStream(val url: String, val player: ExoPlayer) {
    /** How many hold it; [MotionCovers] releases it once none do. */
    var users = 0
    /** The video's width over its height once it's known, else 0. Compose state. */
    var videoAspect by mutableFloatStateOf(0f)
        private set
    /** How many views of it are on a started screen; it loops only while one is. */
    private var onScreen = 0

    fun onScreen(started: Boolean) {
        onScreen = (onScreen + if (started) 1 else -1).coerceAtLeast(0)
        sync()
    }

    /** How many of those are covered by something opaque: they don't keep it looping. */
    private var covered = 0

    fun cover(covered: Boolean) {
        this.covered = (this.covered + if (covered) 1 else -1).coerceAtLeast(0)
        sync()
    }

    /** Whether the player has drawn a frame yet; until it has, there's nothing to hold. */
    private var drawn = false

    /**
     * Plays while on screen, uncovered, and not held with the music ([MotionCovers.pausedNow]).
     * Held from the start (the app opened on a paused song, or it came in covered), it plays until
     * its first frame is drawn, then holds that, so the views and the samplers have a picture.
     */
    fun sync() {
        if (released) return
        val playing = onScreen > 0 && !drawn || onScreen - covered > 0 && !MotionCovers.pausedNow(url)
        if (playing) player.play() else player.pause()
    }
    private var released = false
    private val fanOut = VideoFanOut()

    init {
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (drawn) return
                drawn = true
                sync()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                fanOut.setVideoSize(videoSize.width, videoSize.height)
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        })
        val main = Handler(Looper.getMainLooper())
        fanOut.start { surface -> main.post { if (!released) player.setVideoSurface(surface) } }
    }

    /**
     * Draws every frame into [texture] (a TextureView's), from the last one on. [onFirstFrame] runs
     * on the main thread once a frame is in it. The stream releases [texture] once [removeView]
     * has dropped it, so a TextureView handing it over returns false from `onSurfaceTextureDestroyed`.
     */
    fun addView(texture: SurfaceTexture, onFirstFrame: () -> Unit) = fanOut.addOutput(texture, onFirstFrame)

    /** The view's size changed, which resets its buffers to that size; they go back to the video's. */
    fun resizeView(texture: SurfaceTexture) = fanOut.resizeOutput(texture)

    fun removeView(texture: SurfaceTexture) = fanOut.removeOutput(texture)

    /**
     * Hands [onFrame] a small copy of a frame (ARGB, rows top first, no more than [longSide] on its
     * longer side, the video's shape) at most every [intervalMs], on the stream's own thread, with how
     * long the copy took. Returns what stops it.
     */
    fun addSampler(longSide: Int, intervalMs: Long, onFrame: (MotionSample) -> Unit): () -> Unit =
        fanOut.addSampler(longSide, intervalMs, onFrame)

    fun release() {
        if (released) return
        released = true
        player.release()
        fanOut.release()
    }
}

/** A small copy of one frame: [width]×[height] ARGB pixels, top row first. */
internal class MotionSample(val pixels: IntArray, val width: Int, val height: Int, val copyNanos: Long)

/**
 * The GL side of [MotionCoverStream], on a thread of its own: the decoder's texture, the views'
 * window surfaces and the sampler's two reduction targets.
 */
private class VideoFanOut {
    private val thread = HandlerThread("MotionCoverGL").apply { start() }
    private val handler = Handler(thread.looper)

    // Everything below belongs to the GL thread.
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var idleSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var input: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var inputTexture = 0
    private val texMatrix = FloatArray(16)
    private var hasFrame = false
    private var dead = false
    private var videoWidth = 0
    private var videoHeight = 0
    private val outputs = LinkedHashMap<SurfaceTexture, Output>()
    private val samplers = ArrayList<Sampler>()
    private var copyProgram: Program? = null
    private var reduceOesProgram: Program? = null
    private var reduceProgram: Program? = null
    private var stages: Array<Target>? = null
    private var readBuffer: ByteBuffer? = null

    private class Output(val surface: EGLSurface, val onFirstFrame: () -> Unit) {
        var drawn = false
    }

    private class Sampler(val longSide: Int, val intervalMs: Long, val onFrame: (MotionSample) -> Unit) {
        var lastMs = 0L
    }

    /** An offscreen texture to draw into. */
    private class Target(val width: Int, val height: Int, val texture: Int, val framebuffer: Int)

    fun start(onSurface: (Surface) -> Unit) {
        handler.post {
            try {
                setUpGl()
                val texture = SurfaceTexture(inputTexture)
                texture.setOnFrameAvailableListener({ onFrame() }, handler)
                input = texture
                val surface = Surface(texture)
                inputSurface = surface
                onSurface(surface)
            } catch (e: RuntimeException) {
                // Nothing then shows; the views keep what's beneath them.
                Log.w(TAG, "No GL for the animated cover", e)
                dead = true
            }
        }
    }

    fun setVideoSize(width: Int, height: Int) {
        handler.post {
            if (width <= 0 || height <= 0 || (width == videoWidth && height == videoHeight)) return@post
            videoWidth = width
            videoHeight = height
            for (texture in outputs.keys) texture.setDefaultBufferSize(width, height)
            stages = null
            // The first frame can land before its size does; held still (paused with the music),
            // it's the only one coming, so the samplers get it now.
            if (hasFrame && !dead) sample()
        }
    }

    fun addOutput(texture: SurfaceTexture, onFirstFrame: () -> Unit) {
        val posted = handler.post {
            if (dead) return@post
            if (videoWidth > 0) texture.setDefaultBufferSize(videoWidth, videoHeight)
            val surface = try {
                EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "View surface refused", e)
                return@post
            }
            if (surface == EGL14.EGL_NO_SURFACE) return@post
            val output = Output(surface, onFirstFrame)
            outputs[texture] = output
            // Never waits on a view that isn't drawing: a newer frame replaces one it hasn't taken.
            if (EGL14.eglMakeCurrent(display, surface, surface, context)) EGL14.eglSwapInterval(display, 0)
            if (hasFrame) draw(texture, output)
        }
        if (!posted) texture.release()
    }

    fun resizeOutput(texture: SurfaceTexture) {
        handler.post { if (videoWidth > 0 && texture in outputs) texture.setDefaultBufferSize(videoWidth, videoHeight) }
    }

    fun removeOutput(texture: SurfaceTexture) {
        val posted = handler.post {
            outputs.remove(texture)?.let { destroySurface(it.surface) }
            texture.release()
        }
        // Already released: its surface went with the rest.
        if (!posted) texture.release()
    }

    fun addSampler(longSide: Int, intervalMs: Long, onFrame: (MotionSample) -> Unit): () -> Unit {
        val sampler = Sampler(longSide, intervalMs, onFrame)
        handler.post {
            samplers += sampler
            // The frame up already, so a paused loop (no new frames coming) still gives one.
            if (hasFrame && !dead) sample()
        }
        return { handler.post { samplers -= sampler } }
    }

    fun release() {
        handler.post {
            dead = true
            for (output in outputs.values) destroySurface(output.surface)
            outputs.clear()
            samplers.clear()
            input?.release()
            inputSurface?.release()
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (idleSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, idleSurface)
                // The context takes its textures, framebuffers and programs with it.
                EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
            }
        }
        thread.quitSafely()
    }

    private fun onFrame() {
        val texture = input ?: return
        if (dead) return
        // The idle surface keeps a context current to latch the frame with.
        EGL14.eglMakeCurrent(display, idleSurface, idleSurface, context)
        texture.updateTexImage()
        texture.getTransformMatrix(texMatrix)
        hasFrame = true
        val lost = ArrayList<SurfaceTexture>(0)
        for ((view, output) in outputs) if (!draw(view, output)) lost += view
        // A view that went away without saying (its window gone): stop drawing into it.
        for (view in lost) outputs.remove(view)?.let { destroySurface(it.surface) }
        sample()
    }

    /** Draws the latest frame into [output]; false when its surface is gone. */
    private fun draw(view: SurfaceTexture, output: Output): Boolean {
        if (!EGL14.eglMakeCurrent(display, output.surface, output.surface, context)) return false
        val size = IntArray(2)
        EGL14.eglQuerySurface(display, output.surface, EGL14.EGL_WIDTH, size, 0)
        EGL14.eglQuerySurface(display, output.surface, EGL14.EGL_HEIGHT, size, 1)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, size[0], size[1])
        copyProgram!!.draw(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTexture, texMatrix, 0f, 0f, flip = false)
        if (!EGL14.eglSwapBuffers(display, output.surface)) {
            Log.w(TAG, "View surface lost (EGL error ${EGL14.eglGetError()}) for $view")
            return false
        }
        if (!output.drawn) {
            output.drawn = true
            val callback = output.onFirstFrame
            MAIN.post(callback)
        }
        return true
    }

    /**
     * Shrinks the frame for the samplers that are due: to four times the sample size, then to the
     * sample, each step averaging four bilinear taps (a 4×4 box) so detail doesn't shimmer in.
     */
    private fun sample() {
        if (samplers.isEmpty() || videoWidth <= 0) return
        val now = SystemClock.uptimeMillis()
        // Due a little early rather than a frame late, so the rate holds at the video's own.
        val due = samplers.filter { now - it.lastMs >= it.intervalMs * 3 / 4 }
        if (due.isEmpty()) return
        val longSide = due.maxOf { it.longSide }
        val started = System.nanoTime()
        val stages = stagesFor(longSide)
        EGL14.eglMakeCurrent(display, idleSurface, idleSurface, context)
        val big = stages[0]
        val small = stages[1]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, big.framebuffer)
        GLES20.glViewport(0, 0, big.width, big.height)
        reduceOesProgram!!.draw(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTexture, texMatrix,
            1f / videoWidth, 1f / videoHeight, flip = false,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, small.framebuffer)
        GLES20.glViewport(0, 0, small.width, small.height)
        // Flipped, so the rows read back top first.
        reduceProgram!!.draw(GLES20.GL_TEXTURE_2D, big.texture, IDENTITY, 1f / big.width, 1f / big.height, flip = true)
        val buffer = readBuffer?.takeIf { it.capacity() >= small.width * small.height * 4 }
            ?: ByteBuffer.allocateDirect(small.width * small.height * 4).order(ByteOrder.nativeOrder()).also { readBuffer = it }
        buffer.clear()
        GLES20.glReadPixels(0, 0, small.width, small.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        val pixels = IntArray(small.width * small.height)
        for (i in pixels.indices) {
            val r = buffer.get(i * 4).toInt() and 0xFF
            val g = buffer.get(i * 4 + 1).toInt() and 0xFF
            val b = buffer.get(i * 4 + 2).toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val frame = MotionSample(pixels, small.width, small.height, System.nanoTime() - started)
        for (sampler in due) {
            sampler.lastMs = now
            sampler.onFrame(frame)
        }
    }

    private fun stagesFor(longSide: Int): Array<Target> {
        val (w, h) = if (videoWidth >= videoHeight) {
            longSide to (longSide * videoHeight / videoWidth).coerceAtLeast(1)
        } else {
            (longSide * videoWidth / videoHeight).coerceAtLeast(1) to longSide
        }
        stages?.let { if (it[1].width == w && it[1].height == h) return it }
        stages?.forEach { deleteTarget(it) }
        return arrayOf(createTarget(w * 4, h * 4), createTarget(w, h)).also { stages = it }
    }

    private fun setUpGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "eglChooseConfig" }
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }
        idleSurface = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        check(EGL14.eglMakeCurrent(display, idleSurface, idleSurface, context)) { "eglMakeCurrent" }

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        inputTexture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, inputTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        copyProgram = Program(VERTEX_SHADER, "#extension GL_OES_EGL_image_external : require\n" + fragmentShader("samplerExternalOES", taps = 1))
        reduceOesProgram = Program(VERTEX_SHADER, "#extension GL_OES_EGL_image_external : require\n" + fragmentShader("samplerExternalOES", taps = 4))
        reduceProgram = Program(VERTEX_SHADER, fragmentShader("sampler2D", taps = 4))
    }

    private fun createTarget(width: Int, height: Int): Target {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenFramebuffers(1, ids, 0)
        val framebuffer = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return Target(width, height, texture, framebuffer)
    }

    private fun deleteTarget(target: Target) {
        GLES20.glDeleteFramebuffers(1, intArrayOf(target.framebuffer), 0)
        GLES20.glDeleteTextures(1, intArrayOf(target.texture), 0)
    }

    private fun destroySurface(surface: EGLSurface) {
        EGL14.eglMakeCurrent(display, idleSurface, idleSurface, context)
        EGL14.eglDestroySurface(display, surface)
    }

    /** A full-target quad sampling one texture through a texture matrix. */
    private class Program(vertex: String, fragment: String) {
        private val id = GLES20.glCreateProgram()
        private val position: Int
        private val coord: Int
        private val matrix: Int
        private val texel: Int
        private val flipY: Int
        /** x, y, u, v for a triangle strip over the whole target. */
        private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
            position(0)
        }

        init {
            GLES20.glAttachShader(id, compile(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(id, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(id)
            val status = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "link: " + GLES20.glGetProgramInfoLog(id) }
            position = GLES20.glGetAttribLocation(id, "aPosition")
            coord = GLES20.glGetAttribLocation(id, "aCoord")
            matrix = GLES20.glGetUniformLocation(id, "uTexMatrix")
            texel = GLES20.glGetUniformLocation(id, "uTexel")
            flipY = GLES20.glGetUniformLocation(id, "uFlipY")
        }

        fun draw(target: Int, texture: Int, texMatrix: FloatArray, texelX: Float, texelY: Float, flip: Boolean) {
            GLES20.glUseProgram(id)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(target, texture)
            GLES20.glUniformMatrix4fv(matrix, 1, false, texMatrix, 0)
            if (texel >= 0) GLES20.glUniform2f(texel, texelX, texelY)
            GLES20.glUniform1f(flipY, if (flip) -1f else 1f)
            quad.position(0)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(position)
            quad.position(2)
            GLES20.glVertexAttribPointer(coord, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(coord)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { "compile: " + GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
    }

    companion object {
        private const val TAG = "MotionCoverStream"
        private val MAIN = Handler(Looper.getMainLooper())
        private val IDENTITY = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }

        private const val VERTEX_SHADER = """
attribute vec4 aPosition;
attribute vec4 aCoord;
uniform mat4 uTexMatrix;
uniform float uFlipY;
varying vec2 vCoord;
void main() {
    gl_Position = vec4(aPosition.x, aPosition.y * uFlipY, 0.0, 1.0);
    vCoord = (uTexMatrix * aCoord).xy;
}
"""

        /** One tap copies; four bilinear taps a texel off each way average a 4×4 box. */
        private fun fragmentShader(sampler: String, taps: Int): String = buildString {
            append("precision mediump float;\nvarying vec2 vCoord;\nuniform $sampler uTexture;\n")
            if (taps == 1) {
                append("void main() { gl_FragColor = texture2D(uTexture, vCoord); }\n")
            } else {
                append("uniform vec2 uTexel;\n")
                append("void main() {\n")
                append("    gl_FragColor = 0.25 * (texture2D(uTexture, vCoord + vec2(-uTexel.x, -uTexel.y))\n")
                append("        + texture2D(uTexture, vCoord + vec2(uTexel.x, -uTexel.y))\n")
                append("        + texture2D(uTexture, vCoord + vec2(-uTexel.x, uTexel.y))\n")
                append("        + texture2D(uTexture, vCoord + vec2(uTexel.x, uTexel.y)));\n")
                append("}\n")
            }
        }
    }
}
