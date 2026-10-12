package com.skydex.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydex.app.data.local.Selection
import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.repository.ProfileRepository
import com.skydex.app.ui.common.UiState
import com.skydex.app.ui.common.userMessage
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.ProfileSummary
import com.skydex.shared.model.SkyblockProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    /** No player picked yet: show the search. */
    data object NoSelection : ProfileUiState

    /** [offline] means the server was unreachable and this is the cached copy. */
    data class Content(val profile: SkyblockProfile, val offline: Boolean) : ProfileUiState

    data class Error(val message: String) : ProfileUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val store: SettingsStore,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** When the current selection last loaded from the server. Kept in memory only: a new process starts fresh. */
    private val fetchedAt = MutableStateFlow<Long?>(null)

    /** Whole seconds until a pull is allowed again, ticking once a second while collected; 0 when allowed. */
    val cooldownSeconds: StateFlow<Int> = fetchedAt
        .flatMapLatest { at ->
            if (at == null) {
                flowOf(0)
            } else {
                flow {
                    while (true) {
                        val remaining = cooldownRemaining(at, clock.millis())
                        emit(((remaining + 999) / 1000).toInt())
                        if (remaining <= 0) break
                        val wait = remaining % 1000
                        delay(if (wait == 0L) 1000 else wait)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), 0)

    /** A pull was refused during the cooldown; carries whole seconds left, for the snackbar. */
    private val _refreshRejected = MutableSharedFlow<Int>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val refreshRejected: Flow<Int> = _refreshRejected.asSharedFlow()

    /** Player search results; null until the user searches. */
    private val _search = MutableStateFlow<UiState<PlayerProfiles>?>(null)
    val search: StateFlow<UiState<PlayerProfiles>?> = _search.asStateFlow()

    private var selection: Selection? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            store.selection.collect { selected ->
                val changed = selected?.uuid != selection?.uuid || selected?.profileId != selection?.profileId
                if (changed) fetchedAt.value = null
                selection = selected
                loadJob?.cancel()
                if (selected == null) _state.value = ProfileUiState.NoSelection else load(selected, pull = false)
            }
        }
    }

    /**
     * Pull-to-refresh over existing content, or a full reload from the error state. A pull over fresh online content
     * within [REFRESH_COOLDOWN_MILLIS] of the last server fetch is refused and reported on [refreshRejected].
     */
    fun refresh() {
        val selected = selection ?: return
        val content = _state.value as? ProfileUiState.Content
        when {
            content == null -> load(selected, pull = false)
            content.offline -> load(selected, pull = true)
            else -> {
                val remaining = fetchedAt.value?.let { cooldownRemaining(it, clock.millis()) } ?: 0
                if (remaining > 0) {
                    _refreshRejected.tryEmit(((remaining + 999) / 1000).toInt())
                } else {
                    load(selected, pull = true)
                }
            }
        }
    }

    fun search(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _search.value = UiState.Loading
            _search.value = try {
                UiState.Content(repository.findPlayer(name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.userMessage())
            }
        }
    }

    fun pick(player: PlayerProfiles, profile: ProfileSummary) {
        viewModelScope.launch {
            store.select(Selection(player.uuid, player.username, profile.profileId, profile.cuteName))
            _search.value = null
        }
    }

    private fun load(selected: Selection, pull: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (pull) _isRefreshing.value = true else _state.value = ProfileUiState.Loading
            try {
                val result = repository.profile(selected)
                if (!result.fromCache) fetchedAt.value = clock.millis()
                _state.value = ProfileUiState.Content(result.profile, result.fromCache)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ProfileUiState.Error(e.userMessage())
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    companion object {
        /** How long after a server fetch a pull-to-refresh is refused. */
        const val REFRESH_COOLDOWN_MILLIS = 5 * 60 * 1000L
    }
}

/** Millis left of the cooldown for a fetch at [fetchedAt]; 0 if it has passed or the clock went backwards. */
internal fun cooldownRemaining(fetchedAt: Long, now: Long): Long {
    val elapsed = now - fetchedAt
    return if (elapsed < 0) 0 else (ProfileViewModel.REFRESH_COOLDOWN_MILLIS - elapsed).coerceAtLeast(0)
}
