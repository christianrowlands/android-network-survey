package com.craxiom.networksurvey.ui.cellular

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.SurveyDatabase
import com.craxiom.networksurvey.ui.cellular.model.MapTileSource
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointColorMode
import com.craxiom.networksurvey.ui.cellular.model.SurveyedPointKind
import com.craxiom.networksurvey.ui.cellular.model.TowerMapLibreViewModel
import com.craxiom.networksurvey.ui.cellular.model.TowerSource
import com.craxiom.networksurvey.ui.cellular.towermap.Circle
import com.craxiom.networksurvey.ui.cellular.towermap.DefaultMapLocationSettings
import com.craxiom.networksurvey.ui.cellular.towermap.DistanceSymbol
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_SEARCH_TOWER_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_SERVING_CELL_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_TOWER_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.LineString
import com.craxiom.networksurvey.ui.cellular.towermap.MapLibreMap
import com.craxiom.networksurvey.ui.cellular.towermap.MapUiSettings
import com.craxiom.networksurvey.ui.cellular.towermap.SURVEYED_POINT_SHEET_PEEK
import com.craxiom.networksurvey.ui.cellular.towermap.SearchResultSymbols
import com.craxiom.networksurvey.ui.cellular.towermap.SurveyedPointSheet
import com.craxiom.networksurvey.ui.cellular.towermap.SurveyedPoints
import com.craxiom.networksurvey.ui.cellular.towermap.TowerSheetState
import com.craxiom.networksurvey.ui.cellular.towermap.TowerSymbols
import com.craxiom.networksurvey.ui.cellular.towermap.rememberCircleState
import com.craxiom.networksurvey.ui.cellular.towermap.rememberLineStringState
import com.craxiom.networksurvey.ui.common.InlineNotice
import com.craxiom.networksurvey.ui.map.MAP_TOP_STACK_MAX_WIDTH
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.watchlist.SheetDetent
import com.craxiom.networksurvey.ui.watchlist.rememberMapSheetMetrics
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import kotlinx.coroutines.launch

/** The most towers the tower service returns for one request; more means the map is truncated. */
private const val TOWER_RESPONSE_LIMIT = 5_000

private const val OPENSTREETMAP_STYLE_URL =
    "https://raw.githubusercontent.com/christianrowlands/ns-map-style-uri/refs/heads/main/openstreetmap.json"
private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val MAPTILER_MAP_ID = "basic-v2-dark"

/**
 * The map itself: the MapLibre map with its layers (survey points, towers, the serving-cell lines
 * and coverage, BeaconDB coverage), the survey point sheet, and the messages centered over it. The
 * Map tab draws its chrome (the title, the serving-cell card and the buttons) around it.
 *
 * @param state The camera and the open sheets, shared with the chrome.
 * @param selectedSim The SIM whose serving cell is highlighted, or null to highlight every SIM's.
 * @param contentPadding How much of the map the chrome covers at the top and the bottom.
 */
@Composable
internal fun TowerMapScreen(
    viewModel: TowerMapLibreViewModel,
    state: TowerMapUiState,
    selectedSim: Int?,
    contentPadding: PaddingValues,
) {
    val isMapInitializing by viewModel.isMapInitializing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // Read on every recomposition to catch a change made in Map settings. The modifier is counted,
    // so the map never clears the screen-on that a recording survey holds for the whole app.
    val keepScreenOn = preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MAP_KEEP_SCREEN_ON, true)

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(if (keepScreenOn) Modifier.keepScreenOn() else Modifier),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isMapInitializing) {
                // Wait for the MapTiler key before choosing a style
                MapLoading()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LoadSavedSettingsOnce(viewModel)
                    TowerMap(viewModel, state, selectedSim, contentPadding)
                    SurveyedPointSheetHost(state)
                    MapStyleFallbackToasts(viewModel)
                }
            }
        }

        MapMessages(viewModel, isMapInitializing, contentPadding)
    }
}

@Composable
private fun MapLoading() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.map_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Applies the saved layer and filter settings to [viewModel] once. The view model outlives the
 * map across tab visits, so a later visit keeps what the user changed since.
 */
