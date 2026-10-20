package com.craxiom.networksurvey.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing tokens from the prototype. Read through [NsTheme.spacing]. Use the named layout values
 * where one fits, and the scale steps for everything else.
 */
object NsSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Left and right margin of every scrolling screen. */
    val screenGutter = 16.dp

    /** Gap between stacked panels on a screen. */
    val panelGap = 12.dp

    /** Inner padding of a panel or card. */
    val panelPaddingHorizontal = 16.dp
    val panelPaddingVertical = 14.dp

    /** Minimum height of a list row. */
    val rowMinHeight = 56.dp

    /** Minimum touch target; also keep 8 dp between adjacent targets. */
    val touchTarget = 48.dp

    /**
     * The widest a page's content column grows, centered, in landscape and on tablets, so rows,
     * values and the main action stay together instead of stretching across the screen.
     */
    val contentMaxWidth = 600.dp
}
