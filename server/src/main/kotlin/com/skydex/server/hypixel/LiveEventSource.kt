package com.skydex.server.hypixel

import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.MayorStatus

/** Event data that can't be computed from the calendar: the mayor in office and Jacob's contest crops. */
interface LiveEventSource {
    /** Throws [UpstreamException] if Hypixel fails. */
    suspend fun mayor(): MayorStatus

    /** Contests that haven't ended yet, soonest first. Throws [UpstreamException] if elitebot fails. */
    suspend fun contests(): List<JacobContest>
}

/** A contest lasts 20 real minutes. */
private const val CONTEST_MILLIS = 20 * 60_000L

/**
 * [LiveEventSource] backed by Hypixel and elitebot, cached for [mayorTtlMillis] and [contestTtlMillis] so app
 * requests and the alert job don't call them every time. Concurrent callers share one upstream request; when a
 * refresh fails the last good value is served, and the upstream isn't retried for [failureBackoffMillis] (or its
 * Retry-After, if longer).
 */
class CachedLiveEventSource(
    private val election: ElectionClient,
    private val elite: EliteClient,
    private val clock: () -> Long = System::currentTimeMillis,
    mayorTtlMillis: Long = 5 * 60_000,
    // elitebot publishes a whole year of contests at once, so they rarely change.
    contestTtlMillis: Long = 30 * 60_000,
    failureBackoffMillis: Long = 60_000,
) : LiveEventSource {
    private val mayors = SingleValueCache<MayorStatus>(mayorTtlMillis, failureBackoffMillis, clock)
    private val contests = SingleValueCache<List<JacobContest>>(contestTtlMillis, failureBackoffMillis, clock)

    override suspend fun mayor(): MayorStatus = mayors.get { election.election() }

    override suspend fun contests(): List<JacobContest> {
        val now = clock()
        return contests.get { elite.contestsNow() }.filter { it.startsAt + CONTEST_MILLIS > now }
    }
}