@Composable
private fun LoadSavedSettingsOnce(viewModel: TowerMapLibreViewModel) {
    val context = LocalContext.current
    val mapKeyLoadError by viewModel.mapKeyLoadError.collectAsState()
    LaunchedEffect(viewModel) {
        if (viewModel.savedSettingsLoaded) return@LaunchedEffect
        viewModel.savedSettingsLoaded = true
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        // Only apply the saved tile source when the key loaded; otherwise keep the fallback
        if (!mapKeyLoadError) {
            val savedTileSource = preferences.getString(
                NetworkSurveyConstants.PROPERTY_SELECTED_MAP_TILE_SOURCE,
                MapTileSource.OPENFREEMAP.name
            )
            viewModel.setSelectedMapTileSource(MapTileSource.fromString(savedTileSource!!))
        }

        viewModel.setShowBeaconDbCoverage(
            preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_BEACONDB_COVERAGE, false)
        )
        viewModel.setShowTowersLayer(
            preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_TOWERS_LAYER, true)
        )

        // The survey points layer defaults on everywhere; an empty table draws nothing
        viewModel.initSurveyedPoints(SurveyDatabase.getInstance(context).surveyedPointDao())
        viewModel.setShowSurveyPoints(
            preferences.getBoolean(NetworkSurveyConstants.PROPERTY_SHOW_SURVEY_POINTS, true)
        )
        preferences.getString(NetworkSurveyConstants.PROPERTY_SURVEY_POINTS_COLOR_MODE, null)
            ?.let { name -> SurveyedPointColorMode.entries.firstOrNull { it.name == name } }
            ?.let { viewModel.surveyedPoints.setColorMode(it) }
        val savedKind = preferences.getString(NetworkSurveyConstants.PROPERTY_SURVEY_POINTS_KIND, null)
            ?.let { name -> SurveyedPointKind.entries.firstOrNull { it.name == name } }
        if (savedKind != null) {
            viewModel.surveyedPoints.setKind(savedKind)
        } else {
            // Never chosen: follow whatever kind the current walk is producing
            viewModel.surveyedPoints.applyDefaultKind()
        }

        viewModel.setShowOnlyServingCell(
            preferences.getBoolean(NetworkSurveyConstants.PROPERTY_MAP_SHOW_ONLY_SERVING_CELL, false)
        )
        // 0 = show all
        viewModel.setMaxTowerAgeMonths(
            preferences.getInt(NetworkSurveyConstants.PROPERTY_MAP_MAX_TOWER_AGE_MONTHS, 0)
        )
    }
}

