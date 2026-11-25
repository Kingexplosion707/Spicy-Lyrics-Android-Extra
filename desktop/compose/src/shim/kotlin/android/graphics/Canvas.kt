@file:Suppress("unused", "UNUSED_PARAMETER")

package android.graphics

import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.SamplingMode

/** An Android canvas drawing into a Skia canvas. */
class Canvas(val skia: org.jetbrains.skia.Canvas, private val target: Bitmap? = null) {
    constructor(bitmap: Bitmap) : this(org.jetbrains.skia.Canvas(bitmap.skia), bitmap)
    constructor() : this(org.jetbrains.skia.Canvas(org.jetbrains.skia.Bitmap().apply { allocN32Pixels(1, 1) }))

    val width: Int get() = target?.width ?: 0
    val height: Int get() = target?.height ?: 0
    var density: Int = 160
    val isHardwareAccelerated: Boolean get() = false
    val saveCount: Int get() = skia.saveCount

    fun save(): Int = skia.save()
    fun saveLayer(bounds: RectF?, paint: Paint?): Int = skia.saveLayer(bounds?.skia, paint?.skia)
    fun saveLayer(l: Float, t: Float, r: Float, b: Float, paint: Paint?): Int = skia.saveLayer(l, t, r, b, paint?.skia)
    fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int = skia.saveLayer(bounds?.skia, org.jetbrains.skia.Paint().apply { this.alpha = alpha })
    fun restore() = skia.restore()
    fun restoreToCount(n: Int) = skia.restoreToCount(n)
    fun translate(dx: Float, dy: Float) { skia.translate(dx, dy) }
    fun scale(sx: Float, sy: Float) { skia.scale(sx, sy) }
    fun scale(sx: Float, sy: Float, px: Float, py: Float) { skia.translate(px, py); skia.scale(sx, sy); skia.translate(-px, -py) }
    fun rotate(deg: Float) { skia.rotate(deg) }
    fun rotate(deg: Float, px: Float, py: Float) { skia.rotate(deg, px, py) }
    fun skew(sx: Float, sy: Float) { skia.skew(sx, sy) }
    fun concat(m: Matrix?) { if (m != null) skia.concat(m.skia) }
    fun setMatrix(m: Matrix?) { skia.resetMatrix(); if (m != null) skia.concat(m.skia) }

    fun clipRect(r: RectF): Boolean { skia.clipRect(r.skia); return true }
    fun clipRect(r: Rect): Boolean { skia.clipRect(r.toRectF().skia); return true }
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean { skia.clipRect(org.jetbrains.skia.Rect(l, t, r, b)); return true }
    fun clipRect(l: Int, t: Int, r: Int, b: Int): Boolean = clipRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
    fun clipPath(p: Path): Boolean { skia.clipPath(p.skia, ClipMode.INTERSECT, true); return true }
    fun clipOutPath(p: Path): Boolean { skia.clipPath(p.skia, ClipMode.DIFFERENCE, true); return true }
    fun clipOutRect(r: RectF): Boolean { skia.clipRect(r.skia, ClipMode.DIFFERENCE); return true }

    fun drawColor(c: Int) { skia.drawPaint(org.jetbrains.skia.Paint().apply { color = c; blendMode = org.jetbrains.skia.BlendMode.SRC_OVER }) }
    fun drawColor(c: Int, mode: PorterDuff.Mode) { skia.drawPaint(org.jetbrains.skia.Paint().apply { color = c; blendMode = mode.skia }) }
    fun drawColor(c: Int, mode: BlendMode) { skia.drawPaint(org.jetbrains.skia.Paint().apply { color = c; blendMode = mode.skia }) }
    fun drawARGB(a: Int, r: Int, g: Int, b: Int) = drawColor(Color.argb(a, r, g, b))
    fun drawRGB(r: Int, g: Int, b: Int) = drawColor(Color.rgb(r, g, b))
    fun drawPaint(p: Paint) = skia.drawPaint(p.skia)
    fun drawRect(r: RectF, p: Paint) { skia.drawRect(r.skia, p.skia) }
    fun drawRect(r: Rect, p: Paint) { skia.drawRect(r.toRectF().skia, p.skia) }
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) { skia.drawRect(org.jetbrains.skia.Rect(l, t, r, b), p.skia) }
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) { skia.drawRRect(RRect.makeLTRB(r.left, r.top, r.right, r.bottom, rx, ry), p.skia) }
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) { skia.drawRRect(RRect.makeLTRB(l, t, r, b, rx, ry), p.skia) }
    fun drawOval(r: RectF, p: Paint) { skia.drawOval(r.skia, p.skia) }
    fun drawCircle(cx: Float, cy: Float, radius: Float, p: Paint) { skia.drawCircle(cx, cy, radius, p.skia) }
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { skia.drawLine(x0, y0, x1, y1, p.skia) }
    fun drawArc(r: RectF, start: Float, sweep: Float, useCenter: Boolean, p: Paint) { skia.drawArc(r.left, r.top, r.right, r.bottom, start, sweep, useCenter, p.skia) }
    fun drawPath(path: Path, p: Paint) { skia.drawPath(path.skia, p.skia) }
    fun drawPoint(x: Float, y: Float, p: Paint) { skia.drawPoint(x, y, p.skia) }

    fun drawBitmap(b: Bitmap, left: Float, top: Float, p: Paint?) { skia.drawImage(b.image(), left, top, p?.skia) }
    fun drawBitmap(b: Bitmap, src: Rect?, dst: RectF, p: Paint?) {
        val s = src?.toRectF()?.skia ?: org.jetbrains.skia.Rect.makeWH(b.width.toFloat(), b.height.toFloat())
        skia.drawImageRect(b.image(), s, dst.skia, sampling(p), p?.skia, true)
    }
    fun drawBitmap(b: Bitmap, src: Rect?, dst: Rect, p: Paint?) = drawBitmap(b, src, dst.toRectF(), p)
    fun drawBitmap(b: Bitmap, m: Matrix, p: Paint?) { skia.save(); skia.concat(m.skia); skia.drawImage(b.image(), 0f, 0f, p?.skia); skia.restore() }
    private fun sampling(p: Paint?) = if (p?.isFilterBitmap != false) SamplingMode.LINEAR else SamplingMode.DEFAULT

    fun drawText(text: String, x: Float, y: Float, p: Paint) { skia.drawString(text, x, y, p.font(), p.skia) }
    fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, p: Paint) = drawText(text.subSequence(start, end).toString(), x, y, p)
    fun drawRenderNode(node: Any?) {}
}
