package com.skydex.server.hypixel

import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.MayorStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

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

/** One cached value with single-flight loading, serve-stale-on-error and failure backoff. */
private class SingleValueCache<V : Any>(
    private val ttlMillis: Long,
    private val failureBackoffMillis: Long,
    private val clock: () -> Long,
) {
    private class Entry<V>(val value: V, val storedAt: Long)

    private val mutex = Mutex()

    @Volatile
    private var entry: Entry<V>? = null

    // Guarded by mutex.
    private var retryAt = 0L
    private var lastFailure: Exception? = null

    private fun fresh(now: Long): V? = entry?.takeIf { now - it.storedAt < ttlMillis }?.value

    suspend fun get(load: suspend () -> V): V {
        fresh(clock())?.let { return it }
        return mutex.withLock {
            val now = clock()
            fresh(now)?.let { return@withLock it }
            val stale = entry?.value
            if (now < retryAt) return@withLock stale ?: throw checkNotNull(lastFailure)
            try {
                load().also {
                    entry = Entry(it, now)
                    lastFailure = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
                retryAt = now + backoffMillis(e)
                stale ?: throw e
            }
        }
    }

    private fun backoffMillis(e: Exception): Long {
        val rateLimit = generateSequence(e.cause) { it.cause }.filterIsInstance<RateLimitedException>().firstOrNull()
        return maxOf(failureBackoffMillis, (rateLimit?.retryAfterSeconds ?: 0) * 1000)
    }
}
