package com.tx24.spicyplayer.playback

import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.tx24.spicyplayer.lyrics.LocalLyricsStore
import com.tx24.spicyplayer.lyrics.LyricsFolder
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.RemoteLyricsSelection
import com.tx24.spicyplayer.network.data.TrackNameCleaner
import com.tx24.spicyplayer.network.data.measuredQuality
import java.security.MessageDigest

/*
 * What the lyrics screen and the home screen widget agree on: which session is followed, what the
 * song is called for lookup, and which of the user's own lyrics come before the sources.
 */

/** Neither playing nor stopped: the player is loading, connecting or changing songs. */
internal val IN_BETWEEN_STATES = setOf(
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_CONNECTING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
)

/**
 * The session to follow among [sessions]: the one followed now ([currentToken]) while [stickiness]
 * keeps it, else a playing local player over a playing remote mirror, else the one followed now,
 * else a local player, else any. The caller resets [stickiness] when the pick changes.
 */
internal fun bestSession(
    sessions: List<MediaController>,
    currentToken: MediaSession.Token?,
    stickiness: SessionStickiness,
    nowMs: Long,
): MediaController? {
    val current = sessions.firstOrNull { it.sessionToken == currentToken }
    val kept = current?.takeIf {
        val state = it.playbackState?.state
        stickiness.keep(state == PlaybackState.STATE_PLAYING || state in IN_BETWEEN_STATES, nowMs)
    }
    return kept ?: sessions.firstOrNull {
        it.playbackState?.state == PlaybackState.STATE_PLAYING &&
            it.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
    } ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: current
        ?: sessions.firstOrNull {
            it.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL
        } ?: sessions.firstOrNull()
}

