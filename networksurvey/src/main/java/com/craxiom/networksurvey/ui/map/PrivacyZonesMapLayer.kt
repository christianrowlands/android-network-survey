package com.craxiom.networksurvey.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.ui.cellular.model.SERVING_CELL_COVERAGE_FILL_LAYER_PREFIX
import com.craxiom.networksurvey.ui.cellular.model.SERVING_CELL_COVERAGE_OUTLINE_LAYER_PREFIX
import com.craxiom.networksurvey.ui.cellular.model.SERVING_CELL_LINE_LAYER_PREFIX
import com.craxiom.networksurvey.ui.cellular.towermap.MapApplier
import com.craxiom.networksurvey.ui.cellular.towermap.MapNode
import com.craxiom.networksurvey.ui.cellular.towermap.SEARCH_TOWER_LAYER_KEY
import com.craxiom.networksurvey.ui.cellular.towermap.SURVEYED_POINTS_LAYER_KEY
import com.craxiom.networksurvey.ui.cellular.towermap.TOWER_LAYER_KEY
import com.craxiom.networksurvey.ui.common.featureIdAt
import com.craxiom.networksurvey.ui.privacy.PrivacyZoneMapLayers
import com.craxiom.networksurvey.ui.privacy.PrivacyZoneShape
import com.craxiom.networksurvey.ui.privacy.formatRadius
import com.craxiom.networksurvey.ui.privacy.zoneDisplayName
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * The Map tab's privacy zones layer: the zones beneath the survey points and towers, and a tap on
 * one (that no tower or survey point took first, since their listeners were added before this one)
 * reports its id.
 *
 * Composed before the towers and survey points, the layers go in first and everything added later
 * draws over them. Composed later (the layer switched on, or the first zone saved, while the map is
 * open), they go beneath the lowest of the map's own layers already drawn.
 */
internal class PrivacyZonesNode(
    private val map: MapLibreMap,
    private val style: Style,
    zones: List<PrivacyZone>,
    isDarkMap: Boolean,
    accentArgb: Int,
    private val onZoneClick: () -> ((String) -> Unit),
) : MapNode {
    private val clickListener = MapLibreMap.OnMapClickListener { point ->
        val id = map.featureIdAt(point, TAPPABLE_LAYERS, PrivacyZoneMapLayers.PROP_ID)
        if (id != null) onZoneClick()(id)
        id != null
    }

    init {
        PrivacyZoneMapLayers.install(style, lowestOverlayLayer(style))
        update(zones)
        updateColors(isDarkMap, accentArgb)
        map.addOnMapClickListener(clickListener)
    }

    fun update(zones: List<PrivacyZone>) = PrivacyZoneMapLayers.setData(style, zones.map(PrivacyZoneShape::of))

    fun updateColors(isDarkMap: Boolean, accentArgb: Int) = PrivacyZoneMapLayers.applyColors(style, isDarkMap, accentArgb)

    override fun onRemoved() {
        map.removeOnMapClickListener(clickListener)
        PrivacyZoneMapLayers.remove(style)
    }

    override fun onCleared() = onRemoved()

    private companion object {
        val TAPPABLE_LAYERS = arrayOf(PrivacyZoneMapLayers.FILL_LAYER)

        /** The map's own layers (not the basemap's) that the zones must stay beneath. */
        val OVERLAY_LAYERS = setOf(SURVEYED_POINTS_LAYER_KEY, TOWER_LAYER_KEY, SEARCH_TOWER_LAYER_KEY)
        val OVERLAY_LAYER_PREFIXES = listOf(
            SERVING_CELL_LINE_LAYER_PREFIX,
            SERVING_CELL_COVERAGE_FILL_LAYER_PREFIX,
            SERVING_CELL_COVERAGE_OUTLINE_LAYER_PREFIX,
        )

        /** @return The id of the lowest of the map's own layers already in [style], or null for none. */
        fun lowestOverlayLayer(style: Style): String? = style.layers.firstOrNull { layer ->
            layer.id in OVERLAY_LAYERS || OVERLAY_LAYER_PREFIXES.any { layer.id.startsWith(it) }
        }?.id
    }
}

/**
 * Draws the privacy zones on the Map tab. Compose it before the survey points and towers so the
 * zones sit beneath them.
 *
 * @param isDarkMap   True for a dark basemap, so the neutral zone color stays readable.
 * @param onZoneClick Called with the tapped zone's id (the Map tab shows its sheet).
 */
@Composable
internal fun PrivacyZonesMapLayer(zones: List<PrivacyZone>, isDarkMap: Boolean, onZoneClick: (String) -> Unit) {
    val mapApplier = currentComposer.applier as MapApplier
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    val currentOnZoneClick by rememberUpdatedState(onZoneClick)

    ComposeNode<PrivacyZonesNode, MapApplier>(
        factory = {
            PrivacyZonesNode(mapApplier.map, mapApplier.style, zones, isDarkMap, accentArgb) { currentOnZoneClick }
        },
        update = {
            set(zones) { update(it) }
            set(isDarkMap) { updateColors(it, accentArgb) }
            set(accentArgb) { updateColors(isDarkMap, it) }
        },
    )
}

/**
 * The small sheet a tap on a zone opens: its name and radius, and Edit. A tap inside a zone, even a
 * stray one, never leaves the map on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyZoneTapSheet(zone: PrivacyZone, onEdit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NsTheme.spacing.l)
                .padding(bottom = NsTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = zoneDisplayName(zone),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = NsTheme.spacing.s)
                        .semantics { heading() },
                )
            }
            Text(
                text = stringResource(R.string.privacy_zone_row_radius, formatRadius(zone.radiusMeters)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.privacy_zone_edit))
            }
        }
    }
}
