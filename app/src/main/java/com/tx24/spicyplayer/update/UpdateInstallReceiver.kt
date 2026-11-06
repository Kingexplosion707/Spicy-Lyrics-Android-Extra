package com.tx24.spicyplayer.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.tx24.spicyplayer.SpicyLyricsApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where Android's installer reports on an update. It first asks for the user's confirmation,
 * which this opens while the app is on view. Out of view Android won't let it, so the prompt
 * waits behind a notification and the app's own "Install" ([pending]). A success replaces the
 * app, so only a failure or a cancel comes back here.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    sealed interface Outcome {
        /** Open the installer's prompt now. */
        data object Confirm : Outcome
        /** Keep the prompt for a tap: the app is out of view. */
        data object ConfirmLater : Outcome
        data object Installed : Outcome
        data object Cancelled : Outcome
        data class Failed(val reason: String) : Outcome
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val outcome = outcome(status, SpicyLyricsApplication.visible, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        if (outcome == Outcome.Confirm || outcome == Outcome.ConfirmLater) {
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            } ?: return
            if (outcome == Outcome.Confirm) {
                mutablePending.value = null
                UpdateNotification.cancel(context)
                context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                mutablePending.value = confirm
                UpdateNotification.show(context, confirm)
            }
            return
        }
        mutablePending.value = null
        UpdateNotification.cancel(context)
        when (outcome) {
            Outcome.Cancelled -> mutableResults.tryEmit(null)
            is Outcome.Failed -> mutableResults.tryEmit(outcome.reason)
            else -> Unit
        }
    }

    companion object {
        private val mutableResults = MutableSharedFlow<String?>(extraBufferCapacity = 1)
        /** A cancelled install (null) or why one failed. */
        val results: SharedFlow<String?> get() = mutableResults

        private val mutablePending = MutableStateFlow<Intent?>(null)
        /** The installer's prompt, held while the app was out of view, until it's opened or the install ends. */
        val pending: StateFlow<Intent?> get() = mutablePending

        /** Hands back the held prompt for opening, once. */
        fun takePending(context: Context): Intent? {
            UpdateNotification.cancel(context)
            return mutablePending.value.also { mutablePending.value = null }
        }

        /** What the installer's [status] report means, with the app on view ([visible]) or not. */
        fun outcome(status: Int, visible: Boolean, message: String?): Outcome = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> if (visible) Outcome.Confirm else Outcome.ConfirmLater
            PackageInstaller.STATUS_SUCCESS -> Outcome.Installed
            PackageInstaller.STATUS_FAILURE_ABORTED -> Outcome.Cancelled
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Outcome.Failed(
                "Android refused the update: it's signed differently from the installed app. " +
                    "Uninstall this build, then install the new one from GitHub.",
            )
            else -> Outcome.Failed(message ?: "The update wasn't installed.")
        }
    }
}
