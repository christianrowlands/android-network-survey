package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.WifiNetworkInfo
import com.craxiom.networksurvey.ui.wifi.model.WIFI_SPECTRUM_MAX
import com.craxiom.networksurvey.ui.wifi.model.WIFI_SPECTRUM_MIN
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
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
            labelComponent = rememberTextComponent(
                style = TextStyle(
                    color = getColorForSsid(wifiNetwork.ssid),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                margins = bottomAxisTitleMargins,
            ),
            minX = minX,
            maxX = maxX
        )
    }

    val lines = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            if (wifiList.isEmpty()) {
                listOf(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(color1)),
                        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 3.dp),
                        interpolator = SpectrumInterpolator()
                    )
                )
            } else {
                wifiList.map { wifiNetwork ->
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(getColorForSsid(wifiNetwork.ssid))),
                        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 3.dp),
                        interpolator = SpectrumInterpolator()
                    )
                }
            }
        ),
        rangeProvider = CartesianLayerRangeProvider.fixed(
            minX = minX.toDouble(),
            maxX = maxX.toDouble(),
            maxY = WIFI_SPECTRUM_MAX.toDouble(),
            minY = WIFI_SPECTRUM_MIN.toDouble()
        ),
    )
    CartesianChartHost(
        rememberCartesianChart(
            lines,
            startAxis =
                VerticalAxis.rememberStart(
                    horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Inside,
                    itemPlacer = VerticalAxis.ItemPlacer.step({ 5.0 }),
                ),
            bottomAxis = run {
                val channelTitle = stringResource(R.string.channel)
                HorizontalAxis.rememberBottom(
                    title = { channelTitle },
                    label = rememberAxisLabelComponent(
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        ),
                        padding = Insets(1.dp, 1.dp)
                    ),
                    itemPlacer = remember {
                        ChannelAxisItemPlacer(
                            labelInterval = labelInterval,
                            customLabelValues = customLabelValues
                        )
                    },
                    titleComponent =
                        rememberTextComponent(
                            background = rememberShapeComponent(
                                fill = Fill(colorResource(id = R.color.colorAccent)),
                                shape = CircleShape
                            ),
                            style = TextStyle(
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                            ),
                            padding = axisTitlePadding,
                            margins = bottomAxisTitleMargins,
                        ),
                )
            },
            decorations = decorationList,
        ),
        modelProducer,
        Modifier.height(210.dp),
        rememberVicoScrollState(scrollEnabled = false),
    )
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

// Legends are not currently used in this chart implementation

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
private val axisTitleHorizontalPaddingValue = 8.dp
private val axisTitleVerticalPaddingValue = 2.dp
private val axisTitlePadding =
    Insets(axisTitleHorizontalPaddingValue, axisTitleVerticalPaddingValue)
private val axisTitleMarginValue = 4.dp
private val bottomAxisTitleMargins =
    Insets(axisTitleHorizontalPaddingValue, axisTitleVerticalPaddingValue)

