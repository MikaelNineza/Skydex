package com.skydex.server.hypixel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SingleValueCacheTest {
    private var now = 1_000_000L
    private val cache = SingleValueCache<String>(ttlMillis = 1_000, failureBackoffMillis = 500) { now }

    @Test
    fun concurrentGetsShareOneLoad() = runBlocking<Unit> {
        var loads = 0
        val gate = CompletableDeferred<Unit>()
        val results = List(5) {
            async {
                cache.get {
                    loads++
                    gate.await()
                    "value"
                }
            }
        }
        repeat(10) { yield() }
        gate.complete(Unit)
        assertEquals(List(5) { "value" }, results.awaitAll())
        assertEquals(1, loads)
    }

    @Test
    fun freshWithinTtlThenReloads() = runBlocking<Unit> {
        var loads = 0
        assertEquals("v1", cache.get { "v${++loads}" })
        now += 999
        assertEquals("v1", cache.get { "v${++loads}" })
        now += 1
        assertEquals("v2", cache.get { "v${++loads}" })
        assertEquals(2, loads)
    }

    @Test
    fun servesStaleOnErrorAndBacksOff() = runBlocking<Unit> {
        var loads = 0
        cache.get { loads++; "old" }
        now += 2_000
        assertEquals("old", cache.get { loads++; throw UpstreamException("down") })
        // Within the backoff no load is attempted.
        now += 499
        assertEquals("old", cache.get { loads++; "new" })
        assertEquals(2, loads)
        now += 1
        assertEquals("new", cache.get { loads++; "new" })
        assertEquals(3, loads)
    }

    @Test
    fun backoffHonoursRateLimit() = runBlocking<Unit> {
        var loads = 0
        cache.get { loads++; "old" }
        now += 2_000
        cache.get { loads++; throw UpstreamException("limited", RateLimitedException(10)) }
        now += 9_999
        assertEquals("old", cache.get { loads++; "new" })
        assertEquals(2, loads)
        now += 1
        assertEquals("new", cache.get { loads++; "new" })
    }

    @Test
    fun withoutValueRethrowsLastFailureDuringBackoff() = runBlocking<Unit> {
        val failure = UpstreamException("down")
        var loads = 0
        assertSame(failure, assertFailsWith<UpstreamException> { cache.get { loads++; throw failure } })
        now += 100
        assertSame(failure, assertFailsWith<UpstreamException> { cache.get { loads++; "never" } })
        assertEquals(1, loads)
        now += 400
        assertEquals("ok", cache.get { loads++; "ok" })
        assertEquals(2, loads)
    }
}
