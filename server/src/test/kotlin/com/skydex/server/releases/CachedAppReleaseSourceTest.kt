package com.skydex.server.releases

import com.skydex.server.hypixel.UpstreamException
import com.skydex.server.hypixel.mockHttp
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val releaseJson: String =
    CachedAppReleaseSourceTest::class.java.getResource("/github/release.json")!!.readText()

class CachedAppReleaseSourceTest {
    private var now = 0L
    private var status = HttpStatusCode.NotFound
    private val engineAndHttp = mockHttp {
        respond(if (status == HttpStatusCode.OK) releaseJson else "{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private val engine = engineAndHttp.first
    private val source = CachedAppReleaseSource(
        GitHubReleaseClient(engineAndHttp.second, "MikaelNineza/Skydex", clock = { now }),
        clock = { now },
        ttlMillis = 15 * 60_000,
    )

    @Test
    fun noReleaseIsCachedForTheTtl() = runBlocking<Unit> {
        assertNull(source.latest())
        now += 15 * 60_000 - 1
        assertNull(source.latest())
        assertEquals(1, engine.requestHistory.size)

        status = HttpStatusCode.OK
        now += 1
        assertEquals("1.2.3", source.latest()?.versionName)
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun failureServesTheLastAnswer() = runBlocking<Unit> {
        status = HttpStatusCode.OK
        val first = source.latest()
        status = HttpStatusCode.InternalServerError
        now += 15 * 60_000
        assertEquals(first, source.latest())
    }

    @Test
    fun failureWithoutAnswerThrows() = runBlocking<Unit> {
        status = HttpStatusCode.InternalServerError
        assertFailsWith<UpstreamException> { source.latest() }
    }

    @Test
    fun configRepoDecidesWhetherTheEndpointExists() {
        fun sourceFor(repo: String?) = run {
            var result: AppReleaseSource? = null
            testApplication {
                environment {
                    config = MapApplicationConfig().apply { if (repo != null) put("github.repo", repo) }
                }
                application { result = appReleaseSource(engineAndHttp.second) }
                startApplication()
            }
            result
        }
        assertNull(sourceFor(null))
        assertNull(sourceFor(""))
        assertNull(sourceFor("bad repo"))
        assertNull(sourceFor("a/b/c"))
        assertNull(sourceFor("../x"))
        assertNotNull(sourceFor("a/b"))
        assertNotNull(sourceFor(" MikaelNineza/Skydex "))
    }
}
