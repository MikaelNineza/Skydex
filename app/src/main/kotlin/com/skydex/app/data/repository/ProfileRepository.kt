package com.skydex.app.data.repository

import com.skydex.app.data.local.Selection
import com.skydex.app.data.local.SettingsStore
import com.skydex.app.data.remote.SkydexApi
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.StatsHistory
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/** A profile plus whether it came from the offline cache because the server was unreachable. */
data class ProfileResult(val profile: SkyblockProfile, val fromCache: Boolean)

@Singleton
class ProfileRepository @Inject constructor(private val api: SkydexApi, private val store: SettingsStore) {

    suspend fun findPlayer(name: String): PlayerProfiles = api.player(name.trim())

    /** Fetches the profile and caches it; falls back to the cached copy if the fetch fails. */
    suspend fun profile(selection: Selection): ProfileResult = try {
        val profile = api.profile(selection.uuid, selection.profileId)
        store.cacheProfile(profile)
        ProfileResult(profile, fromCache = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        store.cachedProfile.first()?.let { ProfileResult(it, fromCache = true) } ?: throw e
    }

    suspend fun history(selection: Selection, days: Int): StatsHistory =
        api.history(selection.uuid, selection.profileId, days)
}