/** What lyrics are keyed on: title + artist only, because players fill metadata in across several updates. */
internal fun MediaMetadata?.lyricsKey(): String? = this?.let {
    val title = (getString(MediaMetadata.METADATA_KEY_TITLE) ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
        ?.trim()?.lowercase().orEmpty()
    val artist = (getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.trim()?.lowercase().orEmpty()
    if (title.isBlank() && artist.isBlank()) null else "$title\u001f$artist"
}

/** What the sources are asked for this song: its names cleaned up for lookup. */
internal fun MediaMetadata.lookupRequest(spotifyTrackId: String?): LyricsLookupRequest {
    val names = TrackNameCleaner.clean(
        title = getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty(),
        artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
    )
    return LyricsLookupRequest(
        artist = names.artist,
        title = names.title,
        album = getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
        durationSeconds = (getLong(MediaMetadata.METADATA_KEY_DURATION) / 1_000L).coerceAtLeast(0L).toInt(),
        spotifyTrackId = spotifyTrackId,
    )
}

/** The song's lyrics from the Lyrics Manager: a saved song, else a file in the lyrics folder. */
internal fun savedLyricsFor(request: LyricsLookupRequest, store: LocalLyricsStore, folder: LyricsFolder): RemoteLyricsSelection? =
    store.get(request.title, request.artist)?.let { savedSelection(it, persistent = true) }
        ?: folder.lyrics(request.title, request.artist)?.let { ttml ->
            val payload = RemoteLyricsPayload(ttmlLyrics = ttml, sourceId = FOLDER_SOURCE.id)
            RemoteLyricsSelection(FOLDER_SOURCE, payload, payload.measuredQuality())
                .takeIf { it.quality != RemoteLyricsQuality.NONE }
        }

/** [ttml] from the Lyrics Manager as a pick: saved ([persistent]) or uploaded just once. */
internal fun savedSelection(ttml: String, persistent: Boolean): RemoteLyricsSelection {
    val payload = RemoteLyricsPayload(ttmlLyrics = ttml, sourceId = LOCAL_SOURCE.id)
    return RemoteLyricsSelection(if (persistent) LOCAL_SOURCE else UPLOADED_SOURCE, payload, payload.measuredQuality())
}

/** The Spotify track ID the player gives for this song, if it gives one. */
internal fun MediaMetadata?.sessionSpotifyId(): String? = sequenceOf(
    this?.description?.mediaId,
    this?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
    this?.description?.mediaUri?.toString(),
).mapNotNull { it.spotifyTrackId() }.firstOrNull()

/*
 * Manual Spotify IDs (`spotify_id_overrides`) are saved by title + artist, with the song's length
 * in the value: players often send the length after the title, so it can't be part of the key, but
 * a recording of another length under the same names doesn't get the ID.
 */

/** How far a song's length may be from the one its manual Spotify ID was saved with. */
private const val OVERRIDE_LENGTH_SLACK_MS = 3_000L

/** The key a manual Spotify ID is saved under: title and artist. */
internal fun overrideKey(title: String, artist: String): String? = overrideHash(title, artist, length = null)

/** The key manual Spotify IDs were saved under before, length included; read so they aren't lost. */
internal fun legacyOverrideKey(title: String, artist: String, durationMs: Long): String? =
    overrideHash(title, artist, length = durationMs)

private fun overrideHash(title: String, artist: String, length: Long?): String? {
    val names = listOf(title, artist).map { it.trim().lowercase() }
    if (names.any { it.isBlank() }) return null
    val identity = (names + listOfNotNull(length?.toString())).joinToString("\u001f")
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/** What's saved for [id]: the ID, and the song's length when it's known. */
internal fun overrideValue(id: String, durationMs: Long): String = if (durationMs > 0) "$id\u001f$durationMs" else id

/** A saved manual Spotify ID, and whether it's under the old key (so it's moved). */
internal data class SavedOverride(val id: String, val legacy: Boolean)

/**
 * The manual Spotify ID [read] has for a song of [durationMs] (0 when not known yet). One saved
 * with a length is the song's only while the lengths are close, or either isn't known.
 */
internal fun savedOverride(title: String, artist: String, durationMs: Long, read: (String) -> String?): SavedOverride? {
    overrideKey(title, artist)?.let(read)?.let { value ->
        val id = value.substringBefore('\u001f')
        val savedLength = value.substringAfter('\u001f', "").toLongOrNull() ?: 0L
        val sameSong = savedLength <= 0 || durationMs <= 0 || kotlin.math.abs(savedLength - durationMs) <= OVERRIDE_LENGTH_SLACK_MS
        if (id.isNotBlank() && sameSong) return SavedOverride(id, legacy = false)
    }
    return legacyOverrideKey(title, artist, durationMs)?.let(read)?.let { SavedOverride(it, legacy = true) }
}

private fun MediaMetadata.overrideNames(): Pair<String, String> =
    getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty() to
        (getString(MediaMetadata.METADATA_KEY_ARTIST) ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()

/** The manual Spotify ID saved in [overrides] for this song; one under the old key is moved to the new. */
internal fun MediaMetadata?.savedSpotifyId(overrides: SharedPreferences): String? {
    val metadata = this ?: return null
    val (title, artist) = metadata.overrideNames()
    val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
    val saved = savedOverride(title, artist, duration) { overrides.getString(it, null) } ?: return null
    if (saved.legacy) metadata.saveSpotifyId(overrides, saved.id)
    return saved.id
}

/** Saves [id] in [overrides] as this song's manual Spotify ID. */
internal fun MediaMetadata?.saveSpotifyId(overrides: SharedPreferences, id: String) {
    val metadata = this ?: return
    val (title, artist) = metadata.overrideNames()
    val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
    val key = overrideKey(title, artist) ?: return
    val edit = overrides.edit().putString(key, overrideValue(id, duration))
    legacyOverrideKey(title, artist, duration)?.let(edit::remove)
    edit.apply()
}

/** Drops this song's manual Spotify ID from [overrides]. */
internal fun MediaMetadata?.forgetSpotifyId(overrides: SharedPreferences) {
    val metadata = this ?: return
    val (title, artist) = metadata.overrideNames()
    val key = overrideKey(title, artist) ?: return
    val edit = overrides.edit().remove(key)
    legacyOverrideKey(title, artist, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))?.let(edit::remove)
    edit.apply()
}
