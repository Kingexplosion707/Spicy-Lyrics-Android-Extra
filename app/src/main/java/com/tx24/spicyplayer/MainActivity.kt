package com.tx24.spicyplayer

import com.tx24.spicyplayer.ui.settings.LocalAppIcon
import com.tx24.spicyplayer.ui.settings.AppIcon
import com.tx24.spicyplayer.ui.components.LocalPopupPalette
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.Coil
import coil.ImageLoader
import com.tx24.spicyplayer.network.data.counted
import okhttp3.OkHttpClient
import com.tx24.spicyplayer.haptics.ProvideTouchHaptics
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tx24.spicyplayer.ui.components.LocalGroupedControls
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.SessionAccessService
import com.tx24.spicyplayer.ui.controls.KeyInput
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.settings.AppSettings
import com.tx24.spicyplayer.ui.settings.SystemBarsMode
import androidx.compose.runtime.DisposableEffect
import com.tx24.spicyplayer.update.UpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tx24.spicyplayer.analytics.UsageStats


/** Whether Coil's loader has been swapped for the counted one ([LyricsActivity.countImageDownloads]). */
private var imageLoaderSet = false

/**
 * The lyrics screen. Not named MainActivity: that name belongs to the launcher entry (an alias in
 * the manifest), so home-screen icons pinned before the icon choice existed keep working.
 */
class LyricsActivity : ComponentActivity() {
    private val playbackViewModel: ExternalPlaybackViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private val keyInput by lazy {
        KeyInput().apply {
            onPlayPause = playbackViewModel::playPause
            onPrevious = playbackViewModel::skipPrevious
            onNext = playbackViewModel::skipNext
            onResync = playbackViewModel::resync
        }
    }

    // Activity's own public method: androidx.core's ComponentActivity re-declares it restricted,
    // which lint reads as this override reaching into the library.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        keyInput.handle(event) || super.dispatchKeyEvent(event)

    /** In the small floating window: the lyrics screen then shows the lyrics and nothing else. */
    private var inPip by mutableStateOf(false)
    /** Whether the screen in front is one it's fine to shrink (not settings, a pop-up, the intro). */
    private val pipAllowed = MutableStateFlow(false)
    /** Another screen was opened for an answer ([startActivityForResult]): no window until it's back. */
    private val pipHeld = MutableStateFlow(false)
    private var pipAutoEnter = false
    /** The window's shape from Settings (`AppSettings.pipShape`). */
    private val pipRatio = MutableStateFlow(Rational(16, 9))
    /**
     * The last shape the system took. It refuses shape changes that come too often ("Too many PiP
     * aspect ratio change requests"), and then refuses entering with the new shape too, so the
     * window enters with this one until the new shape is taken.
     */
    private var pipAcceptedRatio = Rational(16, 9)
    private var pipRatioApplied = false
    private val pipSupported by lazy { PictureInPicture.supported(this) }

