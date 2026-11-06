package com.craxiom.networksurvey.scout.link

/**
 * The wait between Scout reconnect attempts: [initialDelayMs] after the first failed attempt,
 * multiplied by [multiplier] after each further one, never more than [maxDelayMs]. The Scout
 * manager only reconnects while something needs the Scout, so the backoff never runs idle.
 */
class ReconnectBackoff(
    val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
) {
    /** @return The wait after failed attempt number [attempt] (1-based; 0 or less counts as 1). */
    fun delayAfterAttemptMs(attempt: Int): Long {
        var delay = initialDelayMs.toDouble()
        val factor = multiplier.coerceAtLeast(1.0)
        repeat((attempt - 1).coerceAtLeast(0)) {
            delay *= factor
            if (delay >= maxDelayMs) return maxDelayMs
        }
        return delay.toLong().coerceAtMost(maxDelayMs)
    }

    companion object {
        /** The first wait, 1 s. */
        const val DEFAULT_INITIAL_DELAY_MS = 1_000L

        /** Each wait doubles. */
        const val DEFAULT_MULTIPLIER = 2.0

        /** The longest wait, 30 s. */
        const val DEFAULT_MAX_DELAY_MS = 30_000L
    }
}
