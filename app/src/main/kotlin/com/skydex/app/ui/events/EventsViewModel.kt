package com.skydex.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydex.app.ui.common.formatCountdown
import com.skydex.shared.calendar.SkyblockEvents
import com.skydex.shared.model.SkyblockEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class EventRow(val event: SkyblockEvent, val happeningNow: Boolean, val status: String)

sealed interface EventsUiState {
    data object Loading : EventsUiState

    /** The shared calendar can't compute events (not implemented yet). */
    data object Unavailable : EventsUiState

    data class Content(val rows: List<EventRow>) : EventsUiState
}

/** Events come from the shared Skyblock calendar, computed on device; no server call. */
@HiltViewModel
class EventsViewModel internal constructor(
    private val clock: Clock,
    private val upcoming: (nowMillis: Long, windowMillis: Long) -> List<SkyblockEvent>,
) : ViewModel() {

    @Inject constructor(clock: Clock) : this(clock, SkyblockEvents::upcoming)

    /** Recomputed every second so countdowns tick. */
    val state: StateFlow<EventsUiState> = flow {
        while (true) {
            val now = clock.millis()
            val events = try {
                upcoming(now, WINDOW_MILLIS)
            } catch (_: NotImplementedError) {
                emit(EventsUiState.Unavailable)
                return@flow
            }
            emit(EventsUiState.Content(events.filter { it.startsAt > now || it.endsAt > now }.map { it.toRow(now) }))
            delay(1000 - now % 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState.Loading)

    private fun SkyblockEvent.toRow(now: Long): EventRow {
        val happeningNow = startsAt <= now
        val status = if (happeningNow) {
            "Happening now · ends in ${formatCountdown(endsAt - now)}"
        } else {
            "in ${formatCountdown(startsAt - now)}"
        }
        return EventRow(this, happeningNow, status)
    }

    companion object {
        const val WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    }
}
