package com.skydex.app.updates

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hands a downloaded APK to Android's installer. */
interface ApkInstaller {
    /** True when the user allowed Skydex to install apps (Settings > Install unknown apps). */
    fun canInstall(): Boolean

    /** Creates a PackageInstaller session for [apk] and commits it; the outcome arrives in [UpdateInstallReceiver]. */
    suspend fun install(apk: File)

    /** Re-shows the system confirmation from the last STATUS_PENDING_USER_ACTION. False if there is none. */
    fun showConfirmation(): Boolean

    /** Abandons this app's install sessions left over from an earlier process. */
    fun abandonStaleSessions()
}

@Singleton
class PackageInstallerApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApkInstaller {
    @Volatile
    internal var pendingConfirmation: Intent? = null

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override suspend fun install(apk: File): Unit = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            // The session can only install Skydex, whatever the file turns out to be.
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("skydex.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // Mutable so the system can add the status extras; allowed because the intent is explicit.
                val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
                val pending = PendingIntent.getBroadcast(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(id)
            throw e
        }
    }

    /** Called by [UpdateInstallReceiver] when Android needs the user to confirm the install. */
    fun onConfirmationNeeded(confirm: Intent) {
        pendingConfirmation = confirm
        showConfirmation()
    }

    /** Called by [UpdateInstallReceiver] when the session ended, so a stale confirmation is never shown again. */
    fun onSessionFinished() {
        pendingConfirmation = null
    }

    override fun abandonStaleSessions() {
        val installer = context.packageManager.packageInstaller
        runCatching { installer.mySessions }.getOrDefault(emptyList())
            .filter { !it.isActive }
            .forEach { runCatching { installer.abandonSession(it.sessionId) } }
    }

    override fun showConfirmation(): Boolean {
        val confirm = pendingConfirmation ?: return false
        return try {
            context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("Skydex", "Could not show the install confirmation", e)
            false
        } catch (e: SecurityException) {
            Log.w("Skydex", "Could not show the install confirmation", e)
            false
        }
    }
}
