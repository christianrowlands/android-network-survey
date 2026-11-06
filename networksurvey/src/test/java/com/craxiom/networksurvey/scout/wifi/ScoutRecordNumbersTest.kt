package com.craxiom.networksurvey.scout.wifi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Windowed Scout record numbers: a leaving sink's early copy and the staying sinks' copy of the
 * same record share one number, and the sequence has no gaps or repeats.
 */
class ScoutRecordNumbersTest {
    private val counter = AtomicInteger(1)
    private val numbers = ScoutRecordNumbers(counter)

    private fun slot(windowStartMs: Long, key: String) = ScoutRecordSlot(windowStartMs, key)

    @Test
    fun `a peeked record keeps its number when the window closes`() {
        val a = slot(0, "a")
        val b = slot(0, "b")

        assertEquals(1, numbers.forPeek(a))
        assertEquals(1, numbers.forPeek(a)) // a second leaving sink gets the same number

        // b was heard after the peek; closing gives a its number and b the next one
        assertEquals(1, numbers.forClose(a))
        assertEquals(2, numbers.forClose(b))
        assertEquals(3, counter.get())
    }

    @Test
    fun `a closed slot is forgotten, so the next window numbers on`() {
        numbers.forPeek(slot(0, "a"))
        numbers.forClose(slot(0, "a"))

        assertEquals(2, numbers.forClose(slot(1_000, "a")))
    }

    @Test
    fun `old reservations that never closed are dropped`() {
        numbers.forPeek(slot(0, "never-closed"))
        numbers.forClose(slot(ScoutRecordAggregator.DEFAULT_EMITTED_RETENTION_MS + 1_000, "a"))

        // The old slot's reservation is gone, so closing it now takes a new number
        assertEquals(3, numbers.forClose(slot(0, "never-closed")))
    }
}
