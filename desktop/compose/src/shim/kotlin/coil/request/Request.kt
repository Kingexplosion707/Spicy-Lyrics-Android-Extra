@file:Suppress("unused", "UNUSED_PARAMETER")

package coil.request

import android.content.Context
import android.graphics.drawable.Drawable

class ImageRequest internal constructor(val data: Any?, val size: Int) {
    class Builder(context: Context) {
        private var data: Any? = null
        private var size = 0
        fun data(d: Any?) = apply { data = d }
        fun size(px: Int) = apply { size = px }
        fun size(w: Int, h: Int) = apply { size = maxOf(w, h) }
        fun size(s: coil.size.Size) = apply { size = maxOf(s.width, s.height) }
        fun allowHardware(b: Boolean) = this
        fun crossfade(b: Boolean) = this
        fun memoryCacheKey(k: String?) = this
        fun diskCacheKey(k: String?) = this
        fun bitmapConfig(c: Any?) = this
        fun build() = ImageRequest(data, size)
    }
}

sealed class ImageResult { abstract val request: ImageRequest; abstract val drawable: Drawable? }
class SuccessResult(override val drawable: Drawable, override val request: ImageRequest) : ImageResult()
class ErrorResult(override val request: ImageRequest) : ImageResult() { override val drawable: Drawable? get() = null }
