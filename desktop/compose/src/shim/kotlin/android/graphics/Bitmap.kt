@file:Suppress("unused", "UNUSED_PARAMETER")

package android.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** An Android bitmap over a Skia bitmap (Android's own graphics are Skia underneath). */
class Bitmap internal constructor(val skia: org.jetbrains.skia.Bitmap, val config: Config = Config.ARGB_8888) {
    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102 }
    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    val width: Int get() = skia.width
    val height: Int get() = skia.height
    var density: Int = 160
    val generationId: Int get() = skia.generationId
    private val bpp: Int get() = if (config == Config.RGBA_F16) 8 else 4
    val byteCount: Int get() = width * height * bpp
    val allocationByteCount: Int get() = byteCount
    val rowBytes: Int get() = width * bpp
    var isRecycled: Boolean = false; private set
    val isMutable: Boolean get() = !skia.isImmutable
    var isPremultiplied: Boolean = true
    val colorSpace: ColorSpace? get() = ColorSpace.get(ColorSpace.Named.SRGB)
    fun hasAlpha() = true
    fun hasMipMap() = false
    fun prepareToDraw() {}
    fun recycle() { if (!isRecycled) { isRecycled = true } }
    fun eraseColor(c: Int) { skia.erase(c) }
    fun copy(config: Config, mutable: Boolean): Bitmap = Bitmap(skia.makeClone(), config)
    fun asShared(): Bitmap = this
    fun sameAs(other: Bitmap?): Boolean =
        other != null && other.width == width && other.height == height && pixels().contentEquals(other.pixels())

    fun getPixel(x: Int, y: Int): Int = skia.getColor(x, y)
    fun setPixel(x: Int, y: Int, c: Int) { val p = pixels(); p[y * width + x] = c; writePixels(p) }

    /** ARGB ints, as Android's getPixels. */
    fun getPixels(out: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        val all = pixels()
        for (row in 0 until h) System.arraycopy(all, (y + row) * width + x, out, offset + row * stride, w)
    }
    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        val all = pixels()
        for (row in 0 until h) System.arraycopy(src, offset + row * stride, all, (y + row) * width + x, w)
        writePixels(all)
    }
    fun copyPixelsToBuffer(buf: java.nio.Buffer) {
        val bytes = skia.readPixels() ?: return
        when (buf) { is java.nio.ByteBuffer -> buf.put(bytes); is java.nio.IntBuffer -> buf.put(java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer()) ; else -> {} }
    }
    fun copyPixelsFromBuffer(buf: java.nio.Buffer) {
        val bytes = ByteArray(byteCount)
        when (buf) {
            is java.nio.ByteBuffer -> { buf.rewind(); buf.get(bytes) }
            is java.nio.IntBuffer -> { val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN); buf.rewind(); while (buf.hasRemaining()) bb.putInt(buf.get()) }
            else -> {}
        }
        if (config == Config.RGBA_F16) { skia.installPixels(skia.imageInfo, bytes, rowBytes); skia.notifyPixelsChanged(); return }
        // Android's ARGB_8888 buffers are RGBA bytes; Skia's N32 here is BGRA.
        for (i in bytes.indices step 4) { val r = bytes[i]; bytes[i] = bytes[i + 2]; bytes[i + 2] = r }
        skia.installPixels(skia.imageInfo, bytes, rowBytes); skia.notifyPixelsChanged()
    }

    fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean {
        val fmt = when (format) { CompressFormat.PNG -> EncodedImageFormat.PNG; CompressFormat.JPEG -> EncodedImageFormat.JPEG; else -> EncodedImageFormat.WEBP }
        val data = image().encodeToData(fmt, quality) ?: return false
        out.write(data.bytes); return true
    }

    /** BGRA bytes in Skia's N32 order, as ARGB ints. */
    internal fun pixels(): IntArray {
        val bytes = skia.readPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), width * 4, 0, 0) ?: return IntArray(width * height)
        val ints = IntArray(width * height)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints)
        return ints
    }
    internal fun writePixels(ints: IntArray) {
        // Exactly width × height ARGB ints, whatever was passed (Android would throw on too few).
        val count = width * height
        val bb = java.nio.ByteBuffer.allocate(count * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.asIntBuffer().put(ints, 0, minOf(ints.size, count))
        val src = runCatching {
            Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), bb.array(), width * 4)
        }.getOrNull() ?: return
        org.jetbrains.skia.Canvas(skia).apply { clear(0); drawImage(src, 0f, 0f); close() }
        src.close()
        skia.notifyPixelsChanged()
    }

    /** The pixels as an image; a copy when Skia won't share them, and transparent when there are none. */
    internal fun image(): Image = runCatching { Image.makeFromBitmap(skia) }.getOrNull()
        ?: skia.readPixels()?.let { runCatching { Image.makeRaster(skia.imageInfo, it, skia.rowBytes) }.getOrNull() }
        ?: Image.makeFromBitmap(org.jetbrains.skia.Bitmap().apply { allocN32Pixels(1, 1); erase(0) })

    companion object {
        @JvmStatic fun createBitmap(w: Int, h: Int, config: Config): Bitmap = Bitmap(org.jetbrains.skia.Bitmap().apply {
            val ww = w.coerceAtLeast(1); val hh = h.coerceAtLeast(1)
            allocPixels(if (config == Config.RGBA_F16) ImageInfo(ww, hh, ColorType.RGBA_F16, ColorAlphaType.PREMUL) else ImageInfo.makeN32(ww, hh, ColorAlphaType.PREMUL))
            erase(0)
        }, config)
        @JvmStatic fun createBitmap(w: Int, h: Int, config: Config, hasAlpha: Boolean): Bitmap = createBitmap(w, h, config)
        @JvmStatic fun createBitmap(w: Int, h: Int, config: Config, hasAlpha: Boolean, cs: ColorSpace?): Bitmap = createBitmap(w, h, config)
        @JvmStatic fun createBitmap(colors: IntArray, w: Int, h: Int, config: Config): Bitmap = createBitmap(w, h, config).also { it.writePixels(colors) }
        @JvmStatic fun createBitmap(colors: IntArray, offset: Int, stride: Int, w: Int, h: Int, config: Config): Bitmap =
            createBitmap(w, h, config).also { b -> val px = IntArray(w * h); for (r in 0 until h) System.arraycopy(colors, offset + r * stride, px, r * w, w); b.writePixels(px) }
        @JvmStatic fun createBitmap(src: Bitmap): Bitmap = src.copy(src.config, true)
        @JvmStatic fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap = createBitmap(w, h, src.config).also { out ->
            org.jetbrains.skia.Canvas(out.skia).apply { drawImage(src.image(), -x.toFloat(), -y.toFloat()); close() }
        }
        @JvmStatic fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int, m: Matrix?, filter: Boolean): Bitmap {
            if (m == null || m.isIdentity) return createBitmap(src, x, y, w, h)
            val r = RectF(0f, 0f, w.toFloat(), h.toFloat()); m.mapRect(r)
            val out = createBitmap(r.width().toInt().coerceAtLeast(1), r.height().toInt().coerceAtLeast(1), src.config)
            org.jetbrains.skia.Canvas(out.skia).apply {
                translate(-r.left, -r.top); concat(m.skia); drawImageRect(src.image(), org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()), org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat())); close()
            }
            return out
        }
        @JvmStatic fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap {
            val out = createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), src.config)
            val sampling = if (filter) SamplingMode.CATMULL_ROM else SamplingMode.DEFAULT
            org.jetbrains.skia.Canvas(out.skia).apply {
                drawImageRect(src.image(), org.jetbrains.skia.Rect.makeWH(src.width.toFloat(), src.height.toFloat()), org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), sampling, null, true); close()
            }
            return out
        }
        fun fromImageForInterop(img: Image): Bitmap = fromImage(img)
        internal fun fromImage(img: Image): Bitmap {
            val b = createBitmap(img.width, img.height, Config.ARGB_8888)
            org.jetbrains.skia.Canvas(b.skia).apply { drawImage(img, 0f, 0f); close() }
            return b
        }
    }
}

