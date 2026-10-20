package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.craxiom.networksurvey.ui.theme.NsSpacing

/**
 * Fills the available width up to [NsSpacing.contentMaxWidth] and centers the content in any
 * width left over. Apply it before the screen gutter padding, so the gutter sits inside the cap and
 * every capped column, bar and tab title lines up. Use it inside a scroll (so the margins still
 * scroll) and on each item of a lazy list.
 */
fun Modifier.nsContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = NsSpacing.contentMaxWidth)
    .fillMaxWidth()
