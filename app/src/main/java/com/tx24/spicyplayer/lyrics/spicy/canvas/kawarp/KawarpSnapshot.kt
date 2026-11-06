package com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * One still frame of the Kawarp background, for a place that can't run it live (the home screen
 * widget). On Android 13+ it is the background's own shader, drawn once offscreen at its
 * [CANVAS_WIDTH]x[CANVAS_HEIGHT], to be stretched over the view like the live one. Earlier, the
 * blurred cover the shader warps, graded like its output, without the warp.
 */
internal object KawarpSnapshot {
    /** How far into the warp's motion the frame is taken: past the first seconds, which barely move. */
    private const val TIME_SECONDS = 40f
    /** The cover is shrunk to this before blurring; the blur works at 128px anyway. */
    private const val SOURCE_PX = 256

    fun render(cover: Bitmap): Bitmap {
        val source = Bitmap.createScaledBitmap(cover, SOURCE_PX, SOURCE_PX, true)
        val (floats, w, h) = bitmapToFloats(source)
        val blurred = KawarpBlurCore.process(
            floats, w, h,
            blurPasses = SPICY_OPTIONS.blurPasses,
            tintColor = SPICY_OPTIONS.tintColor,
            tintIntensity = SPICY_OPTIONS.tintIntensity,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { return warped(blurred) }
        }
        return graded(blurred)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun warped(blurred: FloatArray): Bitmap {
        val album = BitmapShader(floatsToF16Bitmap(blurred, KawarpBlurCore.BLUR_SIZE), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
        val shader = RuntimeShader(KAWARP_FUSED_AGSL).apply {
            setFloatUniform("uResolution", CANVAS_WIDTH.toFloat(), CANVAS_HEIGHT.toFloat())
            setFloatUniform("uTime", TIME_SECONDS)
            setFloatUniform("uBlend", 0f)
            setFloatUniform("uIntensity", SPICY_OPTIONS.warpIntensity)
            setFloatUniform("uSaturation", SPICY_OPTIONS.saturation)
            setFloatUniform("uDithering", SPICY_OPTIONS.dithering)
            setFloatUniform("uScale", SPICY_OPTIONS.scale)
            setInputShader("texCur", album)
            setInputShader("texNext", album)
        }
        // A runtime shader needs the GPU, so the frame is drawn into a hardware buffer and copied out.
        val reader = ImageReader.newInstance(
            CANVAS_WIDTH, CANVAS_HEIGHT, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val node = RenderNode("kawarp-snapshot").apply { setPosition(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT) }
        val renderer = HardwareRenderer()
        try {
            node.beginRecording().drawPaint(Paint().apply { this.shader = shader })
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage().use { image ->
                image.hardwareBuffer!!.use { buffer ->
                    return Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))!!
                        .copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } finally {
            renderer.destroy()
            node.discardDisplayList()
            reader.close()
        }
    }

    /** The blurred cover with the output's grading (`saturate(2.5) brightness(0.65)`), at 128px. */
    private fun graded(blurred: FloatArray): Bitmap {
        val n = KawarpBlurCore.BLUR_SIZE
        val pixels = IntArray(n * n) { i ->
            fun channel(c: Int) = (blurred[i * 4 + c].coerceIn(0f, 1f) * 255f).toInt()
            (255 shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
        }
        val album = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        val grade = ColorMatrix().apply {
            setSaturation(SPICY_OPTIONS.saturation * 2.5f)
            postConcat(ColorMatrix().apply { setScale(0.65f, 0.65f, 0.65f, 1f) })
        }
        return Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(album, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(grade) })
        }
    }
}
