package com.craxiom.networksurvey.ui.common

import com.craxiom.networksurvey.ui.common.NsTimeText.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * Which wording a moment gets: relative within the hour, then today, yesterday, this year, older.
 * With relative wording off, a moment is placed by its date alone. Runs on Robolectric for the
 * platform's ICU and date patterns that [NsTimeText.format] uses.
 */
@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `without relative wording, just after midnight a moment before it is yesterday`() {
        val chicago = ZoneId.of("America/Chicago")
        val then = ms(2026, 10, 14, 23, 50, chicago)
        val seenAt = ms(2026, 10, 15, 0, 10, chicago)

        assertEquals(Bucket.YESTERDAY, NsTimeText.bucket(then, seenAt, chicago, relative = false))
        assertEquals(Bucket.RELATIVE, NsTimeText.bucket(then, seenAt, chicago, relative = true))
    }

    @Test
    fun `without relative wording, five minutes ago reads as today and its time`() {
        val fiveMinutesAgo = ms(2026, 10, 15, 14, 25)

        val fixed = NsTimeText.format(fiveMinutesAgo, now, Locale.US, use24Hour = true, zone = zone, relative = false)
        val relative = NsTimeText.format(fiveMinutesAgo, now, Locale.US, use24Hour = true, zone = zone, relative = true)

        assertEquals(Bucket.TODAY, NsTimeText.bucket(fiveMinutesAgo, now, zone, relative = false))
        assertTrue("Expected the today wording: $fixed", fixed.startsWith("Today"))
        assertTrue("Expected the time of day: $fixed", fixed.contains("14:25"))
        assertFalse("Expected the relative wording: $relative", relative.startsWith("Today"))
    }

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = ZoneOffset.UTC): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