/** The MapLibre map and its layers. */
@Composable
private fun TowerMap(
    viewModel: TowerMapLibreViewModel,
    state: TowerMapUiState,
    selectedSim: Int?,
    contentPadding: PaddingValues,
) {
    val mapTilerKey by viewModel.mapTilerKey.collectAsState()
    val mapKeyLoadError by viewModel.mapKeyLoadError.collectAsState()
    val selectedTileSource by viewModel.selectedMapTileSource.collectAsStateWithLifecycle()
    val searchedTower by viewModel.searchedTower.collectAsStateWithLifecycle()
    val showTowersLayer by viewModel.showTowersLayer.collectAsStateWithLifecycle()
    val showSurveyPoints by viewModel.showSurveyPoints.collectAsStateWithLifecycle()
    val darkMap = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Decide which style URL to use
    val styleUrl = remember(mapTilerKey, mapKeyLoadError, selectedTileSource) {
        when {
            selectedTileSource == MapTileSource.OPENSTREETMAP -> {
                darkMap.value = false
                OPENSTREETMAP_STYLE_URL
            }

            selectedTileSource == MapTileSource.OPENFREEMAP -> {
                darkMap.value = false
                OPENFREEMAP_STYLE_URL
            }

            // Fall back to OSM when the MapTiler key is unavailable
            mapKeyLoadError || mapTilerKey.isNullOrEmpty() -> {
                darkMap.value = false
                OPENSTREETMAP_STYLE_URL
            }

            else -> {
                darkMap.value = true
                "https://api.maptiler.com/maps/$MAPTILER_MAP_ID/style.json?key=$mapTilerKey"
            }
        }
    }

    // SDF tower icons, same for both dark and light maps (color comes from data)
    val sdfIconMap = remember {
        mapOf(
            KEY_TOWER_ICON to R.drawable.ic_cell_tower_map_sdf,
            KEY_SERVING_CELL_ICON to R.drawable.ic_cell_tower_map_serving_sdf,
        )
    }

    // Non-SDF icons (search result keeps its fixed color)
    val iconMap = remember {
        mapOf(KEY_SEARCH_TOWER_ICON to R.drawable.ic_cell_tower_search_dark)
    }

    MapLibreMap(
        styleUri = styleUrl,
        modifier = Modifier.fillMaxSize(),
        paddingInsets = contentPadding,
        images = iconMap,
        sdfImages = sdfIconMap,
        cameraPositionState = state.camera,
        uiSettings = MapUiSettings(
            compassEnabled = true,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = false,
            zoomGesturesEnabled = true
        ),
        locationSettings = DefaultMapLocationSettings,
        onMapReady = { _, map, _ -> viewModel.initMapLibre(map) },
        onStyleLoadFailed = viewModel::onMapStyleLoadFailed,
        onMyLocationChanged = viewModel::updateMyLocation,
        onTowersClick = { towerIds ->
            val towers = towerIds.mapNotNull { id ->
                searchedTower?.takeIf { CellularUtils.getTowerId(it) == id }
                    ?: viewModel.towerById(id)?.tower
            }
            if (towers.isEmpty()) return@MapLibreMap
            state.towerSheet = if (towers.size == 1) {
                TowerSheetState.TowerDetail(towers[0])
            } else {
                TowerSheetState.TowerList(towers)
            }
        },
        onSurveyedPointClick = { tap ->
            scope.launch {
                viewModel.surveyedPoints.resolve(tap)?.let {
                    // The sheet may have been swiped away or left open; always start small
                    state.surveyedSheetState.snapTo(SheetDetent.Peek)
                    state.surveyedSelection = it
                }
            }
        },
        onMapDestroyed = viewModel::onMapDestroyed,
    ) {
        // Survey points sit beneath everything else, so they are composed first
        if (showSurveyPoints) {
            val surveyedData by viewModel.surveyedPoints.data.collectAsStateWithLifecycle()
            val surveyedMode by viewModel.surveyedPoints.colorMode.collectAsStateWithLifecycle()
            val surveyedColorVersion by viewModel.colorOverrideVersion.collectAsStateWithLifecycle()
            SurveyedPoints(
                data = surveyedData,
                mode = surveyedMode,
                isDarkMap = darkMap.value,
                colorVersion = surveyedColorVersion,
            )
        }

        if (showTowersLayer) {
            TowerLayers(viewModel, selectedSim, darkMap.value)
        }

        // Handle BeaconDB overlay
        val showBeaconDbCoverage by viewModel.showBeaconDbCoverage.collectAsStateWithLifecycle()
        DisposableEffect(showBeaconDbCoverage) {
            if (showBeaconDbCoverage) {
                viewModel.addBeaconDbCoverageLayer()
            } else {
                viewModel.removeBeaconDbCoverageLayer()
            }
            onDispose { }
        }
    }
}

/** The towers, the search result, and the serving cells' lines and coverage. */
@Composable
private fun TowerLayers(viewModel: TowerMapLibreViewModel, selectedSim: Int?, isDarkMap: Boolean) {
    val context = LocalContext.current
    val towerWrapperList by viewModel.towers.collectAsStateWithLifecycle()
    val locationBadges by viewModel.locationBadges.collectAsStateWithLifecycle()
    val servingCellInfo by viewModel.servingCells.collectAsStateWithLifecycle()
    val colorOverrideVersion by viewModel.colorOverrideVersion.collectAsStateWithLifecycle()
    val searchedTower by viewModel.searchedTower.collectAsStateWithLifecycle()
    val searchedTowerCoverage by viewModel.searchedTowerCoverage.collectAsStateWithLifecycle()
    val servingCellLines by viewModel.servingCellLines.collectAsStateWithLifecycle()

    // Highlight the selected SIM's serving cell, or every SIM's when none is selected
    val servingIds = if (selectedSim != null && servingCellInfo.containsKey(selectedSim)) {
        servingCellInfo[selectedSim]?.let { setOf(CellularUtils.getTowerId(it)) } ?: emptySet()
    } else {
        servingCellInfo.values.map { CellularUtils.getTowerId(it) }.toSet()
    }

    TowerSymbols(
        towerWrapperList = towerWrapperList,
        badges = locationBadges,
        servingIds = servingIds,
        isDarkMap = isDarkMap,
        colorVersion = colorOverrideVersion,
    )

    // The search result's coverage circle first (behind the icon), then the icon on top
    searchedTowerCoverage?.let { coverageData ->
        val (fillColor, strokeColor) = getCoverageCircleColors()
        Circle(
            state = rememberCircleState(
                center = coverageData.center,
                radiusMeters = coverageData.radiusMeters,
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = 2f
            )
        )
    }
    searchedTower?.let { tower -> SearchResultSymbols(searchedTower = tower) }

    // Serving cell lines first (continuous dashed line), then the distances on top of them
    servingCellLines.forEach { lineData ->
        LineString(
            state = rememberLineStringState(
                points = listOf(lineData.startPoint, lineData.endPoint),
                color = colorResource(R.color.serving_cell_line),
                width = 3f,
                dashArray = listOf(5f, 3f)
            )
        )
    }
    servingCellLines.forEach { lineData ->
        DistanceSymbol(
            startPoint = lineData.startPoint,
            endPoint = lineData.endPoint,
            distanceMeters = lineData.distanceMeters
        )
    }

    if (PreferenceUtils.displayServingCellCoverageOnMap(context)) {
        val servingCellCoverage by viewModel.servingCellCoverage.collectAsStateWithLifecycle()
        val (fillColor, strokeColor) = getCoverageCircleColors()
        servingCellCoverage.forEach { coverageData ->
            Circle(
                state = rememberCircleState(
                    center = coverageData.center,
                    radiusMeters = coverageData.radiusMeters,
                    fillColor = fillColor,
                    strokeColor = strokeColor,
                    strokeWidth = 2f
                )
            )
        }
    }
}

