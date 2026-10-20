package com.craxiom.networksurvey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Upper bound applied to the system font scale for short one-line labels in a fixed-width slot
 * (the tab labels, the Signals sub-tabs). Keeps them legible without letting them wrap.
 */
const val LABEL_MAX_FONT_SCALE = 1.15f

/** Draws [content] with the system font scale capped at [maxFontScale]. */
@Composable
fun CappedFontScale(maxFontScale: Float = LABEL_MAX_FONT_SCALE, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val capped = remember(density, maxFontScale) {
        Density(density.density, density.fontScale.coerceAtMost(maxFontScale))
    }
    CompositionLocalProvider(LocalDensity provides capped, content = content)
}
