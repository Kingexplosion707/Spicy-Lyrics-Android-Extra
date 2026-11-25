package com.tx24.spicyplayer.desktop.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * Registers the spicylyricsplus:// links for this user, so Spotify's sign-in comes back to the app
 * (the same redirect URI as the Android app). Only from the installed app, not a development run.
 */
object WindowsIntegration {
    fun register() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val exe = ProcessHandle.current().info().command().orElse(null) ?: return
        if (exe.endsWith("java.exe", ignoreCase = true) || exe.endsWith("javaw.exe", ignoreCase = true)) return
        runCatching {
            val root = "Software\\Classes\\spicylyricsplus"
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, "$root\\shell\\open\\command")
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, root, "", "URL:Spicy Lyrics Plus")
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, root, "URL Protocol", "")
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, "$root\\shell\\open\\command", "", "\"$exe\" \"%1\"")
        }
    }
}
