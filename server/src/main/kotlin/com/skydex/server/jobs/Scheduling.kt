package com.skydex.server.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import kotlin.time.Duration

/**
 * Runs [block] now and then every [interval] until this scope is cancelled.
 * A failing run is logged and doesn't stop later runs.
 */
fun CoroutineScope.launchEvery(interval: Duration, log: Logger, block: suspend () -> Unit): Job = launch {
    while (true) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Job run failed", e)
        }
        delay(interval)
    }
}
