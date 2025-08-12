package com.craxiom.networksurvey.ui.wifi

import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerDimensions

class ChannelAxisItemPlacer(
    private val labelInterval: Int = 1,
    private val shiftExtremeTicks: Boolean = false,
    private val customLabelValues: List<Float> // Add your custom label values here
) : HorizontalAxis.ItemPlacer {

    override fun getShiftExtremeLines(context: CartesianDrawingContext): Boolean = shiftExtremeTicks

    override fun getFirstLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = customLabelValues.firstOrNull()?.toDouble()

    override fun getLastLabelValue(
        context: CartesianMeasuringContext,
        maxLabelWidth: Float,
    ): Double? = customLabelValues.lastOrNull()?.toDouble()

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        if (labelInterval > 1) {
            val everyOtherLabelValues = mutableListOf<Double>()
            for (i in customLabelValues.indices) {
                if (i % labelInterval == 0) {
                    everyOtherLabelValues.add(customLabelValues[i].toDouble())
                }
            }
            return everyOtherLabelValues
        }
        return customLabelValues.map { it.toDouble() }
    }

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> {
        return listOf(
            customLabelValues[0].toDouble(),
            customLabelValues[customLabelValues.size - 1].toDouble()
        )
    }

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        return listOf(
            customLabelValues[0].toDouble(),
            customLabelValues[customLabelValues.size - 1].toDouble()
        )
    }

    override fun getLineValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double>? {
        return customLabelValues.map { it.toDouble() }
    }

    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float {
        return if (shiftExtremeTicks) tickThickness else tickThickness / 2f
    }

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float {
        return if (shiftExtremeTicks) tickThickness else tickThickness / 2f
    }
}