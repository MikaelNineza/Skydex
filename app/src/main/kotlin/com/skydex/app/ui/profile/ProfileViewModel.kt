package com.skydex.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydex.app.data.local.Selection
import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.repository.DeviceRepository
import com.skydex.app.data.repository.ProfileRepository
import com.skydex.app.ui.common.UiState
import com.skydex.app.ui.common.userMessage
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.ProfileSummary
import com.skydex.shared.model.SkyblockProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    /** No player picked yet: show the search. */
    data object NoSelection : ProfileUiState

    /** [offline] means the server was unreachable and this is the cached copy. */
    data class Content(val profile: SkyblockProfile, val offline: Boolean) : ProfileUiState

    data class Error(val message: String) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val store: SettingsStore,
    private val devices: DeviceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Player search results; null until the user searches. */
    private val _search = MutableStateFlow<UiState<PlayerProfiles>?>(null)
    val search: StateFlow<UiState<PlayerProfiles>?> = _search.asStateFlow()

    private var selection: Selection? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            store.selection.collect { selected ->
                selection = selected
                loadJob?.cancel()
                if (selected == null) _state.value = ProfileUiState.NoSelection else load(selected, pull = false)
            }
        }
    }

    /** Pull-to-refresh over existing content, or a full reload from the error state. */
    fun refresh() {
        selection?.let { load(it, pull = _state.value is ProfileUiState.Content) }
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
            // The server snapshots the selected profile, so tell it about the switch.
            if (store.current().trackHistory) {
                try {
                    devices.sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Settings retries on the next change; the profile itself still loads.
                }
            }
        }
    }

    private fun load(selected: Selection, pull: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (pull) _isRefreshing.value = true else _state.value = ProfileUiState.Loading
            try {
                val result = repository.profile(selected)
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
}
