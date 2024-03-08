package com.craxiom.networksurvey.ui.wifi

import android.graphics.Typeface
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.WifiNetworkInfo
import com.craxiom.networksurvey.ui.wifi.model.WIFI_SPECTRUM_MAX
import com.craxiom.networksurvey.ui.wifi.model.WIFI_SPECTRUM_MIN
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.layout.fullWidth
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.compose.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.component.shape.shader.color
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.compose.legend.horizontalLegend
import com.patrykandpatrick.vico.compose.legend.legendItem
import com.patrykandpatrick.vico.compose.legend.verticalLegend
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.compose.style.currentChartStyle
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.vertical.VerticalAxis
import com.patrykandpatrick.vico.core.chart.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.chart.values.AxisValueOverrider
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.dimensions.MutableDimensions
import com.patrykandpatrick.vico.core.legend.HorizontalLegend
import com.patrykandpatrick.vico.core.legend.LegendItem
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import kotlin.math.absoluteValue

/**
 * A chart that shows a view of the Wi-Fi spectrum so the user can see where there is free space.
 */
@Composable
internal fun WifiSpectrumChart(
    wifiList: List<WifiNetworkInfo>,
    modelProducer: CartesianChartModelProducer,
    minX: Float,
    maxX: Float,
    customLabelValues: List<Float>,
    labelInterval: Int = 1
) {
    ComposeChart(wifiList, modelProducer, minX, maxX, labelInterval, customLabelValues)
}

@Composable
private fun ComposeChart(
    wifiList: List<WifiNetworkInfo>,
    modelProducer: CartesianChartModelProducer,
    minX: Float,
    maxX: Float,
    labelInterval: Int,
    customLabelValues: List<Float>
) {
    val decorationList = wifiList.map { wifiNetwork ->
        SsidLabel(
            ssid = wifiNetwork.ssid,
            signalStrength = wifiNetwork.signalStrength,
            channel = wifiNetwork.centerChannel,
            rememberTextComponent(
                color = getColorForSsid(wifiNetwork.ssid),
                textSize = 10.sp,
                margins = bottomAxisTitleMargins,
                typeface = Typeface.MONOSPACE,
            )
        )
    }

    val lines: List<LineCartesianLayer.LineSpec>
    if (wifiList.isEmpty()) {
        lines = listOf(
            LineCartesianLayer.LineSpec(
                pointConnector = SpectrumPointConnector(),
                thicknessDp = 3f,
                shader = remember { DynamicShaders.color(color1) },
            )
        )
    } else {
        lines = wifiList.map { wifiNetwork ->
            LineCartesianLayer.LineSpec(
                pointConnector = SpectrumPointConnector(),
                thicknessDp = 3f,
                shader = DynamicShaders.color(getColorForSsid(wifiNetwork.ssid)),
            )
        }
    }
    ProvideChartStyle(rememberSpectrumChartStyle(chartColors)) {
        //val defaultLines = currentChartStyle.lineLayer.lines
        CartesianChartHost(
            modifier = Modifier.height(210.dp),
            modelProducer = modelProducer,
            marker = null,//rememberMarker(""),
            runInitialAnimation = false,
            chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
            getXStep = { 1f },
            chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    axisValueOverrider = AxisValueOverrider.fixed(
                        minX = minX,
                        maxX = maxX,
                        maxY = WIFI_SPECTRUM_MAX,
                        minY = WIFI_SPECTRUM_MIN
                    ),
                    lines = lines
                    //remember(defaultLines) { defaultLines.map { it.copy(backgroundShader = null) } },
                ),
                startAxis =
                rememberStartAxis(
                    horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                    itemPlacer = remember { AxisItemPlacer.Vertical.default({ _ -> 5 }) },
                ),
                bottomAxis =
                rememberBottomAxis(
                    title = stringResource(R.string.channel),
                    label = axisLabelComponent(
                        textSize = 12.sp,
                        padding = MutableDimensions(1f, 1f)
                    ),
                    itemPlacer = remember {
                        /*AxisItemPlacer.Horizontal.default(
                            spacing = xSpacing,
                            offset = xOffset
                        )*/
                        ChannelAxisItemPlacer(
                            labelInterval = labelInterval,
                            customLabelValues = customLabelValues
                        )
                    },
                    titleComponent =
                    rememberTextComponent(
                        background = rememberShapeComponent(
                            Shapes.pillShape,
                            colorResource(id = R.color.colorAccent)
                        ),
                        color = Color.White,
                        padding = axisTitlePadding,
                        margins = bottomAxisTitleMargins,
                        typeface = Typeface.MONOSPACE,
                    ),
                ),
                decorations = decorationList,
                //legend = rememberSsidLegend(wifiList),
                //fadingEdges = rememberFadingEdges(),
            ),
            horizontalLayout = horizontalLayout,
        )
    }
}

