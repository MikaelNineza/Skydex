package com.skydex.app.ui.profile

import com.skydex.app.FakePushTokens
import com.skydex.app.FakeServer
import com.skydex.app.MainDispatcherRule
import com.skydex.app.TestStore
import com.skydex.app.data.repository.DeviceRepository
import com.skydex.app.data.repository.ProfileRepository
import com.skydex.app.respondJson
import com.skydex.app.samplePlayer
import com.skydex.app.sampleProfile
import com.skydex.app.sampleSelection
import com.skydex.app.ui.common.UiState
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val server = FakeServer()
    private val testStore by lazy { TestStore(folder) }
    private val store get() = testStore.store

    @After fun tearDown() = testStore.close()

    private fun viewModel() = ProfileViewModel(
        ProfileRepository(server.api, store),
        store,
        DeviceRepository(server.api, store, FakePushTokens()),
    )

    @Test
    fun `without a selection the search is shown`() = runTest {
        val vm = viewModel()

        assertEquals(ProfileUiState.NoSelection, vm.state.first { it != ProfileUiState.Loading })
    }

    @Test
    fun `searching and picking a profile loads it`() = runTest {
        server.handler = { request ->
            if (request.url.encodedPath == "/v1/players/Technoblade") {
                respondJson(samplePlayer)
            } else {
                respondJson(sampleProfile)
            }
        }
        val vm = viewModel()

        vm.search("Technoblade")
        assertEquals(UiState.Content(samplePlayer), vm.search.first { it is UiState.Content })

        vm.pick(samplePlayer, samplePlayer.profiles.first())
        val loaded = vm.state.first { it is ProfileUiState.Content }
        assertEquals(ProfileUiState.Content(sampleProfile, offline = false), loaded)
        assertEquals(sampleSelection, store.current().selection)
        assertNull(vm.search.value)
    }

    @Test
    fun `an unreachable server with nothing cached is an error`() = runTest {
        store.select(sampleSelection)
        server.handler = { throw IOException("offline") }
        val vm = viewModel()

        assertEquals(
            ProfileUiState.Error("Can't reach the Skydex server"),
            vm.state.first { it is ProfileUiState.Error },
        )
    }

    @Test
    fun `refresh falls back to the cache and marks it offline`() = runTest {
        store.select(sampleSelection)
        server.handler = { respondJson(sampleProfile) }
        val vm = viewModel()
        vm.state.first { it is ProfileUiState.Content }

        server.handler = { throw IOException("offline") }
        vm.refresh()

        val offline = vm.state.first { it is ProfileUiState.Content && it.offline }
        assertEquals(ProfileUiState.Content(sampleProfile, offline = true), offline)
        assertFalse(vm.isRefreshing.value)
    }
}
