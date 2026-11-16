package com.tx24.spicyplayer.ivlyrics

import android.graphics.Bitmap
import com.tx24.spicyplayer.ui.background.CoverColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The playing song's cover colour, which motion lyrics build their colours from. Set wherever the
 * cover is learnt: the lyrics screen, and the background session the wallpaper, overlay and
 * always-on display share.
 */
internal object MotionAccent {
    private val colour = MutableStateFlow<String?>(null)
    private val playing = MutableStateFlow("" to "")
    /** The playing song's title and artist, for the Auto theme's genre. */
    val song: StateFlow<Pair<String, String>> = playing

    fun song(title: String?, artist: String?) {
        playing.value = title.orEmpty() to artist.orEmpty()
    }
    val hex: StateFlow<String?> = colour
    private var lastCover: Bitmap? = null

    fun update(cover: Bitmap?) {
        if (cover === lastCover) return
        lastCover = cover
        if (cover == null || cover.isRecycled) return
        colour.value = runCatching {
            val small = Bitmap.createScaledBitmap(cover, 32, 32, true)
            val pixels = IntArray(32 * 32).also { small.getPixels(it, 0, 32, 0, 0, 32, 32) }
            if (small !== cover) small.recycle()
            "#%06X".format(CoverColors.mainColor(pixels) and 0xFFFFFF)
        }.getOrNull() ?: colour.value
    }
}
