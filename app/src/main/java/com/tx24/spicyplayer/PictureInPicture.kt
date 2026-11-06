package com.tx24.spicyplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import com.tx24.spicyplayer.ui.settings.PIP_RATIO_RANGE
import com.tx24.spicyplayer.ui.settings.PipShape

/** The lyrics as a small floating window: whether the phone has one, and what it carries. */
object PictureInPicture {
    const val ACTION_PREVIOUS = "com.tx24.spicyplayer.pip.PREVIOUS"
    const val ACTION_PLAY_PAUSE = "com.tx24.spicyplayer.pip.PLAY_PAUSE"
    const val ACTION_NEXT = "com.tx24.spicyplayer.pip.NEXT"

    val actions = listOf(ACTION_PREVIOUS, ACTION_PLAY_PAUSE, ACTION_NEXT)

    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** The window's shape as Settings has it, kept inside the 1:2.39 to 2.39:1 Android allows. */
    fun ratio(shape: PipShape, customRatio: Int): Rational =
        if (shape == PipShape.Custom) Rational(customRatio.coerceIn(PIP_RATIO_RANGE), 100) else Rational(shape.width, shape.height)

    /**
     * The window at [ratio] (see [ratio]). [autoEnter] makes leaving the app enter it on its own
     * (Android 12+; before that the activity enters it itself, from onUserLeaveHint).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun params(context: Context, isPlaying: Boolean, autoEnter: Boolean, ratio: Rational): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(
                listOf(
                    action(context, ACTION_PREVIOUS, android.R.drawable.ic_media_previous, "Previous"),
                    if (isPlaying) {
                        action(context, ACTION_PLAY_PAUSE, android.R.drawable.ic_media_pause, "Pause")
                    } else {
                        action(context, ACTION_PLAY_PAUSE, android.R.drawable.ic_media_play, "Play")
                    },
                    action(context, ACTION_NEXT, android.R.drawable.ic_media_next, "Next"),
                ),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            // Seamless resizing scales a snapshot, which is for video; the lyrics redraw instead.
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun action(context: Context, action: String, icon: Int, title: String): RemoteAction {
        val intent = Intent(action).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context, actions.indexOf(action), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(context, icon), title, title, pending)
    }
}
