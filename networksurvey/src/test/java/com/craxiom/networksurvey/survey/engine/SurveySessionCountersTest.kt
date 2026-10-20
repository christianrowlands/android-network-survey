package com.craxiom.networksurvey.survey.engine

import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the per record type session counters behind the Recording screen.
 */
class SurveySessionCountersTest {

    private var now = 1_000L
    private val counters = SurveySessionCounters { now }

    @Test
    fun `counts are kept per record type with a total`() {
        counters.increment(RecordType.CELLULAR)
        counters.increment(RecordType.WIFI, 7)
        counters.increment(RecordType.CDR)

        assertEquals(1L, counters.count(RecordType.CELLULAR))
        assertEquals(7L, counters.count(RecordType.WIFI))
        assertEquals(9L, counters.total())
        assertEquals(0L, counters.snapshot()[RecordType.GNSS])
    }

    @Test
    fun `the last record time moves with every record and zero counts are ignored`() {
        assertNull(counters.lastRecordTimeMs())
        counters.increment(RecordType.GNSS)
        assertEquals(1_000L, counters.lastRecordTimeMs())

        now = 5_000L
        counters.increment(RecordType.GNSS, 0)
        assertEquals(1_000L, counters.lastRecordTimeMs())

        counters.increment(RecordType.GNSS)
        assertEquals(5_000L, counters.lastRecordTimeMs())
    }

    @Test
    fun `reset clears everything`() {
        counters.increment(RecordType.BLUETOOTH, 3)
        counters.reset()
        assertEquals(0L, counters.total())
        assertNull(counters.lastRecordTimeMs())
    }
}
