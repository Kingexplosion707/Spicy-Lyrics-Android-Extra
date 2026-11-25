@file:Suppress("unused", "UNUSED_PARAMETER")

package android.graphics

import androidx.compose.ui.graphics.asSkiaPath
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.PathOp

class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0) {
    constructor(r: Rect) : this(r.left, r.top, r.right, r.bottom)
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2
    fun centerY() = (top + bottom) / 2
    fun exactCenterX() = (left + right) / 2f
    fun exactCenterY() = (top + bottom) / 2f
    val isEmpty: Boolean get() = left >= right || top >= bottom
    fun set(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
    fun set(r: Rect) = set(r.left, r.top, r.right, r.bottom)
    fun setEmpty() = set(0, 0, 0, 0)
    fun offset(dx: Int, dy: Int) { left += dx; right += dx; top += dy; bottom += dy }
    fun inset(dx: Int, dy: Int) { left += dx; right -= dx; top += dy; bottom -= dy }
    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    fun intersect(r: Rect): Boolean { if (!Rect.intersects(this, r)) return false; set(maxOf(left, r.left), maxOf(top, r.top), minOf(right, r.right), minOf(bottom, r.bottom)); return true }
    fun toRectF() = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    override fun equals(other: Any?) = other is Rect && other.left == left && other.top == top && other.right == right && other.bottom == bottom
    override fun hashCode() = ((left * 31 + top) * 31 + right) * 31 + bottom
    override fun toString() = "Rect($left, $top - $right, $bottom)"
    companion object { @JvmStatic fun intersects(a: Rect, b: Rect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom }
}

class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    constructor(r: RectF) : this(r.left, r.top, r.right, r.bottom)
    constructor(r: Rect) : this(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
    val isEmpty: Boolean get() = left >= right || top >= bottom
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun set(r: RectF) = set(r.left, r.top, r.right, r.bottom)
    fun set(r: Rect) = set(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    fun setEmpty() = set(0f, 0f, 0f, 0f)
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun offsetTo(x: Float, y: Float) = offset(x - left, y - top)
    fun inset(dx: Float, dy: Float) { left += dx; right -= dx; top += dy; bottom -= dy }
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    fun union(r: RectF) { left = minOf(left, r.left); top = minOf(top, r.top); right = maxOf(right, r.right); bottom = maxOf(bottom, r.bottom) }
    fun round(dst: Rect) = dst.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom))
    fun roundOut(dst: Rect) = dst.set(Math.floor(left.toDouble()).toInt(), Math.floor(top.toDouble()).toInt(), Math.ceil(right.toDouble()).toInt(), Math.ceil(bottom.toDouble()).toInt())
    val skia: org.jetbrains.skia.Rect get() = org.jetbrains.skia.Rect(left, top, right, bottom)
    override fun equals(other: Any?) = other is RectF && other.left == left && other.top == top && other.right == right && other.bottom == bottom
    override fun hashCode() = ((left.hashCode() * 31 + top.hashCode()) * 31 + right.hashCode()) * 31 + bottom.hashCode()
}

class Point(@JvmField var x: Int = 0, @JvmField var y: Int = 0) { fun set(x: Int, y: Int) { this.x = x; this.y = y } }
class PointF(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f) { fun set(x: Float, y: Float) { this.x = x; this.y = y }; fun length() = Math.hypot(x.toDouble(), y.toDouble()).toFloat() }

/** Android's path over Compose's (whose Skia path draws it). */
class Path() {
    var compose: androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path(); private set
    val skia: org.jetbrains.skia.Path get() = compose.asSkiaPath()
    constructor(src: Path) : this() { compose = androidx.compose.ui.graphics.Path().apply { addPath(src.compose) } }
    enum class Direction(val compose: androidx.compose.ui.graphics.Path.Direction) { CW(androidx.compose.ui.graphics.Path.Direction.Clockwise), CCW(androidx.compose.ui.graphics.Path.Direction.CounterClockwise) }
    enum class FillType(val compose: androidx.compose.ui.graphics.PathFillType) { WINDING(androidx.compose.ui.graphics.PathFillType.NonZero), EVEN_ODD(androidx.compose.ui.graphics.PathFillType.EvenOdd), INVERSE_WINDING(androidx.compose.ui.graphics.PathFillType.NonZero), INVERSE_EVEN_ODD(androidx.compose.ui.graphics.PathFillType.EvenOdd) }
    enum class Op(val compose: androidx.compose.ui.graphics.PathOperation) { DIFFERENCE(androidx.compose.ui.graphics.PathOperation.Difference), INTERSECT(androidx.compose.ui.graphics.PathOperation.Intersect), UNION(androidx.compose.ui.graphics.PathOperation.Union), XOR(androidx.compose.ui.graphics.PathOperation.Xor), REVERSE_DIFFERENCE(androidx.compose.ui.graphics.PathOperation.ReverseDifference) }
    var fillType: FillType = FillType.WINDING; set(v) { field = v; compose.fillType = v.compose }
    val isEmpty: Boolean get() = compose.isEmpty
    fun reset() { compose.reset() }
    fun rewind() { compose.rewind() }
    fun set(src: Path) { compose = androidx.compose.ui.graphics.Path().apply { addPath(src.compose) } }
    fun moveTo(x: Float, y: Float) { compose.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { compose.lineTo(x, y) }
    fun rMoveTo(x: Float, y: Float) { compose.relativeMoveTo(x, y) }
    fun rLineTo(x: Float, y: Float) { compose.relativeLineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { compose.quadraticTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { compose.cubicTo(x1, y1, x2, y2, x3, y3) }
    fun arcTo(r: RectF, start: Float, sweep: Float, forceMoveTo: Boolean = false) { compose.arcTo(r.cr(), start, sweep, forceMoveTo) }
    fun close() { compose.close() }
    fun addRect(r: RectF, d: Direction) { compose.addRect(r.cr(), d.compose) }
    fun addRect(l: Float, t: Float, r: Float, b: Float, d: Direction) { compose.addRect(androidx.compose.ui.geometry.Rect(l, t, r, b), d.compose) }
    fun addRoundRect(r: RectF, rx: Float, ry: Float, d: Direction) { compose.addRoundRect(androidx.compose.ui.geometry.RoundRect(r.cr(), androidx.compose.ui.geometry.CornerRadius(rx, ry)), d.compose) }
    fun addRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, d: Direction) = addRoundRect(RectF(l, t, r, b), rx, ry, d)
    fun addRoundRect(r: RectF, radii: FloatArray, d: Direction) {
        fun c(i: Int) = androidx.compose.ui.geometry.CornerRadius(radii[i], radii[i + 1])
        compose.addRoundRect(androidx.compose.ui.geometry.RoundRect(r.cr(), c(0), c(2), c(4), c(6)), d.compose)
    }
    fun addOval(r: RectF, d: Direction) { compose.addOval(r.cr(), d.compose) }
    fun addCircle(x: Float, y: Float, radius: Float, d: Direction) { compose.addOval(androidx.compose.ui.geometry.Rect(x - radius, y - radius, x + radius, y + radius), d.compose) }
    fun addPath(p: Path) { compose.addPath(p.compose) }
    fun addPath(p: Path, dx: Float, dy: Float) { compose.addPath(p.compose, androidx.compose.ui.geometry.Offset(dx, dy)) }
    fun offset(dx: Float, dy: Float) { compose.translate(androidx.compose.ui.geometry.Offset(dx, dy)) }
    fun transform(m: Matrix) { compose.transform(m.compose()) }
    fun op(p: Path, op: Op): Boolean = compose.op(androidx.compose.ui.graphics.Path().apply { addPath(compose) }, p.compose, op.compose)
    fun computeBounds(out: RectF, exact: Boolean) { val b = compose.getBounds(); out.set(b.left, b.top, b.right, b.bottom) }
    private fun RectF.cr() = androidx.compose.ui.geometry.Rect(left, top, right, bottom)
}

class Matrix() {
    /** Row-major 3x3, as Android's getValues. */
    val values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    constructor(src: Matrix) : this() { System.arraycopy(src.values, 0, values, 0, 9) }
    val skia: org.jetbrains.skia.Matrix33 get() = skiaMatrix(values)
    val isIdentity: Boolean get() = values.contentEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
    fun compose(): androidx.compose.ui.graphics.Matrix = androidx.compose.ui.graphics.Matrix().also { m ->
        m[0, 0] = values[0]; m[1, 0] = values[1]; m[3, 0] = values[2]
        m[0, 1] = values[3]; m[1, 1] = values[4]; m[3, 1] = values[5]
        m[0, 3] = values[6]; m[1, 3] = values[7]; m[3, 3] = values[8]
    }
    private fun mul(a: FloatArray, b: FloatArray): FloatArray = FloatArray(9).also { r ->
        for (i in 0 until 3) for (j in 0 until 3) r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
    }
    private fun put(v: FloatArray) { System.arraycopy(v, 0, values, 0, 9) }
    private fun pre(m: FloatArray) = put(mul(values, m))
    private fun post(m: FloatArray) = put(mul(m, values))
    private fun t(dx: Float, dy: Float) = floatArrayOf(1f, 0f, dx, 0f, 1f, dy, 0f, 0f, 1f)
    private fun s(sx: Float, sy: Float) = floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f)
    private fun r(deg: Float): FloatArray { val a = Math.toRadians(deg.toDouble()); val c = Math.cos(a).toFloat(); val sn = Math.sin(a).toFloat(); return floatArrayOf(c, -sn, 0f, sn, c, 0f, 0f, 0f, 1f) }
    private fun around(m: FloatArray, px: Float, py: Float) = mul(mul(t(px, py), m), t(-px, -py))
    fun reset() = put(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
    fun set(src: Matrix) = put(src.values)
    fun setScale(sx: Float, sy: Float) = put(s(sx, sy))
    fun setScale(sx: Float, sy: Float, px: Float, py: Float) = put(around(s(sx, sy), px, py))
    fun setTranslate(dx: Float, dy: Float) = put(t(dx, dy))
    fun setRotate(deg: Float) = put(r(deg))
    fun setRotate(deg: Float, px: Float, py: Float) = put(around(r(deg), px, py))
    fun preScale(sx: Float, sy: Float): Boolean { pre(s(sx, sy)); return true }
    fun postScale(sx: Float, sy: Float): Boolean { post(s(sx, sy)); return true }
    fun postScale(sx: Float, sy: Float, px: Float, py: Float): Boolean { post(around(s(sx, sy), px, py)); return true }
    fun preTranslate(dx: Float, dy: Float): Boolean { pre(t(dx, dy)); return true }
    fun postTranslate(dx: Float, dy: Float): Boolean { post(t(dx, dy)); return true }
    fun preRotate(deg: Float): Boolean { pre(r(deg)); return true }
    fun postRotate(deg: Float): Boolean { post(r(deg)); return true }
    fun postRotate(deg: Float, px: Float, py: Float): Boolean { post(around(r(deg), px, py)); return true }
    fun preConcat(m: Matrix): Boolean { pre(m.values); return true }
    fun postConcat(m: Matrix): Boolean { post(m.values); return true }
    fun getValues(out: FloatArray) { System.arraycopy(values, 0, out, 0, 9) }
    fun setValues(v: FloatArray) = put(v)
    fun mapPoints(pts: FloatArray) {
        val m = values
        for (i in pts.indices step 2) { val x = pts[i]; val y = pts[i + 1]; val w = m[6] * x + m[7] * y + m[8]; pts[i] = (m[0] * x + m[1] * y + m[2]) / w; pts[i + 1] = (m[3] * x + m[4] * y + m[5]) / w }
    }
    fun mapRect(r: RectF): Boolean {
        val p = floatArrayOf(r.left, r.top, r.right, r.top, r.left, r.bottom, r.right, r.bottom); mapPoints(p)
        r.set(minOf(p[0], p[2], p[4], p[6]), minOf(p[1], p[3], p[5], p[7]), maxOf(p[0], p[2], p[4], p[6]), maxOf(p[1], p[3], p[5], p[7])); return true
    }
    fun invert(out: Matrix): Boolean {
        val m = values
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
        if (det == 0f) return false
        out.put(floatArrayOf(
            (m[4] * m[8] - m[5] * m[7]) / det, (m[2] * m[7] - m[1] * m[8]) / det, (m[1] * m[5] - m[2] * m[4]) / det,
            (m[5] * m[6] - m[3] * m[8]) / det, (m[0] * m[8] - m[2] * m[6]) / det, (m[2] * m[3] - m[0] * m[5]) / det,
            (m[3] * m[7] - m[4] * m[6]) / det, (m[1] * m[6] - m[0] * m[7]) / det, (m[0] * m[4] - m[1] * m[3]) / det,
        )); return true
    }
    enum class ScaleToFit { FILL, START, CENTER, END }
    fun setRectToRect(src: RectF, dst: RectF, stf: ScaleToFit): Boolean {
        val sx = dst.width() / src.width(); val sy = dst.height() / src.height()
        if (stf == ScaleToFit.FILL) { put(mul(mul(t(dst.left, dst.top), s(sx, sy)), t(-src.left, -src.top))); return true }
        val k = minOf(sx, sy)
        val dx = when (stf) { ScaleToFit.CENTER -> (dst.width() - src.width() * k) / 2; ScaleToFit.END -> dst.width() - src.width() * k; else -> 0f }
        val dy = when (stf) { ScaleToFit.CENTER -> (dst.height() - src.height() * k) / 2; ScaleToFit.END -> dst.height() - src.height() * k; else -> 0f }
        put(mul(mul(t(dst.left + dx, dst.top + dy), s(k, k)), t(-src.left, -src.top))); return true
    }
    companion object { const val MSCALE_X = 0; const val MSKEW_X = 1; const val MTRANS_X = 2; const val MSKEW_Y = 3; const val MSCALE_Y = 4; const val MTRANS_Y = 5 }
}

class Typeface private constructor(val skia: org.jetbrains.skia.Typeface?) {
    val isBold: Boolean get() = skia?.isBold == true
    val isItalic: Boolean get() = skia?.isItalic == true
    val style: Int get() = (if (isBold) BOLD else 0) or (if (isItalic) ITALIC else 0)
    val weight: Int get() = skia?.fontStyle?.weight ?: 400
    companion object {
        const val NORMAL = 0; const val BOLD = 1; const val ITALIC = 2; const val BOLD_ITALIC = 3
        private val mgr get() = org.jetbrains.skia.FontMgr.default
        @JvmField val DEFAULT = Typeface(mgr.matchFamilyStyle(null, org.jetbrains.skia.FontStyle.NORMAL))
        @JvmField val DEFAULT_BOLD = Typeface(mgr.matchFamilyStyle(null, org.jetbrains.skia.FontStyle.BOLD))
        @JvmField val SANS_SERIF = DEFAULT
        @JvmField val SERIF = Typeface(mgr.matchFamilyStyle("Georgia", org.jetbrains.skia.FontStyle.NORMAL) ?: mgr.matchFamilyStyle("serif", org.jetbrains.skia.FontStyle.NORMAL))
        @JvmField val MONOSPACE = Typeface(mgr.matchFamilyStyle("Consolas", org.jetbrains.skia.FontStyle.NORMAL) ?: mgr.matchFamilyStyle("monospace", org.jetbrains.skia.FontStyle.NORMAL))
        @JvmStatic fun create(family: String?, style: Int): Typeface = Typeface(mgr.matchFamilyStyle(family, fontStyle(style, 400)))
        @JvmStatic fun create(family: Typeface?, style: Int): Typeface = Typeface(mgr.matchFamilyStyle(family?.skia?.familyName, fontStyle(style, 400)))
        @JvmStatic fun create(family: Typeface?, weight: Int, italic: Boolean): Typeface = Typeface(mgr.matchFamilyStyle(family?.skia?.familyName, fontStyle(if (italic) ITALIC else 0, weight)))
        @JvmStatic fun createFromFile(f: java.io.File): Typeface = Typeface(mgr.makeFromFile(f.path))
        @JvmStatic fun createFromFile(path: String): Typeface = Typeface(mgr.makeFromFile(path))
        @JvmStatic fun defaultFromStyle(style: Int) = create(null as String?, style)
        private fun fontStyle(style: Int, weight: Int) = org.jetbrains.skia.FontStyle(if (style and BOLD != 0) 700 else weight, 5, if (style and ITALIC != 0) org.jetbrains.skia.FontSlant.ITALIC else org.jetbrains.skia.FontSlant.UPRIGHT)
        fun ofSkia(t: org.jetbrains.skia.Typeface?) = Typeface(t)
    }
    class Builder(private val file: java.io.File) {
        constructor(fd: java.io.FileDescriptor) : this(java.io.File(""))
        private var weight = 400
        fun setWeight(w: Int) = apply { weight = w }
        fun setItalic(i: Boolean) = this
        fun setFontVariationSettings(s: String?) = this
        fun build(): Typeface? = runCatching { createFromFile(file) }.getOrNull()
    }
}

/** Skia's Matrix33 keeps its float constructor internal; it's a value class over the 9 floats. */
private val matrixBox by lazy { org.jetbrains.skia.Matrix33::class.java.getDeclaredMethod("box-impl", FloatArray::class.java) }
internal fun skiaMatrix(values: FloatArray): org.jetbrains.skia.Matrix33 = matrixBox.invoke(null, values.copyOf()) as org.jetbrains.skia.Matrix33