/** The sheet for a tapped survey point, anchored to the bottom of the map. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SurveyedPointSheetHost(state: TowerMapUiState) {
    val selection = state.surveyedSelection ?: return
    val scope = rememberCoroutineScope()
    val sheetState = state.surveyedSheetState

    // One dismissal for all three routes (drag, X, Back) so the motion matches and only the
    // settle effect below knows how the sheet is torn down
    val dismissSheet = {
        scope.launch { sheetState.animateTo(SheetDetent.Hidden) }
        Unit
    }
    // Back closes the sheet rather than leaving the map
    BackHandler(onBack = dismissSheet)
    // settledValue, not currentValue: currentValue flips at the halfway point of a drag, which
    // would tear the sheet out of composition mid-animation
    LaunchedEffect(sheetState.settledValue) {
        if (sheetState.settledValue == SheetDetent.Hidden) {
            state.surveyedSelection = null
        }
    }

    // Inside the tab the bottom bar has already taken the navigation bar's inset, so only what is
    // left of it raises the peek
    var consumed by remember { mutableStateOf(WindowInsets(0)) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onConsumedWindowInsetsChanged { consumed = it }
    ) {
        val navBarHeight = WindowInsets.navigationBars.exclude(consumed).asPaddingValues().calculateBottomPadding()
        val metrics = rememberMapSheetMetrics(
            state = sheetState,
            availableHeight = maxHeight,
            peekVisibleHeight = SURVEYED_POINT_SHEET_PEEK + navBarHeight,
            dismissible = true,
        )
        SurveyedPointSheet(
            selection = selection,
            state = sheetState,
            sheetHeight = metrics.sheetHeight,
            onDismiss = dismissSheet,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Says when the map fell back from MapTiler to other tiles. */
@Composable
private fun MapStyleFallbackToasts(viewModel: TowerMapLibreViewModel) {
    val context = LocalContext.current
    val mapTilerKey by viewModel.mapTilerKey.collectAsState()
    val mapKeyLoadError by viewModel.mapKeyLoadError.collectAsState()
    val selectedTileSource by viewModel.selectedMapTileSource.collectAsStateWithLifecycle()
    val keyFallbackMessage = stringResource(R.string.map_key_fallback_toast)
    val runtimeFallbackMessage = stringResource(R.string.map_tile_source_runtime_fallback_toast)

    // The key could not be loaded while the saved preference was MapTiler, so OSM is showing
    LaunchedEffect(mapKeyLoadError, selectedTileSource) {
        val savedTileSource = PreferenceManager.getDefaultSharedPreferences(context).getString(
            NetworkSurveyConstants.PROPERTY_SELECTED_MAP_TILE_SOURCE,
            MapTileSource.OPENFREEMAP.name
        )
        val initialPreferenceWasMapTiler = MapTileSource.fromString(savedTileSource!!) == MapTileSource.MAPTILER
        if ((mapKeyLoadError || mapTilerKey.isNullOrEmpty()) &&
            initialPreferenceWasMapTiler &&
            selectedTileSource == MapTileSource.OPENSTREETMAP
        ) {
            Toast.makeText(context, keyFallbackMessage, Toast.LENGTH_LONG).show()
        }
    }

    // MapTiler failed at runtime (for example its quota ran out)
    val runtimeFallback by viewModel.runtimeStyleFallback.collectAsStateWithLifecycle()
    LaunchedEffect(runtimeFallback) {
        if (runtimeFallback) {
            Toast.makeText(context, runtimeFallbackMessage, Toast.LENGTH_LONG).show()
            viewModel.clearRuntimeStyleFallback()
        }
    }
}

