package com.craxiom.networksurvey.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.ui.theme.WifiTokens

/**
 * A single option in an [NsSegmentedToggle].
 *
 * @property label the visible text
 * @property icon an optional leading icon
 * @property contentDescription accessibility description (defaults to [label] when null)
 */
data class SegmentedOption(
    val label: String,
    val icon: ImageVector? = null,
    val contentDescription: String? = null,
)

/**
 * A small pill-shaped segmented toggle: a bordered track with one selected segment highlighted in the
 * SSID accent. Shared by the Watchlist "Match by" selector and the History List/Map switch so both
 * read identically. Mirrors the visual language of the Wi-Fi screen's mode tabs.
 *
 * @param fillWidth when true each segment takes equal width (a full-width control); when false the
 *   control hugs its content (a compact switch).
 */
@Composable
fun NsSegmentedToggle(
    options: List<SegmentedOption>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(999.dp)
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            Segment(
                option = option,
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
                modifier = if (fillWidth) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

@Composable
private fun RowScope.Segment(
    option: SegmentedOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) WifiTokens.SsidSoft else Color.Transparent
    val fg = if (selected) WifiTokens.SsidAccent else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .heightIn(min = 34.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics {
                this.selected = selected
                contentDescription = option.contentDescription ?: option.label
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        option.icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Text(
            text = option.label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}
