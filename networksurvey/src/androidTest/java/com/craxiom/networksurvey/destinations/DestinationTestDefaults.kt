package com.craxiom.networksurvey.destinations

import java.util.concurrent.TimeUnit

/** Every fake server and broker in the destination tests listens on the phone's own loopback. */
const val LOOPBACK_HOST = "127.0.0.1"

/** How long the destination tests wait for an asynchronous effect before failing. */
const val DEFAULT_AWAIT_TIMEOUT_MS = 20_000L

private const val POLL_INTERVAL_MS = 100L

/**
 * Polls [condition] every 100 ms until it holds or [timeoutMs] passes.
 *
 * @return true if the condition was met in time.
 */
fun awaitCondition(timeoutMs: Long = DEFAULT_AWAIT_TIMEOUT_MS, condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    while (System.nanoTime() < deadline) {
        if (condition()) return true
        Thread.sleep(POLL_INTERVAL_MS)
    }
    return condition()
}
