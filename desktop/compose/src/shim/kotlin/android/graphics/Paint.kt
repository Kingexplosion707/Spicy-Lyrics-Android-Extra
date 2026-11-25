@file:Suppress("unused", "UNUSED_PARAMETER")

package android.graphics

import org.jetbrains.skia.Font
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin

open class Paint(flags: Int = 0) {
    val skia = org.jetbrains.skia.Paint().apply { isAntiAlias = flags and ANTI_ALIAS_FLAG != 0 }
    constructor(other: Paint) : this() { set(other) }

    enum class Style(val skia: PaintMode) { FILL(PaintMode.FILL), STROKE(PaintMode.STROKE), FILL_AND_STROKE(PaintMode.STROKE_AND_FILL) }
    enum class Cap(val skia: PaintStrokeCap) { BUTT(PaintStrokeCap.BUTT), ROUND(PaintStrokeCap.ROUND), SQUARE(PaintStrokeCap.SQUARE) }
    enum class Join(val skia: PaintStrokeJoin) { MITER(PaintStrokeJoin.MITER), ROUND(PaintStrokeJoin.ROUND), BEVEL(PaintStrokeJoin.BEVEL) }
    enum class Align { LEFT, CENTER, RIGHT }

    var flags: Int = flags
    var color: Int get() = skia.color; set(v) { skia.color = v }
    var alpha: Int get() = skia.alpha; set(v) { skia.alpha = v.coerceIn(0, 255) }
    var isAntiAlias: Boolean get() = skia.isAntiAlias; set(v) { skia.isAntiAlias = v }
    var isFilterBitmap: Boolean = true
    var isDither: Boolean get() = skia.isDither; set(v) { skia.isDither = v }
    var style: Style = Style.FILL; set(v) { field = v; skia.mode = v.skia }
    var strokeWidth: Float get() = skia.strokeWidth; set(v) { skia.strokeWidth = v }
    var strokeCap: Cap = Cap.BUTT; set(v) { field = v; skia.strokeCap = v.skia }
    var strokeJoin: Join = Join.MITER; set(v) { field = v; skia.strokeJoin = v.skia }
    var strokeMiter: Float get() = skia.strokeMiter; set(v) { skia.strokeMiter = v }
    var shader: Shader? = null; set(v) { field = v; skia.shader = v?.skia() }
    var colorFilter: ColorFilter? = null; set(v) { field = v; skia.colorFilter = v?.skia }
    var maskFilter: Any? = null
    var xfermode: Xfermode? = null; set(v) { field = v; skia.blendMode = (v as? PorterDuffXfermode)?.mode?.skia ?: org.jetbrains.skia.BlendMode.SRC_OVER }
    var blendMode: BlendMode? = null; set(v) { field = v; skia.blendMode = v?.skia ?: org.jetbrains.skia.BlendMode.SRC_OVER }
    var textSize: Float = 12f
    var textAlign: Align = Align.LEFT
    var typeface: Typeface? = null
    var letterSpacing: Float = 0f
    var isFakeBoldText: Boolean = false
    var textScaleX: Float = 1f

