@file:Suppress("unused")
@file:JvmName("AndroidPlatformInterop")

package androidx.compose.ui.platform

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform

/** The app's context: on the desktop there's one. */
val LocalContext = staticCompositionLocalOf<Context> { DesktopPlatform.context }

/** The window's size in dp and its orientation, set by the desktop window. */
val LocalConfiguration = staticCompositionLocalOf { Configuration() }

/** Android's hosting view, for what the shared code reads from it (its height, keeping the screen on). */
val LocalView = staticCompositionLocalOf { android.view.View() }
