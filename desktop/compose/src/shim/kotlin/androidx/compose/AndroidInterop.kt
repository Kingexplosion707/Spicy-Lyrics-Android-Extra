// The Android-only parts of Compose the shared code uses, on the desktop. Each file-level package
// matches the Android one so the shared files' imports find them.
@file:Suppress("unused")
@file:JvmName("AndroidGraphicsInterop")

package androidx.compose.ui.graphics

import org.jetbrains.skia.Image

/** An Android bitmap as a Compose image (Skia underneath both). */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = skia.asComposeImageBitmap()

/** A Compose image as an Android bitmap. */
fun ImageBitmap.asAndroidBitmap(): android.graphics.Bitmap =
    android.graphics.Bitmap.fromImageForInterop(Image.makeFromBitmap(asSkiaBitmap()))

fun android.graphics.RenderEffect.asComposeRenderEffect(): RenderEffect = skia.asComposeRenderEffect()

/** Android's Canvas over a Compose canvas. */
val Canvas.androidCanvas: android.graphics.Canvas get() = android.graphics.Canvas(nativeCanvas)
