@file:Suppress("unused")
package androidx.core.graphics

import android.graphics.Bitmap
import android.graphics.Canvas

fun Bitmap.scale(width: Int, height: Int, filter: Boolean = true): Bitmap = Bitmap.createScaledBitmap(this, width, height, filter)
fun createBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap = Bitmap.createBitmap(width, height, config)
inline fun Bitmap.applyCanvas(block: Canvas.() -> Unit): Bitmap { Canvas(this).block(); return this }
operator fun Bitmap.get(x: Int, y: Int): Int = getPixel(x, y)
fun Int.toColorInt(): Int = this
fun String.toColorInt(): Int = android.graphics.Color.parseColor(this)
object ColorUtils {
    @JvmStatic fun calculateLuminance(c: Int): Double = android.graphics.Color.luminance(c).toDouble()
    @JvmStatic fun blendARGB(a: Int, b: Int, ratio: Float): Int {
        val inv = 1 - ratio
        fun ch(s: Int) = (((a shr s) and 0xff) * inv + ((b shr s) and 0xff) * ratio).toInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    @JvmStatic fun setAlphaComponent(c: Int, a: Int): Int = (c and 0x00ffffff) or (a shl 24)
    @JvmStatic fun colorToHSL(c: Int, out: FloatArray) {
        val r = ((c shr 16) and 0xff) / 255f; val g = ((c shr 8) and 0xff) / 255f; val b = (c and 0xff) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val l = (max + min) / 2; val d = max - min
        var h = 0f; var s = 0f
        if (d != 0f) { s = d / (1 - Math.abs(2 * l - 1)); h = when (max) { r -> ((g - b) / d) % 6; g -> (b - r) / d + 2; else -> (r - g) / d + 4 } * 60; if (h < 0) h += 360 }
        out[0] = h; out[1] = s; out[2] = l
    }
    @JvmStatic fun HSLToColor(hsl: FloatArray): Int {
        val (h, s, l) = Triple(hsl[0], hsl[1], hsl[2])
        val c = (1 - Math.abs(2 * l - 1)) * s; val x = c * (1 - Math.abs((h / 60) % 2 - 1)); val m = l - c / 2
        val (r, g, b) = when { h < 60 -> Triple(c, x, 0f); h < 120 -> Triple(x, c, 0f); h < 180 -> Triple(0f, c, x); h < 240 -> Triple(0f, x, c); h < 300 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        return android.graphics.Color.rgb(((r + m) * 255).toInt().coerceIn(0, 255), ((g + m) * 255).toInt().coerceIn(0, 255), ((b + m) * 255).toInt().coerceIn(0, 255))
    }
}