/**
 * Provided a String SSID, this function will return the same color for that SSID. This is useful
 * because the color would be randomly assigned otherwise, which means the color is likely to
 * change after every scan, which makes it hard to track the same SSID over time.
 */
fun getColorForSsid(ssid: String): Color {
    val index = ssid.hashCode().absoluteValue % chartColors.size
    return chartColors[index]
}

@Composable
private fun rememberSsidLegend(
    wifiList: List<WifiNetworkInfo>
): HorizontalLegend {

    val legendItems: List<LegendItem>
    if (wifiList.isEmpty()) {
        legendItems = listOf(
            legendItem(
                icon = rememberShapeComponent(Shapes.pillShape, chartColors[0]),
                label =
                rememberTextComponent(
                    color = currentChartStyle.axis.axisLabelColor,
                    textSize = legendItemLabelTextSize,
                    typeface = Typeface.MONOSPACE,
                ),
                labelText = "No Wifi Networks Found",
            )
        )
    } else {
        // For this to work the list would need to be filtered for 2.4 GHz and non null ssids
        legendItems = wifiList.mapIndexed { index, wifiNetworkInfo ->
            legendItem(
                icon = rememberShapeComponent(
                    Shapes.pillShape,
                    chartColors[index % chartColors.size]
                ),
                label =
                rememberTextComponent(
                    color = currentChartStyle.axis.axisLabelColor,
                    textSize = legendItemLabelTextSize,
                    typeface = Typeface.MONOSPACE,
                ),
                labelText = wifiNetworkInfo.ssid,
            )
        }
    }
    return horizontalLegend(
        items = legendItems,
        iconSize = legendItemIconSize,
        iconPadding = legendItemIconPaddingValue,
        spacing = legendItemSpacing,
        padding = legendPadding,
    )
}

@Composable
private fun rememberLegend() =
    verticalLegend(
        items =
        chartColors.mapIndexed { index, chartColor ->
            legendItem(
                icon = rememberShapeComponent(Shapes.pillShape, chartColor),
                label =
                rememberTextComponent(
                    color = currentChartStyle.axis.axisLabelColor,
                    textSize = legendItemLabelTextSize,
                    typeface = Typeface.MONOSPACE,
                ),
                labelText = stringResource(R.string.ssid, index + 1),
            )
        },
        iconSize = legendItemIconSize,
        iconPadding = legendItemIconPaddingValue,
        spacing = legendItemSpacing,
        padding = legendPadding,
    )

private val color1 = Color(0xFF835DB1)
private val color2 = Color(0xFF852659)
private val color3 = Color(0xFFB42D2D)
private val color4 = Color(0xFFD33838)
private val color5 = Color(0xffe65100)
private val color6 = Color(0xfff57f17)
private val color7 = Color(0xffff6f00)
private val color8 = Color(0xffe65100)
private val color9 = Color(0xfff9a825)
private val color10 = Color(0xff9e9d24)
private val color11 = Color(0xff558b2f)
private val color12 = Color(0xff2e7d32)
private val color13 = Color(0xff00695c)
private val color14 = Color(0xff004d40)
private val color15 = Color(0xff01579b)
private val color16 = Color(0xFF1C50A0)
private val color17 = Color(0xFF464B83)
private val color18 = Color(0xFF63559E)

private val chartColors = listOf(
    color1,
    color2,
    color3,
    color4,
    color5,
    color6,
    color7,
    color8,
    color9,
    color10,
    color11,
    color12,
    color13,
    color14,
    color15,
    color16,
    color17,
    color18
)
private val legendItemLabelTextSize = 12.sp
private val legendItemIconSize = 8.dp
private val legendItemIconPaddingValue = 10.dp
private val legendItemSpacing = 4.dp
private val legendTopPaddingValue = 8.dp
private val legendPadding = dimensionsOf(top = legendTopPaddingValue)

private val axisTitleHorizontalPaddingValue = 8.dp
private val axisTitleVerticalPaddingValue = 2.dp
private val axisTitlePadding =
    dimensionsOf(axisTitleHorizontalPaddingValue, axisTitleVerticalPaddingValue)
private val axisTitleMarginValue = 4.dp
private val bottomAxisTitleMargins = dimensionsOf(top = axisTitleMarginValue)

private val horizontalLayout = HorizontalLayout.fullWidth()
