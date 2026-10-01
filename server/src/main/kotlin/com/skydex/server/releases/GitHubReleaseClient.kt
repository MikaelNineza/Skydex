package com.skydex.server.releases

import com.skydex.server.hypixel.UpstreamException
import com.skydex.server.hypixel.rateLimited
import com.skydex.server.hypixel.upstreamCall
import com.skydex.server.hypixel.upstreamJson
import com.skydex.shared.model.AppRelease
import com.skydex.shared.model.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.Instant

/** Largest APK we offer; anything bigger is treated as a broken release. */
internal const val MAX_APK_BYTES = 100L * 1024 * 1024

private val SHA256_HEX = Regex("[0-9a-f]{64}")
private val SHA_LINE = Regex("""(?im)^SHA-256:\s*`?([0-9a-fA-F]{64})`?\s*$""")

/** Reads the latest published release of [repo] ("owner/name") from the GitHub REST API. */
class GitHubReleaseClient(
    private val http: HttpClient,
    private val repo: String,
    private val token: String = "",
    private val baseUrl: String = "https://api.github.com",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(GitHubReleaseClient::class.java)

    @Serializable
    private class RawRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<RawAsset> = emptyList(),
    )

    @Serializable
    private class RawAsset(
        val name: String,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
        val size: Long,
        /** "sha256:<hex>", added by GitHub in 2025. */
        val digest: String? = null,
    )

    /** The release as an [AppRelease], or null when there is none or it isn't usable. Throws [UpstreamException]. */
    suspend fun latestRelease(): AppRelease? {
        val response = upstreamCall("GitHub") {
            http.get("$baseUrl/repos/$repo/releases/latest") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        val status = response.status
        when {
            status == HttpStatusCode.NotFound -> return null
            status == HttpStatusCode.TooManyRequests || (status == HttpStatusCode.Forbidden && isRateLimit(response)) ->
                throw rateLimited("GitHub", gitHubRetryAfterSeconds(response, clock()))
            // Includes the 301 GitHub sends for a renamed repo: redirects aren't followed.
            !status.isSuccess() -> throw UpstreamException("GitHub returned ${status.value}")
        }

        val raw = try {
            upstreamJson.decodeFromString<RawRelease>(response.bodyAsText())
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw UpstreamException("Unexpected GitHub response", e)
        }
        return toAppRelease(raw)
    }

    private fun isRateLimit(response: HttpResponse): Boolean =
        response.headers["x-ratelimit-remaining"] == "0" || response.headers[HttpHeaders.RetryAfter] != null

    private fun toAppRelease(raw: RawRelease): AppRelease? {
        fun skip(reason: String): AppRelease? {
            log.warn("Ignoring GitHub release {}: {}", raw.tagName, reason)
            return null
        }

        if (raw.draft || raw.prerelease) return skip("draft or prerelease")
        val version = AppVersion.parse(raw.tagName) ?: return skip("tag is not vMAJOR.MINOR.PATCH")
        val asset = raw.assets.firstOrNull { it.name == AppRelease.ASSET_NAME }
            ?: return skip("no ${AppRelease.ASSET_NAME} asset")
        val url = asset.browserDownloadUrl
        if (!isReleaseAssetUrl(url, repo, raw.tagName)) return skip("unexpected download URL $url")
        if (asset.size !in 1..MAX_APK_BYTES) return skip("asset size ${asset.size}")

        return AppRelease(
            versionName = version.name,
            versionCode = version.versionCode,
            downloadUrl = url,
            releaseUrl = raw.htmlUrl,
            notes = truncateNotes(raw.body),
            publishedAt = raw.publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            sizeBytes = asset.size,
            sha256 = parseDigest(asset.digest) ?: shaFromNotes(raw.body),
        )
    }
}

/**
 * True when [url] is exactly https://github.com/<repo>/releases/download/<tag>/skydex.apk (owner and repo in any case),
 * the only shape the app accepts (UpdateDownloader.isAllowedStart).
 */
internal fun isReleaseAssetUrl(url: String, repo: String, tag: String): Boolean {
    val path = url.removePrefix("https://github.com")
    if (path == url || ".." in path || "//" in path || path.contains("%2e", ignoreCase = true)) return false
    val pattern = Regex(
        """^/(?i:${Regex.escape(repo)})/releases/download/(v\d+\.\d+\.\d+)/${Regex.escape(AppRelease.ASSET_NAME)}$""",
    )
    return pattern.matchEntire(path)?.groupValues?.get(1) == tag
}

/** The hex of a GitHub asset digest "sha256:<64 hex>", lowercased; null for anything else. */
internal fun parseDigest(digest: String?): String? {
    if (digest == null || !digest.startsWith("sha256:")) return null
    return digest.removePrefix("sha256:").lowercase().takeIf { SHA256_HEX.matches(it) }
}

/** The hash from a "SHA-256: `<hex>`" line in the release notes, which the release workflow writes. */
internal fun shaFromNotes(body: String?): String? =
    body?.let { SHA_LINE.find(it) }?.groupValues?.get(1)?.lowercase()

/** Null when blank; otherwise at most [AppRelease.MAX_NOTES_CHARS] chars, ending in "…" when cut. */
internal fun truncateNotes(body: String?): String? {
    if (body.isNullOrBlank()) return null
    if (body.length <= AppRelease.MAX_NOTES_CHARS) return body
    var cut = body.take(AppRelease.MAX_NOTES_CHARS - 1)
    if (cut.last().isHighSurrogate()) cut = cut.dropLast(1)
    return "$cut…"
}

/**
 * Seconds to wait after GitHub rate-limited us: `Retry-After` (delta seconds) if present, else `x-ratelimit-reset`
 * (an epoch timestamp, unlike the `RateLimit-Reset` that [com.skydex.server.hypixel.retryAfterSeconds] reads),
 * else a minute. Kept between 1 s and an hour.
 */
internal fun gitHubRetryAfterSeconds(response: HttpResponse, nowMillis: Long): Long {
    val seconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
        ?: response.headers["x-ratelimit-reset"]?.toLongOrNull()?.let { it - nowMillis / 1000 }
        ?: 60
    return seconds.coerceIn(1, 3600)
}
