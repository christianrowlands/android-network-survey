package com.craxiom.networksurvey.gpstest.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.gpstest.ui.sky.PanelTitle
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsSparkline
import com.craxiom.networksurvey.ui.common.TrendSeries
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlin.math.abs

/** A band's sparkline height: taller than a row's, there is room and it is the panel's point. */
private val TREND_HEIGHT = 28.dp

/** AGC lines never span less than this, so receiver noise does not fill the line like a step. */
private const val AGC_MIN_SPAN_DB = 10f

/** A value this far from its window's median gets its change written beside it. */
private const val NOTABLE_CHANGE_DB = 3f

/**
 * AGC and C/N0 by band over the last two minutes: one block per band with the band's AGC and the
 * average C/N0 of its four strongest signals, each with its line and a dashed line at its median,
 * so a step in either stands out. It reports what the receiver saw; the info button explains what
 * the lines can and cannot say.
 *
 * @param filtered True while the constellation filter hides some systems (the panel never filters).
 */
@Composable
fun BandTrendPanel(trends: GnssTrends, filtered: Boolean, numbers: GnssNumbers, modifier: Modifier = Modifier) {
    var explain by rememberSaveable { mutableStateOf(false) }
    NsPanel(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PanelTitle(stringResource(R.string.gnss_trends_title), Modifier.weight(1f))
            Text(
                text = stringResource(R.string.gnss_trend_window),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { explain = true }) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.gnss_trends_info))
            }
        }
        if (!trends.agcReported) Note(stringResource(R.string.gnss_trends_no_agc))
        if (filtered) Note(stringResource(R.string.gnss_trends_all_systems))
        Column(
            modifier = Modifier.padding(top = NsTheme.spacing.s),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            trends.bands.forEach { BandBlock(it, trends.nowMs, trends.agcReported, numbers) }
        }
    }
    if (explain) {
        NsMessageDialog(
            title = stringResource(R.string.gnss_trends_info_title),
            message = stringResource(R.string.gnss_trends_info_body),
            onDismiss = { explain = false },
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One band: its title, then its AGC (when the phone reports any) beside its C/N0. One TalkBack item. */
@Composable
private fun BandBlock(band: BandTrend, nowMs: Long, agcReported: Boolean, numbers: GnssNumbers) {
    val title = bandTitle(band.group)
    val agcValue = band.agc.last?.let { stringResource(R.string.db_value_label, numbers.decibels(it)) }
    val cn0Value = band.cn0Top.last?.let(numbers::decibels)
    val spoken = buildList {
        add(title)
        if (agcReported) {
            add(stringResource(R.string.gnss_row_agc, band.agc.last?.let(numbers::decibels) ?: stringResource(R.string.signal_none)))
            changeSpoken(band.agc, numbers)?.let(::add)
        }
        add(stringResource(R.string.gnss_trends_spoken_cn0, cn0Value ?: stringResource(R.string.signal_none)))
        changeSpoken(band.cn0Top, numbers)?.let(::add)
    }.joinToString(", ")

    Column(Modifier.fillMaxWidth().semantics { contentDescription = spoken }) {
        // The band speaks as one item; its parts say nothing on their own.
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            BandCells(band, nowMs, agcReported, agcValue, cn0Value, numbers)
        }
    }
}

/** The band's AGC (when the phone reports any) beside its C/N0. */
@Composable
private fun BandCells(band: BandTrend, nowMs: Long, agcReported: Boolean, agcValue: String?, cn0Value: String?, numbers: GnssNumbers) {
    Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.l)) {
        if (agcReported) {
            val (low, high) = agcScale(band.agc)
            TrendCell(stringResource(R.string.sort_sats_agc), agcValue, band.agc, nowMs, low, high, numbers, Modifier.weight(1f))
        }
        TrendCell(stringResource(R.string.gnss_trends_cn0_top4), cn0Value, band.cn0Top, nowMs, CN0_TREND_MIN, CN0_TREND_MAX, numbers, Modifier.weight(1f))
    }
}

/** A value with its name and, when it has moved, its change from the median; its line under it. */
@Composable
private fun TrendCell(
    name: String,
    value: String?,
    series: TrendSeries,
    nowMs: Long,
    min: Float,
    max: Float,
    numbers: GnssNumbers,
    modifier: Modifier,
) {
    val median = series.median()
    val change = notableChange(series)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: stringResource(R.string.signal_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (change != null) {
                Text(
                    text = signed(change, numbers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NsTheme.colors.status.warningText,
                )
            }
        }
        NsSparkline(
            series = series,
            nowMs = nowMs,
            windowMs = TREND_WINDOW_MS,
            gapMs = TREND_GAP_MS,
            min = min,
            max = max,
            baseline = median,
            modifier = Modifier.fillMaxWidth().height(TREND_HEIGHT),
        )
    }
}

/** @return The band group's name: its signals ("L1 · E1 · B1C"), or Other, or All signals. */
@Composable
private fun bandTitle(group: GnssBandGroup): String = group.title ?: when (group) {
    GnssBandGroup.ALL_SIGNALS -> stringResource(R.string.gnss_trends_all_signals)
    else -> stringResource(R.string.gnss_trends_other)
}

/**
 * @return The AGC line's scale: its range, widened around its middle to at least
 * [AGC_MIN_SPAN_DB], so a steady line stays a steady line.
 */
internal fun agcScale(series: TrendSeries): Pair<Float, Float> {
    val range = series.range() ?: return 0f to AGC_MIN_SPAN_DB
    val middle = (range.start + range.endInclusive) / 2
    val half = maxOf(range.endInclusive - range.start, AGC_MIN_SPAN_DB) / 2
    return (middle - half) to (middle + half)
}

/** @return How far the newest value is from the window's median, when that is notable; else null. */
internal fun notableChange(series: TrendSeries): Float? {
    val last = series.last ?: return null
    val median = series.median() ?: return null
    return (last - median).takeIf { abs(it) >= NOTABLE_CHANGE_DB }
}

/** "+3.4" or "-6.2", in the phone's locale. */
private fun signed(change: Float, numbers: GnssNumbers): String =
    if (change > 0) "+${numbers.decibels(change)}" else numbers.decibels(change)

/** @return "down 6.2 from the 2 minute median", or null when the value has not moved notably. */
@Composable
private fun changeSpoken(series: TrendSeries, numbers: GnssNumbers): String? {
    val change = notableChange(series) ?: return null
    val amount = numbers.decibels(abs(change))
    return stringResource(if (change < 0) R.string.gnss_trends_spoken_down else R.string.gnss_trends_spoken_up, amount)
}
