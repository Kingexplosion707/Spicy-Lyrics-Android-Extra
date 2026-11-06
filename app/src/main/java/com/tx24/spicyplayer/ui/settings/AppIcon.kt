package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.R
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.annotation.DrawableRes
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon: each is an activity alias in the manifest, and one is enabled at a time.
 * [cover] is the same logo, standing in for a song without a cover.
 */
enum class AppIcon(val label: String, private val alias: String, @DrawableRes val cover: Int) {
    /** The default: the alias named after the old launcher activity. */
    Purple("Purple", "com.tx24.spicyplayer.MainActivity", R.drawable.fallback_cover_purple),
    Pride("Pride", "com.tx24.spicyplayer.LauncherPride", R.drawable.fallback_cover_pride);

    companion object {
        /**
         * Shows [icon] in the launcher and hides the rest, touching only aliases that are wrong (a
         * restore can change the setting without coming through here). The new one goes on before
         * the old goes off, so the app is never missing from the launcher.
         */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            fun set(entry: AppIcon, on: Boolean) {
                val component = ComponentName(context.packageName, entry.alias)
                val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                val current = pm.getComponentEnabledSetting(component)
                val isOn = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && entry == Purple)
                if (isOn != on) pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
            set(icon, true)
            entries.filter { it != icon }.forEach { set(it, false) }
        }
    }
}

/** The icon picked in Settings → Theme, for what shows the logo inside the app. */
val LocalAppIcon = staticCompositionLocalOf { AppIcon.Purple }
