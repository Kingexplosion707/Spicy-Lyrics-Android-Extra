package com.tx24.spicyplayer.desktop.platform

import java.util.concurrent.CopyOnWriteArrayList

/** Escape (and the mouse's back button) as Android's back: the latest enabled handler takes it. */
object BackDispatcher {
    class Entry(val onBack: () -> Unit) { @Volatile var enabled = true }
    private val entries = CopyOnWriteArrayList<Entry>()
    fun push(e: Entry) { entries += e }
    fun remove(e: Entry) { entries -= e }
    fun back(): Boolean { val e = entries.lastOrNull { it.enabled } ?: return false; e.onBack(); return true }
}
