package com.tx24.spicyplayer.update

import android.content.pm.PackageInstaller
import com.tx24.spicyplayer.update.UpdateInstallReceiver.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallReceiverTest {
    private fun outcome(status: Int, visible: Boolean = true, message: String? = null) =
        UpdateInstallReceiver.outcome(status, visible, message)

    @Test
    fun `on view the installer's prompt opens at once`() {
        assertEquals(Outcome.Confirm, outcome(PackageInstaller.STATUS_PENDING_USER_ACTION, visible = true))
    }

    @Test
    fun `out of view the prompt waits for a tap`() {
        assertEquals(Outcome.ConfirmLater, outcome(PackageInstaller.STATUS_PENDING_USER_ACTION, visible = false))
    }

    @Test
    fun `a cancel and a success are told apart from failures`() {
        assertEquals(Outcome.Cancelled, outcome(PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(Outcome.Installed, outcome(PackageInstaller.STATUS_SUCCESS))
    }

    @Test
    fun `a signing clash explains how to get past it`() {
        listOf(PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE).forEach { status ->
            val failed = outcome(status, message = "INSTALL_FAILED_UPDATE_INCOMPATIBLE") as Outcome.Failed
            assertTrue(failed.reason.startsWith("Android refused the update"))
        }
    }

    @Test
    fun `other failures give the installer's message or a fallback`() {
        assertEquals(Outcome.Failed("No space"), outcome(PackageInstaller.STATUS_FAILURE_STORAGE, message = "No space"))
        assertEquals(Outcome.Failed("The update wasn't installed."), outcome(PackageInstaller.STATUS_FAILURE, message = null))
    }
}
