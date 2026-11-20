package com.craxiom.networksurvey.ui.newtowers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

/** The widest the legend gets, so it stays a compact strip on a tablet or in landscape. */
private val LEGEND_MAX_WIDTH = 560.dp
private val LEGEND_ICON_SIZE = 18.dp
private val SWATCH_SIZE = 14.dp
private val SWATCH_HALO = 2.5.dp

/**
 * The New towers map's legend, always shown as a compact strip over the bottom of the map (never
 * behind an info button). It says what a dot is (where you were, not the tower), explains the
 * tower glyph only when one is drawn, and notes any towers left off the map for want of a location.
 *
 * The example dot is neutral gray because real markers take each provider's color.
 */
@Composable
internal fun NewTowersMapLegendStrip(legend: NewTowersMapLegend, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        modifier = modifier.widthIn(max = LEGEND_MAX_WIDTH),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendEntry(text = sightingLegendText(legend)) { SightingSwatch(MaterialTheme.colorScheme.onSurfaceVariant) }
            if (legend.showsTower) {
                LegendEntry(text = stringResource(R.string.new_towers_tower_location)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cell_tower_map_sdf),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(LEGEND_ICON_SIZE),
                    )
                }
            }
            if (legend.unplacedCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.new_towers_map_unplaced, legend.unplacedCount, legend.unplacedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Where you were when your phone connected, not where the tower is.", plus "A tower is drawn once
 * it's in the tower database." while any shown find lacks a tower position.
 */
@Composable
internal fun sightingLegendText(legend: NewTowersMapLegend): String {
    val sighting = stringResource(R.string.new_towers_map_legend_sighting)
    return if (legend.showsUnknownTowerNote) {
        "$sighting ${stringResource(R.string.new_towers_map_legend_unknown_tower)}"
    } else {
        sighting
    }
}

/**
 * What TalkBack reads for the map itself: how many towers it shows, the legend's sentences, and
 * how many tower positions are drawn.
 */
@Composable
internal fun newTowersMapDescription(markers: List<NewTowerMapMarker>, legend: NewTowersMapLegend): String {
    val towers = markers.count { it.tower != null }
    return listOfNotNull(
        pluralStringResource(R.plurals.new_towers_map_description, markers.size, markers.size),
        sightingLegendText(legend),
        if (towers > 0) pluralStringResource(R.plurals.new_towers_map_description_towers, towers, towers) else null,
    ).joinToString(" ")
}

@Composable
private fun LegendEntry(text: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(LEGEND_ICON_SIZE), contentAlignment = Alignment.Center) { icon() }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A small copy of a sighting marker: the provider fill inside a white halo, outlined so it shows on a light card. */
@Composable
private fun SightingSwatch(fill: Color) {
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .background(Color.White, CircleShape)
            .padding(SWATCH_HALO)
            .background(fill, CircleShape)
    )
}
