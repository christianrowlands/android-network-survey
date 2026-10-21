package com.craxiom.networksurvey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The swatch beside each name in the accent picker. */
internal val ACCENT_PICKER_SWATCH = 20.dp

/** The swatch at the end of the Accent color row, a spot of color among the neutral settings rows. */
internal val ACCENT_ROW_SWATCH = 28.dp

private val SWATCH_RING = 1.dp

/**
 * A filled circle of an accent [color]. A thin ring keeps a pale or grey accent (Graphite in the
 * dark scheme) from blending into the panel behind it.
 */
@Composable
internal fun AccentSwatch(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
            .border(SWATCH_RING, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}
