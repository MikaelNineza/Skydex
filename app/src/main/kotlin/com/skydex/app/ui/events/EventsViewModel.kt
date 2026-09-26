package com.skydex.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydex.app.data.repository.EventsRepository
import com.skydex.app.ui.common.UiState
import com.skydex.app.ui.common.formatCountdown
import com.skydex.app.ui.common.userMessage
import com.skydex.shared.calendar.ActivePerks
import com.skydex.shared.calendar.SkyblockEvents
import com.skydex.shared.calendar.activePerks
import com.skydex.shared.calendar.isPerkpocalypse
import com.skydex.shared.model.Crop
import com.skydex.shared.model.EventCategory
import com.skydex.shared.model.EventType
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.SkyblockEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The next (or running) occurrence of one event type. For Jacob's contests [crops] is empty when the contest list
 * loaded without this contest (a new year whose crops nobody has uploaded yet), and null if it hasn't loaded.
 */
data class EventCard(
    val type: EventType,
    val next: SkyblockEvent,
    val happeningNow: Boolean,
    val status: String,
    val crops: List<Crop>? = null,
)

/** The upcoming occurrences of [type], shown when its card is tapped. */
data class EventDetail(val type: EventType, val rows: List<EventCard>)

sealed interface EventsUiState {
    data object Loading : EventsUiState

    /** The shared calendar can't compute events (not implemented yet). */
    data object Unavailable : EventsUiState

    data class Content(
        /**
         * Events that come at least about once a day, soonest first. Perk-dependent events are left out while the
         * mayor is unknown.
         */
        val common: List<EventCard>,
        /** Events that come at least once per Skyblock year, soonest first. */
        val seasonal: List<EventCard>,
        /** Events that come once every few Skyblock years, soonest first. */
        val rare: List<EventCard>,
        val mayor: UiState<MayorStatus>,
        /** Jerry is mayor: perk events can't be predicted. */
        val perkpocalypse: Boolean,
        val detail: EventDetail?,
        /** e.g. "Term ends in 2d 03h 04m", once the mayor is known. */
        val termStatus: String? = null,
    ) : EventsUiState
}

/**
 * Events are computed on device from the shared Skyblock calendar. The mayor (whose perks add events) and Jacob's
 * contest crops come from our server and are refreshed every few minutes.
 */
