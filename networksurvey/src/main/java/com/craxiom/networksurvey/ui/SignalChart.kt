package com.craxiom.networksurvey.ui

import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill

/**
 * A chart that shows signal values (e.g. RSSI) over time.
 *
 * Callers may swap [viewModel] in place (one chart per SIM, for example); the chart is rebuilt for it.
 *
 * @param viewModel The view model that contains the data to display.
 */
@Composable
internal fun SignalChart(
    viewModel: ASignalChartViewModel
) {
    // Vico throws if one chart host is handed a second model producer, which happens when the same
    // spot switches charts (the Cellular view's SIM tabs), so each view model gets its own host.
    key(viewModel) {
        ComposeChart(viewModel)
    }
}

@Composable
private fun ComposeChart(viewModel: ASignalChartViewModel) {
    val maxRssi by viewModel.maxRssi.collectAsStateWithLifecycle()
    val minRssi by viewModel.minRssi.collectAsStateWithLifecycle()

    val markerList by viewModel.markerList.collectAsStateWithLifecycle()

    val markerLabel = viewModel.getMarkerLabel()
    val eventMarker = rememberMarker(if (markerLabel == 0) "" else stringResource(markerLabel))

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(
                            Fill(MaterialTheme.colorScheme.primary)
                        )
                    )
                ),
                rangeProvider = CartesianLayerRangeProvider.fixed(
                    minY = minRssi.toDouble(),
                    maxY = maxRssi.toDouble(),
                ),
            ),
            // Quiet axis, as in the design: solid hairline guides, no axis line or ticks, small labels
            startAxis = VerticalAxis.rememberStart(
                line = null,
                tick = null,
                guideline = rememberAxisGuidelineComponent(
                    fill = Fill(MaterialTheme.colorScheme.outlineVariant),
                    shape = RectangleShape,
                ),
                label = rememberAxisLabelComponent(
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                ),
                horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                itemPlacer = VerticalAxis.ItemPlacer.step({
                    // Use different tick spacing based on the chart range
                    val range = maxRssi - minRssi
                    if (range == 80f) {
                        // Wi-Fi chart range (-100 to -20)
                        20.0
                    } else {
                        // Default for other charts
                        5.0
                    }
                }),
            ),
            persistentMarkers = if (markerList.isNotEmpty()) {
                { _ ->
                    markerList.forEach { markerX ->
                        eventMarker at markerX
                    }
                }
            } else null,
        ),
        modelProducer = viewModel.modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        animationSpec = snap() // Disable animations to prevent -200 values from animating
    )
}
