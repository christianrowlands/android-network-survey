package com.craxiom.networksurvey.ui.cellular.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.ui.cellular.details.model.CarrierAggregation
import com.craxiom.networksurvey.ui.cellular.details.model.ServingCellDetails
import com.craxiom.networksurvey.ui.common.NetworkTagVariant
import com.craxiom.networksurvey.ui.common.NsNetworkTag
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The serving cell: its signal values as bars, then its identity and channel, the carrier
 * aggregation chips when the phone uses more than one carrier, and the signal chart.
 *
 * @param chart The serving cell's signal chart; tests pass nothing.
 */
@Composable
fun CellularServingCard(
    serving: ServingCellDetails,
    carrierAggregation: CarrierAggregation?,
    onInfo: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
    onBandClick: (BandTapTarget) -> Unit,
    modifier: Modifier = Modifier,
    chart: @Composable () -> Unit = {},
) {
    val protocol = serving.protocol.toString()
    NsPanel(modifier) {
        CellularPanelHeader(
            title = stringResource(R.string.cellular_serving_cell_title),
            trailing = serving.band?.let { stringResource(R.string.cellular_serving_protocol_band, protocol, it) } ?: protocol,
            infoDescription = stringResource(R.string.cellular_terms_info),
            onInfo = onInfo,
        )
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
            ServingSignalBars(serving.signals, onCopy)
            CellFieldGrid(serving.identity, onCopy, onBandClick)
            carrierAggregation?.let { CarrierAggregationChips(it) }
            chart()
            Text(
                text = stringResource(R.string.cellular_chart_caption, stringResource(fieldLabel(serving.chart.metric))),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "Carrier aggregation · 3 carriers · 60 MHz" over one chip per carrier's bandwidth, centered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarrierAggregationChips(aggregation: CarrierAggregation) {
    val count = aggregation.carriersMhz.size
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
    ) {
        Text(
            text = pluralStringResource(R.plurals.carrier_aggregation_summary, count, count, aggregation.totalMhz),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        FlowRow(
            modifier = Modifier.padding(top = NsTheme.spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            aggregation.carriersMhz.forEach { mhz ->
                NsNetworkTag(stringResource(R.string.mhz_value_label, mhz), NetworkTagVariant.Standard)
            }
        }
    }
}
