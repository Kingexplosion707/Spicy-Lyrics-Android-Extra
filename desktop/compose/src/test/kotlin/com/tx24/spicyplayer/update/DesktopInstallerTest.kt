package com.tx24.spicyplayer.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopInstallerTest {
    @Test
    fun `only this app's installers count as updates`() {
        assertTrue(UpdateViewModel.isInstaller("SpicyLyricsPlus-Desktop-1.0.12.msi"))
        assertTrue(UpdateViewModel.isInstaller("SpicyLyricsPlus-Desktop-1.0.12.exe"))
        // The Spicetify app's installers, and the first web build's.
        assertFalse(UpdateViewModel.isInstaller("SpicyLyricsPlus-Setup-1.18.exe"))
        assertFalse(UpdateViewModel.isInstaller("SpicyLyricsPlus-Desktop-Setup-1.0.3.exe"))
        assertFalse(UpdateViewModel.isInstaller("SpicyLyricsPlus-Desktop-1.0.12.exe.sha256"))
    }
}
