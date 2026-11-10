package com.tx24.spicyplayer.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * The Your Photo background's picture: a copy kept in the app's storage, so it stays when the
 * original moves. Each pick gets a new file name, which is what the setting holds, so a new
 * picture shows at once.
 */
internal object BackgroundPhoto {
    private fun dir(context: Context) = File(context.applicationContext.filesDir, "background-photo")

    /** Keeps a copy of [uri]'s picture and returns its file name, or null when it isn't a picture. */
    fun save(context: Context, uri: Uri): String? = runCatching {
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, "photo-${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            file.delete()
            return null
        }
        dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
        file.name
    }.getOrNull()

    fun exists(context: Context, name: String): Boolean = name.isNotBlank() && File(dir(context), name).isFile

    /** The picture, no bigger than about [maxSide] on its long side; null when it has gone. Off the main thread. */
    fun load(context: Context, name: String, maxSide: Int): Bitmap? = runCatching {
        if (name.isBlank()) return null
        val path = File(dir(context), name).takeIf { it.isFile }?.path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun remove(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
