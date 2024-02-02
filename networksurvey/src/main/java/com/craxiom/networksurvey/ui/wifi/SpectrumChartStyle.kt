package com.craxiom.networksurvey.ui.wifi

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.component.shape.shader.color
import com.patrykandpatrick.vico.compose.style.ChartStyle
import com.patrykandpatrick.vico.core.DefaultColors
import com.patrykandpatrick.vico.core.DefaultDimens
import com.patrykandpatrick.vico.core.chart.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders

@Composable
internal fun rememberSpectrumChartStyle(
    columnLayerColors: List<Color>,
    lineLayerColors: List<Color>,
): ChartStyle {
    val isSystemInDarkTheme = isSystemInDarkTheme()

    return remember(columnLayerColors, lineLayerColors, isSystemInDarkTheme) {
        val defaultColors = if (isSystemInDarkTheme) DefaultColors.Dark else DefaultColors.Light
        ChartStyle(
            ChartStyle.Axis(
                axisLabelColor = Color(defaultColors.axisLabelColor),
                axisGuidelineColor = Color(defaultColors.axisGuidelineColor),
                axisLineColor = Color(defaultColors.axisLineColor),
            ),
            ChartStyle.ColumnLayer(
                columnLayerColors.map { columnChartColor ->
                    LineComponent(
                        columnChartColor.toArgb(),
                        DefaultDimens.COLUMN_WIDTH,
                        Shapes.roundedCornerShape(DefaultDimens.COLUMN_ROUNDNESS_PERCENT),
                    )
                },
            ),
            ChartStyle.LineLayer(
                lineLayerColors.map { lineChartColor ->
                    LineCartesianLayer.LineSpec(
                        pointConnector = SpectrumPointConnector(),
                        thicknessDp = 3f,
                        shader = DynamicShaders.color(lineChartColor),
                        //backgroundShader = null,
                    )
                },
            ),
            ChartStyle.Marker(),
            Color(defaultColors.elevationOverlayColor),
        )
    }
}

@Composable
internal fun rememberSpectrumChartStyle(chartColors: List<Color>) =
    rememberSpectrumChartStyle(columnLayerColors = chartColors, lineLayerColors = chartColors)
