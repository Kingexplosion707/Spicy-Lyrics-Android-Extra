package com.tx24.spicyplayer.ui.settings

/**
 * How the synced lyrics look and move. Spring is the original renderer; Bloom fills each line
 * behind one soft edge, swells held words letter by letter, and follows the song with a staggered
 * scroll.
 */
enum class RendererStyle(val label: String) {
    Spring("Spring (Default)"),
    Bloom("Bloom"),
}

/**
 * Carries settings saved before the style choice was [RendererStyle] over to it. The Apple Music
 * style and Simple Lyrics Mode (with or without its Refreshed look) became Bloom; everyone else
 * stays on Spring. Fade Away Previous Lines never applied to the Apple Music style, so it starts
 * off for those who had it.
 */
object RendererStyleMigration {
    const val KEY = "rendererStyle"
    private const val OLD_STYLE = "lyricsStyle"
    private const val OLD_SIMPLE = "simpleLyricsMode"
    private const val OLD_REFRESHED = "refreshedSimpleLyrics"
    private const val MINIMAL = "minimalLyricsMode"

    /** The old keys, with the type each was saved as, so an older backup still restores them. */
    val oldKeys: Map<String, kotlin.reflect.KClass<*>> = mapOf(
        OLD_STYLE to String::class, OLD_SIMPLE to Boolean::class, OLD_REFRESHED to Boolean::class,
    )

    /** What to write and what to remove; both empty when there's nothing to carry over. */
    data class Edit(val set: Map<String, Any>, val remove: Set<String>) {
        val isEmpty: Boolean get() = set.isEmpty() && remove.isEmpty()
    }

    fun migrate(stored: Map<String, *>): Edit {
        val old = oldKeys.keys.filter { it in stored }.toSet()
        if (old.isEmpty()) return Edit(emptyMap(), emptySet())
        val set = mutableMapOf<String, Any>()
        if (KEY !in stored) {
            val appleMusic = stored[OLD_STYLE] == "AppleMusic"
            val simple = stored[OLD_SIMPLE] == true
            set[KEY] = (if (appleMusic || simple) RendererStyle.Bloom else RendererStyle.Spring).name
            if (appleMusic && stored[MINIMAL] == true) set[MINIMAL] = false
        }
        return Edit(set, old)
    }
}
