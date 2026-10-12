package com.skydex.app.ui.profile

import androidx.lifecycle.viewModelScope
import com.skydex.app.FakeServer
import com.skydex.app.MainDispatcherRule
import com.skydex.app.TestStore
import com.skydex.app.data.local.Selection
import com.skydex.app.data.repository.ProfileRepository
import com.skydex.app.respondJson
import com.skydex.app.samplePlayer
import com.skydex.app.sampleProfile
import com.skydex.app.sampleSelection
import com.skydex.app.ui.common.UiState
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val server = FakeServer()
    private val testStore by lazy { TestStore(folder) }
    private val store get() = testStore.store

    private val created = mutableListOf<ProfileViewModel>()

    /** Stops every view model first: a collector resuming on Main after the rule resets it would crash a later test. */
    @After fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        testStore.close()
    }

    private fun viewModel(
        clock: Clock = Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC),
    ) = ProfileViewModel(
        ProfileRepository(server.api, store),
        store,
        clock,
    ).also { created += it }

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
        val clock = MutableClock(t0)
        val vm = viewModel(clock)
        vm.state.first { it is ProfileUiState.Content }

        clock.now += ProfileViewModel.REFRESH_COOLDOWN_MILLIS
        server.handler = { throw IOException("offline") }
        vm.refresh()
        runCurrent()

        val offline = vm.state.first { it is ProfileUiState.Content && it.offline }
        assertEquals(ProfileUiState.Content(sampleProfile, offline = true), offline)
        assertFalse(vm.isRefreshing.value)
    }

    // --- Refresh cooldown (in memory: a new process starts without one) ---

    private val t0 = 1_000_000L
    private val cooldown = ProfileViewModel.REFRESH_COOLDOWN_MILLIS

    private class MutableClock(var now: Long) : Clock() {
        override fun millis() = now

        override fun instant(): Instant = Instant.ofEpochMilli(now)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }

    /** Selects the sample profile, serves it and waits for the first load. */
    private suspend fun loaded(clock: MutableClock = MutableClock(t0)): ProfileViewModel {
        store.select(sampleSelection)
        server.handler = { respondJson(sampleProfile) }
        val vm = viewModel(clock)
        vm.state.first { it is ProfileUiState.Content }
        return vm
    }

    private fun TestScope.collectRejections(vm: ProfileViewModel): List<Int> {
        val seen = mutableListOf<Int>()
        backgroundScope.launch { vm.refreshRejected.collect { seen += it } }
        runCurrent()
        return seen
    }

    /** The mock engine answers off the test dispatcher, so wait in real time until the server saw [count] requests. */
    private suspend fun awaitRequests(count: Int) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (server.requests.size < count) Thread.sleep(5) }
    }

    @Test
    fun `a pull during the cooldown is rejected with the seconds left and sends no request`() = runTest {
        val clock = MutableClock(t0)
        val vm = loaded(clock)
        val rejected = collectRejections(vm)
        val refreshing = mutableListOf<Boolean>()
        backgroundScope.launch { vm.isRefreshing.collect { refreshing += it } }
        runCurrent()
        val before = server.requests.size

        clock.now += 60_000
        vm.refresh()
        runCurrent()

        assertEquals(listOf(240), rejected)
        assertEquals(before, server.requests.size)
        assertFalse(true in refreshing)
    }

    @Test
    fun `a pull right after the first load is rejected with the full five minutes`() = runTest {
        val vm = loaded()
        val rejected = collectRejections(vm)

        vm.refresh()
        runCurrent()

        assertEquals(listOf(300), rejected)
    }

    @Test
    fun `a pull after the cooldown is allowed`() = runTest {
        val clock = MutableClock(t0)
        val vm = loaded(clock)
        val rejected = collectRejections(vm)
        val before = server.requests.size

        clock.now += cooldown
        vm.refresh()
        runCurrent()

        awaitRequests(before + 1)
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun `a successful pull restarts the cooldown`() = runTest {
        val clock = MutableClock(t0)
        val vm = loaded(clock)
        val rejected = collectRejections(vm)
        clock.now += cooldown
        vm.refresh()
        vm.cooldownSeconds.first { it == 300 } // the new load has stamped

        clock.now += 60_000
        vm.refresh()
        runCurrent()

        assertEquals(listOf(240), rejected)
    }

    @Test
    fun `a pull a millisecond early is rejected with one second left`() = runTest {
        val clock = MutableClock(t0)
        val vm = loaded(clock)
        val rejected = collectRejections(vm)
        val before = server.requests.size

        clock.now += cooldown - 1
        vm.refresh()
        runCurrent()

        assertEquals(listOf(1), rejected)
        assertEquals(before, server.requests.size)
    }

    @Test
    fun `a failed load does not start the cooldown and the retry is not rejected`() = runTest {
        store.select(sampleSelection)
        server.handler = { throw IOException("offline") }
        val vm = viewModel(MutableClock(t0))
        vm.state.first { it is ProfileUiState.Error }
        assertEquals(0, vm.cooldownSeconds.value)

        server.handler = { respondJson(sampleProfile) }
        val rejected = collectRejections(vm)
        vm.refresh() // Retry from the error state
        vm.state.first { it is ProfileUiState.Content }
        runCurrent()
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun `offline content is never blocked and never starts the cooldown`() = runTest {
        store.select(sampleSelection)
        store.cacheProfile(sampleProfile)
        server.handler = { throw IOException("offline") }
        val vm = viewModel(MutableClock(t0))
        vm.state.first { it is ProfileUiState.Content && it.offline }
        val rejected = collectRejections(vm)
        val before = server.requests.size

        vm.refresh()
        awaitRequests(before + 1)
        vm.isRefreshing.first { !it }
        vm.refresh()
        awaitRequests(before + 2)
        vm.isRefreshing.first { !it }
        runCurrent()

        assertTrue(rejected.isEmpty())
        assertEquals(0, vm.cooldownSeconds.value)
    }

    @Test
    fun `retry from the error state is never blocked`() = runTest {
        val vm = loaded()
        server.handler = { throw IOException("offline") }
        store.select(Selection("abc123", "Technoblade", "p2", "Peach"))
        vm.state.first { it is ProfileUiState.Error }
        val rejected = collectRejections(vm)
        val before = server.requests.size
        server.handler = { respondJson(sampleProfile) }

        vm.refresh()

        awaitRequests(before + 1)
        vm.state.first { it is ProfileUiState.Content }
        runCurrent()
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun `switching profile loads at once and its own fetch restarts the cooldown`() = runTest {
        val clock = MutableClock(t0)
        val vm = loaded(clock)
        val rejected = collectRejections(vm)
        clock.now += 4 * 60_000
        val before = server.requests.size

        store.select(Selection("abc123", "Technoblade", "p2", "Peach"))
        awaitRequests(before + 1)
        vm.cooldownSeconds.first { it == 300 } // the new profile's load has stamped

        // The first profile's stamp would have expired by now; the new one's has not.
        clock.now = t0 + cooldown
        vm.refresh()
        runCurrent()
        assertEquals(listOf(240), rejected)
    }

    @Test
    fun `switching to a profile that fails leaves no cooldown, so the first pull is allowed`() = runTest {
        val vm = loaded()
        server.handler = { throw IOException("offline") }

        store.select(Selection("abc123", "Technoblade", "p2", "Peach"))
        vm.state.first { it is ProfileUiState.Error }
        assertEquals(0, vm.cooldownSeconds.value)

        val rejected = collectRejections(vm)
        server.handler = { respondJson(sampleProfile) }
        vm.refresh()
        vm.state.first { it is ProfileUiState.Content }
        runCurrent()
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun `a new view model starts without a cooldown, refetches and restarts it`() = runTest {
        val clock = MutableClock(t0)
        loaded(clock)
        val before = server.requests.size

        clock.now = t0 + 120_000
        val vm2 = viewModel(clock)
        assertEquals(0, vm2.cooldownSeconds.value) // nothing carried over from the first
        vm2.state.first { it is ProfileUiState.Content }

        assertEquals(before + 1, server.requests.size) // startup always fetches
        val rejected = collectRejections(vm2)
        clock.now += 60_000
        vm2.refresh()
        runCurrent()
        assertEquals(listOf(240), rejected) // measured from the new load
    }

    @Test
    fun `a clock that went backwards allows the pull`() = runTest {
        val clock = MutableClock(t0)
        val vm = loaded(clock)
        val rejected = collectRejections(vm)
        val before = server.requests.size

        clock.now = t0 - 10 * 60_000
        vm.refresh()
        runCurrent()

        awaitRequests(before + 1)
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun `cooldownSeconds counts down from 300 to 0`() = runTest {
        val clock = object : Clock() {
            override fun millis() = t0 + testScheduler.currentTime

            override fun instant(): Instant = Instant.ofEpochMilli(millis())

            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId?): Clock = this
        }
        store.select(sampleSelection)
        server.handler = { respondJson(sampleProfile) }
        val vm = viewModel(clock)
        val seen = java.util.concurrent.CopyOnWriteArrayList<Int>()
        backgroundScope.launch { vm.cooldownSeconds.collect { seen += it } }
        vm.state.first { it is ProfileUiState.Content }
        runCurrent()
        // Subscribe and let the first value reach the collector; resumptions can land off this thread, so retry.
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (300 !in seen) { runCurrent(); Thread.sleep(5) } } }
        advanceTimeBy(cooldown + 1_000)
        runCurrent()

        assertEquals((300 downTo 0).toList(), seen.dropWhile { it == 0 }.take(301))
        assertEquals(0, vm.cooldownSeconds.value)
    }

    @Test
    fun `cooldownRemaining is clamped to the window`() {
        assertEquals(cooldown, cooldownRemaining(1_000, 1_000))
        assertEquals(1L, cooldownRemaining(0, cooldown - 1))
        assertEquals(0L, cooldownRemaining(0, cooldown))
        assertEquals(0L, cooldownRemaining(0, cooldown * 3))
        assertEquals(0L, cooldownRemaining(10_000, 0)) // clock went backwards
    }
}
