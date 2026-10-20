package com.craxiom.networksurvey.gpstest.ui.sky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The average C/N0 panel: the color scale with a marker for each average, then the averages
 * written out ("In view 31.2", "Used in fix 36.8", or "--" when there is none), so the markers'
 * position and color are never the only way to read them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkyAveragesPanel(summary: SkySummary, modifier: Modifier = Modifier) {
    NsPanel(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PanelTitle(stringResource(R.string.sky_cn0_average_title), Modifier.weight(1f))
            Text(
                text = stringResource(R.string.sky_legend_cn0_units),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Cn0Scale(
            modifier = Modifier.padding(vertical = NsTheme.spacing.s),
            inViewAverage = summary.inViewAverage,
            usedAverage = summary.usedAverage,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.xs),
        ) {
            Average(stringResource(R.string.sky_legend_shape_in_view), summary.inViewAverage, filledMarker = false)
            Average(stringResource(R.string.sky_legend_shape_used_in_fix), summary.usedAverage, filledMarker = true)
        }
    }
}

/** One average with the marker glyph that stands for it on the scale. */
@Composable
private fun Average(label: String, average: Float?, filledMarker: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val value = average?.let { String.format(locale, "%.1f", it) }
    val none = stringResource(R.string.signal_none)
    val spoken = if (value != null) {
        stringResource(R.string.sky_cn0_value, label, value)
    } else {
        stringResource(R.string.sky_cn0_none, label)
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
    ) {
        Cn0MarkerGlyph(filled = filledMarker)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value ?: none,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