    fun set(o: Paint) {
        color = o.color; isAntiAlias = o.isAntiAlias; isFilterBitmap = o.isFilterBitmap; style = o.style; strokeWidth = o.strokeWidth
        shader = o.shader; colorFilter = o.colorFilter; textSize = o.textSize; typeface = o.typeface; blendMode = o.blendMode
    }
    fun reset() { set(Paint()) }
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, c: Int) {
        skia.imageFilter = if (radius <= 0f) null else ImageFilter.makeDropShadow(dx, dy, radius / 2f, radius / 2f, c)
    }
    fun clearShadowLayer() { skia.imageFilter = null }
    fun setBlurMask(radius: Float) { skia.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, radius / 2f) }

    internal fun font(): Font = Font(typeface?.skia, textSize)
    fun measureText(s: String): Float = font().measureTextWidth(s) + letterSpacing * textSize * s.length
    fun measureText(s: CharSequence, start: Int, end: Int): Float = measureText(s.subSequence(start, end).toString())
    fun getTextBounds(s: String, start: Int, end: Int, bounds: Rect) {
        val r = font().measureText(s.substring(start, end))
        bounds.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
    }
    val fontMetrics: FontMetrics get() = font().metrics.let { FontMetrics(it.top, it.ascent, it.descent, it.bottom, it.leading) }
    fun getFontMetrics(m: FontMetrics?): Float { val f = fontMetrics; m?.apply { top = f.top; ascent = f.ascent; descent = f.descent; bottom = f.bottom; leading = f.leading }; return f.descent - f.ascent + f.leading }
    val fontSpacing: Float get() = fontMetrics.let { it.descent - it.ascent + it.leading }
    fun ascent() = fontMetrics.ascent
    fun descent() = fontMetrics.descent

    open class FontMetrics(@JvmField var top: Float = 0f, @JvmField var ascent: Float = 0f, @JvmField var descent: Float = 0f, @JvmField var bottom: Float = 0f, @JvmField var leading: Float = 0f)

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
        const val SUBPIXEL_TEXT_FLAG = 128
        const val LINEAR_TEXT_FLAG = 64
    }
}

