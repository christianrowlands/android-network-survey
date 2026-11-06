package com.craxiom.networksurvey.scout.wifi

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Runs submitted work at once on the caller's thread and never runs scheduled work, so a test
 * drives the Scout Wi-Fi source's ticks itself.
 */
class InlineScheduledExecutor : AbstractExecutorService(), ScheduledExecutorService {
    private var shutdown = false

    override fun execute(command: Runnable) = command.run()
    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): MutableList<Runnable> = mutableListOf<Runnable>().also { shutdown() }
    override fun isShutdown() = shutdown
    override fun isTerminated() = shutdown
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> = NeverFuture<Unit>()
    override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> = NeverFuture()
    override fun scheduleAtFixedRate(command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> =
        NeverFuture<Unit>()

    override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
        NeverFuture<Unit>()

    /** A scheduled task that never runs. */
    private class NeverFuture<V> : ScheduledFuture<V> {
        private var canceled = false
        override fun compareTo(other: Delayed): Int = 0
        override fun getDelay(unit: TimeUnit): Long = Long.MAX_VALUE
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = true.also { canceled = true }
        override fun isCancelled() = canceled
        override fun isDone() = canceled
        override fun get(): V = throw UnsupportedOperationException()
        override fun get(timeout: Long, unit: TimeUnit): V = throw UnsupportedOperationException()
    }
}
