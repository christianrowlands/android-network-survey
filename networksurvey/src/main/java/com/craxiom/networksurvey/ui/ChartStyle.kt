package com.craxiom.networksurvey.ui

import android.graphics.Paint
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
import com.patrykandpatrick.vico.core.component.Component
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.context.DrawContext

/**
 * A Circle component that can be used to draw a point on a canvas. This could be used instead of
 * drawing lines between the points.
 */
class CircleComponent(
    private val radius: Float,
    private val circleColor: Int
) : Component() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = circleColor
    }

    override fun draw(
        context: DrawContext,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        opacity: Float
    ) {
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        paint.alpha = (opacity * 255).toInt()
        context.canvas.drawCircle(centerX, centerY, radius, paint)
    }
}

@Composable
internal fun rememberChartStyle(
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
                        thicknessDp = 3f,
                        shader = DynamicShaders.color(lineChartColor),
                        backgroundShader = null,
                        //point = CircleComponent(5f, lineChartColor.toArgb()),
                    )
                },
            ),
            ChartStyle.Marker(),
            Color(defaultColors.elevationOverlayColor),
        )
    }
}

@Composable
internal fun rememberChartStyle(chartColors: List<Color>) =
    rememberChartStyle(columnLayerColors = chartColors, lineLayerColors = chartColors)