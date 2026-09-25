package com.skydex.app.ui.settings

import com.skydex.app.FakePushTokens
import com.skydex.app.FakeServer
import com.skydex.app.MainDispatcherRule
import com.skydex.app.TestStore
import com.skydex.app.data.repository.DeviceRepository
import com.skydex.app.respondJson
import com.skydex.app.sampleSelection
import io.ktor.client.engine.mock.respond
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
    fun `push config is exposed`() = runTest {
        val vm = viewModel(pushConfigured = false)
        backgroundScope.launch { vm.state.collect {} }

        assertFalse(vm.state.first { it != null }!!.pushConfigured)
    }
}
