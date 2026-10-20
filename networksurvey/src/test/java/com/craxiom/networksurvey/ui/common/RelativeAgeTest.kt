package com.craxiom.networksurvey.ui.common

import com.craxiom.networksurvey.ui.common.RelativeAge.AgeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * How long ago a tower or survey point time was: one unit per bucket, a future time is now, and
 * the words come from the platform for the language (capitalized only when standing alone).
 */
@RunWith(RobolectricTestRunner::class)
class RelativeAgeTest {

    private val now = TimeUnit.DAYS.toMillis(20_000)

    private fun ageOf(diffMs: Long) = RelativeAge.of(now - diffMs, now)

    @Test
    fun eachBucket_hasItsUnitAndAmount() {
        assertEquals(RelativeAge(AgeUnit.NOW, 0), ageOf(TimeUnit.SECONDS.toMillis(59)))
        assertEquals(RelativeAge(AgeUnit.MINUTES, 1), ageOf(TimeUnit.MINUTES.toMillis(1)))
        assertEquals(RelativeAge(AgeUnit.MINUTES, 59), ageOf(TimeUnit.MINUTES.toMillis(60) - 1))
        assertEquals(RelativeAge(AgeUnit.HOURS, 1), ageOf(TimeUnit.HOURS.toMillis(1)))
        assertEquals(RelativeAge(AgeUnit.DAYS, 1), ageOf(TimeUnit.DAYS.toMillis(1)))
        assertEquals(RelativeAge(AgeUnit.DAYS, 6), ageOf(TimeUnit.DAYS.toMillis(7) - 1))
        assertEquals(RelativeAge(AgeUnit.WEEKS, 1), ageOf(TimeUnit.DAYS.toMillis(7)))
        assertEquals(RelativeAge(AgeUnit.WEEKS, 4), ageOf(TimeUnit.DAYS.toMillis(29)))
        assertEquals(RelativeAge(AgeUnit.MONTHS, 1), ageOf(TimeUnit.DAYS.toMillis(30)))
        assertEquals(RelativeAge(AgeUnit.MONTHS, 12), ageOf(TimeUnit.DAYS.toMillis(364)))
        assertEquals(RelativeAge(AgeUnit.YEARS, 1), ageOf(TimeUnit.DAYS.toMillis(365)))
        assertEquals(RelativeAge(AgeUnit.YEARS, 3), ageOf(TimeUnit.DAYS.toMillis(3 * 365 + 10)))
    }

    @Test
    fun aTimeInTheFuture_isNow() {
        assertEquals(RelativeAge(AgeUnit.NOW, 0), ageOf(-TimeUnit.MINUTES.toMillis(5)))
    }

    @Test
    fun english_readsAsAgo_andYesterdayFollowsTheSentence() {
        assertEquals("5 minutes ago", RelativeAge(AgeUnit.MINUTES, 5).format(Locale.US))
        assertEquals("2 years ago", RelativeAge(AgeUnit.YEARS, 2).format(Locale.US))
        assertEquals("Yesterday", RelativeAge(AgeUnit.DAYS, 1).format(Locale.US))
        assertEquals("yesterday", RelativeAge(AgeUnit.DAYS, 1).format(Locale.US, midSentence = true))
        assertEquals("3 days ago", RelativeAge(AgeUnit.DAYS, 3).format(Locale.US))
    }
}
