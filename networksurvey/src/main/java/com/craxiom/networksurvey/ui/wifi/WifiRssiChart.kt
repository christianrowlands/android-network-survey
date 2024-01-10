package com.craxiom.networksurvey.ui.wifi

import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.LineCartesianLayerModel

/**
 * A chart that shows the RSSI values for the currently selected WiFi network.
 *
 * @param modelProducer The model producer that will provide the data to be displayed in the chart.
 */
@Composable
internal fun WifiRssiChart(
    modelProducer: CartesianChartModelProducer,
) {
    ComposeChart(modelProducer)
}

@Composable
private fun ComposeChart(modelProducer: CartesianChartModelProducer) {
    ProvideChartStyle(rememberChartStyle(chartColors)) {
        CartesianChartHost(
            modelProducer = modelProducer,
            marker = rememberMarker(),
            runInitialAnimation = false,
            diffAnimationSpec = snap(),
            horizontalLayout = horizontalLayout,
            chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
            chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    axisValueOverrider = axisValueOverrider,
                    //lines = remember(defaultLines) { defaultLines.map { it.copy(backgroundShader = null) } },
                ),
                startAxis =
                rememberStartAxis(
                    horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                    itemPlacer = remember { AxisItemPlacer.Vertical.default({ _ -> 5 }) },
                ),
                fadingEdges = rememberFadingEdges(),
            )
        )
    }
}

private val lineColor = Color(0xFF03A9F4)
private val chartColors = listOf(lineColor)
private val axisValueOverrider = AxisValueOverrider.fixed<LineCartesianLayerModel>(
    maxY = MAX_WIFI_RSSI,
    minY = MIN_WIFI_RSSI,
)
private val horizontalLayout = HorizontalLayout.fullWidth()
