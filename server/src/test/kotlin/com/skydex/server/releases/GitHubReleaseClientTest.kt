package com.skydex.server.releases

import com.skydex.server.hypixel.RateLimitedException
import com.skydex.server.hypixel.UpstreamException
import com.skydex.server.hypixel.mockHttp
import com.skydex.shared.model.AppRelease
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val releaseFixture: String =
    GitHubReleaseClientTest::class.java.getResource("/github/release.json")!!.readText()

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val REPO = "MikaelNineza/Skydex"
private const val NOW = 1_790_000_000_000L

class GitHubReleaseClientTest {
    private fun client(body: String = releaseFixture, token: String = "", status: HttpStatusCode = HttpStatusCode.OK) =
        mockHttp { respond(body, status, jsonHeaders) }.let { (engine, http) ->
            engine to GitHubReleaseClient(http, REPO, token, clock = { NOW })
        }

    /** The fixture with the top-level [key] replaced (or removed when [value] is null). */
    private fun fixtureWith(key: String, value: JsonElement?, base: String = releaseFixture): String {
        val obj = Json.parseToJsonElement(base).jsonObject
        return JsonObject(if (value == null) obj - key else obj + (key to value)).toString()
    }

    /** The fixture with the skydex.apk asset's [key] replaced (or removed when [value] is null). */
    private fun fixtureWithApkAsset(key: String, value: JsonElement?): String {
        val obj = Json.parseToJsonElement(releaseFixture).jsonObject
        val assets = obj.getValue("assets").jsonArray.map { asset ->
            val a = asset.jsonObject
            if ((a["name"] as JsonPrimitive).content != "skydex.apk") a
            else JsonObject(if (value == null) a - key else a + (key to value))
        }
        return JsonObject(obj + ("assets" to JsonArray(assets))).toString()
    }

