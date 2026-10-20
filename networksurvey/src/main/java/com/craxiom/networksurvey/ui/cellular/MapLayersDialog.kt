package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.model.MapTileSource
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.cellular.towermap.SurveyPointsLayerSummary
import com.craxiom.networksurvey.ui.common.NsRadioRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The Map tab's Layers sheet: the tile source as a single-choice group, then the overlays (towers
 * with their display mode, survey points, BeaconDB coverage) as switch rows, in the same row groups
 * as the destination pages. Every change applies at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersDialog(
    currentTileSource: MapTileSource,
    showBeaconDbCoverage: Boolean,
    showTowersLayer: Boolean,
    showOnlyServingCell: Boolean,
    showSurveyPoints: Boolean,
    surveyPointsCount: Int,
    surveyedMode: SurveyedPointColorMode,
    surveyedKind: SurveyedPointKind,
    onChangeSurveyPoints: () -> Unit,
    onAboutSurveyPoints: () -> Unit,
    onSetShowSurveyPoints: (Boolean) -> Unit,
    onSetTileSource: (MapTileSource) -> Unit,
    onSetShowBeaconDbCoverage: (Boolean) -> Unit,
    onSetShowTowersLayer: (Boolean) -> Unit,
    onSetShowOnlyServingCell: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NsTheme.spacing.screenGutter)
                .padding(bottom = NsTheme.spacing.xxl)
        ) {
            Text(
                text = stringResource(R.string.map_layers_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SectionHeader(title = stringResource(R.string.map_layers_tiles))
            NsRowGroup(selectable = true) {
                MapTileSource.entries.forEachIndexed { index, source ->
                    if (index > 0) NsRowDivider()
                    NsRadioRow(
                        title = source.displayName,
                        selected = source == currentTileSource,
                        onSelect = { onSetTileSource(source) },
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.map_layers_overlays))
            NsRowGroup {
                NsSwitchRow(title = stringResource(R.string.map_layers_towers), checked = showTowersLayer, onCheckedChange = onSetShowTowersLayer)
                if (showTowersLayer) {
                    // The display mode belongs to the towers layer, so it sits under its switch
                    Column(modifier = Modifier.padding(start = NsTheme.spacing.l).selectableGroup()) {
                        NsRadioRow(
                            title = stringResource(R.string.map_layers_all_towers),
                            selected = !showOnlyServingCell,
                            onSelect = { onSetShowOnlyServingCell(false) },
                        )
                        NsRadioRow(
                            title = stringResource(R.string.map_layers_serving_only),
                            subtitle = stringResource(R.string.map_layers_serving_only_summary),
                            selected = showOnlyServingCell,
                            onSelect = { onSetShowOnlyServingCell(true) },
                        )
                    }
                }
                NsRowDivider()
                SurveyPointsLayerSummary(
                    checked = showSurveyPoints,
                    count = surveyPointsCount,
                    mode = surveyedMode,
                    kind = surveyedKind,
                    onCheckedChange = onSetShowSurveyPoints,
                    onChange = onChangeSurveyPoints,
                    onAbout = onAboutSurveyPoints
                )
                NsRowDivider()
                NsSwitchRow(
                    title = stringResource(R.string.map_layers_beacondb),
                    checked = showBeaconDbCoverage,
                    onCheckedChange = onSetShowBeaconDbCoverage,
                )
            }
        }
    }
}
