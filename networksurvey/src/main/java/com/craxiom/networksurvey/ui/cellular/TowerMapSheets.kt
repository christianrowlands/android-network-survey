package com.craxiom.networksurvey.ui.cellular

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.ui.cellular.model.MapTileSource
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.cellular.model.TowerMapLibreViewModel
import com.craxiom.networksurvey.ui.cellular.towermap.SurveyPointsAboutDialog
import com.craxiom.networksurvey.ui.cellular.towermap.SurveyedPointsOptionsSheet
import com.craxiom.networksurvey.ui.cellular.towermap.TowerBottomSheet
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber

/**
 * The map's sheets and dialogs: Layers, Filters, Search, About, the survey points options and the
 * tower details. [state] says which is open; each one saves what the user changes.
 */
@Composable
fun TowerMapSheets(viewModel: TowerMapLibreViewModel, state: TowerMapUiState) {
    val context = LocalContext.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val surveyed = rememberSurveyedPointsSettings(viewModel)

    if (state.showAbout) {
        MapAboutDialog(onDismiss = { state.showAbout = false })
    }

    if (state.showSurveyPointsAbout) {
        SurveyPointsAboutDialog(onDismiss = { state.showSurveyPointsAbout = false })
    }

    TowerBottomSheet(
        state = state.towerSheet,
        onStateChange = { state.towerSheet = it },
        onColorOverrideChanged = { viewModel.bumpColorOverrideVersion() }
    )

    if (state.showLayers) {
        LayersSheet(viewModel, state)
    }

    if (state.showSurveyedOptions) {
        val surveyedKindCounts by viewModel.surveyedPoints.countByKind.collectAsStateWithLifecycle(
            initialValue = emptyMap()
        )
        val availableSurveyedKinds = remember(surveyedKindCounts) {
            surveyedKindCounts.keys.mapNotNull { SurveyedPointKind.fromMask(it) }.toSet()
        }
        val surveyedData by viewModel.surveyedPoints.data.collectAsStateWithLifecycle()
        SurveyedPointsOptionsSheet(
            mode = surveyed.mode,
            kind = surveyed.kind,
            availableKinds = availableSurveyedKinds,
            data = surveyedData,
            uploadFilter = surveyed.filters.upload,
            onModeChange = surveyed.setMode,
            onKindChange = surveyed.setKind,
            onDismiss = { state.showSurveyedOptions = false }
        )
    }

    if (state.showSearch) {
        SearchSheet(viewModel, state)
    }

    if (state.showFilters) {
        val showTowersLayer by viewModel.showTowersLayer.collectAsStateWithLifecycle()
        val showSurveyPoints by viewModel.showSurveyPoints.collectAsStateWithLifecycle()
        val surveyedSources by viewModel.surveyedPoints.sources.collectAsStateWithLifecycle(initialValue = emptyList())
        val surveyedLatestMission by viewModel.surveyedPoints.latestMission.collectAsStateWithLifecycle()
        val surveyedLatestMissionStart by viewModel.surveyedPoints.latestMissionStart.collectAsStateWithLifecycle()
        val currentPlmnFilter by viewModel.plmnFilter.collectAsStateWithLifecycle()
        val radio by viewModel.selectedRadioType.collectAsStateWithLifecycle()
        val currentSource by viewModel.selectedSource.collectAsStateWithLifecycle()
        val currentMaxTowerAgeMonths by viewModel.maxTowerAgeMonths.collectAsStateWithLifecycle()
        CombinedFiltersBottomSheet(
            showTowerFilters = showTowersLayer,
            showSurveyedFilters = showSurveyPoints,
            currentSurveyedFilters = surveyed.filters,
            latestSurveyAvailable = surveyedLatestMission != null,
            latestSurveyStart = surveyedLatestMissionStart,
            showSourceFilter = surveyedSources.size > 1,
            onSetSurveyedFilters = surveyed.applyFilters,
            currentPlmn = currentPlmnFilter,
            currentRadio = radio,
            currentSource = currentSource,
            currentMaxAgeMonths = currentMaxTowerAgeMonths,
            onSetPlmnFilter = { mcc, mnc, mncString ->
                // The sheet reports the operator every time it closes; only a change is the user's pick
                val plmn = Plmn(mcc, mnc, mncString)
                if (plmn != viewModel.plmnFilter.value) viewModel.setPlmnFilter(plmn, isManualSelection = true)
            },
            onSetRadioType = { protocol ->
                if (viewModel.selectedRadioType.value != protocol) {
                    Timber.i("The Selected radio type changed to $protocol")
                    viewModel.setSelectedRadioType(protocol, isManualSelection = true)
                }
            },
            onSetTowerSource = { source ->
                if (source != currentSource) {
                    viewModel.setTowerSource(source)
                    PreferenceUtils.setLastSelectedTowerSource(context, source)
                }
            },
            onSetMaxAgeMonths = { months ->
                viewModel.setMaxTowerAgeMonths(months)
                preferences.edit {
                    putInt(NetworkSurveyConstants.PROPERTY_MAP_MAX_TOWER_AGE_MONTHS, months)
                }
            },
            onDismiss = { state.showFilters = false }
        )
    }
}

