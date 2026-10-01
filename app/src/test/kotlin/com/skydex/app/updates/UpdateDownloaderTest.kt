package com.skydex.app.updates

import com.skydex.app.sampleRelease
import com.skydex.shared.model.AppRelease
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val START = "https://github.com/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk"
private const val CDN = "https://objects.githubusercontent.com/github-production-release-asset/1/2?sig=SECRET&exp=1"

class UpdateDownloaderTest {
    @get:Rule val folder = TemporaryFolder()

    private val dir by lazy { File(folder.root, "updates") }
    private val apkBytes = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val requests = mutableListOf<Url>()

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun release(bytes: ByteArray = apkBytes, url: String = START, version: String = "1.2.3") =
        sampleRelease.copy(versionName = version, downloadUrl = url, sizeBytes = bytes.size.toLong(), sha256 = sha256(bytes))

    private fun downloader(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        UpdateDownloader(
            createDownloadClient(
                MockEngine { request ->
                    requests += request.url
                    handler(request)
                },
            ),
            dir,
        )

    private fun MockRequestHandleScope.redirect(location: String) =
        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))

    private fun MockRequestHandleScope.apk(bytes: ByteArray = apkBytes, contentLength: Long? = bytes.size.toLong()) =
        respond(
            bytes,
            HttpStatusCode.OK,
            if (contentLength == null) headersOf() else headersOf(HttpHeaders.ContentLength, "$contentLength"),
        )

    /** Redirects the start URL to the CDN, which serves [body]. */
    private fun githubThenCdn(body: ByteArray = apkBytes, contentLength: Long? = body.size.toLong()) =
        downloader { request ->
            when (request.url.toString()) {
                START -> redirect(CDN)
                CDN -> apk(body, contentLength)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

    private fun files() = dir.listFiles()?.map { it.name }.orEmpty()

    private fun assertFails(release: AppRelease, downloader: UpdateDownloader, message: String? = null) = runBlocking {
        try {
            downloader.download(release) { _, _ -> }
            fail("expected UpdateException")
        } catch (e: UpdateException) {
            if (message != null) assertEquals(message, e.message)
        }
        assertEquals("no partial or complete file is left", emptyList<String>(), files())
    }

    @Test
    fun `downloads through the CDN redirect and verifies the file`() = runBlocking {
        val progress = mutableListOf<Pair<Long, Long>>()

        val file = githubThenCdn().download(release()) { read, total -> progress += read to total }

        assertArrayEquals(apkBytes, file.readBytes())
        assertEquals("skydex-10203.apk", file.name)
        assertEquals(listOf("skydex-10203.apk"), files())
        assertEquals(listOf(Url(START), Url(CDN)), requests)
        assertEquals(apkBytes.size.toLong() to apkBytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b.first > a.first })
        assertTrue(progress.all { it.second == apkBytes.size.toLong() })
    }

    @Test
    fun `a missing Content-Length is fine`() = runBlocking {
        val file = githubThenCdn(contentLength = null).download(release()) { _, _ -> }
        assertArrayEquals(apkBytes, file.readBytes())
    }

    @Test
    fun `a relative Location resolves against the current hop without its query or fragment`() = runBlocking {
        val downloader = downloader { request ->
            when (request.url.encodedPath) {
                "/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk" ->
                    redirect("https://objects.githubusercontent.com/a/b?sig=SECRET#frag")
                "/a/b" -> redirect("/c/d")
                "/c/d" -> redirect("/e?x=1")
                "/e" -> apk()
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        downloader.download(release()) { _, _ -> }

        assertEquals(
            listOf(
                START,
                // MockEngine shows the fragment it was given; OkHttp never sends it.
                "https://objects.githubusercontent.com/a/b?sig=SECRET#frag",
                "https://objects.githubusercontent.com/c/d",
                "https://objects.githubusercontent.com/e?x=1",
            ),
            requests.map { it.toString() },
        )
        assertFalse(requests.drop(2).any { "SECRET" in it.toString() || it.fragment.isNotEmpty() })
    }

    @Test
    fun `unsafe releases are rejected before any request`() {
        val bad = listOf(
            release(url = START.replace("https://", "http://")),
            release(url = START.replace("github.com", "evil.com")),
            release(url = START.replace("github.com", "github.com.evil.com")),
            release(url = START.replace("github.com", "objects.githubusercontent.com")),
            release(url = START.replace("github.com", "github.com:8443")),
            release(url = START.replace("github.com", "user:pass@github.com")),
            release(url = START.replace("github.com", "user@github.com")),
            release(url = START.replace("MikaelNineza/Skydex", "Evil/Skydex")),
            release(url = START.replace("v1.2.3", "v1.2.4")),
            release(url = START, version = "1.2.4"),
            release(url = START.replace("skydex.apk", "other.apk")),
            release(url = START.replace("/releases/", "/releases/../releases/")),
            release(url = START.replace("/releases/", "/releases/%2e%2e/releases/")),
            release(url = START.replace("/releases/", "/releases/%2E%2E/releases/")),
            release(url = START.replace("/releases/", "/releases//")),
            release(url = "$START?x=1"),
            release(url = "$START#frag"),
            release(url = "not a url"),
            release().copy(sha256 = null),
            release().copy(sha256 = "abc"),
            release().copy(sha256 = sha256(apkBytes).uppercase()),
            release().copy(sizeBytes = 0),
            release().copy(sizeBytes = UpdateDownloader.MAX_APK_BYTES + 1),
        )
        val downloader = githubThenCdn()
        for (release in bad) {
            assertFails(release, downloader)
            assertEquals(release.downloadUrl, emptyList<Url>(), requests)
        }
    }

    @Test
    fun `start URL allow-list`() {
        assertTrue(UpdateDownloader.isAllowedStart(Url(START), "1.2.3"))
        assertTrue(UpdateDownloader.isAllowedStart(Url(START.replace("MikaelNineza/Skydex", "mikaelnineza/skydex")), "1.2.3"))
        assertFalse(UpdateDownloader.isAllowedStart(Url(START), "1.2.4"))
        assertFalse(UpdateDownloader.isAllowedStart(Url(START), "v1.2.3"))
        assertFalse(UpdateDownloader.isAllowedStart(Url(START.replace("github.com", "github.com.")), "1.2.3"))
        assertFalse(UpdateDownloader.isAllowedStart(Url(START.replace("skydex.apk", "skydex.apk/")), "1.2.3"))
    }

    @Test
    fun `redirect hop allow-list`() {
        for (ok in listOf(
            "https://github.com/x",
            "https://objects.githubusercontent.com/x?sig=1",
            "https://release-assets.githubusercontent.com/x",
        )) {
            assertTrue(ok, UpdateDownloader.isAllowedHop(Url(ok)))
        }
        for (bad in listOf(
            "http://objects.githubusercontent.com/x",
            "https://raw.githubusercontent.com/x",
            "https://evil.githubusercontent.com/x",
            "https://githubusercontent.com/x",
            "https://github.com.evil.com/x",
            "https://objects.githubusercontent.com.evil.com/x",
            "https://github.com./x",
            "https://evil.com/x",
            "https://objects.githubusercontent.com:8443/x",
            "https://user@objects.githubusercontent.com/x",
            "https://user:pw@github.com/x",
        )) {
            assertFalse(bad, UpdateDownloader.isAllowedHop(Url(bad)))
        }
    }

    @Test
    fun `redirects to unexpected places fail`() {
        for (location in listOf(
            "https://evil.com/skydex.apk",
            "http://objects.githubusercontent.com/x",
            "https://raw.githubusercontent.com/MikaelNineza/Skydex/main/evil.apk",
            "https://github.com.evil.com/x",
            "https://objects.githubusercontent.com:444/x",
        )) {
            requests.clear()
            val downloader = downloader { request -> if (request.url.toString() == START) redirect(location) else apk() }
            assertFails(release(), downloader, "The download was redirected to an unexpected address.")
            assertEquals(location, 1, requests.size)
        }
    }

    @Test
    fun `a redirect without Location fails`() {
        val downloader = downloader { respond("", HttpStatusCode.Found) }
        assertFails(release(), downloader, "Download failed (redirect without a location)")
    }

    @Test
    fun `at most five redirects are followed`() = runBlocking {
        fun chain(hops: Int) = downloader { request ->
            val n = request.url.parameters["n"]?.toInt() ?: 0
            if (n < hops) redirect("https://github.com/hop?n=${n + 1}") else apk()
        }
        chain(UpdateDownloader.MAX_REDIRECTS).download(release()) { _, _ -> }
        assertEquals(UpdateDownloader.MAX_REDIRECTS + 1, requests.size)

        dir.deleteRecursively()
        requests.clear()
        assertFails(release(), chain(UpdateDownloader.MAX_REDIRECTS + 1), "Download failed (too many redirects)")
        assertEquals(UpdateDownloader.MAX_REDIRECTS + 1, requests.size)
    }

    @Test
    fun `corrupted downloads fail and leave no file`() {
        val corrupted = "The download was corrupted. Try again."
        val other = apkBytes.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
        // Hash mismatch.
        assertFails(release(), githubThenCdn(other), corrupted)
        // Shorter body (no Content-Length to give it away).
        assertFails(release(), githubThenCdn(apkBytes.copyOf(apkBytes.size - 1), contentLength = null), corrupted)
        // Longer body: stops as soon as it's past the size.
        assertFails(release(), githubThenCdn(apkBytes + ByteArray(10), contentLength = null), corrupted)
        // Content-Length disagrees with the release.
        assertFails(release(), githubThenCdn(contentLength = apkBytes.size + 1L), corrupted)
        // Size in the release disagrees with the file.
        assertFails(release().copy(sizeBytes = apkBytes.size - 1L), githubThenCdn(contentLength = null), corrupted)
    }

    @Test
    fun `an HTTP error fails`() {
        assertFails(release(), downloader { respond("", HttpStatusCode.NotFound) }, "Download failed (HTTP 404)")
    }

    @Test
    fun `cancelling deletes the partial file`() = runBlocking {
        val body = ByteChannel()
        val started = CompletableDeferred<Unit>()
        val downloader = downloader { respond(body, HttpStatusCode.OK, headersOf()) }

        val job = async(Dispatchers.Default) { downloader.download(release()) { _, _ -> started.complete(Unit) } }
        body.writeFully(apkBytes, 0, 1000)
        body.flush()
        withTimeout(5_000) { started.await() }
        assertEquals(listOf("skydex-10203.apk.part"), files())

        job.cancelAndJoin()

        assertEquals(emptyList<String>(), files())
    }

    @Test
    fun `clear deletes leftover downloads`() = runBlocking {
        dir.mkdirs()
        File(dir, "skydex-1.apk").writeText("x")
        File(dir, "skydex-2.apk.part").writeText("x")

        githubThenCdn().clear()

        assertEquals(emptyList<String>(), files())
    }

    @Test
    fun `release page links stay on the repo's releases`() {
        val page = "https://github.com/MikaelNineza/Skydex/releases/tag/v1.2.3"
        assertEquals(page, UpdateDownloader.releasePageUrl(page))
        assertEquals(
            "https://github.com/mikaelnineza/skydex/releases/latest",
            UpdateDownloader.releasePageUrl("https://github.com/mikaelnineza/skydex/releases/latest"),
        )
        for (bad in listOf(
            "http://github.com/MikaelNineza/Skydex/releases/tag/v1.2.3",
            "https://evil.com/MikaelNineza/Skydex/releases/tag/v1.2.3",
            "https://github.com.evil.com/MikaelNineza/Skydex/releases/tag/v1.2.3",
            "https://user@github.com/MikaelNineza/Skydex/releases/tag/v1.2.3",
            "https://github.com:8443/MikaelNineza/Skydex/releases/tag/v1.2.3",
            "https://github.com/Evil/Skydex/releases/tag/v1.2.3",
            "https://github.com/MikaelNineza/Skydex/releases/../../../Evil/x",
            "https://github.com/MikaelNineza/Skydex/releases/%2e%2e/%2e%2e/Evil",
            "https://github.com/MikaelNineza/Skydex/issues",
            "javascript:alert(1)",
            "intent://x#Intent;end",
            "",
            "not a url",
        )) {
            assertEquals(bad, UpdateDownloader.RELEASES_PAGE, UpdateDownloader.releasePageUrl(bad))
        }
        assertEquals("https://github.com/MikaelNineza/Skydex/releases/latest", UpdateDownloader.RELEASES_PAGE)
    }
}