object Color {
    const val BLACK = 0xFF000000.toInt(); const val WHITE = 0xFFFFFFFF.toInt(); const val TRANSPARENT = 0
    const val RED = 0xFFFF0000.toInt(); const val GREEN = 0xFF00FF00.toInt(); const val BLUE = 0xFF0000FF.toInt()
    const val GRAY = 0xFF888888.toInt(); const val DKGRAY = 0xFF444444.toInt(); const val LTGRAY = 0xFFCCCCCC.toInt()
    const val YELLOW = 0xFFFFFF00.toInt(); const val CYAN = 0xFF00FFFF.toInt(); const val MAGENTA = 0xFFFF00FF.toInt()
    @JvmStatic fun alpha(c: Int) = c ushr 24
    @JvmStatic fun red(c: Int) = (c shr 16) and 0xFF
    @JvmStatic fun green(c: Int) = (c shr 8) and 0xFF
    @JvmStatic fun blue(c: Int) = c and 0xFF
    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun argb(a: Float, r: Float, g: Float, b: Float) = argb((a * 255 + .5f).toInt(), (r * 255 + .5f).toInt(), (g * 255 + .5f).toInt(), (b * 255 + .5f).toInt())
    @JvmStatic fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)
    @JvmStatic fun rgb(r: Float, g: Float, b: Float) = argb(1f, r, g, b)
    @JvmStatic fun parseColor(s: String): Int {
        val h = s.removePrefix("#")
        return when (h.length) { 6 -> (0xFF000000 or h.toLong(16)).toInt(); 8 -> h.toLong(16).toInt(); else -> throw IllegalArgumentException("Unknown color") }
    }
    @JvmStatic fun luminance(c: Int): Float {
        fun lin(v: Int): Double { val x = v / 255.0; return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return (0.2126 * lin(red(c)) + 0.7152 * lin(green(c)) + 0.0722 * lin(blue(c))).toFloat()
    }
    @JvmStatic fun colorToHSV(c: Int, hsv: FloatArray) {
        val r = red(c) / 255f; val g = green(c) / 255f; val b = blue(c) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        var h = when { d == 0f -> 0f; max == r -> 60 * (((g - b) / d) % 6); max == g -> 60 * ((b - r) / d + 2); else -> 60 * ((r - g) / d + 4) }
        if (h < 0) h += 360f
        hsv[0] = h; hsv[1] = if (max == 0f) 0f else d / max; hsv[2] = max
    }
    @JvmStatic fun RGBToHSV(r: Int, g: Int, b: Int, hsv: FloatArray) = colorToHSV(rgb(r, g, b), hsv)
    @JvmStatic fun HSVToColor(hsv: FloatArray): Int = HSVToColor(255, hsv)
    @JvmStatic fun HSVToColor(a: Int, hsv: FloatArray): Int {
        val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
        val c = v * s; val x = c * (1 - Math.abs((h / 60f) % 2 - 1)); val m = v - c
        val (r, g, b) = when { h < 60 -> Triple(c, x, 0f); h < 120 -> Triple(x, c, 0f); h < 180 -> Triple(0f, c, x); h < 240 -> Triple(0f, x, c); h < 300 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        return argb(a, ((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }
}

open class ColorFilter internal constructor(internal val skia: org.jetbrains.skia.ColorFilter?)
class ColorMatrixColorFilter(m: ColorMatrix) : ColorFilter(org.jetbrains.skia.ColorFilter.makeMatrix(org.jetbrains.skia.ColorMatrix(m.forSkia()))) {
    constructor(array: FloatArray) : this(ColorMatrix(array))
}
class PorterDuffColorFilter(color: Int, mode: PorterDuff.Mode) : ColorFilter(org.jetbrains.skia.ColorFilter.makeBlend(color, mode.skia))
class BlendModeColorFilter(color: Int, mode: BlendMode) : ColorFilter(org.jetbrains.skia.ColorFilter.makeBlend(color, mode.skia))
class LightingColorFilter(mul: Int, add: Int) : ColorFilter(org.jetbrains.skia.ColorFilter.makeLighting(mul, add))

/** Android's 4x5 colour matrix (translation in 0–255). */
class ColorMatrix() {
    val array = FloatArray(20).also { reset(it) }
    constructor(src: FloatArray) : this() { System.arraycopy(src, 0, array, 0, 20) }
    constructor(src: ColorMatrix) : this(src.array)
    private fun reset(a: FloatArray) { a.fill(0f); a[0] = 1f; a[6] = 1f; a[12] = 1f; a[18] = 1f }
    fun reset() = reset(array)
    fun set(src: FloatArray) { System.arraycopy(src, 0, array, 0, 20) }
    fun set(src: ColorMatrix) = set(src.array)
    fun setScale(r: Float, g: Float, b: Float, a: Float) { reset(); array[0] = r; array[6] = g; array[12] = b; array[18] = a }
    fun setSaturation(sat: Float) {
        reset(); val inv = 1 - sat; val R = 0.213f * inv; val G = 0.715f * inv; val B = 0.072f * inv
        array[0] = R + sat; array[1] = G; array[2] = B
        array[5] = R; array[6] = G + sat; array[7] = B
        array[10] = R; array[11] = G; array[12] = B + sat
    }
    fun setRotate(axis: Int, deg: Float) {
        reset(); val r = Math.toRadians(deg.toDouble()); val c = Math.cos(r).toFloat(); val s = Math.sin(r).toFloat()
        when (axis) { 0 -> { array[6] = c; array[7] = s; array[11] = -s; array[12] = c }; 1 -> { array[0] = c; array[2] = -s; array[10] = s; array[12] = c }; else -> { array[0] = c; array[1] = s; array[5] = -s; array[6] = c } }
    }
    fun setConcat(a: ColorMatrix, b: ColorMatrix) {
        val tmp = FloatArray(20); var i = 0
        for (j in 0 until 20 step 5) {
            for (k in 0 until 4) tmp[i++] = a.array[j] * b.array[k] + a.array[j + 1] * b.array[k + 5] + a.array[j + 2] * b.array[k + 10] + a.array[j + 3] * b.array[k + 15]
            tmp[i++] = a.array[j] * b.array[4] + a.array[j + 1] * b.array[9] + a.array[j + 2] * b.array[14] + a.array[j + 3] * b.array[19] + a.array[j + 4]
        }
        System.arraycopy(tmp, 0, array, 0, 20)
    }
    fun preConcat(pre: ColorMatrix) = setConcat(ColorMatrix(this), pre)
    fun postConcat(post: ColorMatrix) = setConcat(post, ColorMatrix(this))
    /** Skia's matrix takes translation in 0–1. */
    internal fun forSkia(): FloatArray = array.copyOf().also { for (r in 0 until 4) it[r * 5 + 4] = it[r * 5 + 4] / 255f }
}

object PorterDuff {
    enum class Mode(val skia: org.jetbrains.skia.BlendMode) {
        CLEAR(org.jetbrains.skia.BlendMode.CLEAR), SRC(org.jetbrains.skia.BlendMode.SRC), DST(org.jetbrains.skia.BlendMode.DST),
        SRC_OVER(org.jetbrains.skia.BlendMode.SRC_OVER), DST_OVER(org.jetbrains.skia.BlendMode.DST_OVER), SRC_IN(org.jetbrains.skia.BlendMode.SRC_IN),
        DST_IN(org.jetbrains.skia.BlendMode.DST_IN), SRC_OUT(org.jetbrains.skia.BlendMode.SRC_OUT), DST_OUT(org.jetbrains.skia.BlendMode.DST_OUT),
        SRC_ATOP(org.jetbrains.skia.BlendMode.SRC_ATOP), DST_ATOP(org.jetbrains.skia.BlendMode.DST_ATOP), XOR(org.jetbrains.skia.BlendMode.XOR),
        ADD(org.jetbrains.skia.BlendMode.PLUS), MULTIPLY(org.jetbrains.skia.BlendMode.MULTIPLY), SCREEN(org.jetbrains.skia.BlendMode.SCREEN),
        OVERLAY(org.jetbrains.skia.BlendMode.OVERLAY), DARKEN(org.jetbrains.skia.BlendMode.DARKEN), LIGHTEN(org.jetbrains.skia.BlendMode.LIGHTEN)
    }
}
open class Xfermode
class PorterDuffXfermode(val mode: PorterDuff.Mode) : Xfermode()
enum class BlendMode(val skia: org.jetbrains.skia.BlendMode) {
    CLEAR(org.jetbrains.skia.BlendMode.CLEAR), SRC(org.jetbrains.skia.BlendMode.SRC), DST(org.jetbrains.skia.BlendMode.DST), SRC_OVER(org.jetbrains.skia.BlendMode.SRC_OVER),
    DST_OVER(org.jetbrains.skia.BlendMode.DST_OVER), SRC_IN(org.jetbrains.skia.BlendMode.SRC_IN), DST_IN(org.jetbrains.skia.BlendMode.DST_IN), SRC_OUT(org.jetbrains.skia.BlendMode.SRC_OUT),
    DST_OUT(org.jetbrains.skia.BlendMode.DST_OUT), SRC_ATOP(org.jetbrains.skia.BlendMode.SRC_ATOP), DST_ATOP(org.jetbrains.skia.BlendMode.DST_ATOP), XOR(org.jetbrains.skia.BlendMode.XOR),
    PLUS(org.jetbrains.skia.BlendMode.PLUS), MODULATE(org.jetbrains.skia.BlendMode.MODULATE), SCREEN(org.jetbrains.skia.BlendMode.SCREEN), OVERLAY(org.jetbrains.skia.BlendMode.OVERLAY),
    DARKEN(org.jetbrains.skia.BlendMode.DARKEN), LIGHTEN(org.jetbrains.skia.BlendMode.LIGHTEN), COLOR_DODGE(org.jetbrains.skia.BlendMode.COLOR_DODGE), COLOR_BURN(org.jetbrains.skia.BlendMode.COLOR_BURN),
    HARD_LIGHT(org.jetbrains.skia.BlendMode.HARD_LIGHT), SOFT_LIGHT(org.jetbrains.skia.BlendMode.SOFT_LIGHT), DIFFERENCE(org.jetbrains.skia.BlendMode.DIFFERENCE), EXCLUSION(org.jetbrains.skia.BlendMode.EXCLUSION),
    MULTIPLY(org.jetbrains.skia.BlendMode.MULTIPLY), HUE(org.jetbrains.skia.BlendMode.HUE), SATURATION(org.jetbrains.skia.BlendMode.SATURATION), COLOR(org.jetbrains.skia.BlendMode.COLOR), LUMINOSITY(org.jetbrains.skia.BlendMode.LUMINOSITY)
}
