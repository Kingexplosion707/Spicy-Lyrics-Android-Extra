package com.tx24.spicyplayer.widget

/**
 * Holds the widget's own lyric lookups back while the screen is off, so songs that play
 * unseen aren't looked up, and gives back the one held back once the screen comes on.
 */
internal class ScreenOffLookup {
    /** A lookup was held back and hasn't been made or made pointless since. */
    var held = false
        private set

    /** Whether to look up now; with the screen off it's held back for [screenOn]. */
    fun request(screenOn: Boolean): Boolean {
        held = !screenOn
        return screenOn
    }

    /** The screen came on: whether a held-back lookup should be made now. */
    fun screenOn(): Boolean = held.also { held = false }

    /** The song changed or got its lyrics another way: nothing is owed. */
    fun clear() {
        held = false
    }
}