@Composable
private fun LayersSheet(viewModel: TowerMapLibreViewModel, state: TowerMapUiState) {
    val context = LocalContext.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val surveyed = rememberSurveyedPointsSettings(viewModel)
    val currentTileSource by viewModel.selectedMapTileSource.collectAsStateWithLifecycle()
    val showBeaconDbCoverage by viewModel.showBeaconDbCoverage.collectAsStateWithLifecycle()
    val showTowersLayer by viewModel.showTowersLayer.collectAsStateWithLifecycle()
    val showOnlyServingCell by viewModel.showOnlyServingCell.collectAsStateWithLifecycle()
    val showSurveyPoints by viewModel.showSurveyPoints.collectAsStateWithLifecycle()
    val surveyPointsCount by viewModel.surveyedPoints.count.collectAsStateWithLifecycle(initialValue = 0)
    val mapKeyLoadError by viewModel.mapKeyLoadError.collectAsState()
    val mapTilerKey by viewModel.mapTilerKey.collectAsState()
    val fallbackMessage = stringResource(R.string.map_key_fallback_toast)

    MapLayersDialog(
        currentTileSource = currentTileSource,
        showBeaconDbCoverage = showBeaconDbCoverage,
        showTowersLayer = showTowersLayer,
        showOnlyServingCell = showOnlyServingCell,
        showSurveyPoints = showSurveyPoints,
        surveyPointsCount = surveyPointsCount,
        surveyedMode = surveyed.mode,
        surveyedKind = surveyed.kind,
        onChangeSurveyPoints = {
            state.showLayers = false
            state.showSurveyedOptions = true
        },
        onAboutSurveyPoints = { state.showSurveyPointsAbout = true },
        onSetShowSurveyPoints = { show ->
            viewModel.setShowSurveyPoints(show)
            preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_SHOW_SURVEY_POINTS, show) }
        },
        onSetTileSource = { source ->
            val previousSource = currentTileSource
            viewModel.setSelectedMapTileSource(source)
            preferences.edit { putString(NetworkSurveyConstants.PROPERTY_SELECTED_MAP_TILE_SOURCE, source.name) }
            // Say so when switching from OSM to MapTiler while the key could not be loaded
            if (previousSource == MapTileSource.OPENSTREETMAP &&
                source == MapTileSource.MAPTILER &&
                (mapKeyLoadError || mapTilerKey.isNullOrEmpty())
            ) {
                Toast.makeText(context, fallbackMessage, Toast.LENGTH_LONG).show()
            }
        },
        onSetShowBeaconDbCoverage = { show ->
            viewModel.setShowBeaconDbCoverage(show)
            preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_SHOW_BEACONDB_COVERAGE, show) }
        },
        onSetShowTowersLayer = { show ->
            viewModel.setShowTowersLayer(show)
            preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_SHOW_TOWERS_LAYER, show) }
        },
        onSetShowOnlyServingCell = { show ->
            viewModel.setShowOnlyServingCell(show)
            preferences.edit { putBoolean(NetworkSurveyConstants.PROPERTY_MAP_SHOW_ONLY_SERVING_CELL, show) }
        },
        onDismiss = { state.showLayers = false }
    )
}

