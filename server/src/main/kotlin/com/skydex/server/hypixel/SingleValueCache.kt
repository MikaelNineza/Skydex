package com.skydex.server.hypixel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** One cached value with single-flight loading, serve-stale-on-error and failure backoff. */
internal class SingleValueCache<V : Any>(
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
