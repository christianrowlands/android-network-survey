package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * What a stream's record queues hold right now, shared by every queue of one connection so the
 * Survey screen can say how many records are waiting and whether any are being thrown away, and
 * when the connection started trying to connect.
 */
class StreamQueueHealth {
    private val pending = AtomicInteger()
    private val dropped = AtomicLong()
    private val fullQueues = AtomicInteger()

    @Volatile
    var connectingSinceMs: Long = 0L
        private set

    @Volatile
    var everConnected: Boolean = false
        private set

    /** @return The records waiting to be sent, across every queue. */
    fun pendingCount(): Int = pending.get()

    /** @return The records thrown away because a queue was full, since [onConnectRequested]. */
    fun droppedCount(): Long = dropped.get()

    /** @return True while any queue is full, so new records of that kind are thrown away. */
    fun isDropping(): Boolean = fullQueues.get() > 0

    /** A new connection was requested: the drop count and the connected history start over. */
    fun onConnectRequested(nowMs: Long) {
        dropped.set(0)
        everConnected = false
        connectingSinceMs = nowMs
    }

    /** The connection moved to CONNECTING (a first attempt or a retry). */
    fun onConnecting(nowMs: Long) {
        connectingSinceMs = nowMs
    }

    /** The connection succeeded. */
    fun onConnected() {
        everConnected = true
    }

    internal fun added() = pending.incrementAndGet()
    internal fun removed(count: Int) = pending.addAndGet(-count)
    internal fun droppedOne() = dropped.incrementAndGet()
    internal fun queueFull() = fullQueues.incrementAndGet()
    internal fun queueDrained() = fullQueues.decrementAndGet()
}

/**
 * A record queue for one stream that holds records while the connection is down or still
 * connecting, so they are sent once it connects instead of being thrown away.
 *
 * The queue is bounded by the streaming queue limit, or by
 * [NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT] when the limit is turned off, so an
 * unreachable server can't fill the phone's memory. When it is full, new records are dropped and
 * counted, and it accepts records again once it drains below half the limit.
 *
 * @param health The counters shared by every queue of the connection.
 * @param limit The streaming queue limit; 0 or less means the default bound.
 */
class StreamRecordQueue<T : Any>(private val health: StreamQueueHealth, private val limit: () -> Int) {
    private val queue = ConcurrentLinkedQueue<T>()
    private val size = AtomicInteger()
    private val full = AtomicBoolean(false)

    private fun bound(): Int = limit().takeIf { it > 0 } ?: NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT

    /**
     * Adds a record unless the queue is full.
     *
     * @return True if the record was queued, false if it was dropped.
     */
    fun offer(record: T): Boolean {
        if (!full.get()) {
            // Count first, so several threads can't all pass the check before any of them counts
            if (size.incrementAndGet() <= bound()) {
                health.added()
                queue.add(record)
                return true
            }
            size.decrementAndGet()
            if (full.compareAndSet(false, true)) health.queueFull()
        }
        health.droppedOne()
        return false
    }

    /** Adds every record in [records], dropping the ones that don't fit. */
    fun offerAll(records: Collection<T>) {
        records.forEach { offer(it) }
    }

    /** @return The oldest record, or null if the queue is empty. */
    fun poll(): T? {
        val record = queue.poll() ?: return null
        val remaining = size.decrementAndGet()
        health.removed(1)
        if (full.get() && remaining < (bound() + 1) / 2 && full.compareAndSet(true, false)) health.queueDrained()
        return record
    }

    /** Throws away every queued record (the stream was turned off). */
    fun clear() {
        var removed = 0
        while (queue.poll() != null) removed++
        if (removed > 0) {
            size.addAndGet(-removed)
            health.removed(removed)
        }
        if (full.compareAndSet(true, false)) health.queueDrained()
    }

    /** @return The number of queued records. */
    fun size(): Int = size.get()
}
