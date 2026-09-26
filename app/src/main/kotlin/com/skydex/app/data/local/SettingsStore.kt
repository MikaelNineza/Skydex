package com.skydex.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.skydex.app.data.remote.SkydexJson
import com.skydex.shared.model.Crop
import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockProfile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The player profile the app is following. */
data class Selection(val uuid: String, val username: String, val profileId: String, val cuteName: String)

data class Settings(
    val selection: Selection? = null,
    val subscribedEvents: Set<EventType> = emptySet(),
    val leadMinutes: Int = 5,
    /** Crops a Jacob's contest must include to be worth a push. Empty = any crop. */
    val jacobCrops: Set<Crop> = emptySet(),
)

/** App settings, the device's installation id, and the last fetched profile for offline use. */
@Singleton
class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    val selection: Flow<Selection?> = settings.map { it.selection }.distinctUntilChanged()

    /** The last profile fetched for the current selection, if any. */
    val cachedProfile: Flow<SkyblockProfile?> = dataStore.data.map { prefs ->
        prefs[CACHED_PROFILE]
            ?.let { runCatching { SkydexJson.decodeFromString<SkyblockProfile>(it) }.getOrNull() }
            ?.takeIf { it.profileId == prefs[PROFILE_ID] && it.uuid == prefs[UUID_KEY] }
    }

    suspend fun current(): Settings = settings.first()

    suspend fun select(selection: Selection) = dataStore.edit {
        it[UUID_KEY] = selection.uuid
        it[USERNAME] = selection.username
        it[PROFILE_ID] = selection.profileId
        it[CUTE_NAME] = selection.cuteName
        it.remove(CACHED_PROFILE)
    }

    suspend fun clearSelection() = dataStore.edit {
        for (key in listOf(UUID_KEY, USERNAME, PROFILE_ID, CUTE_NAME, CACHED_PROFILE)) it.remove(key)
    }

    suspend fun cacheProfile(profile: SkyblockProfile) = dataStore.edit {
        it[CACHED_PROFILE] = SkydexJson.encodeToString(profile)
    }

    suspend fun setEventEnabled(type: EventType, enabled: Boolean) = dataStore.edit {
        val current = it[EVENTS].orEmpty()
        it[EVENTS] = if (enabled) current + type.name else current - type.name
    }

    suspend fun setCropEnabled(crop: Crop, enabled: Boolean) = dataStore.edit {
        val current = it[JACOB_CROPS].orEmpty()
        it[JACOB_CROPS] = if (enabled) current + crop.name else current - crop.name
    }

    suspend fun setLeadMinutes(minutes: Int) = dataStore.edit { it[LEAD_MINUTES] = minutes }

    /** The last FCM token Firebase handed us, so registration works without asking Firebase again. */
    suspend fun fcmToken(): String? = dataStore.data.first()[FCM_TOKEN]

    suspend fun setFcmToken(token: String) = dataStore.edit { it[FCM_TOKEN] = token }

    /** A random id generated on first use and kept for the life of the install. */
    suspend fun installationId(): String {
        dataStore.data.first()[INSTALLATION_ID]?.let { return it }
        return dataStore.edit { if (it[INSTALLATION_ID] == null) it[INSTALLATION_ID] = UUID.randomUUID().toString() }
            .let { checkNotNull(it[INSTALLATION_ID]) }
    }

    private fun Preferences.toSettings(): Settings {
        val uuid = this[UUID_KEY]
        val profileId = this[PROFILE_ID]
        return Settings(
            selection = if (uuid != null && profileId != null) {
                Selection(uuid, this[USERNAME].orEmpty(), profileId, this[CUTE_NAME].orEmpty())
            } else {
                null
            },
            subscribedEvents = this[EVENTS].orEmpty()
                .mapNotNull { name -> EventType.entries.find { it.name == name } }
                .toSet(),
            leadMinutes = this[LEAD_MINUTES] ?: 5,
            jacobCrops = this[JACOB_CROPS].orEmpty()
                .mapNotNull { name -> Crop.entries.find { it.name == name } }
                .toSet(),
        )
    }

    private companion object {
        val UUID_KEY = stringPreferencesKey("uuid")
        val USERNAME = stringPreferencesKey("username")
        val PROFILE_ID = stringPreferencesKey("profile_id")
        val CUTE_NAME = stringPreferencesKey("cute_name")
        val CACHED_PROFILE = stringPreferencesKey("cached_profile")
        val EVENTS = stringSetPreferencesKey("subscribed_events")
        val LEAD_MINUTES = intPreferencesKey("lead_minutes")
        val JACOB_CROPS = stringSetPreferencesKey("jacob_crops")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val INSTALLATION_ID = stringPreferencesKey("installation_id")
    }
}
