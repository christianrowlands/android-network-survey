package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.model.RecordType
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Per record type counts for the current survey session, plus the time the last record arrived.
 * They back the Recording screen's records panel and its "last one N s ago" heartbeat.
 *
 * Counts are bumped from the record processing thread and read from the UI, so every field is an
 * atomic and no lock is held.
 */
class SurveySessionCounters(private val clock: () -> Long = System::currentTimeMillis) {

    private val counts = AtomicLongArray(RecordType.entries.size)
    private val lastRecordAtMs = AtomicLong(NO_RECORD)

    /** Counts [count] new records of [type]. */
    fun increment(type: RecordType, count: Int = 1) {
        if (count <= 0) return
        counts.addAndGet(type.ordinal, count.toLong())
        lastRecordAtMs.set(clock())
    }

    /** @return The number of [type] records in this session. */
    fun count(type: RecordType): Long = counts.get(type.ordinal)

    /** @return The number of records of every type in this session. */
    fun total(): Long = RecordType.entries.sumOf { counts.get(it.ordinal) }

    /** @return When the last record of any type arrived, in epoch milliseconds, or null if none has. */
    fun lastRecordTimeMs(): Long? = lastRecordAtMs.get().takeIf { it != NO_RECORD }

    /** @return The count of every record type, including zeros. */
    fun snapshot(): Map<RecordType, Long> = RecordType.entries.associateWith { counts.get(it.ordinal) }

    /** Clears every count, for a new session. */
    fun reset() {
        for (i in 0 until counts.length()) counts.set(i, 0)
        lastRecordAtMs.set(NO_RECORD)
    }

    private companion object {
        const val NO_RECORD = -1L
    }
}
