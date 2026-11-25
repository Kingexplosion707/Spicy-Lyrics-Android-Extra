package com.tx24.spicyplayer

// Android-only parts of :app the shared code names, as they are on the desktop.

import android.content.Context

/** No floating window on the desktop (the window itself can be made small). */
object PictureInPicture {
    fun supported(context: Context): Boolean = false
}

/** The desktop app's process: visible while its window is. */
object SpicyLyricsApplication {
    @Volatile var visible: Boolean = true
}
