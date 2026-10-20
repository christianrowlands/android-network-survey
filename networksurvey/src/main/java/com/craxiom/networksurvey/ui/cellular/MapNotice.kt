package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.InlineNotice
import com.craxiom.networksurvey.ui.map.MAP_TOP_STACK_MAX_WIDTH
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * A map-wide message (zoom in, no towers here, missing key) as a panel just under the top stack,
 * like the location banner, so it never sits on the user's position or over unreadable tiles.
 */
@Composable
internal fun MapNotice(text: String, contentPadding: PaddingValues, iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(horizontal = NsTheme.spacing.s),
    ) {
        Surface(
            shape = NsTheme.shapes.panel,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = NsTheme.spacing.xs,
            modifier = Modifier.widthIn(max = MAP_TOP_STACK_MAX_WIDTH).fillMaxWidth(),
        ) {
            InlineNotice(
                text = text,
                icon = R.drawable.ic_info,
                iconTint = iconTint,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = NsTheme.spacing.m),
            )
        }
    }
}
