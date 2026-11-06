package com.tx24.spicyplayer.widget

import android.content.Context

/** How one placed widget looks, chosen on its settings screen ([WidgetSettingsActivity]). */
internal data class WidgetSettings(
    /** Lines shown above and below the current one, 0 to [WidgetLines.MAX_AROUND]. */
    val around: Int = 1,
    val romanization: Boolean = true,
    /** The source's own translation, under the line it translates. */
    val translation: Boolean = false,
    /** Cover, title and artist, and where. */
    val songInfo: SongInfo = SongInfo.BOTTOM,
    val progress: Boolean = true,
    /** "Provided by …" under the progress bar. */
    val credit: Boolean = true,
    val controls: Controls = Controls.PLAY_PAUSE,
    val background: Background = Background.BLURRED_COVER,
    /** How dark the veil over a cover background is, in percent. */
    val dim: Int = 45,
    val textSize: TextSize = TextSize.MEDIUM,
    val centered: Boolean = false,
) {
    enum class SongInfo(val label: String) {
        TOP("At the top, like the lyrics screen"),
        BOTTOM("At the bottom, like the floating window"),
        HIDDEN("Hidden"),
    }

    enum class Background(val label: String) {
        BLURRED_COVER("Spicy background"),
        COVER_COLOR("Cover colour"),
        BLACK("Black"),
        CLEAR("See-through"),
    }

    enum class Controls(val label: String) {
        NONE("None"),
        PLAY_PAUSE("Play/pause"),
        ALL("Previous, play/pause, next"),
    }

    /** Sizes in sp: the current line, the lines around it, and romanization and translations. */
    enum class TextSize(val label: String, val currentSp: Float, val aroundSp: Float, val noteSp: Float) {
        SMALL("Small", 17f, 13f, 11f),
        MEDIUM("Medium", 21f, 15f, 12f),
        LARGE("Large", 25f, 18f, 14f),
    }
}

/** Each widget's [WidgetSettings], by app widget ID. */
internal class WidgetSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

    fun read(id: Int): WidgetSettings {
        val defaults = WidgetSettings()
        fun key(name: String) = "$id.$name"
        return WidgetSettings(
            around = preferences.getInt(key("around"), defaults.around).coerceIn(0, WidgetLines.MAX_AROUND),
            romanization = preferences.getBoolean(key("romanization"), defaults.romanization),
            translation = preferences.getBoolean(key("translation"), defaults.translation),
            songInfo = enumOr(preferences.getString(key("songInfo"), null), defaults.songInfo),
            progress = preferences.getBoolean(key("progress"), defaults.progress),
            credit = preferences.getBoolean(key("credit"), defaults.credit),
            controls = enumOr(preferences.getString(key("controls"), null), defaults.controls),
            background = enumOr(preferences.getString(key("background"), null), defaults.background),
            dim = preferences.getInt(key("dim"), defaults.dim).coerceIn(0, 100),
            textSize = enumOr(preferences.getString(key("textSize"), null), defaults.textSize),
            centered = preferences.getBoolean(key("centered"), defaults.centered),
        )
    }

    fun save(id: Int, settings: WidgetSettings) {
        fun key(name: String) = "$id.$name"
        preferences.edit()
            .putInt(key("around"), settings.around)
            .putBoolean(key("romanization"), settings.romanization)
            .putBoolean(key("translation"), settings.translation)
            .putString(key("songInfo"), settings.songInfo.name)
            .putBoolean(key("progress"), settings.progress)
            .putBoolean(key("credit"), settings.credit)
            .putString(key("controls"), settings.controls.name)
            .putString(key("background"), settings.background.name)
            .putInt(key("dim"), settings.dim)
            .putString(key("textSize"), settings.textSize.name)
            .putBoolean(key("centered"), settings.centered)
            .apply()
    }

    fun forget(ids: IntArray) {
        val prefixes = ids.map { "$it." }
        val editor = preferences.edit()
        preferences.all.keys.filter { key -> prefixes.any(key::startsWith) }.forEach(editor::remove)
        editor.apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
