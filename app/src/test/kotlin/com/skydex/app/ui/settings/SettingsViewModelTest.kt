package com.skydex.app.ui.settings

import com.skydex.app.FakePushTokens
import com.skydex.app.FakeServer
import com.skydex.app.MainDispatcherRule
import com.skydex.app.TestStore
import com.skydex.app.data.repository.DeviceRepository
import com.skydex.app.respondJson
import com.skydex.app.sampleSelection
import com.skydex.app.data.remote.SkydexJson
import com.skydex.shared.model.Crop
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val server = FakeServer()
    private val testStore by lazy { TestStore(folder) }
    private val store get() = testStore.store

    @After fun tearDown() = testStore.close()

    private suspend fun HttpRequestData.registration() =
        SkydexJson.decodeFromString<DeviceRegistration>(body.toByteArray().decodeToString())

    private fun viewModel(pushConfigured: Boolean = true): SettingsViewModel {
        val push = FakePushTokens(isConfigured = pushConfigured)
        return SettingsViewModel(store, DeviceRepository(server.api, store, push), push)
    }

    @Test
    fun `enabling tracking registers the device`() = runTest {
        val sent = CompletableDeferred<HttpMethod>()
        server.handler = { request ->
            sent.complete(request.method)
            respond("", HttpStatusCode.NoContent)
        }
        store.select(sampleSelection)
        val vm = viewModel()

        vm.setTrackHistory(true)

        assertEquals(HttpMethod.Put, sent.await())
    }

    @Test
    fun `a failed sync is reported`() = runTest {
        server.handler = { respondJson("""{"message":"Database down"}""", HttpStatusCode.InternalServerError) }
        store.select(sampleSelection)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        vm.setTrackHistory(true)

        assertEquals(
            "Couldn't save to the server: Database down",
            vm.state.first { it?.syncError != null }?.syncError,
        )
    }

    @Test
    fun `a crop toggle is saved and synced`() = runTest {
        val sent = CompletableDeferred<DeviceRegistration>()
        server.handler = { request ->
            sent.complete(runBlocking { request.registration() })
            respond("", HttpStatusCode.NoContent)
        }
        store.setEventEnabled(EventType.JACOBS_CONTEST, true)
        val vm = viewModel()

        vm.setCropEnabled(Crop.WILD_ROSE, true)

        assertEquals(setOf(Crop.WILD_ROSE), sent.await().jacobCrops)
        assertEquals(setOf(Crop.WILD_ROSE), store.current().jacobCrops)
    }

    @Test
    fun `a burst of crop toggles collapses into few syncs`() = runTest {
        val firstPutStarted = CompletableDeferred<Unit>()
        val releaseFirstPut = CompletableDeferred<Unit>()
        val allCropsSent = CompletableDeferred<Unit>()
        val puts = Collections.synchronizedList(mutableListOf<Set<Crop>>())
        val burst = setOf(Crop.WHEAT, Crop.CARROT, Crop.POTATO, Crop.MELON, Crop.PUMPKIN)
        server.handler = { request ->
            val crops = runBlocking { request.registration() }.jacobCrops
            puts += crops
            if (firstPutStarted.complete(Unit)) runBlocking { releaseFirstPut.await() }
            if (crops == burst) allCropsSent.complete(Unit)
            respond("", HttpStatusCode.NoContent)
        }
        store.setEventEnabled(EventType.JACOBS_CONTEST, true)
        val vm = viewModel()

        vm.setCropEnabled(Crop.WHEAT, true)
        firstPutStarted.await()
        // Four more taps while the first PUT is in flight.
        for (crop in burst - Crop.WHEAT) vm.setCropEnabled(crop, true)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { store.settings.first { it.jacobCrops == burst } }
            // Let the taps' coroutines queue up behind the running sync.
            Thread.sleep(300)
        }
        releaseFirstPut.complete(Unit)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { allCropsSent.await() }
            Thread.sleep(300)
        }

        assertEquals(listOf(setOf(Crop.WHEAT), burst), puts.toList())
    }

    @Test
    fun `push config is exposed`() = runTest {
        val vm = viewModel(pushConfigured = false)
        backgroundScope.launch { vm.state.collect {} }

        assertFalse(vm.state.first { it != null }!!.pushConfigured)
    }
}
