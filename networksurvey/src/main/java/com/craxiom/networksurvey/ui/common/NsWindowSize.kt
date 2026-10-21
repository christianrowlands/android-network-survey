package com.craxiom.networksurvey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/*
 * Window size rules shared by the tab shell and the bars that sit around a page's content, so a
 * short window (a phone in landscape, or one with the display size turned up) keeps room to scroll.
 */

/** The window width from which the tabs move from the bottom bar to a navigation rail. */
private val RAIL_MIN_WIDTH = 600.dp

/** The window height under which the top bars and the pinned action bar take their compact sizes. */
private val COMPACT_HEIGHT_MAX = 480.dp

/**
 * True when the tabs belong in a navigation rail: a window at least [RAIL_MIN_WIDTH] wide, or any
 * landscape window, since a bottom bar there takes height a landscape phone can't spare.
 */
fun useRail(size: DpSize): Boolean = size.width >= RAIL_MIN_WIDTH || size.width > size.height

/** True when the window is shorter than [COMPACT_HEIGHT_MAX]. */
fun isCompactHeight(size: DpSize): Boolean = size.height < COMPACT_HEIGHT_MAX

/** @see useRail */
@Composable
fun rememberUseRail(): Boolean = useRail(LocalWindowInfo.current.containerDpSize)

/** @see isCompactHeight */
@Composable
fun rememberCompactHeight(): Boolean = isCompactHeight(LocalWindowInfo.current.containerDpSize)
