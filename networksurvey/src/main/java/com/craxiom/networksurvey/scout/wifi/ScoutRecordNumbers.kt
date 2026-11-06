package com.craxiom.networksurvey.scout.wifi

import java.util.concurrent.atomic.AtomicInteger

/**
 * The one record an item gets in one aggregation window: the window's start and the item's
 * aggregator key (a BSSID, a client MAC, or a client MAC and requested SSID).
 */
data class ScoutRecordSlot(val windowStartMs: Long, val key: Any)

/**
 * Record numbers for windowed Scout records, so each record keeps one number whichever sink gets it.
 *
 * A sink that leaves while a window is still open gets that window early ([forPeek]); the sinks
 * that stay get it when it closes ([forClose]). Both take the number reserved for the record's
 * [ScoutRecordSlot], so the same record never has two numbers and no number is skipped: a slot
 * reserved by a peek is used once more at close and then forgotten.
 *
 * Thread safe: peeks run on the caller's thread, closes on the record processing thread.
 *
 * @param counter The record number sequence it shares (the phone's Wi-Fi numbers, for beacons).
 */
class ScoutRecordNumbers(private val counter: AtomicInteger) {
    private val reserved = HashMap<ScoutRecordSlot, Int>()

    /** @return The number for [slot] in an open window, reserving the next one the first time. */
    @Synchronized
    fun forPeek(slot: ScoutRecordSlot?): Int {
        if (slot == null) return counter.getAndIncrement()
        return reserved.getOrPut(slot) { counter.getAndIncrement() }
    }

    /** @return The number for [slot] in a closed window: its reserved number, or the next one. */
    @Synchronized
    fun forClose(slot: ScoutRecordSlot?): Int {
        if (slot == null) return counter.getAndIncrement()
        val number = reserved.remove(slot) ?: counter.getAndIncrement()
        // A reserved slot whose record was filtered out at close would otherwise stay forever
        reserved.keys.removeAll { it.windowStartMs < slot.windowStartMs - FORGET_AFTER_MS }
        return number
    }

    /** @return [forPeek] when [peek], else [forClose]. */
    fun next(slot: ScoutRecordSlot?, peek: Boolean): Int = if (peek) forPeek(slot) else forClose(slot)

    private companion object {
        /** Windows older than this, relative to the one closing, can no longer close. */
        const val FORGET_AFTER_MS = ScoutRecordAggregator.DEFAULT_EMITTED_RETENTION_MS
    }
}
