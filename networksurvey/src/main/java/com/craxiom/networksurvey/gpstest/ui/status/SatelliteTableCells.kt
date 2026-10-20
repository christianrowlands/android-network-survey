package com.craxiom.networksurvey.gpstest.ui.status

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.ui.sky.skyColors
import com.craxiom.networksurvey.ui.common.LABEL_MAX_FONT_SCALE
import com.craxiom.networksurvey.ui.common.NsSparkline
import com.craxiom.networksurvey.ui.common.TrendSeries
import com.craxiom.networksurvey.ui.common.signalTextColor
import com.craxiom.networksurvey.ui.theme.NsTheme

/** A row's minimum height: dense, so a phone shows most of the sky at once. */
private val ROW_MIN_HEIGHT = 36.dp

/** The sparkline's height in a row. */
private val TREND_HEIGHT = 20.dp

/** The flag and its outline. */
private val FLAG_HEIGHT = 12.dp
private val FLAG_OUTLINE = 1.dp

/** The widest text each column expects, measured to size the column. */
private const val WIDEST_ID = "000"
private const val WIDEST_BAND = "E5a"
private const val WIDEST_LEVEL = "00.0"
private const val WIDEST_AGC = "-00.0"
private const val WIDEST_ELEVATION = "00"
private const val WIDEST_AZIMUTH = "000"

/**
 * Measures each column for its widest expected text at the current font scale, the headers'
 * capped scale included, and fits them into [available].
 */
@Composable
fun rememberTableLayout(rows: List<SatelliteRow>, available: Dp, numbers: GnssNumbers): TableLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val value = MaterialTheme.typography.bodyMedium
    val header = MaterialTheme.typography.labelMedium
    val headerScale = density.fontScale.coerceAtMost(LABEL_MAX_FONT_SCALE) / density.fontScale
    fun width(text: String, style: TextStyle): Dp = with(density) { measurer.measure(text, style).size.width.toDp() }
    fun headerWidth(text: String): Dp = width(text, header) * headerScale

    val bands = rows.map { bandText(it, numbers) } + WIDEST_BAND
    val letters = listOf(R.string.gnss_flag_used, R.string.gnss_flag_ephemeris, R.string.gnss_flag_almanac).map { stringResource(it) }
    val statusSlot = letters.maxOf { width(it, value) }
    val natural = mapOf(
        TableColumn.ID to maxOf(width(WIDEST_ID, value), headerWidth(stringResource(R.string.gnss_col_id))),
        TableColumn.FLAG to FLAG_COLUMN_WIDTH,
        TableColumn.BAND to maxOf(bands.maxOf { width(it, value) }, headerWidth(stringResource(R.string.gnss_col_band))),
        TableColumn.CN0 to maxOf(width(WIDEST_LEVEL, value), headerWidth(stringResource(R.string.gnss_col_cn0))),
        TableColumn.TREND to maxOf(TREND_MIN_WIDTH, headerWidth(stringResource(R.string.gnss_trend_window))),
        TableColumn.AGC to maxOf(width(WIDEST_AGC, value), headerWidth(stringResource(R.string.sort_sats_agc))),
        TableColumn.EL to maxOf(width(WIDEST_ELEVATION, value), headerWidth(stringResource(R.string.gnss_col_el))),
        TableColumn.AZ to maxOf(width(WIDEST_AZIMUTH, value), headerWidth(stringResource(R.string.gnss_col_az))),
        TableColumn.STATUS to maxOf(statusSlot * letters.size + NsTheme.spacing.xs * (letters.size - 1), headerWidth(letters.joinToString(""))),
    )
    return TableLayout.fit(natural, available, anyAgc = rows.any { it.agcDb != null })
}

/** @return What the Band column shows: the band ("L1", "E5a") or, with none, the MHz ("1575.4"). */
internal fun bandText(row: SatelliteRow, numbers: GnssNumbers): String = when (val carrier = row.carrier) {
    is Carrier.Band -> carrier.label
    is Carrier.Frequency -> numbers.megahertz(carrier.mhz)
    null -> ""
}

/**
 * One signal in the table. ID, band, position and status dim when the signal is not used in the
 * fix; C/N0, its trend and AGC stay full strength, since a strong signal left out of the fix is
 * worth seeing. One TalkBack item.
 *
 * @param nowMs The time the trends end at, the right edge of the sparkline.
 */
