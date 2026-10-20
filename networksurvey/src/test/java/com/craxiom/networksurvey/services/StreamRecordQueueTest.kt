package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the bounded queue that holds gRPC records while the connection is down, and the shared
 * counters the Survey screen reads.
 */
class StreamRecordQueueTest {
    private val health = StreamQueueHealth()

    @Test
    fun `holds records in order and counts them as pending`() {
        val queue = StreamRecordQueue<String>(health) { 10 }
        queue.offer("1")
        queue.offer("2")

        assertEquals(2, health.pendingCount())
        assertEquals("1", queue.poll())
        assertEquals("2", queue.poll())
        assertNull(queue.poll())
        assertEquals(0, health.pendingCount())
    }

    @Test
    fun `a full queue drops and counts, then accepts again below half the limit`() {
        val queue = StreamRecordQueue<Int>(health) { 4 }
        repeat(4) { assertTrue(queue.offer(it)) }

        assertFalse(queue.offer(99))
        assertFalse(queue.offer(100))
        assertTrue(health.isDropping())
        assertEquals(2L, health.droppedCount())

        queue.poll()
        queue.poll()
        assertTrue("Still full at half the limit", health.isDropping())
        queue.poll()
        assertFalse(health.isDropping())
        assertTrue(queue.offer(5))
    }

    @Test
    fun `pending is the sum across queues, and clear removes a queue's share`() {
        val cellular = StreamRecordQueue<Int>(health) { 10 }
        val wifi = StreamRecordQueue<Int>(health) { 10 }
        cellular.offerAll(listOf(1, 2, 3))
        wifi.offer(4)
        assertEquals(4, health.pendingCount())

        cellular.clear()
        assertEquals(1, health.pendingCount())
        assertEquals(0, cellular.size())
    }

    @Test
    fun `clearing a full queue stops the dropping`() {
        val queue = StreamRecordQueue<Int>(health) { 1 }
        queue.offer(1)
        queue.offer(2)
        assertTrue(health.isDropping())

        queue.clear()
        assertFalse(health.isDropping())
    }

    @Test
    fun `a limit of one accepts again once its record is sent`() {
        val queue = StreamRecordQueue<Int>(health) { 1 }
        queue.offer(1)
        assertFalse(queue.offer(2))

        queue.poll()
        assertFalse(health.isDropping())
        assertTrue(queue.offer(3))
    }

    @Test
    fun `no limit means the default bound, not an unbounded queue`() {
        val queue = StreamRecordQueue<Int>(health) { NetworkSurveyConstants.STREAMING_QUEUE_LIMIT_DISABLED }
        repeat(NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT) { queue.offer(it) }

        assertFalse(queue.offer(-1))
        assertEquals(NetworkSurveyConstants.DEFAULT_STREAMING_QUEUE_LIMIT, queue.size())
    }

    @Test
    fun `a new connect request resets the drop count and the connected history`() {
        val queue = StreamRecordQueue<Int>(health) { 1 }
        queue.offer(1)
        queue.offer(2)
        health.onConnected()

        health.onConnectRequested(nowMs = 1_000L)

        assertEquals(0L, health.droppedCount())
        assertFalse(health.everConnected)
        assertEquals(1_000L, health.connectingSinceMs)
    }
}
