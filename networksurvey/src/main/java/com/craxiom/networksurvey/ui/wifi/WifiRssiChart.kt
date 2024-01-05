package com.craxiom.networksurvey.ui.wifi

import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.edges.rememberFadingEdges
import com.patrykandpatrick.vico.compose.chart.layer.lineSpec
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.layout.fullWidth
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.component.shape.shader.color
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.vertical.VerticalAxis
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.chart.values.AxisValueOverrider
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
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
        //val defaultLines = currentChartStyle.lineLayer.lines
        CartesianChartHost(
            chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    axisValueOverrider = axisValueOverrider,
                    //lines = remember(defaultLines) { defaultLines.map { it.copy(backgroundShader = null) } },
                ),
                startAxis =
                rememberStartAxis(
                    guideline = null,
                    horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                    itemPlacer = remember { AxisItemPlacer.Vertical.default({ _ -> 5 }) },
                ),
                /*bottomAxis =
                rememberBottomAxis(
                    titleComponent =
                    rememberTextComponent(
                        background = rememberShapeComponent(Shapes.pillShape, color2),
                        color = Color.White,
                        padding = axisTitlePadding,
                        margins = bottomAxisTitleMargins,
                        typeface = Typeface.MONOSPACE,
                    ),
                    title = stringResource(R.string.y_axis_time),
                ),*/
                fadingEdges = rememberFadingEdges(),
            ),
            modelProducer = modelProducer,
            marker = rememberMarker(),
            runInitialAnimation = false,
            diffAnimationSpec = snap(),
            horizontalLayout = horizontalLayout,
            chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false)
        )
    }
}

private const val COLOR_1_CODE = 0xffffbb00
private const val COLOR_2_CODE = 0xff9db591

private val lineColor = Color(0xFF03A9F4)
private val color1 = Color(COLOR_1_CODE)
private val color2 = Color(COLOR_2_CODE)
private val chartColors = listOf(lineColor, color2)
private val lineSpec = listOf(
    lineSpec(
        thickness = 4.dp,
        backgroundShader = null,
        shader = DynamicShaders.color(Color.DarkGray),
    ),
)
private val axisValueOverrider = AxisValueOverrider.fixed<LineCartesianLayerModel>(
    maxY = -30f,
    minY = -110f,
)
private val axisTitleHorizontalPaddingValue = 8.dp
private val axisTitleVerticalPaddingValue = 2.dp
private val axisTitlePadding =
    dimensionsOf(axisTitleHorizontalPaddingValue, axisTitleVerticalPaddingValue)
private val axisTitleMarginValue = 4.dp
private val startAxisTitleMargins = dimensionsOf(end = axisTitleMarginValue)
private val bottomAxisTitleMargins = dimensionsOf(top = axisTitleMarginValue)
private val horizontalLayout = HorizontalLayout.fullWidth()
