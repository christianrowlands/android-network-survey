package com.craxiom.networksurvey.ui.common

import com.craxiom.networksurvey.ui.common.NsTimeText.Bucket
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Which wording a moment gets: relative within the hour, then today, yesterday, this year, older. */
class NsTimeTextTest {

    private val zone = ZoneOffset.UTC
    private val now = ms(2026, 10, 15, 14, 30)

    @Test
    fun `within the hour reads relative`() {
        assertEquals(Bucket.RELATIVE, NsTimeText.bucket(now, now, zone))
        assertEquals(Bucket.RELATIVE, NsTimeText.bucket(ms(2026, 10, 15, 13, 31), now, zone))
    }

    @Test
    fun `earlier today and yesterday name the day`() {
        assertEquals(Bucket.TODAY, NsTimeText.bucket(ms(2026, 10, 15, 0, 5), now, zone))
        assertEquals(Bucket.YESTERDAY, NsTimeText.bucket(ms(2026, 10, 14, 23, 50), now, zone))
    }

    @Test
    fun `this year leaves the year out, and older adds it`() {
        assertEquals(Bucket.THIS_YEAR, NsTimeText.bucket(ms(2026, 10, 13, 8, 5), now, zone))
        assertEquals(Bucket.THIS_YEAR, NsTimeText.bucket(ms(2026, 1, 1, 0, 0), now, zone))
        assertEquals(Bucket.OLDER, NsTimeText.bucket(ms(2025, 12, 31, 23, 59), now, zone))
    }

    @Test
    fun `a moment ahead of the clock still reads as a date, not a negative age`() {
        assertEquals(Bucket.TODAY, NsTimeText.bucket(ms(2026, 10, 15, 14, 45), now, zone))
    }

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
}
