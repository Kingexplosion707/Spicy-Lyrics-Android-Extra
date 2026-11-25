// The small part of Coil (Android's image loader) the shared code uses, over OkHttp and Skia.
@file:Suppress("unused", "UNUSED_PARAMETER")

package coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ImageLoader internal constructor(private val client: OkHttpClient) {
    private val memory = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap>?) = size > 96 }

    suspend fun execute(request: ImageRequest): ImageResult = withContext(Dispatchers.IO) {
        val key = "${request.data}@${request.size}"
        synchronized(memory) { memory[key] }?.let { return@withContext SuccessResult(BitmapDrawable(it), request) }
        val bytes = runCatching { load(request.data) }.getOrNull() ?: return@withContext ErrorResult(request)
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext ErrorResult(request)
        val max = request.size
        if (max > 0 && maxOf(bitmap.width, bitmap.height) > max) {
            val s = max.toFloat() / maxOf(bitmap.width, bitmap.height)
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt().coerceAtLeast(1), (bitmap.height * s).toInt().coerceAtLeast(1), true)
        }
        synchronized(memory) { memory[key] = bitmap }
        SuccessResult(BitmapDrawable(bitmap), request)
    }

    private fun load(data: Any?): ByteArray? = when (data) {
        is ByteArray -> data
        is File -> data.readBytes()
        is Bitmap -> null
        null -> null
        else -> {
            val s = data.toString()
            when {
                s.startsWith("http://") || s.startsWith("https://") -> client.newCall(Request.Builder().url(s).build()).execute().use { r -> if (r.isSuccessful) r.body.bytes() else null }
                s.startsWith("file:") -> File(android.net.Uri.parse(s).path ?: "").readBytes()
                s.startsWith("data:") -> java.util.Base64.getDecoder().decode(s.substringAfter(","))
                else -> File(s).takeIf { it.exists() }?.readBytes()
            }
        }
    }

    class Builder(context: Context) {
        private var client: OkHttpClient? = null
        fun okHttpClient(c: OkHttpClient) = apply { client = c }
        fun okHttpClient(f: () -> OkHttpClient) = apply { client = f() }
        fun crossfade(b: Boolean) = this
        fun build() = ImageLoader(client ?: OkHttpClient())
    }
}

object Coil {
    @Volatile private var loader: ImageLoader? = null
    @JvmStatic fun imageLoader(context: Context): ImageLoader = loader ?: ImageLoader(OkHttpClient()).also { loader = it }
    @JvmStatic fun setImageLoader(l: ImageLoader) { loader = l }
    @JvmStatic fun setImageLoader(f: () -> ImageLoader) { loader = f() }
}

val Context.imageLoader: ImageLoader get() = Coil.imageLoader(this)
