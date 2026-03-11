package com.craxiom.networksurvey.ui.cellular

import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker

/**
 * A [DefaultCartesianMarker.ValueFormatter] that displays the provided text.
 */
class CustomMarkerValueFormatter(private val text: String) :
    DefaultCartesianMarker.ValueFormatter {
    override fun format(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ): CharSequence = text
}