@HiltViewModel
class EventsViewModel internal constructor(
    private val clock: Clock,
    /** null means no live data: perk-dependent events stay hidden. */
    private val repository: EventsRepository?,
    private val calendar: (type: EventType, fromMillis: Long, count: Int, perks: ActivePerks) -> List<SkyblockEvent>,
) : ViewModel() {

    @Inject constructor(clock: Clock, repository: EventsRepository) :
        this(clock, repository, SkyblockEvents::occurrences)

    private class Live(
        val mayor: UiState<MayorStatus>,
        val contests: List<JacobContest>,
        /** The contest list has loaded at least once, so a missing contest means its crops aren't known yet. */
        val contestsLoaded: Boolean = false,
    )

    private val selected = MutableStateFlow<EventType?>(null)

    /** Ticks every second so countdowns update. */
    private val ticks: Flow<Long> = flow {
        while (true) {
            val now = clock.millis()
            emit(now)
            delay(1000 - now % 1000)
        }
    }

    /**
     * The last live data, kept for the ViewModel's lifetime so returning to the tab neither flashes Loading nor
     * drops perk events and crops. Only [refreshLive] writes it.
     */
    private val liveState = MutableStateFlow(
        Live(if (repository == null) UiState.Error("Mayor unavailable") else UiState.Loading, emptyList()),
    )

    /** When mayor and contests last both loaded, per [clock]; null before the first success. */
    private var lastLiveSuccess: Long? = null

    /** Wakes the refresh loop early (the Retry button). */
    private val retryRequests = Channel<Unit>(Channel.CONFLATED)

    /** [liveState], refreshed every [LIVE_REFRESH_MILLIS] (or [LIVE_RETRY_MILLIS] after a failure) while collected. */
    private val live: Flow<Live> = channelFlow {
        if (repository != null) {
            launch {
                while (true) {
                    val since = lastLiveSuccess?.let { clock.millis() - it }
                    val wait = when {
                        since != null && since < LIVE_REFRESH_MILLIS -> LIVE_REFRESH_MILLIS - since
                        refreshLive(repository) -> LIVE_REFRESH_MILLIS
                        else -> LIVE_RETRY_MILLIS
                    }
                    withTimeoutOrNull(wait) { retryRequests.receive() }?.let { lastLiveSuccess = null }
                }
            }
        }
        liveState.collect { send(it) }
    }

    /** Loads mayor and contests, keeping the previous good value of each on failure. True if both loaded. */
    private suspend fun refreshLive(repository: EventsRepository): Boolean {
        val previous = liveState.value
        var mayorLoaded = true
        val mayor = try {
            UiState.Content(repository.mayor())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mayorLoaded = false
            previous.mayor as? UiState.Content ?: UiState.Error(e.userMessage())
        }
        // Without crops, contest cards simply don't list them.
        var contestsLoaded = true
        val contests = try {
            repository.contests()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            contestsLoaded = false
            previous.contests
        }
        liveState.value = Live(mayor, contests, contestsLoaded || previous.contestsLoaded)
        val ok = mayorLoaded && contestsLoaded
        if (ok) lastLiveSuccess = clock.millis()
        return ok
    }

    val state: StateFlow<EventsUiState> = combine(ticks, live, selected) { now, live, selected ->
        build(now, live, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState.Loading)

    /** Reloads the mayor now, e.g. after it failed to load. */
    fun retryLive() {
        if (repository == null) return
        liveState.update { if (it.mayor is UiState.Error) Live(UiState.Loading, it.contests, it.contestsLoaded) else it }
        retryRequests.trySend(Unit)
    }

    /** Shows the upcoming occurrences of [type], or closes them when null. */
    fun select(type: EventType?) {
        selected.value = type
    }

    private fun build(now: Long, live: Live, selected: EventType?): EventsUiState {
        val mayor = (live.mayor as? UiState.Content)?.data
        val perks = mayor?.activePerks() ?: ActivePerks.NONE
        val crops = live.contests.associate { it.startsAt to it.crops }
        // Ask from now + 1 so an instantaneous event at exactly now gives way to its next occurrence instead of
        // leaving the type with nothing for a tick; the filter guards against a calendar returning past events.
        fun upcoming(type: EventType, count: Int) =
            calendar(type, now + 1, count, perks).filter { it.startsAt > now || it.endsAt > now }
        return try {
            val cards = EventType.entries
                .mapNotNull { type -> upcoming(type, 1).firstOrNull()?.toCard(now, crops, live.contestsLoaded) }
                .sortedBy { it.next.startsAt }
            val detail = selected?.let { type ->
                EventDetail(
                    type,
                    upcoming(type, DETAIL_COUNT).map { it.toCard(now, crops, live.contestsLoaded) },
                )
            }
            EventsUiState.Content(
                common = cards.filter { it.type.category == EventCategory.COMMON },
                seasonal = cards.filter { it.type.category == EventCategory.SEASONAL },
                rare = cards.filter { it.type.category == EventCategory.RARE },
                mayor = live.mayor,
                perkpocalypse = mayor?.isPerkpocalypse() == true,
                detail = detail,
                termStatus = mayor?.let { "Term ends in ${formatCountdown(it.termEndsAt - now)}" },
            )
        } catch (_: NotImplementedError) {
            EventsUiState.Unavailable
        }
    }

    private fun SkyblockEvent.toCard(now: Long, crops: Map<Long, List<Crop>>, cropsLoaded: Boolean): EventCard {
        val happeningNow = startsAt <= now
        val status = if (happeningNow) {
            "Happening now · ends in ${formatCountdown(endsAt - now)}"
        } else {
            "in ${formatCountdown(startsAt - now)}"
        }
        val contestCrops = when {
            type != EventType.JACOBS_CONTEST -> null
            else -> crops[startsAt] ?: emptyList<Crop>().takeIf { cropsLoaded }
        }
        return EventCard(type, this, happeningNow, status, contestCrops)
    }

    companion object {
        /** Occurrences listed when a card is tapped. */
        const val DETAIL_COUNT = 10
        const val LIVE_REFRESH_MILLIS = 5 * 60 * 1000L
        const val LIVE_RETRY_MILLIS = 30 * 1000L
    }
}
