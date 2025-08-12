package com.craxiom.networksurvey.ui

import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craxiom.networksurvey.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer

/**
 * A chart that shows signal values (e.g. RSSI) over time.
 *
 * @param viewModel The view model that contains the data to display.
 */
@Composable
internal fun SignalChart(
    viewModel: ASignalChartViewModel
) {
    ComposeChart(viewModel)
}

@Composable
private fun ComposeChart(viewModel: ASignalChartViewModel) {
    val maxRssi by viewModel.maxRssi.collectAsStateWithLifecycle()
    val minRssi by viewModel.minRssi.collectAsStateWithLifecycle()

    val markerList by viewModel.markerList.collectAsStateWithLifecycle()

    val eventMarker = rememberMarker(viewModel.getMarkerLabel())

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(
                            fill(colorResource(id = R.color.colorAccent))
                        )
                    )
                ),
                rangeProvider = CartesianLayerRangeProvider.fixed(
                    minY = minRssi.toDouble(),
                    maxY = maxRssi.toDouble(),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(
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
