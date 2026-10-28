package com.craxiom.networksurvey.ui.common

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * How a moment reads to a person, by how long ago it was: "12 minutes ago" within the hour, "Today,
 * 09:12" and "Yesterday, 16:40" for the last two days, "Mon, Oct 13, 08:05" within the year, and the
 * full date before that. The words come from the platform's ICU data, so nothing needs translating.
 */
object NsTimeText {

    /** Which wording a moment gets; see [NsTimeText]. */
    enum class Bucket { RELATIVE, TODAY, YESTERDAY, THIS_YEAR, OLDER }

    /** @return The wording for [thenMs] seen at [nowMs] in [zone]. */
    fun bucket(thenMs: Long, nowMs: Long, zone: ZoneId): Bucket {
        if (nowMs - thenMs in 0 until TimeUnit.HOURS.toMillis(1)) return Bucket.RELATIVE
        val then = Instant.ofEpochMilli(thenMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return when {
            then == today -> Bucket.TODAY
            then == today.minus(1, ChronoUnit.DAYS) -> Bucket.YESTERDAY
            then.year == today.year -> Bucket.THIS_YEAR
            else -> Bucket.OLDER
        }
    }

    /**
     * [ms] as a date and time spelled and ordered for [locale], from a skeleton such as "MMMd" with
     * the hour in [use24Hour] style appended.
     */
    fun dateTime(ms: Long, locale: Locale, dateSkeleton: String, use24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, dateSkeleton + if (use24Hour) HOUR_24 else HOUR_12)
        return SimpleDateFormat(pattern, locale).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(ms))
    }

    /** @return [ms] in the wording its [Bucket] calls for. */
    fun format(ms: Long, nowMs: Long, locale: Locale, use24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String =
        when (val bucket = bucket(ms, nowMs, zone)) {
            Bucket.RELATIVE -> RelativeAge.of(ms, nowMs).format(locale)
            Bucket.TODAY, Bucket.YESTERDAY -> {
                val formatter = RelativeDateTimeFormatter.getInstance(
                    ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.LONG,
                    DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,
                )
                val day = formatter.format(if (bucket == Bucket.TODAY) Direction.THIS else Direction.LAST, AbsoluteUnit.DAY)
                formatter.combineDateAndTime(day, dateTime(ms, locale, "", use24Hour, zone))
            }

            Bucket.THIS_YEAR -> dateTime(ms, locale, THIS_YEAR_SKELETON, use24Hour, zone)
            Bucket.OLDER -> dateTime(ms, locale, OLDER_SKELETON, use24Hour, zone)
        }

    private const val HOUR_24 = "Hmm"
    private const val HOUR_12 = "hmm"

    /** Weekday, month and day. */
    private const val THIS_YEAR_SKELETON = "EEEMMMd"

    /** Month, day and year. */
    const val OLDER_SKELETON = "MMMdyyyy"
}

/**
 * [ms] the way [NsTimeText] words it, in the phone's language and 12 or 24 hour setting. Kept
 * across recompositions, since a list can show many of these.
 */
@Composable
fun friendlyTimeText(ms: Long, nowMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(ms, nowMs, locale, use24Hour) { NsTimeText.format(ms, nowMs, locale, use24Hour) }
}

/** [ms] as a full date and time ("Oct 13, 2026, 08:05") in the phone's language and hour setting. */
@Composable
fun fullDateTimeText(ms: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(ms, locale, use24Hour) { NsTimeText.dateTime(ms, locale, NsTimeText.OLDER_SKELETON, use24Hour) }
}

/**
 * How long ago [thenMs] was at [nowMs] ("Now", "5 minutes ago", "Yesterday"), in the phone's
 * language: the one relative age every list in the app uses (see [RelativeAge]), so under a minute
 * never reads "0 minutes ago". With [midSentence] it is not capitalized, for placing inside a line.
 */
@Composable
fun relativeAgeText(thenMs: Long, nowMs: Long, midSentence: Boolean = false): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(thenMs, nowMs, locale, midSentence) { RelativeAge.of(thenMs, nowMs).format(locale, midSentence) }
}
