package com.skydex.app.data.repository

import com.skydex.app.FakePushTokens
import com.skydex.app.FakeServer
import com.skydex.app.TestStore
import com.skydex.app.data.remote.SkydexJson
import com.skydex.app.sampleSelection
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = FakeServer().apply { handler = { respond("", HttpStatusCode.NoContent) } }
    private val testStore by lazy { TestStore(folder) }
    private val store get() = testStore.store

    @After fun tearDown() = testStore.close()

    private fun repository(pushConfigured: Boolean = true) =
        DeviceRepository(server.api, store, FakePushTokens(isConfigured = pushConfigured))

    private suspend fun HttpRequestData.registration() =
        SkydexJson.decodeFromString<DeviceRegistration>(body.toByteArray().decodeToString())

    @Test
    fun `tracking and events are sent with the installation id`() = runTest {
        store.select(sampleSelection)
        store.setTrackHistory(true)
        store.setEventEnabled(EventType.DARK_AUCTION, true)
        store.setLeadMinutes(10)

        repository().sync()

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/v1/devices/${store.installationId()}", request.url.encodedPath)
        assertEquals(
            DeviceRegistration("fcm-token", "abc123", "p1", setOf(EventType.DARK_AUCTION), 10),
            request.registration(),
        )
    }

    @Test
    fun `nothing to do deletes the registration`() = runTest {
        store.select(sampleSelection)

        repository().sync()

        assertEquals(HttpMethod.Delete, server.requests.single().method)
    }

    @Test
    fun `events are not subscribed when push is not configured`() = runTest {
        store.select(sampleSelection)
        store.setTrackHistory(true)
        store.setEventEnabled(EventType.DARK_AUCTION, true)

        repository(pushConfigured = false).sync()

        assertEquals(emptySet<EventType>(), server.requests.single().registration().subscribedEvents)
    }

    @Test
    fun `installation id is stable`() = runTest {
        assertEquals(store.installationId(), store.installationId())
    }
}
