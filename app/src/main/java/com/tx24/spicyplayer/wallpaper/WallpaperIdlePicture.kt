package com.tx24.spicyplayer.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * The picture the live wallpaper shows while nothing plays, picked in Settings → Device → Live
 * wallpaper: a copy of it kept in the app's files, so it stays when the original moves. Its
 * stamp in the `ui` settings changes with each pick, which tells the wallpaper to load it again.
 */
internal object WallpaperIdlePicture {
    /** When the picture was last picked (its version), or 0 for none. */
    const val PREF_STAMP = "wallpaperIdlePicture"
    private const val FILE_NAME = "wallpaper_idle_picture"

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun isSet(context: Context): Boolean =
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).getLong(PREF_STAMP, 0L) != 0L && file(context).isFile

    /** Keeps a copy of [uri]'s picture; false when it couldn't be read. */
    fun save(context: Context, uri: Uri): Boolean {
        val target = file(context)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val temp = File(target.parentFile, "$FILE_NAME.tmp")
                temp.outputStream().use { input.copyTo(it) }
                // A file that isn't a picture is not kept.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temp.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) { temp.delete(); return@use false }
                temp.renameTo(target)
            } ?: false
        }.getOrDefault(false)
        if (copied) {
            context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
                .putLong(PREF_STAMP, System.currentTimeMillis()).apply()
        }
        return copied
    }

    fun remove(context: Context) {
        file(context).delete()
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().remove(PREF_STAMP).apply()
    }

    /** The picture scaled down to about [width]×[height] (it is cropped to fill), or null with none. Off the main thread. */
    fun load(context: Context, width: Int, height: Int): Bitmap? = runCatching {
        val path = file(context).takeIf { it.isFile }?.path ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
