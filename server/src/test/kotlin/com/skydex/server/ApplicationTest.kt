package com.skydex.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun healthReturnsOk() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun historyEndpointIsGone() = testApplication {
        application { module() }

        val response = client.get(
            "/v1/players/0f1e2d3c4b5a69788796a5b4c3d2e1f0/profiles/a1b2c3d4e5f64a1b8c9d0e1f2a3b4c5d/history",
        )

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
