package com.craxiom.networksurvey.ui.common

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.icu.util.ULocale
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * How long ago something happened, in the one unit worth saying: "Now", "5 minutes ago", "Yesterday",
 * "3 weeks ago", "2 years ago". The buckets are the tower sheet's: under a minute is now, then
 * minutes, hours, days (under a week), weeks (under 30 days), months (30-day months, under a year)
 * and years. A time in the future (clock skew) is now.
 */
data class RelativeAge(val unit: AgeUnit, val amount: Long) {

    /** The unit of an age; [AgeUnit.NOW] has no amount. */
    enum class AgeUnit { NOW, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

    /**
     * This age in [locale]'s words, from the platform's ICU data (no translation needed). With
     * [midSentence], a phrase that would be capitalized on its own ("Yesterday") is not, so it can be
     * placed inside a sentence ("Started yesterday").
     */
    fun format(locale: Locale, midSentence: Boolean = false): String {
        val capitalization = if (midSentence) {
            DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE
        } else {
            DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
        }
        val formatter = RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.LONG, capitalization
        )
        val relativeUnit = when (unit) {
            AgeUnit.NOW -> return formatter.format(Direction.PLAIN, AbsoluteUnit.NOW)
            AgeUnit.DAYS -> if (amount == 1L) return formatter.format(Direction.LAST, AbsoluteUnit.DAY) else RelativeUnit.DAYS
            AgeUnit.MINUTES -> RelativeUnit.MINUTES
            AgeUnit.HOURS -> RelativeUnit.HOURS
            AgeUnit.WEEKS -> RelativeUnit.WEEKS
            AgeUnit.MONTHS -> RelativeUnit.MONTHS
            AgeUnit.YEARS -> RelativeUnit.YEARS
        }
        return formatter.format(amount.toDouble(), Direction.LAST, relativeUnit)
    }

    companion object {
        private const val DAYS_PER_WEEK = 7
        private const val DAYS_PER_MONTH = 30
        private const val DAYS_PER_YEAR = 365

        /** The age of [thenMs] at [nowMs], both in milliseconds since the epoch. */
        fun of(thenMs: Long, nowMs: Long): RelativeAge {
            val diff = nowMs - thenMs
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> RelativeAge(AgeUnit.NOW, 0)
                diff < TimeUnit.HOURS.toMillis(1) -> RelativeAge(AgeUnit.MINUTES, TimeUnit.MILLISECONDS.toMinutes(diff))
                diff < TimeUnit.DAYS.toMillis(1) -> RelativeAge(AgeUnit.HOURS, TimeUnit.MILLISECONDS.toHours(diff))
                days < DAYS_PER_WEEK -> RelativeAge(AgeUnit.DAYS, days)
                days < DAYS_PER_MONTH -> RelativeAge(AgeUnit.WEEKS, days / DAYS_PER_WEEK)
                days < DAYS_PER_YEAR -> RelativeAge(AgeUnit.MONTHS, days / DAYS_PER_MONTH)
                else -> RelativeAge(AgeUnit.YEARS, days / DAYS_PER_YEAR)
            }
        }
    }
}
