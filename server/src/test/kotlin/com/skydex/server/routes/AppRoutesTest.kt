package com.skydex.server.routes

import com.skydex.server.hypixel.RateLimitedException
import com.skydex.server.hypixel.UpstreamException
import com.skydex.server.plugins.configureSerialization
import com.skydex.server.plugins.configureStatusPages
import com.skydex.server.releases.AppReleaseSource
import com.skydex.shared.model.AppRelease
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRoutesTest {
    private val release = AppRelease(
        versionName = "1.2.3",
        versionCode = 10203,
        downloadUrl = "https://github.com/MikaelNineza/Skydex/releases/download/v1.2.3/skydex.apk",
        releaseUrl = "https://github.com/MikaelNineza/Skydex/releases/tag/v1.2.3",
        notes = "Notes",
        publishedAt = 1L,
        sizeBytes = 42,
        sha256 = "a".repeat(64),
    )

    private class FakeSource(var release: AppRelease? = null, var failure: Exception? = null) : AppReleaseSource {
        override suspend fun latest(): AppRelease? = failure?.let { throw it } ?: release
    }

    private val source = FakeSource()

    private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            routing { appRoutes(source) }
        }
        block()
    }

    @Test
    fun latestReleaseIsReturned() = routesTest {
        source.release = release
        val response = client.get("/v1/app/latest")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(release, Json.decodeFromString<AppRelease>(response.bodyAsText()))
    }

    @Test
    fun noReleaseIsNoContent() = routesTest {
        val response = client.get("/v1/app/latest")
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun upstreamFailuresMapToGatewayErrors() = routesTest {
        source.failure = UpstreamException("GitHub returned 500")
        assertEquals(HttpStatusCode.BadGateway, client.get("/v1/app/latest").status)

        source.failure = UpstreamException("GitHub rate limit reached", RateLimitedException(30))
        val limited = client.get("/v1/app/latest")
        assertEquals(HttpStatusCode.ServiceUnavailable, limited.status)
        assertEquals("30", limited.headers[HttpHeaders.RetryAfter])
    }
}
