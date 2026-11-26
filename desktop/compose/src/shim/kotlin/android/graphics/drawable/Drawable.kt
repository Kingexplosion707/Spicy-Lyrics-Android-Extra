@file:Suppress("unused", "UNUSED_PARAMETER")

package android.graphics.drawable

import android.graphics.Bitmap

abstract class Drawable { open val intrinsicWidth: Int get() = -1; open val intrinsicHeight: Int get() = -1 }
class BitmapDrawable(val bitmap: Bitmap) : Drawable() {
    constructor(res: android.content.res.Resources?, bitmap: Bitmap) : this(bitmap)
    override val intrinsicWidth get() = bitmap.width
    override val intrinsicHeight get() = bitmap.height
}
class Icon private constructor(val bitmap: Bitmap?) {
    companion object {
        @JvmStatic fun createWithBitmap(b: Bitmap?) = Icon(b)
        @JvmStatic fun createWithResource(c: android.content.Context?, id: Int) = Icon(null)
        @JvmStatic fun createWithAdaptiveBitmap(b: Bitmap?) = Icon(b)
    }
}
/** androidx.core.graphics.drawable.toBitmap. */
fun Drawable.toBitmap(width: Int = intrinsicWidth, height: Int = intrinsicHeight, config: Bitmap.Config? = null): Bitmap =
    (this as? BitmapDrawable)?.bitmap ?: Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