    // The window's own buttons come back as broadcasts.
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PictureInPicture.ACTION_PREVIOUS -> playbackViewModel.skipPrevious()
                PictureInPicture.ACTION_PLAY_PAUSE -> playbackViewModel.playPause()
                PictureInPicture.ACTION_NEXT -> playbackViewModel.skipNext()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UsageStats.init(this)
        countImageDownloads()
        enableEdgeToEdge()
        applySystemBars()
        if (pipSupported) {
            // Recreated inside the window (the system's call, not a resize), no change is reported:
            // the window's own state says where it starts.
            inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
            addOnPictureInPictureModeChangedListener { inPip = it.isInPictureInPictureMode }
            // Keeps the window's play/pause icon and shape right, and whether leaving the app enters it.
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        pipAllowed,
                        pipHeld,
                        playbackViewModel.state.map { it.isPlaying to (it.sourcePackage != null) }.distinctUntilChanged(),
                        pipRatio,
                    ) { allowed, held, (playing, hasSession), ratio -> Triple(playing, allowed && !held && hasSession, ratio) }
                        .collectLatest { (playing, autoEnter, ratio) ->
                            pipAutoEnter = autoEnter
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@collectLatest
                            // A refused shape is asked for again a little later, with the shape the
                            // system already took in the meantime.
                            while (true) {
                                val sent = runCatching {
                                    setPictureInPictureParams(PictureInPicture.params(this@LyricsActivity, playing, autoEnter, ratio))
                                }
                                if (sent.isSuccess) {
                                    pipAcceptedRatio = ratio
                                    break
                                }
                                runCatching {
                                    setPictureInPictureParams(PictureInPicture.params(this@LyricsActivity, playing, autoEnter, pipAcceptedRatio))
                                }
                                delay(PIP_RATIO_RETRY_MS)
                            }
                        }
                }
            }
        }
        setContent {
            val settings = remember { AppSettings(getSharedPreferences("ui", Context.MODE_PRIVATE)) }
            // A Quick Settings tile (ivLyrics) may change a setting while this screen is up.
            DisposableEffect(settings) {
                val prefs = getSharedPreferences("ui", Context.MODE_PRIVATE)
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "motionLyrics" || key?.startsWith("motionOn") == true) settings.reload()
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            // A change in Settings shows or hides the bars at once.
            LaunchedEffect(settings.systemBars) { applySystemBars(settings.systemBars) }
            LaunchedEffect(settings.pipShape, settings.pipCustomRatio) {
                // Dragging the custom shape's slider changes it many times a second; only the shape
                // it settles on goes to the system, which refuses changes that come too often.
                if (pipRatioApplied) delay(PIP_RATIO_SETTLE_MS)
                pipRatioApplied = true
                pipRatio.value = PictureInPicture.ratio(settings.pipShape, settings.pipCustomRatio)
            }
            MaterialTheme(colorScheme = darkColorScheme()) {
                ProvideTouchHaptics(settings.touchHaptics) {
                    CompositionLocalProvider(
                        LocalUiAnimations provides (settings.uiAnimations && !settings.lowPerformance),
                        LocalGroupedControls provides settings.groupedControls,
                        LocalPopupPalette provides settings.popupTheme.palette,
                        LocalAppIcon provides settings.appIcon,
                    ) {
                    LyricsApp(
                        viewModel = playbackViewModel,
                        updater = updateViewModel,
                        settings = settings,
                        openNotificationAccess = {
                            // Many TVs have no notification access screen; there it's granted over adb.
                            try {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            } catch (missing: android.content.ActivityNotFoundException) {
                                playbackViewModel.showMessage("This device has no notification access screen. Allow it from a computer: " +
                                    "adb shell cmd notification allow_listener $packageName/${SessionAccessService::class.java.name}")
                            }
                        },
                        inPip = inPip,
                        onEnterPip = ::enterPip.takeIf { pipSupported },
                        onPipAllowed = { pipAllowed.value = it && pipSupported },
                        keyInput = keyInput,
                    )
                    }
                }
            }
        }
    }

    /** Covers and artist images go through Coil's own client: counted like the rest. Once per process. */
    private fun countImageDownloads() {
        if (imageLoaderSet) return
        imageLoaderSet = true
        val context = applicationContext
        Coil.setImageLoader { ImageLoader.Builder(context).okHttpClient { OkHttpClient.Builder().counted().build() }.build() }
    }

    /** Shrinks the lyrics into the floating window (the button's way in). */
    private fun enterPip() {
        if (!pipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val playing = playbackViewModel.state.value.isPlaying
        runCatching { enterPictureInPictureMode(PictureInPicture.params(this, playing, pipAutoEnter, pipRatio.value)) }
            .recoverCatching { enterPictureInPictureMode(PictureInPicture.params(this, playing, pipAutoEnter, pipAcceptedRatio)) }
    }

    // Android 12+ enters the window on its own (see PictureInPicture.params); before that, here.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipAutoEnter && Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) enterPip()
    }

    override fun onStart() {
        super.onStart()
        if (pipSupported) {
            ContextCompat.registerReceiver(
                this, pipReceiver, IntentFilter().apply { PictureInPicture.actions.forEach(::addAction) },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        playbackViewModel.setUiStarted(true)
        UsageStats.appOpened(this) { lookupSnapshot(playbackViewModel.state.value) }
    }

    override fun onResume() {
        super.onResume()
        pipHeld.value = false
        playbackViewModel.refresh()
    }

    /**
     * Opening a file picker (or anything else that answers back) is leaving the app as far as
     * Android's concerned, so the lyrics would shrink and the screen waiting for the answer would
     * close and miss it. The window stays shut until the app is back.
     */
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (requestCode >= 0 && pipSupported) {
            pipHeld.value = true
            pipAutoEnter = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    setPictureInPictureParams(
                        PictureInPicture.params(this, playbackViewModel.state.value.isPlaying, false, pipAcceptedRatio),
                    )
                }
            }
        }
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onStop() {
        if (pipSupported) runCatching { unregisterReceiver(pipReceiver) }
        playbackViewModel.setUiStarted(false)
        // Switching the launcher icon disables the alias this screen was opened through, and
        // Android closes it then: so the switch waits until the app is left. Also picks up a
        // restore that changed the setting.
        if (!isChangingConfigurations) {
            val icon = runCatching { AppIcon.valueOf(getSharedPreferences("ui", Context.MODE_PRIVATE).getString("appIcon", null)!!) }
                .getOrDefault(AppIcon.Purple)
            AppIcon.apply(this, icon)
        }
        super.onStop()
    }

    // Dialogs and the keyboard can bring the bars back; hide them again once the window is ours.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    /** The System bars setting: a swipe from the edge shows a hidden bar for a moment over the page. */
    internal fun applySystemBars(mode: SystemBarsMode = AppSettings(getSharedPreferences("ui", Context.MODE_PRIVATE)).systemBars) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (mode.showStatusBar) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
            if (mode.showNavigationBar) show(WindowInsetsCompat.Type.navigationBars()) else hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