    @Test
    fun requestsTheLatestReleaseWithGitHubHeaders() = runBlocking<Unit> {
        val (engine, client) = client()
        client.latestRelease()
        val request = engine.requestHistory.single()
        assertEquals("https://api.github.com/repos/MikaelNineza/Skydex/releases/latest", request.url.toString())
        assertEquals("application/vnd.github+json", request.headers[HttpHeaders.Accept])
        assertEquals("2022-11-28", request.headers["X-GitHub-Api-Version"])
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun sendsTheTokenOnlyWhenSet() = runBlocking<Unit> {
        val (engine, client) = client(token = "ghp_secret")
        client.latestRelease()
        assertEquals("Bearer ghp_secret", engine.requestHistory.single().headers[HttpHeaders.Authorization])

        val (blankEngine, blankClient) = client(token = "   ")
        blankClient.latestRelease()
        assertNull(blankEngine.requestHistory.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun mapsTheReleaseAndPicksTheApkAsset() = runBlocking<Unit> {
        val release = client().second.latestRelease()!!
        assertEquals("1.2.3", release.versionName)
        assertEquals(10203, release.versionCode)
        assertEquals("https://github.com/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk", release.downloadUrl)
        assertEquals("https://github.com/MikaelNineza/Skydex/releases/tag/v1.2.3", release.releaseUrl)
        assertEquals(12_345_678, release.sizeBytes)
        assertEquals(Instant.parse("2026-09-30T12:00:00Z").toEpochMilli(), release.publishedAt)
        // From the apk asset's digest (lowercased), not the body line or the .sha256 asset's digest.
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", release.sha256)
        assertTrue(release.notes!!.contains("What's Changed"))
    }

    @Test
    fun unparseablePublishedAtGivesNull() = runBlocking<Unit> {
        val release = client(fixtureWith("published_at", JsonPrimitive("yesterday"))).second.latestRelease()!!
        assertNull(release.publishedAt)
    }

    @Test
    fun shaFallsBackToTheNotesLine() = runBlocking<Unit> {
        val fromBody = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
        assertEquals(fromBody, client(fixtureWithApkAsset("digest", null)).second.latestRelease()!!.sha256)
        assertEquals(
            fromBody,
            client(fixtureWithApkAsset("digest", JsonPrimitive("sha512:abc"))).second.latestRelease()!!.sha256,
        )
    }

    @Test
    fun shaIsNullWhenDigestAndNotesLineAreMissingOrMalformed() = runBlocking<Unit> {
        val noDigest = fixtureWithApkAsset("digest", null)
        val badLine = fixtureWith("body", JsonPrimitive("SHA-256: `abc123`\nNotes"), noDigest)
        assertNull(client(badLine).second.latestRelease()!!.sha256)
        assertNull(client(fixtureWith("body", null, noDigest)).second.latestRelease()!!.sha256)
    }

    @Test
    fun parseDigestAndShaFromNotes() {
        val hex = "0123456789abcdef".repeat(4)
        assertEquals(hex, parseDigest("sha256:$hex"))
        assertEquals(hex, parseDigest("sha256:${hex.uppercase()}"))
        assertNull(parseDigest(null))
        assertNull(parseDigest(hex))
        assertNull(parseDigest("sha256:${hex}0"))
        assertNull(parseDigest("sha256:${hex.dropLast(1)}g"))
        assertNull(parseDigest("SHA256:$hex"))

        assertEquals(hex, shaFromNotes("SHA-256: `$hex`"))
        assertEquals(hex, shaFromNotes("intro\nsha-256: ${hex.uppercase()}\nmore"))
        assertNull(shaFromNotes(null))
        assertNull(shaFromNotes("The SHA-256: `$hex` is inline"))
        assertNull(shaFromNotes("SHA-256: `${hex}00`"))
    }

    @Test
    fun noReleaseGivesNull() = runBlocking<Unit> {
        assertNull(client("""{"message":"Not Found"}""", status = HttpStatusCode.NotFound).second.latestRelease())
    }

    @Test
    fun unusableReleasesGiveNull() = runBlocking<Unit> {
        val apk = "https://github.com/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk"
        val cases = mapOf(
            "non-semver tag" to fixtureWith("tag_name", JsonPrimitive("release-1")),
            "leading-zero tag" to fixtureWith("tag_name", JsonPrimitive("v1.02.3")),
            "draft" to fixtureWith("draft", JsonPrimitive(true)),
            "prerelease" to fixtureWith("prerelease", JsonPrimitive(true)),
            "no apk asset" to fixtureWithApkAsset("name", JsonPrimitive("skydex-1.2.3.apk")),
            "foreign host" to fixtureWithApkAsset("browser_download_url", JsonPrimitive(apk.replace("github.com", "evil.com"))),
            "foreign repo" to fixtureWithApkAsset(
                "browser_download_url",
                JsonPrimitive(apk.replace("MikaelNineza/Skydex", "Evil/Skydex")),
            ),
            "tag mismatch" to fixtureWithApkAsset("browser_download_url", JsonPrimitive(apk.replace("v1.2.3", "v1.2.4"))),
            "size 0" to fixtureWithApkAsset("size", JsonPrimitive(0)),
            "size over cap" to fixtureWithApkAsset("size", JsonPrimitive(MAX_APK_BYTES + 1)),
        )
        for ((name, body) in cases) assertNull(client(body).second.latestRelease(), name)
    }

    @Test
    fun releaseAssetUrlAllowList() {
        val tag = "v1.2.3"
        val ok = "https://github.com/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk"
        assertTrue(isReleaseAssetUrl(ok, REPO, tag))
        assertTrue(isReleaseAssetUrl(ok.replace("MikaelNineza/Skydex", "mikaelnineza/SKYDEX"), REPO, tag))
        val bad = listOf(
            ok.replace("https://", "http://"),
            ok.replace("github.com", "github.com:8443"),
            ok.replace("github.com", "github.com:443"),
            ok.replace("github.com", "github.com.evil.com"),
            ok.replace("github.com", "user@github.com"),
            ok.replace("https://github.com", "https://github.com@evil.com"),
            ok.replace("github.com", "GITHUB.COM"),
            ok.replace("github.com", "github.com."),
            "$ok?x=1",
            "$ok#frag",
            ok.replace("/releases/", "/releases/../releases/"),
            ok.replace("/releases/", "/releases/%2e%2e/releases/"),
            ok.replace("/releases/", "/releases/%2E%2E/releases/"),
            ok.replace("/releases/", "/releases//"),
            ok.replace("skydex.apk", "skydex.apk.sha256"),
            ok.replace("v1.2.3", "v1.2.4"),
            "https://objects.githubusercontent.com/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk",
        )
        for (url in bad) assertFalse(isReleaseAssetUrl(url, REPO, tag), url)
        assertFalse(isReleaseAssetUrl(ok, REPO, "1.2.3"))
    }

    @Test
    fun truncateNotes() {
        assertNull(truncateNotes(null))
        assertNull(truncateNotes("  \n "))
        assertEquals("short", truncateNotes("short"))
        val exact = "x".repeat(AppRelease.MAX_NOTES_CHARS)
        assertEquals(exact, truncateNotes(exact))

        val long = truncateNotes("y".repeat(10_000))!!
        assertTrue(long.endsWith("…"))
        assertEquals(AppRelease.MAX_NOTES_CHARS, long.length)

        // An emoji (surrogate pair) straddling the cut is dropped whole.
        val emoji = "a".repeat(AppRelease.MAX_NOTES_CHARS - 2) + "😀" + "tail"
        val cut = truncateNotes(emoji)!!
        assertTrue(cut.length <= AppRelease.MAX_NOTES_CHARS)
        assertTrue(cut.endsWith("…"))
        assertFalse(cut.any { it.isSurrogate() }, "dangling surrogate")
    }

    @Test
    fun gitHubRetryAfterSeconds() = runBlocking<Unit> {
        suspend fun seconds(vararg headers: Pair<String, String>): Long {
            val (_, http) = mockHttp {
                respond("", HttpStatusCode.Forbidden, headersOf(*headers.map { it.first to listOf(it.second) }.toTypedArray()))
            }
            return gitHubRetryAfterSeconds(http.get("https://api.github.com/x"), NOW)
        }
        assertEquals(120, seconds("x-ratelimit-reset" to "${NOW / 1000 + 120}"))
        assertEquals(1, seconds("x-ratelimit-reset" to "${NOW / 1000 - 500}"))
        assertEquals(30, seconds(HttpHeaders.RetryAfter to "30"))
        assertEquals(30, seconds(HttpHeaders.RetryAfter to "30", "x-ratelimit-reset" to "${NOW / 1000 + 120}"))
        assertEquals(60, seconds())
        assertEquals(3600, seconds("x-ratelimit-reset" to "${NOW / 1000 + 100_000}"))
    }

    @Test
    fun rateLimitsThrowWithRateLimitCause() = runBlocking<Unit> {
        val cases = listOf(
            Triple(
                HttpStatusCode.Forbidden,
                headersOf("x-ratelimit-remaining" to listOf("0"), "x-ratelimit-reset" to listOf("${NOW / 1000 + 120}")),
                120L,
            ),
            Triple(HttpStatusCode.Forbidden, headersOf(HttpHeaders.RetryAfter, "45"), 45L),
            Triple(HttpStatusCode.TooManyRequests, headersOf(), 60L),
        )
        for ((status, headers, expected) in cases) {
            val (_, http) = mockHttp { respond("""{"message":"API rate limit exceeded"}""", status, headers) }
            val e = assertFailsWith<UpstreamException> { GitHubReleaseClient(http, REPO, clock = { NOW }).latestRelease() }
            assertEquals(expected, assertIs<RateLimitedException>(e.cause).retryAfterSeconds)
        }
    }

    @Test
    fun otherFailuresThrowWithoutRateLimitCause() = runBlocking<Unit> {
        for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.InternalServerError, HttpStatusCode.MovedPermanently)) {
            val (_, http) = mockHttp { respond("""{"message":"x"}""", status, headersOf("x-ratelimit-remaining", "42")) }
            val e = assertFailsWith<UpstreamException> { GitHubReleaseClient(http, REPO).latestRelease() }
            assertFalse(e.cause is RateLimitedException, "$status")
            assertEquals("GitHub returned ${status.value}", e.message)
        }
    }

    @Test
    fun malformedJsonThrowsUpstreamException() = runBlocking<Unit> {
        for (body in listOf("not json", """{"tag_name":"v1.2.3"}""", "[]")) {
            assertFailsWith<UpstreamException>(body) { client(body).second.latestRelease() }
        }
    }

    @Test
    fun networkFailureThrowsUpstreamException() = runBlocking<Unit> {
        val engine = MockEngine { throw IOException("boom") }
        val e = assertFailsWith<UpstreamException> { GitHubReleaseClient(HttpClient(engine), REPO).latestRelease() }
        assertEquals("GitHub is unreachable", e.message)
    }
}
