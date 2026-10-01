package com.skydex.app.updates

import com.skydex.app.di.DownloadClient
import com.skydex.app.di.UpdatesDir
import com.skydex.shared.model.AppRelease
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A download that can't be used; [message] is fit to show the user. */
class UpdateException(message: String) : Exception(message)

/**
 * The client for APK downloads: no base URL, no JSON, and no automatic redirects, so [UpdateDownloader] can check
 * every hop. (Ktor's OkHttp engine also turns off OkHttp's own redirect handling.)
 */
fun createDownloadClient(engine: HttpClientEngine) = HttpClient(engine) {
    followRedirects = false
    expectSuccess = false
}

private val SHA256_HEX = Regex("[0-9a-f]{64}")
private const val CORRUPTED = "The download was corrupted. Try again."

/** Downloads release APKs from GitHub into the app's cache, checking where they come from and what they contain. */
class UpdateDownloader @Inject constructor(
    @DownloadClient private val http: HttpClient,
    @UpdatesDir private val dir: File,
) {
    /**
     * Downloads [release]'s APK into the updates directory, verifying its size and SHA-256 while it streams.
     * Returns the verified file. Throws [UpdateException] (or an IOException) and leaves no partial file behind.
     */
    suspend fun download(release: AppRelease, onProgress: (bytesRead: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val sha256 = release.sha256?.takeIf { SHA256_HEX.matches(it) }
                ?: throw UpdateException("This update has no checksum, so it can't be installed from the app.")
            val total = release.sizeBytes
            if (total !in 1..MAX_APK_BYTES) throw UpdateException("This update's size looks wrong.")
            var url = runCatching { Url(release.downloadUrl) }.getOrNull()?.takeIf { isAllowedStart(it, release.versionName) }
                ?: throw UpdateException("This update has an unexpected download address.")

            val part = File(dir, "skydex-${release.versionCode}.apk.part")
            try {
                // The first request plus at most MAX_REDIRECTS redirects.
                repeat(MAX_REDIRECTS + 1) {
                    val next = http.prepareGet(url).execute { response ->
                        when {
                            response.status.value in 300..399 -> nextHop(url, response)
                            response.status == HttpStatusCode.OK -> {
                                save(response, part, total, sha256, onProgress)
                                null
                            }
                            else -> throw UpdateException("Download failed (HTTP ${response.status.value})")
                        }
                    }
                    if (next == null) {
                        val apk = File(dir, "skydex-${release.versionCode}.apk")
                        apk.delete()
                        if (!part.renameTo(apk)) throw IOException("Could not rename ${part.name}")
                        return@withContext apk
                    }
                    url = next
                }
                throw UpdateException("Download failed (too many redirects)")
            } catch (e: Throwable) { // includes cancellation
                part.delete()
                throw e
            }
        }

    /** Deletes leftover downloads (called once per process start). */
    suspend fun clear() {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun nextHop(current: Url, response: HttpResponse): Url {
        val location = response.headers[HttpHeaders.Location]
            ?: throw UpdateException("Download failed (redirect without a location)")
        // A relative Location must not inherit the current URL's query or fragment (signed CDN URLs carry tokens there).
        val next = runCatching {
            URLBuilder(current).apply {
                parameters.clear()
                fragment = ""
            }.takeFrom(location).build()
        }.getOrNull()
        if (next == null || !isAllowedHop(next)) throw UpdateException("The download was redirected to an unexpected address.")
        return next
    }

    private suspend fun save(
        response: HttpResponse,
        part: File,
        total: Long,
        sha256: String,
        onProgress: (Long, Long) -> Unit,
    ) {
        response.contentLength()?.let { if (it != total) throw UpdateException(CORRUPTED) }
        dir.mkdirs()
        val channel = response.bodyAsChannel()
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var read = 0L
        var lastPercent = -1L
        part.outputStream().use { out ->
            while (true) {
                val n = channel.readAvailable(buffer, 0, buffer.size)
                if (n == -1) break
                read += n
                if (read > total) throw UpdateException(CORRUPTED)
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
                val percent = read * 100 / total
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(read, total)
                }
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (read != total || hex != sha256) throw UpdateException(CORRUPTED)
    }

    companion object {
        /** The only repository the app installs releases from. */
        const val RELEASE_REPO = "MikaelNineza/Skydex"
        const val MAX_APK_BYTES = 100L * 1024 * 1024
        const val MAX_REDIRECTS = 5

        /** Where the app sends the user when a release page URL isn't one of [RELEASE_REPO]'s. */
        const val RELEASES_PAGE = "https://github.com/$RELEASE_REPO/releases/latest"

        private val DOWNLOAD_PATH = Regex(
            """^/(?i:${Regex.escape(RELEASE_REPO)})/releases/download/(v\d+\.\d+\.\d+)/${Regex.escape(AppRelease.ASSET_NAME)}$""",
        )
        private val DOWNLOAD_HOPS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

        /**
         * The release asset URL itself, exactly https://github.com/<RELEASE_REPO>/releases/download/v<versionName>/skydex.apk
         * (owner and repo in any case, no query or fragment).
         */
        internal fun isAllowedStart(url: Url, versionName: String): Boolean {
            if (!isPlainHttps(url) || url.host != "github.com" || hasTraversal(url.encodedPath)) return false
            if (url.encodedQuery.isNotEmpty() || url.encodedFragment.isNotEmpty()) return false
            val tag = DOWNLOAD_PATH.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return false
            return tag == "v$versionName"
        }

        /** Where GitHub may redirect the download: github.com or its release CDN hosts, over https. */
        internal fun isAllowedHop(url: Url): Boolean = isPlainHttps(url) && url.host in DOWNLOAD_HOPS

        /** [url] when it's a page under https://github.com/<RELEASE_REPO>/releases/, otherwise [RELEASES_PAGE]. */
        fun releasePageUrl(url: String): String {
            val parsed = runCatching { Url(url) }.getOrNull() ?: return RELEASES_PAGE
            val ok = isPlainHttps(parsed) && parsed.host == "github.com" && !hasTraversal(parsed.encodedPath) &&
                parsed.encodedPath.startsWith("/$RELEASE_REPO/releases/", ignoreCase = true)
            // Re-serialized, so what's opened is exactly what was checked.
            return if (ok) parsed.toString() else RELEASES_PAGE
        }

        private fun hasTraversal(path: String) =
            ".." in path || "//" in path || path.contains("%2e", ignoreCase = true)

        /** https on the default port, with no user info. */
        private fun isPlainHttps(url: Url) = url.protocol == URLProtocol.HTTPS &&
            url.port == URLProtocol.HTTPS.defaultPort && url.user == null && url.password == null
    }
}
