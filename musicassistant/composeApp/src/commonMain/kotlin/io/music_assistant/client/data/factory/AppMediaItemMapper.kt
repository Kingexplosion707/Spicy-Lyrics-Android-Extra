package io.music_assistant.client.data.factory

import io.music_assistant.client.data.model.client.items.MarkableItem
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.mediaItemEchoJson
import kotlinx.serialization.json.JsonElement

/**
 * Builds the `media_item` payload for `music/mark_played` / `music/mark_unplayed` by
 * echoing the original server DTO back verbatim (nulls omitted). The server expects a
 * full media item it can deserialize into a typed dataclass; reconstructing one from
 * the decomposed [io.music_assistant.client.data.model.client.items.AppMediaItem]
 * loses required fields (e.g. a podcast episode's `position`/`podcast`), so we return
 * exactly what we received.
 */
fun MarkableItem.toMarkMediaItem(): JsonElement =
    mediaItemEchoJson.encodeToJsonElement(ServerMediaItem.serializer(), source)

/**
 * Builds the `track` payload for `metadata/get_track_lyrics` by echoing the original
 * server DTO back verbatim (nulls omitted), exactly like [toMarkMediaItem]. The server
 * resolves lyrics from the full track (artists, album, duration, provider mappings) —
 * reconstructing a minimal shape from our decomposed [Track] starves that lookup and
 * yields empty results even when lyrics exist.
 */
fun Track.toLyricsRequestArg(): JsonElement =
    mediaItemEchoJson.encodeToJsonElement(ServerMediaItem.serializer(), source)
