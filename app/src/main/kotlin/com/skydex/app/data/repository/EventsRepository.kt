package com.skydex.app.data.repository

import com.skydex.app.data.remote.SkydexApi
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.MayorStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Event data the calendar can't compute on device: the mayor in office and Jacob's contest crops. */
@Singleton
class EventsRepository @Inject constructor(private val api: SkydexApi) {

    suspend fun mayor(): MayorStatus = api.mayor()

    suspend fun contests(): List<JacobContest> = api.contests()
}
