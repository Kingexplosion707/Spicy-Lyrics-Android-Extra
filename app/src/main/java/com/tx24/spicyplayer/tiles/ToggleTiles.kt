package com.tx24.spicyplayer.tiles

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.aod.AlwaysOnService
import com.tx24.spicyplayer.overlay.LyricsOverlayService

/**
 * A Quick Settings tile that turns one of the app's features on and off from the shade, showing
 * whether it's on. Turning it on starts its service; when the system won't let a service start
 * from here, the app opens instead and starts it.
 */
@RequiresApi(Build.VERSION_CODES.N)
abstract class FeatureTileService : TileService() {
    /** The `ui` setting the feature is on by. */
    protected abstract val pref: String

    /** Starts or stops the feature's service for the setting; false when it couldn't start. */
    protected abstract fun sync(context: Context): Boolean

    /** What must be allowed before it can turn on, or null when nothing is missing. */
    protected open fun missingPermission(): Intent? = null

    private fun on() = getSharedPreferences("ui", MODE_PRIVATE).getBoolean(pref, false)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val turnOn = !on()
        if (turnOn) missingPermission()?.let { launch(it); return }
        // The new value is read back at once from memory; the file catches up in the background.
        getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean(pref, turnOn).apply()
        if (!sync(this) && turnOn) launch(Intent(this, LyricsActivity::class.java))
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = on()
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }

    /** Opens [intent] and closes the shade. */
    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        }
    }
}

/** Quick Settings: the lyrics overlay. */
@RequiresApi(Build.VERSION_CODES.N)
class OverlayTileService : FeatureTileService() {
    override val pref = LyricsOverlayService.PREF_ENABLED
    override fun sync(context: Context) = LyricsOverlayService.sync(context)
    override fun missingPermission(): Intent? = if (Settings.canDrawOverlays(this)) null
        else Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
}

/** Quick Settings: the always-on display. */
@RequiresApi(Build.VERSION_CODES.N)
class AlwaysOnTileService : FeatureTileService() {
    override val pref = AlwaysOnService.PREF_ENABLED
    override fun sync(context: Context) = AlwaysOnService.sync(context)
    // It opens over the lock screen by drawing over other apps.
    override fun missingPermission(): Intent? = if (Settings.canDrawOverlays(this)) null
        else Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
}
