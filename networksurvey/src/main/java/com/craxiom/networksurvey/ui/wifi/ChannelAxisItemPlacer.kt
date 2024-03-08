package com.craxiom.networksurvey.ui.wifi

import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.dimensions.HorizontalDimensions
import com.patrykandpatrick.vico.core.chart.draw.ChartDrawContext
import com.patrykandpatrick.vico.core.context.MeasureContext

class ChannelAxisItemPlacer(
    private val labelInterval: Int = 1,
    private val shiftExtremeTicks: Boolean = false,
    private val customLabelValues: List<Float> // Add your custom label values here
) : AxisItemPlacer.Horizontal {

    override fun getShiftExtremeTicks(context: ChartDrawContext): Boolean = shiftExtremeTicks

    override fun getAddFirstLabelPadding(context: MeasureContext) = false

    override fun getAddLastLabelPadding(context: MeasureContext): Boolean = false

    override fun getLabelValues(
        context: ChartDrawContext,
        visibleXRange: ClosedFloatingPointRange<Float>,
        fullXRange: ClosedFloatingPointRange<Float>,
    ): List<Float> {
        if (labelInterval > 1) {
            val everyOtherLabelValues = mutableListOf<Float>()
            for (i in customLabelValues.indices) {
                if (i % labelInterval == 0) {
                    everyOtherLabelValues.add(customLabelValues[i])
                }
            }
            return everyOtherLabelValues
        }
        return customLabelValues
    }

    override fun getMeasuredLabelValues(
        context: MeasureContext,
        horizontalDimensions: HorizontalDimensions,
        fullXRange: ClosedFloatingPointRange<Float>
    ): List<Float> {
        return listOf(customLabelValues[0], customLabelValues[customLabelValues.size - 1])
    }

    override fun getLineValues(
        context: ChartDrawContext,
        visibleXRange: ClosedFloatingPointRange<Float>,
        fullXRange: ClosedFloatingPointRange<Float>
    ): List<Float> {
        return customLabelValues
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