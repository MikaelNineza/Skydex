package com.skydex.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydex.app.data.local.Settings
import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.repository.DeviceRepository
import com.skydex.app.notifications.PushTokens
import com.skydex.app.ui.common.userMessage
import com.skydex.shared.model.Crop
import com.skydex.shared.model.EventType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val settings: Settings,
    val pushConfigured: Boolean,
    /** Set when the last change couldn't be sent to the server. */
    val syncError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val devices: DeviceRepository,
    pushTokens: PushTokens,
) : ViewModel() {

    private val syncError = MutableStateFlow<String?>(null)

    /** null until settings are read from disk. */
    val state: StateFlow<SettingsUiState?> = combine(store.settings, syncError) { settings, error ->
        SettingsUiState(settings, pushTokens.isConfigured, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Forgets the selected profile so the Profile tab shows the search again. */
    fun changePlayer() = update(syncNeeded = { it.trackHistory }) { store.clearSelection() }

    fun setTrackHistory(enabled: Boolean) = update { store.setTrackHistory(enabled) }

    fun setEventEnabled(type: EventType, enabled: Boolean) = update { store.setEventEnabled(type, enabled) }

    fun setCropEnabled(crop: Crop, enabled: Boolean) = update { store.setCropEnabled(crop, enabled) }

    fun setLeadMinutes(minutes: Int) = update { store.setLeadMinutes(minutes) }

    fun retrySync() = update { }

    /** Syncs run one at a time, so their results (and [syncError]) land in order. */
    private val syncMutex = Mutex()

    /** A change is saved locally but no sync has picked it up yet. Only touched on the main thread. */
    private var syncPending = false

    private fun update(syncNeeded: (Settings) -> Boolean = { true }, change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            if (!syncNeeded(store.current())) return@launch
            syncPending = true
            syncMutex.withLock {
                // A sync that started after our change already sent it (sync sends the latest settings): e.g. rapid
                // crop chip taps collapse into one or two PUTs.
                if (!syncPending) return@withLock
                syncPending = false
                syncError.value = try {
                    devices.sync()
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Couldn't save to the server: ${e.userMessage()}"
                }
            }
        }
    }

    companion object {
        val LEAD_MINUTES = listOf(1, 5, 10, 15)
    }
}
