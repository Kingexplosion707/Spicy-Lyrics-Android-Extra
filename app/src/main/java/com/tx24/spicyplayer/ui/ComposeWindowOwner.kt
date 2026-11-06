package com.tx24.spicyplayer.ui

import android.view.View
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
 * What a ComposeView needs from its window when no activity hosts it (an overlay window a
 * service adds): a lifecycle, saved state and view models. [attach] hands them to a view tree;
 * [setActive] moves the lifecycle between resumed and created; [destroy] ends it. Main thread only.
 */
internal class ComposeWindowOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun attach(root: View) {
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
    }

    fun setActive(active: Boolean) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
