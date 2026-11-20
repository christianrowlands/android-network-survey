package com.craxiom.networksurvey

/** How many times [waitFor] tries before giving up. */
private const val WAIT_ATTEMPTS = 100

/** How long [waitFor] sleeps between tries, in milliseconds. */
private const val WAIT_STEP_MS = 50L

/**
 * Polls [attempt] until it returns a value, for a test whose code under test works on a
 * background thread the test cannot join. Gives up after [WAIT_ATTEMPTS] tries, [WAIT_STEP_MS]
 * apart (five seconds).
 *
 * @return The first non-null value [attempt] returned, or null when it never returned one.
 */
fun <T> waitFor(attempt: () -> T?): T? {
    repeat(WAIT_ATTEMPTS) {
        attempt()?.let { return it }
        Thread.sleep(WAIT_STEP_MS)
    }
    return null
}
