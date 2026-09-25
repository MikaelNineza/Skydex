package com.skydex.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.repository.ProfileRepository
import com.skydex.app.ui.common.userMessage
import com.skydex.shared.model.StatsHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

sealed interface StatsUiState {
    data object Loading : StatsUiState

    data object NoSelection : StatsUiState

    data class Content(val history: StatsHistory) : StatsUiState

    data class Error(val message: String) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(repository: ProfileRepository, store: SettingsStore) : ViewModel() {

    private val _days = MutableStateFlow(30)
    val days: StateFlow<Int> = _days.asStateFlow()

    private val retries = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StatsUiState> = combine(store.selection, _days, retries) { selection, days, _ ->
        selection to days
    }.transformLatest { (selection, days) ->
        if (selection == null) {
            emit(StatsUiState.NoSelection)
            return@transformLatest
        }
        emit(StatsUiState.Loading)
        emit(
            try {
                StatsUiState.Content(repository.history(selection, days))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StatsUiState.Error(e.userMessage())
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    fun setDays(days: Int) {
        _days.value = days
    }

    fun retry() {
        retries.value++
    }

    companion object {
        val RANGES = listOf(7, 30, 90)
    }
}