/**
 * The messages over the map: a missing API key, zoom in for towers, no towers, the loading
 * spinner, and the truncation and BTSearch age notes. They keep clear of the chrome.
 */
@Composable
private fun MapMessages(viewModel: TowerMapLibreViewModel, isMapInitializing: Boolean, contentPadding: PaddingValues) {
    val showTowersLayer by viewModel.showTowersLayer.collectAsStateWithLifecycle()
    val showOnlyServingCell by viewModel.showOnlyServingCell.collectAsStateWithLifecycle()
    val isZoomedOutTooFar by viewModel.isZoomedOutTooFar.collectAsStateWithLifecycle()
    val noTowersFound by viewModel.noTowersFound.collectAsStateWithLifecycle()
    val isLoadingInProgress by viewModel.isLoadingInProgress.collectAsStateWithLifecycle()
    val towersTruncated by viewModel.towersTruncated.collectAsStateWithLifecycle()
    val maxTowerAgeMonths by viewModel.maxTowerAgeMonths.collectAsStateWithLifecycle()
    val currentSource by viewModel.selectedSource.collectAsStateWithLifecycle()

    if (BuildConfig.NS_API_KEY.isEmpty()) {
        MapNotice(stringResource(R.string.map_missing_api_key), contentPadding, MaterialTheme.colorScheme.error)
        return
    }

    if (showTowersLayer && isZoomedOutTooFar) {
        MapNotice(stringResource(R.string.map_zoom_in_for_towers), contentPadding)
    } else if (showTowersLayer && noTowersFound) {
        MapNotice(
            stringResource(
                if (showOnlyServingCell) R.string.tower_map_no_serving_cell_tower
                else R.string.tower_map_no_towers_in_area
            ),
            contentPadding,
        )
    }

    if (!isMapInitializing && isLoadingInProgress) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            CircularProgressIndicator()
        }
    }

    val showBtSearchAgeNote = showTowersLayer && currentSource == TowerSource.BTSearch &&
            maxTowerAgeMonths > 0 && !isLoadingInProgress
    val showTruncationNote = showTowersLayer && towersTruncated && !isLoadingInProgress
    if (showTruncationNote || showBtSearchAgeNote) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp, start = 16.dp, end = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showBtSearchAgeNote) {
                    MapInfoChip(text = stringResource(R.string.map_age_filter_not_btsearch))
                }
                if (showTruncationNote) {
                    MapInfoChip(text = pluralStringResource(R.plurals.map_towers_truncated, TOWER_RESPONSE_LIMIT, TOWER_RESPONSE_LIMIT))
                }
            }
        }
    }
}

/**
 * Small info-styled chip used to surface map context (truncation, filter-inert state, etc.).
 */
@Composable
private fun MapInfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * Gets the color for the coverage circle based on user preference.
 * Returns a pair of (fillColor, strokeColor) where fillColor has the user-specified opacity
 * and strokeColor is always 100% opaque.
 */
@Composable
private fun getCoverageCircleColors(): Pair<Color, Color> {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val selectedColor = preferences.getString(
        NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_COLOR,
        NetworkSurveyConstants.DEFAULT_COVERAGE_CIRCLE_COLOR
    )
    val opacity =
        preferences.getInt(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_OPACITY, 30)

    // Convert opacity percentage (0-100) to alpha float (0.0-1.0)
    val alpha = opacity / 100f

    val baseColor = when (selectedColor) {
        "red" -> colorResource(R.color.coverage_circle_red)
        "green" -> colorResource(R.color.coverage_circle_green)
        "orange" -> colorResource(R.color.coverage_circle_orange)
        "purple" -> colorResource(R.color.coverage_circle_purple)
        "yellow" -> colorResource(R.color.coverage_circle_yellow)
        "cyan" -> colorResource(R.color.coverage_circle_cyan)
        "white" -> colorResource(R.color.coverage_circle_white)
        else -> colorResource(R.color.serving_cell_dark) // Default blue
    }

    // Return fill color with opacity and stroke color with 100% opacity
    return Pair(baseColor.copy(alpha = alpha), baseColor)
}
