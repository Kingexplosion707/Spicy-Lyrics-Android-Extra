package com.tx24.spicyplayer.widget

/**
 * Whether the widget can be seen: not with the screen off, nor while the lyrics screen fills the
 * display. Nothing is sent to it then; the first send once it can be seen again is in full.
 */
internal class WidgetVisibility {
    var screenOn = true
    /** The lyrics screen is in front and full size (not in the floating window). */
    var appCovering = false
    /** Something changed while it couldn't be seen that it hasn't been sent yet. */
    var missed = false
        private set

    val visible: Boolean get() = screenOn && !appCovering

    /** Whether to send now in full ([full], or something was missed); null while it can't be seen. */
    fun send(full: Boolean): Boolean? {
        if (!visible) {
            missed = true
            return null
        }
        return (full || missed).also { missed = false }
    }
}
