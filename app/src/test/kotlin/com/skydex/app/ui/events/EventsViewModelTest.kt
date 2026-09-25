package com.skydex.app.ui.events

import com.skydex.app.MainDispatcherRule
import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** A clock that follows the test's virtual time, starting at [start]. */
    private fun TestScope.clock(start: Long) = object : Clock() {
        override fun millis() = start + testScheduler.currentTime

        override fun instant(): Instant = Instant.ofEpochMilli(millis())

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId) = this
    }

    private fun EventsViewModel.statuses() = (state.value as EventsUiState.Content).rows.map { it.status }

    @Test
    fun `an unimplemented calendar shows the unavailable state`() = runTest {
        val vm = EventsViewModel(clock(0)) { _, _ -> TODO("calendar task") }
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(EventsUiState.Unavailable, vm.state.value)
    }

    @Test
    fun `countdowns tick every second and past events are dropped`() = runTest {
        val start = 1_000_000L
        val events = listOf(
            SkyblockEvent(EventType.BANK_INTEREST, start - 1_000, start - 1_000),
            SkyblockEvent(EventType.JACOBS_CONTEST, start - 100_000, start + 200_000),
            SkyblockEvent(EventType.DARK_AUCTION, start + 65_000, start + 125_000),
        )
        var window = 0L
        val vm = EventsViewModel(clock(start)) { _, windowMillis ->
            window = windowMillis
            events
        }
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(24 * 60 * 60 * 1000L, window)
        assertEquals(listOf("Happening now · ends in 3m 20s", "in 1m 05s"), vm.statuses())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("Happening now · ends in 3m 19s", "in 1m 04s"), vm.statuses())
    }
}
