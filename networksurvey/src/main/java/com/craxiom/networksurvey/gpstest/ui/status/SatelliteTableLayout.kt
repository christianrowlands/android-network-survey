package com.craxiom.networksurvey.gpstest.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

/** The trend column's narrowest and widest; it takes the room the other columns leave. */
internal val TREND_MIN_WIDTH = 40.dp
internal val TREND_MAX_WIDTH = 96.dp

/** The flag column; the PNGs are drawn 12dp tall at their own proportions. */
internal val FLAG_COLUMN_WIDTH = 20.dp

/** The space between two columns. */
internal val COLUMN_GAP = 6.dp

/**
 * The satellite table's columns, in order.
 *
 * @param sortIndex The sort_sats index a tap on the header selects, or null when it sorts nothing.
 * @param hideOrder When the table is too narrow, columns go in this order (lowest first); null
 * columns always stay. El and Az are on the Sky page and in the spoken row.
 */
enum class TableColumn(val sortIndex: Int?, val hideOrder: Int?) {
    ID(SORT_CONSTELLATION, null),
    FLAG(null, null),
    BAND(SORT_CARRIER_FREQUENCY, null),
    CN0(SORT_SIGNAL_STRENGTH, null),
    TREND(null, null),
    AGC(SORT_AGC, null),
    EL(null, 1),
    AZ(null, 0),
    STATUS(SORT_USED_IN_FIX, null),
}

/** The sort_sats indexes the headers select. */
private const val SORT_CONSTELLATION = 0
private const val SORT_CARRIER_FREQUENCY = 1
private const val SORT_SIGNAL_STRENGTH = 2
private const val SORT_USED_IN_FIX = 3
private const val SORT_AGC = 7

/**
 * Which columns the table shows and how wide each is.
 *
 * @property scrolls True when even the narrowest table is wider than the room (very large text):
 * the header and rows then scroll sideways together.
 */
@Immutable
data class TableLayout(val widths: Map<TableColumn, Dp>, val scrolls: Boolean) {
    val columns: List<TableColumn> get() = TableColumn.entries.filter { it in widths }

    /** The width of every column and the gaps between them. */
    val totalWidth: Dp get() = widths.values.fold(0.dp) { sum, width -> sum + width } + COLUMN_GAP * (widths.size - 1)

    companion object {
        /**
         * Fits the table into [available]: the AGC column goes when no signal has AGC, then Az, then
         * El, until it fits. Room left over widens the trend column up to [TREND_MAX_WIDTH]. If it
         * still does not fit, it [scrolls].
         *
         * @param natural Each column's width for its widest expected text, the trend at its minimum.
         */
        fun fit(natural: Map<TableColumn, Dp>, available: Dp, anyAgc: Boolean): TableLayout {
            val widths = natural.toMutableMap()
            if (!anyAgc) widths.remove(TableColumn.AGC)
            val hideable = TableColumn.entries.filter { it.hideOrder != null }.sortedBy { it.hideOrder }
            for (column in hideable) {
                if (TableLayout(widths, false).totalWidth <= available) break
                widths.remove(column)
            }
            val spare = available - TableLayout(widths, false).totalWidth
            if (spare <= 0.dp) return TableLayout(widths, scrolls = spare < 0.dp)
            widths[TableColumn.TREND] = (widths.getValue(TableColumn.TREND) + spare).coerceAtMost(TREND_MAX_WIDTH)
            return TableLayout(widths, scrolls = false)
        }
    }
}

/** Formats the table's numbers in the phone's locale: decimal commas where the language uses them. */
class GnssNumbers(locale: Locale) {
    private val whole = NumberFormat.getIntegerInstance(locale)
    private val angle = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    private val oneDecimal = NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }

    /** An angle in whole degrees, for the table ("62"). */
    fun degrees(degrees: Float): String = whole.format(Math.round(degrees))

    /** An angle to a tenth of a degree, for TalkBack ("62.5"). */
    fun angle(degrees: Float): String = angle.format(degrees)

    /** A level in dB or dB-Hz, or a frequency in MHz, to one decimal. */
    fun decibels(value: Float): String = oneDecimal.format(value)
    fun megahertz(mhz: Double): String = oneDecimal.format(mhz)
}

@Composable
fun rememberGnssNumbers(): GnssNumbers {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { GnssNumbers(locale) }
}
