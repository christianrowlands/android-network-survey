package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private const val SIGNAL_CELL_FONT_SCALE_CAP = 1.3f

/**
 * Caps the font scale applied to text inside [content] so that a large accessibility font
 * size does not grow the right-hand signal column beyond what the left column can absorb.
 * Dp-based sizing is untouched; only sp-based text sizes are clamped.
 */
@Composable
fun CappedSignalCellDensity(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val capped = remember(base) {
        Density(
            density = base.density,
            fontScale = base.fontScale.coerceAtMost(SIGNAL_CELL_FONT_SCALE_CAP),
        )
    }
    CompositionLocalProvider(LocalDensity provides capped, content = content)
}
