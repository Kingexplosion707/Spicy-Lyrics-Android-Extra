package com.tx24.spicyplayer.desktop.platform

import java.awt.Window

/** Windows 10/11's dark title bar for the app's window (DwmSetWindowAttribute through JNA). */
object WindowsTheme {
    fun darkTitleBar(window: Window) {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        runCatching {
            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(window))
            val dark = com.sun.jna.ptr.IntByReference(1)
            // DWMWA_USE_IMMERSIVE_DARK_MODE: 20 on current Windows, 19 on early Windows 10 builds.
            if (Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, 20, dark.pointer, 4) != 0) Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, 19, dark.pointer, 4)
            // DWMWA_SYSTEMBACKDROP_TYPE = 38: Mica on Windows 11.
            Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, 38, com.sun.jna.ptr.IntByReference(2).pointer, 4)
        }
    }

    private interface Dwm : com.sun.jna.Library {
        fun DwmSetWindowAttribute(hwnd: com.sun.jna.platform.win32.WinDef.HWND, attribute: Int, value: com.sun.jna.Pointer, size: Int): Int
        companion object { val INSTANCE: Dwm by lazy { com.sun.jna.Native.load("dwmapi", Dwm::class.java) } }
    }
}
