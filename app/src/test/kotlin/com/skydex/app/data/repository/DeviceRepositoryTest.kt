package com.skydex.app.data.repository

import com.skydex.app.FakePushTokens
import com.skydex.app.FakeServer
import com.skydex.app.TestStore
import com.skydex.app.data.remote.SkydexJson
import com.skydex.app.sampleSelection
import com.skydex.shared.model.Crop
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
import org.junit.Assert.assertFalse
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
    fun `events are sent with the installation id`() = runTest {
        store.select(sampleSelection)
        store.setEventEnabled(EventType.DARK_AUCTION, true)
        store.setLeadMinutes(10)

        repository().sync()

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/v1/devices/${store.installationId()}", request.url.encodedPath)
        assertEquals(
            DeviceRegistration("fcm-token", subscribedEvents = setOf(EventType.DARK_AUCTION), leadMinutes = 10),
            request.registration(),
        )
        // The stats history's tracked profile is gone from the wire format.
        val body = request.body.toByteArray().decodeToString()
        assertFalse(body, "tracked" in body)
    }

    @Test
    fun `jacob crops are sent`() = runTest {
        store.setEventEnabled(EventType.JACOBS_CONTEST, true)
        store.setCropEnabled(Crop.WHEAT, true)
        store.setCropEnabled(Crop.COCOA_BEANS, true)
        store.setCropEnabled(Crop.MELON, true)
        store.setCropEnabled(Crop.MELON, false)

        repository().sync()

        val sent = server.requests.single().registration()
        assertEquals(setOf(EventType.JACOBS_CONTEST), sent.subscribedEvents)
        assertEquals(5, sent.leadMinutes)
        assertEquals(setOf(Crop.WHEAT, Crop.COCOA_BEANS), sent.jacobCrops)
    }

    @Test
    fun `nothing to do deletes the registration`() = runTest {
        store.select(sampleSelection)

        repository().sync()

        assertEquals(HttpMethod.Delete, server.requests.single().method)
    }

    @Test
    fun `without push the registration is deleted`() = runTest {
        store.select(sampleSelection)
        store.setEventEnabled(EventType.DARK_AUCTION, true)

        repository(pushConfigured = false).sync()

        assertEquals(HttpMethod.Delete, server.requests.single().method)
    }

    @Test
    fun `installation id is stable`() = runTest {
        assertEquals(store.installationId(), store.installationId())
    }
}
