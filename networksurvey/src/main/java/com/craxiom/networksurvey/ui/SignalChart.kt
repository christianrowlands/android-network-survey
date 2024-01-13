package com.craxiom.networksurvey.ui

import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.edges.rememberFadingEdges
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.layout.fullWidth
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.vertical.VerticalAxis
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.chart.values.AxisValueOverrider
import com.patrykandpatrick.vico.core.marker.Marker

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

    val pushMarker = rememberMarker("")
    val eventMarker = rememberMarker(viewModel.getMarkerLabel())

    fun convertToPair(intValue: Int): Pair<Float, Marker> {
        val floatValue = intValue.toFloat() // Convert Int to Float
        return Pair(floatValue, eventMarker)
    }

    ProvideChartStyle(rememberChartStyle(chartColors)) {
        CartesianChartHost(
            modelProducer = viewModel.modelProducer,
            marker = pushMarker,
            runInitialAnimation = false,
            diffAnimationSpec = snap(),
            horizontalLayout = horizontalLayout,
            chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
            chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    axisValueOverrider = AxisValueOverrider.fixed(
                        maxY = maxRssi,
                        minY = minRssi,
                    ),
                    //lines = remember(defaultLines) { defaultLines.map { it.copy(backgroundShader = null) } },
                ),
                startAxis =
                rememberStartAxis(
                    horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                    itemPlacer = remember { AxisItemPlacer.Vertical.default({ _ -> 5 }) },
                ),
                fadingEdges = rememberFadingEdges(),
                persistentMarkers = remember(markerList) { markerList.associate { convertToPair(it) } },
            )
        )
    }
}

private val lineColor = Color(0xFF03A9F4)
private val chartColors = listOf(lineColor)
private val horizontalLayout = HorizontalLayout.fullWidth()
