package androidx.core.graphics.drawable
fun android.graphics.drawable.Drawable.toBitmap(width: Int = intrinsicWidth, height: Int = intrinsicHeight, config: android.graphics.Bitmap.Config? = null): android.graphics.Bitmap =
    (this as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: android.graphics.Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
fun android.graphics.Bitmap.toDrawable(r: android.content.res.Resources?): android.graphics.drawable.BitmapDrawable = android.graphics.drawable.BitmapDrawable(this)
