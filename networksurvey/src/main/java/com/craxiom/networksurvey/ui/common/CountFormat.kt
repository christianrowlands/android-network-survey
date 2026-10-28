package com.craxiom.networksurvey.ui.common

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import java.text.NumberFormat

/**
 * Writes the counts the app shows grouped for the phone's language ("9,982", "9 982"), so the
 * Survey screen, the destination pages, the survey notification and the snackbars all write a
 * number the same way. A plural still picks its form from the raw count; only the shown number is
 * formatted, so the plural's placeholder for it is `%s` (or `%1$s`), never `%d`.
 */
object CountFormat {

    /** @return [count] grouped for the phone's language: "1,204". */
    fun format(count: Long): String = NumberFormat.getIntegerInstance().format(count)

    /** @return [count] grouped for the phone's language: "1,204". */
    fun format(count: Int): String = format(count.toLong())

    /** @return The plural selector for [count], capped to what a plural lookup takes. */
    fun quantity(count: Long): Int = count.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    /**
     * @param more The plural's other arguments, after the count.
     * @return The plural [id] for [count], with the count grouped as its first argument.
     */
    fun plural(resources: Resources, @PluralsRes id: Int, count: Long, vararg more: Any): String =
        resources.getQuantityString(id, quantity(count), format(count), *more)

    /** @see plural */
    fun plural(resources: Resources, @PluralsRes id: Int, count: Int, vararg more: Any): String =
        plural(resources, id, count.toLong(), *more)
}

/**
 * @param more The plural's other arguments, after the count.
 * @return The plural [plural] for [count], with the count grouped as its first argument ("1,204 records").
 */
@Composable
fun countText(@PluralsRes plural: Int, count: Long, vararg more: Any): String =
    pluralStringResource(plural, CountFormat.quantity(count), CountFormat.format(count), *more)

/** @see countText */
@Composable
fun countText(@PluralsRes plural: Int, count: Int, vararg more: Any): String = countText(plural, count.toLong(), *more)
