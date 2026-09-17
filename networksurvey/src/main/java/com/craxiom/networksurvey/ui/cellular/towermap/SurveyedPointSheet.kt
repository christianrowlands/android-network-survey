package com.craxiom.networksurvey.ui.cellular.towermap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.model.SurveyedPointEntity
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointFeatures
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointSelection
import com.craxiom.networksurvey.ui.watchlist.MapSheetSurface
import com.craxiom.networksurvey.ui.watchlist.SheetDetent
import com.craxiom.networksurvey.util.CalculationUtils
import com.craxiom.networksurvey.util.SignalBuckets

/** Visible height of the survey point sheet at its Peek detent, before the nav-bar inset. */
val SURVEYED_POINT_SHEET_PEEK: Dp = 96.dp

/** Surveyed points store epoch milliseconds; the tower sheet's time helpers take epoch seconds. */
internal const val MILLIS_PER_SECOND = 1000L

/**
 * The draggable sheet that explains a tapped survey point: one line at Peek (kind,
 * technology, signal, age), everything at Half. Several overlapping points show as a list; a
 * zoomed-out cell shows a summary of the places it covers.
 */
@Composable
fun SurveyedPointSheet(
    selection: SurveyedPointSelection,
    state: AnchoredDraggableState<SheetDetent>,
    sheetHeight: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val single = selection.aggregateCount == null && selection.points.size == 1
    var focused by remember(selection) { mutableStateOf(if (single) selection.points.first() else null) }
    val point = focused

    MapSheetSurface(
        state = state,
        sheetHeight = sheetHeight,
        modifier = modifier,
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (point != null && !single) {
                    IconButton(onClick = { focused = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
                Text(
                    text = when {
                        point != null -> peekLine(point)
                        selection.aggregateCount != null -> pluralStringResource(
                            R.plurals.survey_point_aggregate_title,
                            selection.aggregateCount,
                            selection.aggregateCount
                        )

                        else -> pluralStringResource(
                            R.plurals.survey_point_multiple_title,
                            selection.points.size,
                            selection.points.size
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.survey_points_hint_dismiss)
                    )
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when {
                    point != null -> PointDetails(point)
                    selection.aggregateCount != null -> AggregateDetails(selection)
                    else -> selection.points.forEach { candidate ->
                        Text(
                            text = peekLine(candidate),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { focused = candidate }
                                .padding(vertical = 8.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    )
}

@Composable
private fun peekLine(point: SurveyedPointEntity): String {
    val ago = getRelativeTimeString(point.time / MILLIS_PER_SECOND)
    return when (point.observedMask) {
        SurveyedPointEntity.OBSERVED_CELLULAR -> {
            val tech = techLabel(SurveyedPointFeatures.techOf(point.protocol, point.nrScg))
            val signal = if (point.signal != 0) {
                stringResource(
                    R.string.survey_point_dbm,
                    point.signal
                ) + " " + bucketLabel(point.signalBucket)
            } else bucketLabel(SignalBuckets.UNKNOWN)
            "${stringResource(R.string.survey_points_kind_cellular)} · $tech · $signal · $ago"
        }

        SurveyedPointEntity.OBSERVED_WIFI -> stringResource(
            R.string.survey_point_peek_heard,
            stringResource(R.string.survey_points_kind_wifi),
            pluralStringResource(
                R.plurals.survey_point_networks_count,
                point.deviceCount,
                point.deviceCount
            ),
            point.signal,
            ago
        )

        else -> stringResource(
            R.string.survey_point_peek_heard,
            stringResource(R.string.survey_points_kind_bluetooth),
            pluralStringResource(
                R.plurals.survey_point_devices_count,
                point.deviceCount,
                point.deviceCount
            ),
            point.signal,
            ago
        )
    }
}

@Composable
private fun PointDetails(point: SurveyedPointEntity) {
    val cellular = point.observedMask == SurveyedPointEntity.OBSERVED_CELLULAR
    if (cellular) {
        val tech = SurveyedPointFeatures.techOf(point.protocol, point.nrScg)
        DetailRow(
            stringResource(R.string.survey_point_signal),
            if (point.signal != 0) {
                stringResource(
                    R.string.survey_point_dbm_metric,
                    point.signal,
                    metricName(point.protocol)
                ) + " · " + bucketLabel(point.signalBucket)
            } else bucketLabel(SignalBuckets.UNKNOWN),
            swatch = SurveyedPointPalette.bucket(point.signalBucket)
        )
        if (point.signal2 != 0) {
            DetailRow(
                secondaryMetricName(point.protocol),
                stringResource(R.string.survey_point_dbm, point.signal2)
            )
        }
        DetailRow(
            stringResource(R.string.survey_point_provider),
            listOfNotNull(point.provider, point.plmn?.let { "($it)" }).joinToString(" ")
                .ifBlank { bucketLabel(SignalBuckets.UNKNOWN) },
            swatch = point.plmn?.let { SurveyedPointPalette.plmn(it) }
        )
        DetailRow(
            stringResource(R.string.survey_point_technology),
            if (point.nrScg == 1) "${techLabel(SurveyedPointFeatures.TECH_LTE)} + ${stringResource(R.string.survey_point_nsa)}" else techLabel(
                tech
            ),
            swatch = SurveyedPointPalette.tech(tech)
        )
        point.cellId?.let { cellId ->
            val enb =
                if (point.protocol == SurveyedPointEntity.PROTOCOL_LTE && point.cid in 0..Int.MAX_VALUE) {
                    stringResource(
                        R.string.survey_point_enb_sector,
                        CalculationUtils.getEnodebIdFromCellId(point.cid.toInt()),
                        CalculationUtils.getSectorIdFromCellId(point.cid.toInt())
                    )
                } else null
            DetailRow(
                stringResource(R.string.survey_point_cell),
                cellId,
                secondary = enb,
                swatch = SurveyedPointPalette.hashed(SurveyedPointFeatures.colorIndex(cellId))
            )
            DetailRow(
                stringResource(R.string.survey_point_area),
                stringResource(
                    if (point.protocol >= SurveyedPointEntity.PROTOCOL_LTE) R.string.survey_point_tac else R.string.survey_point_lac,
                    point.area
                ),
                swatch = SurveyedPointPalette.hashed(
                    SurveyedPointFeatures.colorIndex(
                        SurveyedPointFeatures.areaKey(point.plmn, point.area)
                    )
                )
            )
        }
    } else {
        val countLabel =
            if (point.observedMask == SurveyedPointEntity.OBSERVED_WIFI) R.string.survey_point_networks_heard else R.string.survey_point_devices_heard
        DetailRow(stringResource(countLabel), point.deviceCount.toString())
        DetailRow(
            stringResource(R.string.survey_point_strongest),
            listOfNotNull(
                stringResource(R.string.survey_point_dbm, point.signal) + " · " + bucketLabel(
                    point.signalBucket
                ),
                point.label?.let { "\"$it\"" }
            ).joinToString(" "),
            swatch = SurveyedPointPalette.bucket(point.signalBucket)
        )
    }
    TimestampRow(stringResource(R.string.survey_point_surveyed), point.time / MILLIS_PER_SECOND)
    DetailRow(
        stringResource(R.string.survey_point_collected_for),
        stringResource(if (point.source == SurveyedPointEntity.SOURCE_NS_ANALYTICS) R.string.survey_point_destination_ns else R.string.survey_point_destination_community)
    )
    destinations(point).forEach { (nameRes, sent) ->
        DetailRow(
            stringResource(nameRes),
            stringResource(if (sent) R.string.survey_point_sent else R.string.survey_point_not_sent),
            swatch = if (sent) SurveyedPointPalette.SENT else SurveyedPointPalette.PENDING
        )
    }
}

@Composable
private fun AggregateDetails(selection: SurveyedPointSelection) {
    val points = selection.points
    if (points.isEmpty()) {
        Text(
            stringResource(R.string.survey_point_zoom_in),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    val best = points.maxByOrNull { it.signalBucket }
    if (best != null && best.signalBucket != SignalBuckets.UNKNOWN) {
        DetailRow(
            stringResource(R.string.survey_point_best_signal),
            stringResource(
                R.string.survey_point_dbm,
                best.signal
            ) + " · " + bucketLabel(best.signalBucket),
            swatch = SurveyedPointPalette.bucket(best.signalBucket)
        )
    }
    val techs = points.filter { it.observedMask == SurveyedPointEntity.OBSERVED_CELLULAR }
        .map { SurveyedPointFeatures.techOf(it.protocol, it.nrScg) }
        .filter { it != SurveyedPointFeatures.TECH_UNKNOWN }
    techs.groupingBy { it }.eachCount().maxByOrNull { it.value }?.let { (tech, _) ->
        DetailRow(
            stringResource(R.string.survey_point_common_tech),
            techLabel(tech),
            swatch = SurveyedPointPalette.tech(tech)
        )
    }
    points.mapNotNull { it.plmn }.groupingBy { it }.eachCount().maxByOrNull { it.value }
        ?.let { (plmn, _) ->
            val provider = points.firstOrNull { it.plmn == plmn }?.provider
            DetailRow(
                stringResource(R.string.survey_point_common_provider),
                listOfNotNull(provider, "($plmn)").joinToString(" "),
                swatch = SurveyedPointPalette.plmn(plmn)
            )
        }
    TimestampRow(
        stringResource(R.string.survey_point_first_surveyed),
        points.minOf { it.time } / MILLIS_PER_SECOND)
    TimestampRow(
        stringResource(R.string.survey_point_last_surveyed),
        points.maxOf { it.time } / MILLIS_PER_SECOND)
    DetailRow(
        stringResource(R.string.survey_point_sent),
        pluralStringResource(
            R.plurals.survey_point_sent_count,
            points.size,
            points.count { it.uploadedMask != 0 },
            points.size
        )
    )
    Text(
        stringResource(R.string.survey_point_zoom_in),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