@Composable
private fun SearchSheet(viewModel: TowerMapLibreViewModel, state: TowerMapUiState) {
    val searchedTower by viewModel.searchedTower.collectAsStateWithLifecycle()
    val isSearchInProgress by viewModel.isSearchInProgress.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val searchMccInput by viewModel.searchMccInput.collectAsStateWithLifecycle()
    val searchMncInput by viewModel.searchMncInput.collectAsStateWithLifecycle()
    val searchAreaInput by viewModel.searchAreaInput.collectAsStateWithLifecycle()
    val searchCidInput by viewModel.searchCidInput.collectAsStateWithLifecycle()
    CellSearchBottomSheet(
        mccValue = searchMccInput,
        mncValue = searchMncInput,
        areaValue = searchAreaInput,
        cidValue = searchCidInput,
        onMccChange = viewModel::updateSearchMcc,
        onMncChange = viewModel::updateSearchMnc,
        onAreaChange = viewModel::updateSearchArea,
        onCidChange = viewModel::updateSearchCid,
        onClearAll = viewModel::clearSearchInputs,
        onSearch = { mcc, mnc, area, cid -> viewModel.searchForTower(mcc, mnc, area, cid) },
        onDismiss = { state.showSearch = false },
        isSearching = isSearchInProgress,
        searchError = searchError,
        hasSearchResult = searchedTower != null,
        onClearSearchResult = viewModel::clearSearchResult
    )
}

/** The survey points layer's color mode, kind and filters, with setters that save them. */
class SurveyedPointsSettings(
    val mode: SurveyedPointColorMode,
    val kind: SurveyedPointKind,
    val filters: SurveyedPointFilterSelection,
    val setMode: (SurveyedPointColorMode) -> Unit,
    val setKind: (SurveyedPointKind) -> Unit,
    val applyFilters: (SurveyedPointFilterSelection) -> Unit,
)

/** Reads the survey points settings from [viewModel] and saves a change to the preferences. */
@Composable
fun rememberSurveyedPointsSettings(viewModel: TowerMapLibreViewModel): SurveyedPointsSettings {
    val context = LocalContext.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val mode by viewModel.surveyedPoints.colorMode.collectAsStateWithLifecycle()
    val kind by viewModel.surveyedPoints.kind.collectAsStateWithLifecycle()
    val time by viewModel.surveyedPoints.timeFilter.collectAsStateWithLifecycle()
    val source by viewModel.surveyedPoints.sourceFilter.collectAsStateWithLifecycle()
    val upload by viewModel.surveyedPoints.uploadFilter.collectAsStateWithLifecycle()
    val filters = remember(time, source, upload) { SurveyedPointFilterSelection(time, source, upload) }

    // The controller may snap the other option, so both are persisted after either changes
    val persist = remember(viewModel) {
        {
            preferences.edit {
                putString(NetworkSurveyConstants.PROPERTY_SURVEY_POINTS_COLOR_MODE, viewModel.surveyedPoints.colorMode.value.name)
                putString(NetworkSurveyConstants.PROPERTY_SURVEY_POINTS_KIND, viewModel.surveyedPoints.kind.value.name)
            }
        }
    }
    return SurveyedPointsSettings(
        mode = mode,
        kind = kind,
        filters = filters,
        setMode = { viewModel.surveyedPoints.setColorMode(it); persist() },
        setKind = { viewModel.surveyedPoints.setKind(it); persist() },
        applyFilters = { selection ->
            viewModel.surveyedPoints.setTimeFilter(selection.time)
            viewModel.surveyedPoints.setSourceFilter(selection.source)
            viewModel.surveyedPoints.setUploadFilter(selection.upload)
        },
    )
}
