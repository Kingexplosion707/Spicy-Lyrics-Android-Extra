package com.tx24.spicyplayer.ivlyrics

import android.content.Context
import com.tx24.spicyplayer.network.data.LyricsCapability
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderFailureCategory
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.SourceReleaseChannel

/** ivLyrics' own lookup lives in its Android library: on the desktop this source is unavailable. */
internal class IvLyricsProvider(@Suppress("UNUSED_PARAMETER") context: Context) : com.tx24.spicyplayer.network.data.RemoteLyricsProvider {
    override val descriptor = LyricsSourceDescriptor(
        id = ID, displayName = "ivLyrics", defaultPriority = 115,
        capabilities = setOf(LyricsCapability.WORD_SYNC, LyricsCapability.LINE_SYNC, LyricsCapability.PLAIN_TEXT),
        releaseChannel = SourceReleaseChannel.EXTENDED,
    )

    override suspend fun fetch(request: LyricsLookupRequest): ProviderResult =
        ProviderResult.Unavailable(ProviderFailureCategory.CLIENT_REQUEST, "Only in the Android app")

    companion object { const val ID = "ivlyrics" }
}
