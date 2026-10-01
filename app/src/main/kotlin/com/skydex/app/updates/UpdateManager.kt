package com.skydex.app.updates

import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.remote.SkydexApi
import com.skydex.app.di.ApplicationScope
import com.skydex.app.ui.common.userMessage
import com.skydex.shared.model.AppRelease
import com.skydex.shared.model.AppVersion
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Android reported for an install session. */
sealed interface InstallResult {
    data object NeedsUser : InstallResult

    data object Success : InstallResult

    data object Cancelled : InstallResult

    data object Conflict : InstallResult

    data object Incompatible : InstallResult

    data object Storage : InstallResult

    data class Failed(val message: String?) : InstallResult
}

/** Where an in-app update is. */
sealed interface InstallStep {
    data object Idle : InstallStep

    /** The UI shows the "Install unknown apps" dialog. */
    data object NeedsPermission : InstallStep

    data class Downloading(val bytesRead: Long, val total: Long) : InstallStep

    /** The install session is being written and committed. */
    data object Installing : InstallStep

    /** The system confirmation is shown; the UI offers "Install" to show it again. */
    data object WaitingForUser : InstallStep

    data class Failed(val message: String) : InstallStep
}

data class UpdateState(
    val installedVersion: String,
    val checking: Boolean = false,
    /** A check finished (Settings shows "You're up to date"). */
    val checked: Boolean = false,
    /** Manual checks only; launch checks fail silently. */
    val checkError: String? = null,
    /** Only when newer than the installed version. */
    val available: AppRelease? = null,
    val skippedVersionCode: Int? = null,
    /** The banner's close button: this process only. */
    val bannerDismissed: Boolean = false,
    val install: InstallStep = InstallStep.Idle,
) {
    val showBanner: Boolean
        get() = available != null &&
            (install != InstallStep.Idle || (!bannerDismissed && available.versionCode != skippedVersionCode))

    /** False when the server gave no hash: offer the release page instead. */
    val canInstallInApp: Boolean get() = available?.sha256 != null
}

/** The single source of truth for update checks and in-app installs. Jobs run in the app scope, so they survive navigation. */
@Singleton
class UpdateManager @Inject constructor(
    private val api: SkydexApi,
    private val store: SettingsStore,
    private val downloader: UpdateDownloader,
    private val installer: ApkInstaller,
    private val appInfo: AppInfo,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow(UpdateState(appInfo.versionName))
    private val launched = AtomicBoolean(false)
    private var downloadJob: Job? = null

    /** The launch cleanup; a download waits for it so a new file or install session is never cleared. */
    @Volatile
    private var cleanup: Job? = null

    /** Guards [reportCheckError] and the start and failure of checks. */
    private val checkLock = Any()

    /** Whether the running check shows its error: true once the user asked for a check. */
    private var reportCheckError = false

    val state: StateFlow<UpdateState> = combine(current, store.skippedUpdateVersionCode) { state, skipped ->
        state.copy(skippedVersionCode = skipped)
    }.stateIn(scope, SharingStarted.Eagerly, current.value)

    /** Once per process: clears old downloads and install sessions, then checks if this build checks on launch. */
    fun checkOnLaunch() {
        if (!launched.compareAndSet(false, true)) return
        cleanup = scope.launch {
            runCatching { downloader.clear() }
            runCatching { installer.abandonStaleSessions() }
        }
        if (appInfo.checkUpdatesOnLaunch) check(manual = false)
    }

    /** A check the user asked for. While one is already running, that check's result (or error) is shown instead. */
    fun check() = check(manual = true)

    private fun check(manual: Boolean) {
        synchronized(checkLock) {
            if (current.value.checking) {
                if (manual) reportCheckError = true
                return
            }
            reportCheckError = manual
            current.update { it.copy(checking = true, checkError = null) }
        }
        scope.launch {
            try {
                val release = api.latestRelease()?.takeIf(::isNewer)
                current.update {
                    // Keep the release an install is already working on.
                    val available = if (it.install == InstallStep.Idle) release else it.available
                    it.copy(checking = false, checked = true, available = available)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(checkLock) {
                    val message = if (reportCheckError) e.userMessage() else null
                    current.update { it.copy(checking = false, checkError = message) }
                }
            }
        }
    }

    private fun isNewer(release: AppRelease): Boolean =
        release.versionCode > appInfo.versionCode &&
            AppVersion.parse(release.versionName)?.versionCode == release.versionCode

    /** Closes the banner; a failed install is cleared too, so the banner really goes away. */
    fun dismissBanner() = current.update {
        it.copy(bannerDismissed = true, install = if (it.install is InstallStep.Failed) InstallStep.Idle else it.install)
    }

    fun skip(versionCode: Int) {
        scope.launch { store.skipUpdate(versionCode) }
    }

    /** Downloads and installs the available release, asking for the install permission first if needed. */
    fun download() {
        val release = current.value.available ?: return
        if (release.sha256 == null || downloadJob?.isActive == true) return
        if (!installer.canInstall()) {
            setStep(InstallStep.NeedsPermission)
            return
        }
        setStep(InstallStep.Downloading(0, release.sizeBytes))
        downloadJob = scope.launch {
            try {
                cleanup?.join()
                val apk = downloader.download(release) { read, total -> setStep(InstallStep.Downloading(read, total)) }
                setStep(InstallStep.Installing)
                // The committed session holds its own copy of the APK.
                try {
                    installer.install(apk)
                } finally {
                    apk.delete()
                }
                // The receiver may have reported a result already.
                current.update { if (it.install == InstallStep.Installing) it.copy(install = InstallStep.WaitingForUser) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is UpdateException -> e.message ?: "Download failed."
                    is IOException -> "Download failed. Check your connection and try again."
                    else -> e.userMessage()
                }
                setStep(InstallStep.Failed(message))
            }
        }
    }

    /** The user came back from the "Install unknown apps" settings screen. */
    fun onPermissionResult() {
        if (installer.canInstall()) {
            setStep(InstallStep.Idle)
            download()
        } else {
            setStep(InstallStep.Failed("Allow Skydex to install apps to update it."))
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        setStep(InstallStep.Idle)
    }

    fun showConfirmation() {
        if (!installer.showConfirmation()) setStep(InstallStep.Failed("Tap Download to try again."))
    }

    fun onInstallResult(result: InstallResult) {
        val step = when (result) {
            InstallResult.NeedsUser -> InstallStep.WaitingForUser
            InstallResult.Success, InstallResult.Cancelled -> InstallStep.Idle
            InstallResult.Conflict -> InstallStep.Failed(
                "This copy of Skydex was signed with a different key (for example a debug build). " +
                    "Uninstall it, then install the new version from GitHub.",
            )
            InstallResult.Incompatible -> InstallStep.Failed("This version can't be installed on this device.")
            InstallResult.Storage -> InstallStep.Failed("Not enough storage to install the update.")
            is InstallResult.Failed -> InstallStep.Failed("Install failed: ${result.message}")
        }
        setStep(step)
    }

    private fun setStep(step: InstallStep) = current.update { it.copy(install = step) }
}
