package com.skydex.app.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** PackageInstaller.ACTION_CONFIRM_INSTALL, which is a system API. */
internal const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"

/** Only the system's install confirmation may be launched from a session's STATUS_PENDING_USER_ACTION. */
internal fun isInstallConfirmation(action: String?): Boolean = action == ACTION_CONFIRM_INSTALL

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updateManager(): UpdateManager

    fun installer(): PackageInstallerApkInstaller
}

/**
 * Receives PackageInstaller session results. Uses an EntryPoint instead of @AndroidEntryPoint to avoid Hilt's rules
 * about receivers calling super.onReceive.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entry = EntryPointAccessors.fromApplication(context, UpdateEntryPoint::class.java)
        val updates = entry.updateManager()
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) entry.installer().onSessionFinished()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                // Only ever launch the system's install confirmation, never some other activity.
                if (confirm != null && isInstallConfirmation(confirm.action)) {
                    entry.installer().onConfirmationNeeded(confirm)
                    updates.onInstallResult(InstallResult.NeedsUser)
                } else {
                    updates.onInstallResult(InstallResult.Failed("unexpected confirmation request"))
                }
            }
            // Usually the update kills this process before this arrives.
            PackageInstaller.STATUS_SUCCESS -> updates.onInstallResult(InstallResult.Success)
            PackageInstaller.STATUS_FAILURE_ABORTED -> updates.onInstallResult(InstallResult.Cancelled)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> updates.onInstallResult(InstallResult.Conflict)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> updates.onInstallResult(InstallResult.Incompatible)
            PackageInstaller.STATUS_FAILURE_STORAGE -> updates.onInstallResult(InstallResult.Storage)
            else -> updates.onInstallResult(InstallResult.Failed(message))
        }
    }
}
