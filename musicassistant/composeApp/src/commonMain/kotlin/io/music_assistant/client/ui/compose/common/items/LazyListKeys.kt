package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem

/**
 * Stable canonical identity for an [AppMediaItem] as a single collision-safe
 * [String], combining `mediaType`, `provider`, and `itemId`.
 *
 * Use this as a Compose lazy-layout `key` ONLY when the rendered collection
 * cannot contain the same canonical item twice. For collections that may
 * legitimately repeat an item (folders, playlists, queues, search results),
 * use [lazyListOccurrenceKeys] instead.
 *
 * The encoding is length-prefixed so field contents can never collide across
 * field boundaries — e.g. (provider="apple_music", itemId="xyz") is distinct
 * from (provider="apple", itemId="music_xyz").
 */
fun AppMediaItem.lazyListKey(): String =
    mediaItemKey(mediaType.name, provider, itemId)

/**
 * Keys a lazy-layout list of media items that may legitimately contain the same
 * canonical media item more than once, such as folders, playlists, queues, or
 * other occurrence-based server data.
 */
fun List<AppMediaItem>.lazyListOccurrenceKeys(): List<String> =
    occurrenceKeys { it.lazyListKey() }

/**
 * Keys playable lists with the same canonical identity as [AppMediaItem].
 */
fun List<PlayableItem>.playableLazyListOccurrenceKeys(): List<String> =
    occurrenceKeys { item -> mediaItemKey(item.mediaType.name, item.provider, item.itemId) }

private fun <T> List<T>.occurrenceKeys(keyOf: (T) -> String): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return map { item ->
        val key = keyOf(item)
        val occurrence = occurrences.getOrElse(key) { 0 }
        occurrences[key] = occurrence + 1
        if (occurrence == 0) key else mediaItemKey("occurrence", key, occurrence.toString())
    }
}

private fun mediaItemKey(vararg fields: String): String =
    fields.joinToString(separator = "") { field -> "${field.length}:$field" }
