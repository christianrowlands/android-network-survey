package com.craxiom.networksurvey.ui.wifi

import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.dimensions.HorizontalDimensions
import com.patrykandpatrick.vico.core.chart.draw.ChartDrawContext
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.context.MeasureContext

class ChannelAxisItemPlacer(
    private val spacing: Int,
    private val offset: Int,
    private val shiftExtremeTicks: Boolean = false,
    private val addExtremeLabelPadding: Boolean = false,
    private val customLabelValues: List<Float> // Add your custom label values here
) : AxisItemPlacer.Horizontal {

    override fun getShiftExtremeTicks(context: ChartDrawContext): Boolean = shiftExtremeTicks

    override fun getAddFirstLabelPadding(context: MeasureContext) =
        context.horizontalLayout is HorizontalLayout.FullWidth && addExtremeLabelPadding && offset == 0

    override fun getAddLastLabelPadding(context: MeasureContext): Boolean =
        with(context) {
            context.horizontalLayout is HorizontalLayout.FullWidth && addExtremeLabelPadding
        }

    override fun getLabelValues(
        context: ChartDrawContext,
        visibleXRange: ClosedFloatingPointRange<Float>,
        fullXRange: ClosedFloatingPointRange<Float>,
    ): List<Float> {
        // Return your custom label values here
        return customLabelValues
    }

    override fun getMeasuredLabelValues(
        context: MeasureContext,
        horizontalDimensions: HorizontalDimensions,
        fullXRange: ClosedFloatingPointRange<Float>
    ): List<Float> {
        return listOf(customLabelValues[0], customLabelValues[customLabelValues.size - 1])
    }

    override fun getStartHorizontalAxisInset(
        context: MeasureContext,
        horizontalDimensions: HorizontalDimensions,
        tickThickness: Float
    ): Float {
       return 0f
    }

    override fun getEndHorizontalAxisInset(
        context: MeasureContext,
        horizontalDimensions: HorizontalDimensions,
        tickThickness: Float
    ): Float {
        return 0f
    }
}