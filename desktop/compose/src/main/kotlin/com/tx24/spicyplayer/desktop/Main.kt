package com.tx24.spicyplayer.desktop

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.LyricsApp
import com.tx24.spicyplayer.desktop.platform.BackDispatcher
import com.tx24.spicyplayer.desktop.platform.DesktopPlatform
import com.tx24.spicyplayer.desktop.platform.DesktopWindow
import com.tx24.spicyplayer.desktop.platform.FilePicker
import com.tx24.spicyplayer.desktop.platform.FilePreferences
import com.tx24.spicyplayer.desktop.platform.SingleInstance
import com.tx24.spicyplayer.desktop.platform.WindowsTheme
import com.tx24.spicyplayer.haptics.ProvideTouchHaptics
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.SleepTimerService
import com.tx24.spicyplayer.ui.components.LocalGroupedControls
import com.tx24.spicyplayer.ui.components.LocalPopupPalette
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.controls.KeyInput
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.LocalAppIcon
import com.tx24.spicyplayer.update.UpdateViewModel
import kotlinx.coroutines.flow.collect

/**
 * Spicy Lyrics Plus on the desktop: the Android app's lyrics screen (LyricsApp) in a window, following
 * whatever plays through Windows' media controls.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    DesktopPlatform.detectWallpaper(args)
    val wallpaper = DesktopPlatform.wallpaper
    if (wallpaper) {
        // Beside the app, not instead of it: the app keeps the settings and the single instance.
        FilePreferences.readOnly = true
        FilePreferences.watch()
    } else {
        // A second start hands its arguments (a Spotify sign-in's answer) to the running app and ends.
        if (!SingleInstance.claim(args)) return
        com.tx24.spicyplayer.desktop.platform.WindowsIntegration.register()
    }
    System.setProperty("compose.interop.blending", "true")
    val app = DesktopPlatform.application
    com.tx24.spicyplayer.playback.CrashLog.install(app)
    val playback = ExternalPlaybackViewModel(app)
    val updates = UpdateViewModel(app)
    val keyInput = KeyInput().apply {
        onPlayPause = playback::playPause
        onPrevious = playback::skipPrevious
        onNext = playback::skipNext
        onResync = playback::resync
    }
    SleepTimerService.pause = {
        if (playback.state.value.isPlaying) playback.playPause()
    }
    SleepTimerService.remainingMs = {
        val s = playback.state.value
        if (s.durationMs > 0) s.durationMs - playback.currentPositionMs() else null
    }
    if (!wallpaper) {
        SingleInstance.onArguments = { DesktopLinks.handle(it) }
        args.forEach(DesktopLinks::handle)
    }

    application(exitProcessOnExit = true) {
        val prefs = remember { app.getSharedPreferences("desktop", Context.MODE_PRIVATE) }
        val windowState = if (wallpaper) {
            // The whole main screen; Wallpaper Engine moves and sizes it from there.
            val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
            rememberWindowState(
                size = DpSize(screen.width.dp, screen.height.dp),
                position = WindowPosition(screen.x.dp, screen.y.dp),
            )
        } else rememberWindowState(
            size = DpSize(prefs.getFloat("width", 1280f).dp, prefs.getFloat("height", 800f).dp),
            position = if (prefs.contains("x")) WindowPosition(prefs.getFloat("x", 0f).dp, prefs.getFloat("y", 0f).dp) else WindowPosition.PlatformDefault,
            placement = if (prefs.getBoolean("maximized", false)) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        var alwaysOnTop by remember { mutableStateOf(prefs.getBoolean("onTop", false)) }
        DesktopPlatform.onQuit = {
            if (!wallpaper) saveWindow(prefs, windowState)
            FilePreferences.flush()
            exitApplication()
        }
        Window(
            onCloseRequest = { DesktopPlatform.quit() },
            state = windowState,
            title = if (wallpaper) "Spicy Lyrics Plus Wallpaper" else "Spicy Lyrics Plus",
            icon = painterResource("icon.png"),
            undecorated = wallpaper,
            resizable = !wallpaper,
            alwaysOnTop = alwaysOnTop && !wallpaper,
            onPreviewKeyEvent = { event ->
                when {
                    wallpaper -> false
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> BackDispatcher.back()
                    event.type == KeyEventType.KeyDown && event.key == Key.F11 -> {
                        windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.T -> {
                        alwaysOnTop = !alwaysOnTop
                        prefs.edit().putBoolean("onTop", alwaysOnTop).apply()
                        playback.showMessage(if (alwaysOnTop) "Stays on top of other windows" else "No longer on top")
                        true
                    }
                    else -> KeyMap.toAndroid(event)?.let(keyInput::handle) ?: false
                }
            },
        ) {
            LaunchedEffect(Unit) {
                FilePicker.window = window
                if (!wallpaper) {
                    WindowsTheme.darkTitleBar(window)
                    window.minimumSize = java.awt.Dimension(420, 320)
                }
            }
            val density = LocalDensity.current
            val size = windowState.size
            DesktopWindow.widthPx = with(density) { size.width.roundToPx() }
            DesktopWindow.heightPx = with(density) { size.height.roundToPx() }
            val configuration = remember(size) {
                Configuration().apply {
                    screenWidthDp = size.width.value.toInt()
                    screenHeightDp = size.height.value.toInt()
                    smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp)
                    orientation = if (size.width > size.height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                }
            }
            DisposableEffect(Unit) {
                playback.setUiStarted(true)
                playback.refresh()
                onDispose { playback.setUiStarted(false) }
            }
            LaunchedEffect(Unit) { DesktopPlatform.toastMessages.collect { playback.showMessage(it) } }
            if (!wallpaper) LaunchedEffect(Unit) { updates.checkOnLaunch(includePrereleases = true) }
            // Nobody is there to answer a pop-up on a wallpaper.
            if (wallpaper) LaunchedEffect(Unit) {
                playback.state.collect { if (it.limitNotice != null) playback.dismissLimitNotice() }
            }

            val settings = remember { AppSettings(app.getSharedPreferences("ui", Context.MODE_PRIVATE)) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideTouchHaptics(false) {
                    CompositionLocalProvider(
                        LocalConfiguration provides configuration,
                        LocalUiAnimations provides (settings.uiAnimations && !settings.lowPerformance),
                        LocalGroupedControls provides settings.groupedControls,
                        LocalPopupPalette provides settings.popupTheme.palette,
                        LocalAppIcon provides settings.appIcon,
                    ) {
                        // A moving mouse shows the controls, as a touch does on a phone.
                        androidx.compose.foundation.layout.Box(
                            if (wallpaper) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) {
                                keyInput.presses.value = android.os.SystemClock.uptimeMillis()
                            },
                        ) {
                        LyricsApp(
                            openNotificationAccess = {},
                            viewModel = playback,
                            updater = updates,
                            settings = settings,
                            inPip = false,
                            onEnterPip = null,
                            onPipAllowed = {},
                            keyInput = keyInput,
                        )
                        // A wallpaper is to look at: clicks passed on by the wallpaper app go nowhere.
                        if (wallpaper) androidx.compose.foundation.layout.Box(
                            androidx.compose.ui.Modifier.matchParentSize().pointerInput(Unit) {
                                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                            },
                        )
                        }
                    }
                }
            }
        }
    }
}

private fun saveWindow(prefs: android.content.SharedPreferences, state: androidx.compose.ui.window.WindowState) {
    prefs.edit().apply {
        putBoolean("maximized", state.placement == WindowPlacement.Maximized)
        if (state.placement == WindowPlacement.Floating) {
            putFloat("width", state.size.width.value)
            putFloat("height", state.size.height.value)
            val p = state.position
            if (p is WindowPosition.Absolute) { putFloat("x", p.x.value); putFloat("y", p.y.value) }
        }
    }.commit()
}
