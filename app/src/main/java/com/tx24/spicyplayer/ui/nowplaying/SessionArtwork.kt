package com.tx24.spicyplayer.ui.nowplaying

import com.tx24.spicyplayer.ui.settings.LocalAppIcon
import com.tx24.spicyplayer.ui.settings.AppIcon
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import com.tx24.spicyplayer.R
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Cover art published by the remote session, as a software bitmap no larger than the size asked
 * for, plus a small fingerprint of its pixels. Players often republish the same cover as a new
 * Bitmap on every metadata update, so the fingerprint (not the object) says whether it changed.
 */
class SessionArtwork private constructor(val bitmap: Bitmap, private val signature: IntArray) {
    val fingerprint: Int = signature.contentHashCode()

    /**
     * Whether [other] is the same picture, maybe at another size or encoding. Spotify sends a
     * song's cover as a URI first and as a bitmap a moment later, sometimes twice at different
     * sizes, and those never match pixel for pixel.
     */
    fun sameCover(other: SessionArtwork): Boolean {
        if (other.fingerprint == fingerprint) return true
        var diff = 0
        for (i in signature.indices) diff += abs(signature[i] - other.signature[i])
        return diff <= SAME_COVER_MAX_MEAN_DIFF * signature.size
    }

    companion object {
        fun of(bitmap: Bitmap) = SessionArtwork(bitmap, signature(bitmap))
    }
}

/** Mean channel difference (0..255) up to which two covers count as the same picture. */
private const val SAME_COVER_MAX_MEAN_DIFF = 8

/**
 * Loads the session's cover: its bitmap when it sent one, else its artwork URI through Coil.
 * When there is none, the app logo stands in, but only after [FALLBACK_GRACE_MS]: players often
 * publish a new song's metadata a moment before its cover, and the logo flashing up in between
 * would be noise. Without a session ([hasSession] false) no cover is coming, so it shows at once.
 */
@Composable
fun rememberSessionArtwork(
    artwork: Bitmap?,
    artworkUri: String?,
    maxDimension: Int,
    hasSession: Boolean = true,
): SessionArtwork? {
    val context = LocalContext.current
    val icon = LocalAppIcon.current
    var loaded by remember { mutableStateOf<SessionArtwork?>(null) }
    LaunchedEffect(artwork, artworkUri, maxDimension, hasSession, icon) {
        val cover = loadSessionArtwork(context, artwork, artworkUri, maxDimension)
        if (cover != null) {
            // Keep the one shown when this is the same cover again, or the header slides it in twice.
            if (loaded?.sameCover(cover) != true) loaded = cover
            return@LaunchedEffect
        }
        if (hasSession) delay(FALLBACK_GRACE_MS)
        loaded = fallbackArtwork(context, icon)
    }
    return loaded
}

private const val FALLBACK_GRACE_MS = 3_000L

private val fallbacks = java.util.concurrent.ConcurrentHashMap<AppIcon, SessionArtwork>()

/** The app logo the user picked, on its gradient, cropped to the launcher icon's visible square. */
private suspend fun fallbackArtwork(context: Context, icon: AppIcon): SessionArtwork =
    fallbacks[icon] ?: withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, icon.cover, options)
        SessionArtwork.of(bitmap).also { fallbacks[icon] = it }
    }

suspend fun loadSessionArtwork(
    context: Context,
    artwork: Bitmap?,
    artworkUri: String?,
    maxDimension: Int,
): SessionArtwork? {
    val source = artwork ?: artworkUri?.let { uri ->
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(Size(maxDimension, maxDimension))
                .allowHardware(false)
                .build()
            ((Coil.imageLoader(context).execute(request) as? SuccessResult)
                ?.drawable as? BitmapDrawable)?.bitmap
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    } ?: return null
    return withContext(Dispatchers.Default) {
        val software = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            source.config == Bitmap.Config.HARDWARE
        ) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else source
        val longest = maxOf(software.width, software.height)
        val sized = if (longest <= maxDimension) software else Bitmap.createScaledBitmap(
            software,
            (software.width * maxDimension.toFloat() / longest).toInt().coerceAtLeast(1),
            (software.height * maxDimension.toFloat() / longest).toInt().coerceAtLeast(1),
            true,
        )
        SessionArtwork.of(sized)
    }
}

/**
 * The cover's 8x8 block averages, RGB. Averaging every pixel (rather than scaling down, which
 * samples only a few) keeps it stable across sizes and encodings of the same picture.
 */
private fun signature(bitmap: Bitmap): IntArray {
    val width = bitmap.width
    val height = bitmap.height
    val sums = LongArray(SIGNATURE_CELLS * SIGNATURE_CELLS * 3)
    val counts = IntArray(SIGNATURE_CELLS * SIGNATURE_CELLS)
    val row = IntArray(width)
    for (y in 0 until height) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        val cellRow = y * SIGNATURE_CELLS / height * SIGNATURE_CELLS
        for (x in 0 until width) {
            val cell = cellRow + x * SIGNATURE_CELLS / width
            val pixel = row[x]
            sums[cell * 3] += (pixel shr 16 and 0xFF).toLong()
            sums[cell * 3 + 1] += (pixel shr 8 and 0xFF).toLong()
            sums[cell * 3 + 2] += (pixel and 0xFF).toLong()
            counts[cell]++
        }
    }
    return IntArray(sums.size) { i -> (sums[i] / counts[i / 3].coerceAtLeast(1)).toInt() }
}

private const val SIGNATURE_CELLS = 8
