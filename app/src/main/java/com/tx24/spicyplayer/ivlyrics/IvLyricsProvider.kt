package com.tx24.spicyplayer.ivlyrics

import android.content.Context
import com.tx24.spicyplayer.network.data.LyricsAttribution
import com.tx24.spicyplayer.network.data.LyricsCapability
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderFailureCategory
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.SourceReleaseChannel
import com.tx24.spicyplayer.network.data.providers.IvLine
import com.tx24.spicyplayer.network.data.providers.IvLyricsDocument
import com.tx24.spicyplayer.network.data.providers.IvSyllable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.ivlis.ivlyricsandroid.SpicyBridge

/**
 * ivLyrics' own lookup as one source: its sources (LRCLIB, Open Lyrics, lrc.red, LyricsPlus,
 * Unison) with its community syncs laid over them, through [SpicyBridge.lookUp].
 */
internal class IvLyricsProvider(context: Context) : com.tx24.spicyplayer.network.data.RemoteLyricsProvider {
    private val app = context.applicationContext

    override val descriptor = LyricsSourceDescriptor(
        id = ID, displayName = "ivLyrics", defaultPriority = 115,
        capabilities = setOf(LyricsCapability.WORD_SYNC, LyricsCapability.LINE_SYNC, LyricsCapability.PLAIN_TEXT),
        releaseChannel = SourceReleaseChannel.EXTENDED,
    )

    override suspend fun fetch(request: LyricsLookupRequest): ProviderResult = withContext(Dispatchers.IO) {
        if (!SpicyBridge.supported()) {
            return@withContext ProviderResult.Unavailable(ProviderFailureCategory.CLIENT_REQUEST, "Needs Android 8")
        }
        val found = runCatching {
            SpicyBridge.lookUp(app, request.title, request.artist, request.album, request.durationSeconds * 1_000L,
                request.spotifyTrackId, TIMEOUT_MS)
        }.getOrNull() ?: return@withContext ProviderResult.Miss
        val lines = found.lines.map { line ->
            IvLine(line.text, line.startMs, line.endMs, line.syllables.map { IvSyllable(it.text, it.startMs, it.endMs) }, line.background, line.singer)
        }
        val attribution = LyricsAttribution(providerName = "ivLyrics", originName = found.provider.takeIf { it.isNotBlank() })
        val payload = when {
            found.wordSynced -> IvLyricsDocument.ttml(lines)?.let { RemoteLyricsPayload(ttmlLyrics = it, sourceId = ID, attribution = attribution) }
            found.lineSynced -> RemoteLyricsPayload(syncedLyrics = IvLyricsDocument.lrc(lines), sourceId = ID, attribution = attribution)
            else -> null
        } ?: lines.filter { !it.background }.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
            ?.let { RemoteLyricsPayload(plainLyrics = it, sourceId = ID, attribution = attribution) }
        if (payload == null) ProviderResult.Miss else ProviderResult.Hit(payload)
    }

    companion object {
        const val ID = "ivlyrics"
        private const val TIMEOUT_MS = 15_000L
    }
}
