package com.tx24.spicyplayer.ui.settings

/** Which of the phone's own bars stay on screen over the lyrics. A swipe from the edge shows a hidden one for a moment. */
enum class SystemBarsMode(val label: String, val showStatusBar: Boolean, val showNavigationBar: Boolean) {
    HideBoth("Hide both", showStatusBar = false, showNavigationBar = false),
    ShowStatusBar("Show the status bar", showStatusBar = true, showNavigationBar = false),
    ShowNavigationBar("Show the navigation bar", showStatusBar = false, showNavigationBar = true),
    ShowBoth("Show both", showStatusBar = true, showNavigationBar = true),
}
