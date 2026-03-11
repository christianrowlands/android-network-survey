package com.craxiom.networksurvey.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.cellular.CustomMarkerValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

@Composable
internal fun rememberMarker(labelText: String): CartesianMarker {
    val labelBackgroundShape = MarkerCornerBasedShape(RoundedCornerShape(4.dp))
    val labelBackgroundColor = MaterialTheme.colorScheme.surface
    val labelBackground =
        rememberShapeComponent(
            fill = Fill(labelBackgroundColor),
            shape = labelBackgroundShape,
            strokeThickness = 1.dp,
            strokeFill = Fill(MaterialTheme.colorScheme.outline),
        )
    val label =
        rememberTextComponent(
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            ),
            background = labelBackground,
            padding = Insets(8.dp, 4.dp),
        )
    val indicatorFrontComponent =
        rememberShapeComponent(Fill(MaterialTheme.colorScheme.surface), CircleShape)
    val guideline = rememberAxisGuidelineComponent()

    val valueFormatter = if (labelText.isNotEmpty()) {
        CustomMarkerValueFormatter(labelText)
    } else {
        DefaultCartesianMarker.ValueFormatter.default()
    }

    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = valueFormatter,
        indicator = { color ->
            LayeredComponent(
                back = ShapeComponent(Fill(color.copy(alpha = 0.15f)), CircleShape),
                front =
                    LayeredComponent(
                        back = ShapeComponent(fill = Fill(color), shape = CircleShape),
                        front = indicatorFrontComponent,
                        padding = Insets(5.dp),
                    ),
                padding = Insets(10.dp),
            )
        },
        indicatorSize = 36.dp,
        guideline = guideline,
    )
}
