package com.skydex.app.updates

import com.skydex.app.FakeServer
import com.skydex.app.TestStore
import com.skydex.app.respondJson
import com.skydex.app.sampleRelease
import com.skydex.shared.model.AppRelease
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateManagerTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = FakeServer()
    private val testStore by lazy { TestStore(folder) }
    private val store get() = testStore.store
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updatesDir by lazy { folder.newFolder("updates") }

    private val apkBytes = ByteArray(50_000) { (it % 97).toByte() }
    private val release: AppRelease = sampleRelease.copy(
        sizeBytes = apkBytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(apkBytes).joinToString("") { "%02x".format(it) },
    )

    /** Download requests; [downloadGate] (when set) holds the APK response until counted down. */
    private val downloads = AtomicInteger()
    @Volatile private var downloadGate: CountDownLatch? = null
    @Volatile private var downloadFailure: Throwable? = null

    private val downloader by lazy {
        UpdateDownloader(
            createDownloadClient(
                MockEngine {
                    downloads.incrementAndGet()
                    downloadGate?.await(10, TimeUnit.SECONDS)
                    downloadFailure?.let { throw it }
                    respond(apkBytes, HttpStatusCode.OK)
                },
            ),
            updatesDir,
        )
    }

    private val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private inner class FakeInstaller : ApkInstaller {
        @Volatile var allowed = true
        @Volatile var confirmationShown = true
        @Volatile var installGate: CompletableDeferred<Unit>? = null
        @Volatile var installFailure: Exception? = null
        @Volatile var abandonGate: CountDownLatch? = null
        val installed: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())
        var installedFile: File? = null

        override fun canInstall() = allowed

        override suspend fun install(apk: File) {
            events += "install"
            installedFile = apk
            installed += apk.readBytes()
            installGate?.await()
            installFailure?.let { throw it }
        }

        override fun showConfirmation() = confirmationShown

        override fun abandonStaleSessions() {
            events += "abandon-start"
            abandonGate?.await(10, TimeUnit.SECONDS)
            events += "abandon-end"
        }
    }

    private val installer = FakeInstaller()

    private fun manager(appInfo: AppInfo = AppInfo("0.0.1", 1, checkUpdatesOnLaunch = false)) =
        UpdateManager(server.api, store, downloader, installer, appInfo, scope)

    private fun serve(release: AppRelease?) {
        server.handler = { if (release == null) respond("", HttpStatusCode.NoContent) else respondJson(release) }
    }

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    private fun UpdateManager.awaitState(predicate: (UpdateState) -> Boolean): UpdateState =
        await { state.first(predicate) }

    /** Waits until the install step is [expected] (state is collected on another thread). */
    private fun UpdateManager.awaitStep(expected: InstallStep): InstallStep = awaitState { it.install == expected }.install

    /** The state once pending updates have propagated (for checks that something did NOT change). */
    private val UpdateManager.settled: UpdateState
        get() {
            Thread.sleep(200)
            return state.value
        }

    /** A manager that has found [release] through a manual check. */
    private fun available(appInfo: AppInfo = AppInfo("0.0.1", 1, false)): UpdateManager {
        serve(release)
        return manager(appInfo).apply {
            check()
            awaitState { it.available != null }
        }
    }

    @After fun tearDown() {
        scope.cancel()
        testStore.close()
    }

    @Test
    fun `launch check runs once and is off when the build doesn't check on launch`() {
        serve(release)
        File(updatesDir, "skydex-1.apk").writeText("old")

        val off = manager()
        off.checkOnLaunch()
        await { while (events.count { it == "abandon-end" } < 1) delay(10) }
        assertTrue("old downloads are cleared", await { while (updatesDir.list()!!.isNotEmpty()) delay(10); true })
        assertEquals(0, server.requests.size)
        assertFalse(off.settled.checked)

        val on = manager(AppInfo("0.0.1", 1, checkUpdatesOnLaunch = true))
        on.checkOnLaunch()
        on.checkOnLaunch()
        on.awaitState { it.checked }
        assertEquals(1, server.requests.size)
        assertEquals("/v1/app/latest", server.requests.single().url.encodedPath)
        // Once per manager (process): the second call did no second cleanup either.
        await { while (events.count { it == "abandon-end" } < 2) delay(10) }
        Thread.sleep(100)
        assertEquals(2, events.count { it == "abandon-start" })
    }

    @Test
    fun `a newer release is offered with the banner`() {
        val updates = available()
        val state = updates.settled
        assertEquals(release, state.available)
        assertTrue(state.checked)
        assertTrue(state.showBanner)
        assertTrue(state.canInstallInApp)
        assertNull(state.checkError)
        assertEquals("0.0.1", state.installedVersion)
    }

    @Test
    fun `versionCode comparisons decide what counts as newer`() {
        fun offered(installed: AppInfo, offered: AppRelease): Boolean {
            serve(offered)
            val updates = manager(installed)
            updates.check()
            return updates.awaitState { it.checked && !it.checking }.available != null
        }
        val debug = AppInfo("0.0.1", 1, false)
        assertTrue(offered(debug, sampleRelease.copy(versionName = "0.0.2", versionCode = 2)))
        assertFalse(offered(debug, sampleRelease.copy(versionName = "0.0.1", versionCode = 1)))
        val installed = AppInfo("1.2.3", 10203, false)
        assertFalse("same version", offered(installed, release))
        assertFalse("older version", offered(installed, release.copy(versionName = "1.2.2", versionCode = 10202)))
        assertTrue(offered(installed, release.copy(versionName = "1.3.0", versionCode = 10300)))
        // 1.10.0 is newer than 1.9.99 even though "1.10.0" < "1.9.99" as text.
        assertTrue(offered(AppInfo("1.9.99", 10999, false), release.copy(versionName = "1.10.0", versionCode = 11000)))
        assertFalse("name/code mismatch", offered(debug, release.copy(versionCode = 10204)))
        assertFalse("bad name", offered(debug, release.copy(versionName = "1.2.3-beta")))
    }

    @Test
    fun `skipping persists, hides the banner, and Settings still offers the release`() {
        val first = available()
        first.skip(release.versionCode)
        first.awaitState { it.skippedVersionCode == release.versionCode }
        assertFalse(first.settled.showBanner)
        assertEquals(release.versionCode, await { store.skippedUpdateVersionCode.first { it != null } })

        // A new process reading the same store.
        val second = available()
        val state = second.awaitState { it.skippedVersionCode == release.versionCode }
        assertEquals(release, state.available)
        assertFalse(state.showBanner)

        // A newer release than the skipped one shows again.
        val newer = release.copy(versionName = "1.2.4", versionCode = 10204)
        serve(newer)
        second.check()
        assertTrue(second.awaitState { it.available == newer }.showBanner)
    }

    @Test
    fun `dismissing the banner affects only this instance`() {
        val first = available()
        first.dismissBanner()
        assertFalse(first.settled.showBanner)
        assertNotNull(first.settled.available)
        assertTrue(available().settled.showBanner)
    }

    @Test
    fun `dismissing a failed install clears it`() {
        installer.allowed = false
        val updates = available()
        updates.download()
        updates.onPermissionResult()
        updates.awaitState { it.install is InstallStep.Failed }
        assertTrue(updates.settled.showBanner)

        updates.dismissBanner()

        val state = updates.settled
        assertEquals(InstallStep.Idle, state.install)
        assertFalse(state.showBanner)
    }

    @Test
    fun `launch check failures are silent but manual ones are shown`() {
        server.handler = { respondJson("""{"message":"Upstream service unavailable"}""", HttpStatusCode.BadGateway) }
        val launch = manager(AppInfo("0.0.1", 1, checkUpdatesOnLaunch = true))
        launch.checkOnLaunch()
        await { while (server.requests.isEmpty() || launch.settled.checking) delay(10) }
        assertNull(launch.settled.checkError)

        launch.check()
        assertEquals("Upstream service unavailable", launch.awaitState { it.checkError != null }.checkError)
    }

    @Test
    fun `a manual check during the launch check reports its error`() {
        val gate = CountDownLatch(1)
        server.handler = {
            gate.await(10, TimeUnit.SECONDS)
            respondJson("""{"message":"Upstream service unavailable"}""", HttpStatusCode.BadGateway)
        }
        val updates = manager(AppInfo("0.0.1", 1, checkUpdatesOnLaunch = true))
        updates.checkOnLaunch()
        updates.awaitState { it.checking }
        updates.check()
        gate.countDown()

        assertEquals("Upstream service unavailable", updates.awaitState { it.checkError != null }.checkError)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun `a manual check during the launch check reports up to date`() {
        val gate = CountDownLatch(1)
        server.handler = {
            gate.await(10, TimeUnit.SECONDS)
            respond("", HttpStatusCode.NoContent)
        }
        val updates = manager(AppInfo("0.0.1", 1, checkUpdatesOnLaunch = true))
        updates.checkOnLaunch()
        updates.awaitState { it.checking }
        updates.check()
        gate.countDown()

        val state = updates.awaitState { it.checked && !it.checking }
        assertNull(state.available)
        assertNull(state.checkError)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun `download asks for permission, then downloads, installs and waits for the user`() {
        installer.allowed = false
        val updates = available()
        updates.download()
        assertEquals(InstallStep.NeedsPermission, updates.awaitStep(InstallStep.NeedsPermission))
        assertEquals(0, downloads.get())

        installer.allowed = true
        downloadGate = CountDownLatch(1)
        installer.installGate = CompletableDeferred()
        updates.onPermissionResult()
        assertEquals(InstallStep.Downloading(0, release.sizeBytes), updates.awaitState { it.install is InstallStep.Downloading }.install)
        assertTrue(updates.settled.showBanner)
        downloadGate!!.countDown()
        updates.awaitState { it.install == InstallStep.Installing }
        installer.installGate!!.complete(Unit)
        updates.awaitState { it.install == InstallStep.WaitingForUser }

        assertEquals(1, installer.installed.size)
        assertTrue(installer.installed.single().contentEquals(apkBytes))
        assertEquals(1, downloads.get())
        // The committed session holds its own copy, so the downloaded APK is gone.
        assertFalse(installer.installedFile!!.exists())
        assertEquals(emptyList<String>(), updatesDir.list()!!.toList())
    }

    @Test
    fun `the APK is deleted even when the install fails`() {
        installer.installFailure = IOException("session broke")
        val updates = available()
        updates.download()
        val failed = updates.awaitState { it.install is InstallStep.Failed }.install as InstallStep.Failed
        assertEquals("Download failed. Check your connection and try again.", failed.message)
        assertFalse(installer.installedFile!!.exists())
    }

    @Test
    fun `download failures show a message`() {
        downloadFailure = IOException("offline")
        val updates = available()
        updates.download()
        assertEquals(
            InstallStep.Failed("Download failed. Check your connection and try again."),
            updates.awaitState { it.install is InstallStep.Failed }.install,
        )
        assertTrue(installer.installed.isEmpty())
    }

    @Test
    fun `a verification failure shows the downloader's message`() {
        serve(release.copy(sha256 = "f".repeat(64)))
        val updates = manager()
        updates.check()
        updates.awaitState { it.available != null }
        updates.download()
        assertEquals(
            InstallStep.Failed("The download was corrupted. Try again."),
            updates.awaitState { it.install is InstallStep.Failed }.install,
        )
        assertTrue(installer.installed.isEmpty())
    }

    @Test
    fun `denying the permission fails`() {
        installer.allowed = false
        val updates = available()
        updates.download()
        updates.onPermissionResult()
        assertEquals(InstallStep.Failed("Allow Skydex to install apps to update it."), updates.awaitStep(InstallStep.Failed("Allow Skydex to install apps to update it.")))
        assertEquals(0, downloads.get())
    }

    @Test
    fun `without a hash the release can't be installed in-app`() {
        serve(release.copy(sha256 = null))
        val updates = manager()
        updates.check()
        val state = updates.awaitState { it.available != null }
        assertFalse(state.canInstallInApp)
        assertTrue(state.showBanner)

        updates.download()
        Thread.sleep(100)
        assertEquals(InstallStep.Idle, updates.settled.install)
        assertEquals(0, downloads.get())
    }

    @Test
    fun `install results map to steps`() {
        val updates = available()
        updates.onInstallResult(InstallResult.NeedsUser)
        assertEquals(InstallStep.WaitingForUser, updates.awaitStep(InstallStep.WaitingForUser))
        updates.onInstallResult(InstallResult.Cancelled)
        assertEquals(InstallStep.Idle, updates.awaitStep(InstallStep.Idle))
        updates.onInstallResult(InstallResult.Conflict)
        assertTrue((updates.awaitState { it.install is InstallStep.Failed }.install as InstallStep.Failed).message.contains("different key"))
        updates.onInstallResult(InstallResult.Incompatible)
        assertEquals(InstallStep.Failed("This version can't be installed on this device."), updates.awaitStep(InstallStep.Failed("This version can't be installed on this device.")))
        updates.onInstallResult(InstallResult.Storage)
        assertEquals(InstallStep.Failed("Not enough storage to install the update."), updates.awaitStep(InstallStep.Failed("Not enough storage to install the update.")))
        updates.onInstallResult(InstallResult.Failed("boom"))
        assertEquals(InstallStep.Failed("Install failed: boom"), updates.awaitStep(InstallStep.Failed("Install failed: boom")))
        updates.onInstallResult(InstallResult.Success)
        assertEquals(InstallStep.Idle, updates.awaitStep(InstallStep.Idle))
    }

    @Test
    fun `showing the confirmation again fails when there is none`() {
        val updates = available()
        installer.confirmationShown = true
        updates.showConfirmation()
        assertEquals(InstallStep.Idle, updates.settled.install)
        installer.confirmationShown = false
        updates.showConfirmation()
        assertEquals(InstallStep.Failed("Tap Download to try again."), updates.awaitStep(InstallStep.Failed("Tap Download to try again.")))
    }

    @Test
    fun `cancelling a download returns to idle and leaves no file`() {
        downloadGate = CountDownLatch(1)
        val updates = available()
        updates.download()
        updates.awaitState { it.install is InstallStep.Downloading }
        await { while (downloads.get() == 0) delay(10) }

        updates.cancelDownload()
        downloadGate!!.countDown()

        assertEquals(InstallStep.Idle, updates.awaitStep(InstallStep.Idle))
        Thread.sleep(200)
        assertEquals(InstallStep.Idle, updates.settled.install)
        assertTrue(installer.installed.isEmpty())
        assertEquals(emptyList<String>(), updatesDir.list()!!.toList())
    }

    @Test
    fun `a download right after launch waits for the stale-session cleanup`() {
        installer.abandonGate = CountDownLatch(1)
        val updates = available()
        updates.checkOnLaunch()
        await { while ("abandon-start" !in events) delay(10) }

        updates.download()
        Thread.sleep(300)
        assertEquals("nothing is downloaded or installed during the cleanup", 0, downloads.get())
        assertFalse("install" in events)

        installer.abandonGate!!.countDown()
        updates.awaitState { it.install == InstallStep.WaitingForUser }
        assertEquals(listOf("abandon-start", "abandon-end", "install"), events.toList())
    }

    @Test
    fun `only the system install confirmation is launched`() {
        assertTrue(isInstallConfirmation("android.content.pm.action.CONFIRM_INSTALL"))
        assertFalse(isInstallConfirmation(null))
        assertFalse(isInstallConfirmation("android.intent.action.VIEW"))
        assertFalse(isInstallConfirmation("android.content.pm.action.CONFIRM_INSTALL "))
        assertFalse(isInstallConfirmation("android.content.pm.action.CONFIRM_PERMISSIONS"))
    }
}
