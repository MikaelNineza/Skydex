package com.skydex.app.data.repository

import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.remote.SkydexApi
import com.skydex.app.notifications.PushTokens
import com.skydex.shared.model.DeviceRegistration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the server's record of this device in step with the user's settings. */
@Singleton
class DeviceRepository @Inject constructor(
    private val api: SkydexApi,
    private val store: SettingsStore,
    private val pushTokens: PushTokens,
) {
    private val mutex = Mutex()

    /**
     * PUTs the current registration, or DELETEs it when the device wants no alerts (or push isn't configured).
     * Calls run one at a time and each sends the latest settings, so rapid toggles can't land out of order.
     */
    suspend fun sync() = mutex.withLock {
        val settings = store.current()
        val installationId = store.installationId()
        val events = if (pushTokens.isConfigured) settings.subscribedEvents else emptySet()
        if (events.isEmpty()) {
            api.unregisterDevice(installationId)
            return@withLock
        }
        api.registerDevice(
            installationId,
            DeviceRegistration(
                // Empty until Firebase registers; the messaging service re-syncs then.
                fcmToken = pushTokens.token().orEmpty(),
                subscribedEvents = events,
                leadMinutes = settings.leadMinutes,
                jacobCrops = settings.jacobCrops,
            ),
        )
    }
}
