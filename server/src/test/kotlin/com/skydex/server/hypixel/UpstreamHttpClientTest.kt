package com.skydex.server.hypixel

import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UpstreamHttpClientTest {
    @Test
    fun redirectsAreNotFollowed() = runBlocking<Unit> {
        val skinRequests = AtomicInteger()
        val png = skin().bytes()
        lateinit var http: HttpClient
        val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            // The production client, built by the same Application extension the server module uses.
            http = upstreamHttpClient()
            routing {
                get("/skin") {
                    skinRequests.incrementAndGet()
                    call.respondBytes(png, ContentType.Image.PNG)
                }
                get("/redirect") { call.respondRedirect("/skin") }
            }
        }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            val skins = SkinClient(http)

            assertNull(skins.face("http://127.0.0.1:$port/redirect"))
            assertEquals(0, skinRequests.get())
            // The same client does download the skin when asked directly.
            assertNotNull(skins.face("http://127.0.0.1:$port/skin"))
            assertEquals(1, skinRequests.get())
        } finally {
            server.stop(0, 1_000)
        }
    }
}
