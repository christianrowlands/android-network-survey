package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared building blocks for list management screens (Watchlist, SSID Exclusion List, and
 * Provider Color Overrides): a hero status card, an explainer info card, and a monospace section
 * label. Keeping these in one place keeps the three screens visually in sync.
 */

/**
 * A full width status card with a circular icon badge, a title and subtitle, and an optional
 * trailing control (for example a Switch). When [isError] is true the card swaps to the error
 * container colors so attention states stand out. The 16dp outer padding is baked in, so this
 * card also provides the vertical rhythm between the top app bar and the content below it.
 */
@Composable
fun NsHeroStatusCard(
    title: String,
    subtitle: String,
    icon: Painter,
    isError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val container =
        if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val titleColor =
        if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val subtitleColor =
        if (isError) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    val badgeBg =
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
    val badgeFg =
        if (isError) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = badgeFg,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * The secondaryContainer explainer card shown under the hero card on list management screens.
 * Horizontal 16dp outer padding is baked in; vertical spacing comes from the hero card above and
 * the list content padding below.
 */
@Composable
fun NsInfoCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * A monospace section label with widened letter spacing, used for list count headers
 * (for example "Watched · 4") and form group labels.
 */
@Composable
fun NsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