@Composable
fun SatelliteTableRow(
    row: SatelliteRow,
    trend: TrendSeries,
    nowMs: Long,
    layout: TableLayout,
    numbers: GnssNumbers,
    scroll: ScrollState?,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val identity = if (row.usedInFix) ink else muted
    val cn0 = row.cn0DbHz?.let(numbers::decibels)
    val spoken = spokenRow(row, numbers, trend)
    val cell: @Composable (TableColumn) -> Unit = { column ->
        Box(Modifier.width(layout.widths.getValue(column)), contentAlignment = column.alignment) {
            when (column) {
                TableColumn.ID -> CellText(row.svid.toString(), identity)
                TableColumn.FLAG -> row.system?.let {
                    Image(
                        painter = painterResource(it.flag),
                        contentDescription = null,
                        modifier = Modifier.height(FLAG_HEIGHT).border(FLAG_OUTLINE, MaterialTheme.colorScheme.outlineVariant),
                    )
                }

                TableColumn.BAND -> CellText(bandText(row, numbers), identity)
                TableColumn.CN0 -> CellText(cn0 ?: stringResource(R.string.signal_none), if (cn0 != null) signalTextColor(row.cn0Bucket) else muted)
                TableColumn.TREND -> NsSparkline(
                    series = trend,
                    nowMs = nowMs,
                    windowMs = TREND_WINDOW_MS,
                    gapMs = TREND_GAP_MS,
                    min = CN0_TREND_MIN,
                    max = CN0_TREND_MAX,
                    endColor = skyColors().cn0Color(row.cn0DbHz ?: 0f),
                    modifier = Modifier.fillMaxWidth().height(TREND_HEIGHT),
                )

                TableColumn.AGC -> CellText(row.agcDb?.let(numbers::decibels) ?: stringResource(R.string.signal_none), if (row.agcDb != null) ink else muted)
                TableColumn.EL -> CellText(row.elevationDegrees?.let(numbers::degrees) ?: stringResource(R.string.signal_none), identity)
                TableColumn.AZ -> CellText(row.azimuthDegrees?.let(numbers::degrees) ?: stringResource(R.string.signal_none), identity)
                TableColumn.STATUS -> StatusLetters(row, identity)
            }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken }
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = NsTheme.spacing.panelPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics {}.sideways(scroll),
            horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            layout.columns.forEach { cell(it) }
        }
    }
}

@Composable
private fun CellText(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** U, E and A, each in its own slot so the letters line up down the column; a missing one is blank. */
@Composable
private fun StatusLetters(row: SatelliteRow, color: Color) {
    val letters = listOf(
        row.usedInFix to R.string.gnss_flag_used,
        row.hasEphemeris to R.string.gnss_flag_ephemeris,
        row.hasAlmanac to R.string.gnss_flag_almanac,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
        letters.forEach { (has, letter) ->
            Text(
                text = stringResource(letter),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.alpha(if (has) 1f else 0f),
            )
        }
    }
}

/** @return What TalkBack reads for a row: name and number, carrier, C/N0 and its range, position, AGC, what is missing. */
@Composable
private fun spokenRow(row: SatelliteRow, numbers: GnssNumbers, trend: TrendSeries): String {
    val name = row.system?.let { stringResource(it.shortName) } ?: stringResource(R.string.unknown)
    val cn0 = row.cn0DbHz?.let(numbers::decibels)
    val range = trend.range()?.takeIf { trend.points.size > 1 }
    return buildList {
        add(stringResource(R.string.gnss_row_spoken_name, name, row.svid))
        bandText(row, numbers).takeIf { it.isNotEmpty() }?.let(::add)
        add(if (cn0 != null) stringResource(R.string.gnss_row_spoken_cn0, cn0) else stringResource(R.string.gnss_row_spoken_not_heard))
        range?.let { add(stringResource(R.string.gnss_row_spoken_range, numbers.decibels(it.start), numbers.decibels(it.endInclusive))) }
        if (row.elevationDegrees != null && row.azimuthDegrees != null) {
            add(stringResource(R.string.gnss_row_spoken_elevation, numbers.angle(row.elevationDegrees)))
            add(stringResource(R.string.gnss_row_spoken_azimuth, numbers.angle(row.azimuthDegrees)))
        }
        row.agcDb?.let { add(stringResource(R.string.gnss_row_agc, numbers.decibels(it))) }
        if (!row.usedInFix) add(stringResource(R.string.gnss_row_not_used))
        if (!row.hasEphemeris) add(stringResource(R.string.gnss_row_no_ephemeris))
        if (!row.hasAlmanac) add(stringResource(R.string.gnss_row_no_almanac))
    }.joinToString(", ")
}
