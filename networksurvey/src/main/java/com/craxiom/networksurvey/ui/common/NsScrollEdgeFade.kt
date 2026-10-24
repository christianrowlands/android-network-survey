package com.craxiom.networksurvey.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The fade at the bottom of a scrolling page, just above a pinned bar such as [NsCtaBar]. While
 * more of the page is below, the content dissolves into the bar's color, which shows that the page
 * scrolls under the bar and the bar stays put. It fades out at the end of the page.
 *
 * Place it over the scrolling content (for example aligned to the bottom of a `Box`), under any
 * snackbar host. It draws only, so touches pass through to the content.
 *
 * @param visible True while the content can scroll further, usually `ScrollState.canScrollForward`.
 */
@Composable
fun NsScrollEdgeFade(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(FADE_MS),
        label = "scrollEdgeFade",
    )
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FADE_HEIGHT)
            .alpha(alpha)
            .background(Brush.verticalGradient(listOf(Color.Transparent, barColor))),
    )
}

private val FADE_HEIGHT = 24.dp
private const val FADE_MS = 150
