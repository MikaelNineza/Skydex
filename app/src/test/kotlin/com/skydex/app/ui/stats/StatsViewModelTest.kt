package com.skydex.app.ui.stats

import com.skydex.app.FakeServer
import com.skydex.app.MainDispatcherRule
import com.skydex.app.TestStore
import com.skydex.app.data.repository.ProfileRepository
import com.skydex.app.respondJson
import com.skydex.app.sampleSelection
import com.skydex.shared.model.StatsHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StatsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    // Echo the requested range back in the uuid field so tests can tell responses apart.
    private val server = FakeServer().apply {
        handler = { request -> respondJson(StatsHistory(request.url.parameters["days"]!!, "p1", emptyList())) }
    }
    private val testStore by lazy { TestStore(folder) }

    @After fun tearDown() = testStore.close()

    private fun viewModel() = StatsViewModel(ProfileRepository(server.api, testStore.store), testStore.store)

    @Test
    fun `without a selection nothing is requested`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(StatsUiState.NoSelection, vm.state.first { it != StatsUiState.Loading })
        assertEquals(0, server.requests.size)
    }

    @Test
    fun `history loads for 30 days and reloads when the range changes`() = runTest {
        testStore.store.select(sampleSelection)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        vm.state.first { it is StatsUiState.Content && it.history.uuid == "30" }

        vm.setDays(7)
        vm.state.first { it is StatsUiState.Content && it.history.uuid == "7" }
        assertEquals(7, vm.days.value)
    }
}