/** androidx.core.graphics.scale. */
fun Bitmap.scale(width: Int, height: Int, filter: Boolean = true): Bitmap = Bitmap.createScaledBitmap(this, width, height, filter)
/** androidx.core.graphics.createBitmap. */
fun createBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap = Bitmap.createBitmap(width, height, config)
fun Bitmap.applyCanvas(block: Canvas.() -> Unit): Bitmap { Canvas(this).block(); return this }
operator fun Bitmap.get(x: Int, y: Int): Int = getPixel(x, y)

object BitmapFactory {
    class Options {
        @JvmField var inJustDecodeBounds = false
        @JvmField var inSampleSize = 1
        @JvmField var inPreferredConfig: Bitmap.Config? = Bitmap.Config.ARGB_8888
        @JvmField var inMutable = false
        @JvmField var inScaled = true
        @JvmField var inDensity = 0
        @JvmField var inTargetDensity = 0
        @JvmField var outWidth = -1
        @JvmField var outHeight = -1
        @JvmField var outMimeType: String? = null
        @JvmField var inPreferredColorSpace: ColorSpace? = null
    }
    @JvmStatic fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = decodeByteArray(data, offset, length, null)
    @JvmStatic fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? = decode(data.copyOfRange(offset, offset + length), opts)
    @JvmStatic fun decodeStream(s: InputStream?): Bitmap? = s?.use { decode(it.readBytes(), null) }
    @JvmStatic fun decodeStream(s: InputStream?, pad: Rect?, opts: Options?): Bitmap? = s?.use { decode(it.readBytes(), opts) }
    @JvmStatic fun decodeFile(path: String?): Bitmap? = decodeFile(path, null)
    @JvmStatic fun decodeFile(path: String?, opts: Options?): Bitmap? = path?.let { runCatching { decode(File(it).readBytes(), opts) }.getOrNull() }
    @JvmStatic fun decodeResource(res: android.content.res.Resources?, id: Int): Bitmap? = decodeResource(res, id, null)
    @JvmStatic fun decodeResource(res: android.content.res.Resources?, id: Int, opts: Options?): Bitmap? =
        runCatching { decode(com.tx24.spicyplayer.desktop.platform.AndroidResources.open(id).readBytes(), opts) }.getOrNull()

    private fun decode(bytes: ByteArray, opts: Options?): Bitmap? {
        val img = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        opts?.outWidth = img.width; opts?.outHeight = img.height
        if (opts?.inJustDecodeBounds == true) return null
        val sample = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
        val full = Bitmap.fromImage(img)
        return if (sample > 1) Bitmap.createScaledBitmap(full, img.width / sample, img.height / sample, true) else full
    }
}

class ColorSpace private constructor(val name: String) {
    enum class Named { SRGB, LINEAR_SRGB, DISPLAY_P3, EXTENDED_SRGB, LINEAR_EXTENDED_SRGB, BT2020, DCI_P3, ADOBE_RGB }
    val isWideGamut: Boolean get() = false
    companion object { @JvmStatic fun get(n: Named) = ColorSpace(n.name) }
}
object PixelFormat { const val TRANSLUCENT = -3; const val TRANSPARENT = -2; const val OPAQUE = -1; const val RGBA_8888 = 1 }

