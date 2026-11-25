package com.tx24.spicyplayer.translation

import android.content.Context

/** Android's on-device language identification has no desktop counterpart: no answer. */
class SystemLanguageDetector(@Suppress("UNUSED_PARAMETER") context: Context) : SongLanguageDetector {
    override fun detect(originalLyrics: String): String? = null
}
