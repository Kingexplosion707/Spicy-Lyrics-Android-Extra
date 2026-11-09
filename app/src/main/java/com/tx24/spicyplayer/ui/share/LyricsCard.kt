package com.tx24.spicyplayer.ui.share

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.tx24.spicyplayer.lyrics.spicy.canvas.StackBlur
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A card to share a few lines of a song as a picture, 4:5 like a feed post: the cover, blurred
 * and darkened, fills it; the cover, title and artist sit at the top; the lines, in large bold
 * type sized to fit, under them; the app's name small at the bottom. Drawn with the platform's
 * canvas, so it can be made off the main thread.
 */
internal object LyricsCard {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    /** More lines than this would only be small print. */
    const val MAX_LINES = 8

    /** The card at [scale] times its full size (a preview is smaller). */
    fun render(lines: List<String>, title: String, artist: String, cover: Bitmap?, scale: Float = 1f): Bitmap {
        val w = (WIDTH * scale).roundToInt().coerceAtLeast(1)
        val h = (HEIGHT * scale).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawBackground(canvas, cover, w, h)

        val pad = PAD * scale
        val thumb = THUMB * scale
        // The song: its cover, then its title over its artist beside it.
        if (cover != null) drawCover(canvas, cover, RectF(pad, pad, pad + thumb, pad + thumb), THUMB_RADIUS * scale)
        val textLeft = if (cover != null) pad + thumb + HEADER_GAP * scale else pad
        val textWidth = (w - pad - textLeft).coerceAtLeast(1f)
        val titlePaint = textPaint(TITLE_SIZE * scale, bold = true, alpha = 0xFF)
        val artistPaint = textPaint(ARTIST_SIZE * scale, bold = false, alpha = 0xB8)
        val titleText = TextUtils.ellipsize(title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString()
        val artistText = TextUtils.ellipsize(artist, artistPaint, textWidth, TextUtils.TruncateAt.END).toString()
        val headerMiddle = pad + (if (cover != null) thumb else (TITLE_SIZE + ARTIST_SIZE) * scale * 1.3f) / 2f
        val titleBase = headerMiddle - 6f * scale
        canvas.drawText(titleText, textLeft, titleBase, titlePaint)
        canvas.drawText(artistText, textLeft, titleBase + ARTIST_SIZE * scale * 1.35f, artistPaint)

        // The lines: as large as fits between the song and the footer.
        val top = pad + (if (cover != null) thumb else (TITLE_SIZE + ARTIST_SIZE) * scale * 1.3f) + LYRICS_GAP * scale
        val bottom = h - pad - FOOTER_SIZE * scale * 2f
        val room = (bottom - top).coerceAtLeast(1f)
        val width = (w - 2 * pad).roundToInt().coerceAtLeast(1)
        val text = lines.joinToString("\n")
        var size = LYRICS_MAX_SIZE * scale
        var layout = lyricsLayout(text, size, width)
        while (layout.height > room && size > LYRICS_MIN_SIZE * scale) {
            size -= 3f * scale
            layout = lyricsLayout(text, size, width)
        }
        canvas.save()
        canvas.translate(pad, top)
        canvas.clipRect(0f, 0f, width.toFloat(), room)
        layout.draw(canvas)
        canvas.restore()

        val footerPaint = textPaint(FOOTER_SIZE * scale, bold = true, alpha = 0x8C)
        canvas.drawText(APP_NAME, pad, h - pad, footerPaint)
        return out
    }

    /** The cover filling the card, blurred and darkened toward the bottom; a dark gradient with none. */
    private fun drawBackground(canvas: Canvas, cover: Bitmap?, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val blurred = cover?.let { art ->
            runCatching {
                val small = Bitmap.createScaledBitmap(art, BLUR_WIDTH, max(1, BLUR_WIDTH * art.height / art.width), true)
                StackBlur.blur(small, BLUR_RADIUS).also { if (small !== art) small.recycle() }
            }.getOrNull()
        }
        if (blurred != null) {
            // Cropped to fill, like centre-crop.
            val scale = max(w.toFloat() / blurred.width, h.toFloat() / blurred.height)
            val dx = (w - blurred.width * scale) / 2f
            val dy = (h - blurred.height * scale) / 2f
            val matrix = Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) }
            canvas.drawBitmap(blurred, matrix, paint)
            blurred.recycle()
            canvas.drawColor(0x59000000)
        } else {
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF3B2B55.toInt(), 0xFF120D1A.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x14000000, 0x8C000000.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
    }

    private fun drawCover(canvas: Canvas, cover: Bitmap, rect: RectF, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(rect.width() / cover.width, rect.height() / cover.height)
        shader.setLocalMatrix(Matrix().apply {
            setScale(scale, scale)
            postTranslate(rect.left + (rect.width() - cover.width * scale) / 2f, rect.top + (rect.height() - cover.height * scale) / 2f)
        })
        paint.shader = shader
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun lyricsLayout(text: String, size: Float, width: Int): StaticLayout {
        val paint = textPaint(size, bold = true, alpha = 0xFF, heavy = true)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .setIncludePad(false)
            .build()
    }

    private fun textPaint(size: Float, bold: Boolean, alpha: Int, heavy: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = (alpha shl 24) or 0xFFFFFF
        typeface = when {
            heavy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Typeface.create(Typeface.SANS_SERIF, 800, false)
            bold -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }

    private const val APP_NAME = "Spicy Lyrics Plus"
    private const val PAD = 88f
    private const val THUMB = 136f
    private const val THUMB_RADIUS = 20f
    private const val HEADER_GAP = 32f
    private const val TITLE_SIZE = 42f
    private const val ARTIST_SIZE = 34f
    private const val LYRICS_GAP = 84f
    private const val LYRICS_MAX_SIZE = 78f
    private const val LYRICS_MIN_SIZE = 38f
    private const val LINE_SPACING = 1.14f
    private const val FOOTER_SIZE = 28f
    private const val BLUR_WIDTH = 72
    private const val BLUR_RADIUS = 7
}
