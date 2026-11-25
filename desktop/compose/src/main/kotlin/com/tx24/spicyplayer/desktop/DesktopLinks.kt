package com.tx24.spicyplayer.desktop

import android.net.Uri
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform
import java.awt.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** spicylyricsplus:// links (Spotify's sign-in answer), and bringing the window forward. */
object DesktopLinks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun handle(arg: String) {
        when {
            arg == "--show" -> Frame.getFrames().firstOrNull { it.isVisible }?.let { it.extendedState = it.extendedState and Frame.ICONIFIED.inv(); it.toFront(); it.requestFocus() }
            arg.startsWith("spicylyricsplus://callback") -> scope.launch {
                val error = com.tx24.spicyplayer.spotify.SpotifyAccount.finishLogin(DesktopPlatform.context, Uri.parse(arg))
                DesktopPlatform.toast(error ?: "Signed in to Spotify")
                com.tx24.spicyplayer.spotify.SpotifyConnectSession.sync(DesktopPlatform.context)
            }
        }
    }
}
