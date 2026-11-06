package com.tx24.spicyplayer.wallpaper

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Draws Compose content into a live wallpaper's surface, hardware-accelerated and paced by the
 * display like any window: a private virtual display shows the wallpaper's surface, and a
 * [Presentation] on it holds the content. That lets the wallpaper use the app's own composables
 * (the lyrics renderer, the looping video views) instead of copies drawn by hand.
 *
 * [setActive] moves the content's lifecycle between started (the loops play) and created (they
 * hold their frame). Main thread only.
 */
internal class WallpaperHost(
    context: Context,
    surface: Surface,
    width: Int,
    height: Int,
    private val densityDpi: Int,
    content: @Composable () -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    private val display: VirtualDisplay
    private val presentation: Presentation
    private var released = false

    init {
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        val manager = context.getSystemService(DisplayManager::class.java)
        display = manager.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME, width, height, densityDpi, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        ) ?: error("No virtual display for the wallpaper")
        try {
            presentation = Presentation(context, display.display).apply {
                window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                val view = ComposeView(this.context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent(content)
                }
                setContentView(view)
                window?.decorView?.let { decor ->
                    decor.setViewTreeLifecycleOwner(this@WallpaperHost)
                    decor.setViewTreeSavedStateRegistryOwner(this@WallpaperHost)
                    decor.setViewTreeViewModelStoreOwner(this@WallpaperHost)
                }
                show()
            }
        } catch (e: RuntimeException) {
            display.release()
            throw e
        }
    }

    /** The wallpaper's surface changed size. */
    fun resize(width: Int, height: Int) {
        if (released || width <= 0 || height <= 0) return
        display.resize(width, height, densityDpi)
    }

    /** Started while the wallpaper shows and the music plays: the loops play; otherwise they hold. */
    fun setActive(active: Boolean) {
        if (released) return
        lifecycleRegistry.currentState = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }

    fun release() {
        if (released) return
        released = true
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        runCatching { presentation.dismiss() }
        display.release()
        viewModelStore.clear()
    }

    private companion object {
        const val VIRTUAL_DISPLAY_NAME = "SpicyWallpaper"
    }
}
