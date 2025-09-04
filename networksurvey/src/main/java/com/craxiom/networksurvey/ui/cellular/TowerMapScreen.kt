package com.craxiom.networksurvey.ui.cellular

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.craxiom.messaging.CdmaRecord
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.data.api.Tower
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.ui.activesurvey.model.SurveyTrack
import com.craxiom.networksurvey.ui.cellular.model.INITIAL_ZOOM
import com.craxiom.networksurvey.ui.cellular.model.MINIMUM_LOCATION_ZOOM
import com.craxiom.networksurvey.ui.cellular.model.MapTileSource
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.ServingSignalInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerMapLibreViewModel
import com.craxiom.networksurvey.ui.cellular.model.TowerSource
import com.craxiom.networksurvey.ui.cellular.towermap.CameraMode
import com.craxiom.networksurvey.ui.cellular.towermap.Circle
import com.craxiom.networksurvey.ui.cellular.towermap.DefaultMapLocationSettings
import com.craxiom.networksurvey.ui.cellular.towermap.DistanceSymbol
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_SEARCH_TOWER_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_SERVING_CELL_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_TOWER_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.LineString
import com.craxiom.networksurvey.ui.cellular.towermap.MapLibreMap
import com.craxiom.networksurvey.ui.cellular.towermap.MapUiSettings
import com.craxiom.networksurvey.ui.cellular.towermap.SearchResultSymbols
import com.craxiom.networksurvey.ui.cellular.towermap.TowerInfoDialog
import com.craxiom.networksurvey.ui.cellular.towermap.TowerSymbols
import com.craxiom.networksurvey.ui.cellular.towermap.rememberCameraPositionState
import com.craxiom.networksurvey.ui.cellular.towermap.rememberCircleState
import com.craxiom.networksurvey.ui.cellular.towermap.rememberLineStringState
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import okhttp3.internal.toImmutableMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import timber.log.Timber

// Helper functions to get the correct preference key based on context
private fun getMapTileSourceKey(context: MapContext): String {
    return when (context) {
        MapContext.TOWER_MAP -> NetworkSurveyConstants.PROPERTY_SELECTED_MAP_TILE_SOURCE
        MapContext.SURVEY_MONITOR -> NetworkSurveyConstants.PROPERTY_SURVEY_MAP_TILE_SOURCE
    }
}

private fun getBeaconDbCoverageKey(context: MapContext): String {
    return when (context) {
        MapContext.TOWER_MAP -> NetworkSurveyConstants.PROPERTY_SHOW_BEACONDB_COVERAGE
        MapContext.SURVEY_MONITOR -> NetworkSurveyConstants.PROPERTY_SURVEY_SHOW_BEACONDB_COVERAGE
    }
}

private fun getTowersLayerKey(context: MapContext): String {
    return when (context) {
        MapContext.TOWER_MAP -> NetworkSurveyConstants.PROPERTY_SHOW_TOWERS_LAYER
        MapContext.SURVEY_MONITOR -> NetworkSurveyConstants.PROPERTY_SURVEY_SHOW_TOWERS_LAYER
    }
}

private fun getKeepScreenOnKey(context: MapContext): String {
    return when (context) {
        MapContext.TOWER_MAP -> NetworkSurveyConstants.PROPERTY_MAP_KEEP_SCREEN_ON
        MapContext.SURVEY_MONITOR -> NetworkSurveyConstants.PROPERTY_SURVEY_MAP_KEEP_SCREEN_ON
    }
}

private fun getDefaultBeaconDbCoverage(context: MapContext): Boolean {
    return when (context) {
        MapContext.TOWER_MAP -> false      // Default for tower map
        MapContext.SURVEY_MONITOR -> true   // Default for survey monitor
    }
}

private fun getDefaultShowTowers(context: MapContext): Boolean {
    return when (context) {
        MapContext.TOWER_MAP -> true       // Default for tower map
        MapContext.SURVEY_MONITOR -> false  // Default for survey monitor
    }
}

private fun getDefaultKeepScreenOn(context: MapContext): Boolean {
    return when (context) {
        MapContext.TOWER_MAP -> true
        MapContext.SURVEY_MONITOR -> true
    }
}

/**
 * Creates the map view for displaying the tower locations. The tower locations are pulled from the
 * NS backend.
 */
@Composable
internal fun TowerMapScreen(
    viewModel: TowerMapLibreViewModel = viewModel(),
    onBackButtonPressed: () -> Unit,
    onNavigateToTowerMapSettings: () -> Unit,
    mapContext: MapContext = MapContext.TOWER_MAP,
    surveyTracks: List<SurveyTrack>? = null,
    initialBeaconDbEnabled: Boolean? = null,
    initialShowTowers: Boolean? = null,
    initialCameraMode: CameraMode? = null
) {
    val paddingInsets by viewModel.paddingInsets.collectAsStateWithLifecycle()

    val isLoadingInProgress by viewModel.isLoadingInProgress.collectAsStateWithLifecycle()
    val isZoomedOutTooFar by viewModel.isZoomedOutTooFar.collectAsStateWithLifecycle()
    val radio by viewModel.selectedRadioType.collectAsStateWithLifecycle()
    val currentPlmnFilter by viewModel.plmnFilter.collectAsStateWithLifecycle()
    val currentSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val noTowersFound by viewModel.noTowersFound.collectAsStateWithLifecycle()
    val isMapInitializing by viewModel.isMapInitializing.collectAsStateWithLifecycle()

    // Handle screen wake lock based on preference
    val context = LocalContext.current
    val view = LocalView.current
    val preferences = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // Read preference value on every recomposition to catch updates
    val keepScreenOn =
        preferences.getBoolean(getKeepScreenOnKey(mapContext), getDefaultKeepScreenOn(mapContext))

    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn

        onDispose {
            // Always reset to false when leaving the screen
            view.keepScreenOn = false
        }
    }

    val missingApiKey = BuildConfig.NS_API_KEY.isEmpty()

    val servingCells by viewModel.servingCells.collectAsStateWithLifecycle()
    var selectedSimIndex by remember { mutableIntStateOf(-1) }
    val servingCellSignals by viewModel.servingSignals.collectAsStateWithLifecycle()
    val servingCellLines by viewModel.servingCellLines.collectAsStateWithLifecycle()
    val showTowersLayer by viewModel.showTowersLayer.collectAsStateWithLifecycle()
    val searchedTower by viewModel.searchedTower.collectAsStateWithLifecycle()
    val isSearchInProgress by viewModel.isSearchInProgress.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val searchMccInput by viewModel.searchMccInput.collectAsStateWithLifecycle()
    val searchMncInput by viewModel.searchMncInput.collectAsStateWithLifecycle()
    val searchAreaInput by viewModel.searchAreaInput.collectAsStateWithLifecycle()
    val searchCidInput by viewModel.searchCidInput.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showTowerInfoDialog by remember { mutableStateOf(false) }
    var selectedTower by remember { mutableStateOf<Tower?>(null) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showFiltersDialog by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        // FIXME Is this redundant because there is similar logic in the view model?
        viewModel.lastQueriedBounds.value?.let { bounds ->
            position = CameraPosition.Builder()
                .target(LatLng(bounds.center.latitude, bounds.center.longitude))
                .zoom(INITIAL_ZOOM)
                .build()
        }
        // Set initial camera mode if provided
        initialCameraMode?.let { cameraMode = it }
    }

    val statusBarHeight = paddingInsets.calculateTopPadding()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isMapInitializing) {
            // Show loading screen while fetching MapTiler key
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading map...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val mapId = "basic-v2-dark" // "basic-v2" for light mode
                val mapTilerKey by viewModel.mapTilerKey.collectAsState()
                val mapKeyLoadError by viewModel.mapKeyLoadError.collectAsState()
                val selectedTileSource by viewModel.selectedMapTileSource.collectAsStateWithLifecycle()
                val darkMap = remember { mutableStateOf(false) }

                // Load preferences on startup
                DisposableEffect(Unit) {
                    val savedTileSource = preferences.getString(
                        getMapTileSourceKey(mapContext),
                        MapTileSource.MAPTILER.name
                    )
                    // Only set the saved tile source if there's no map key error
                    // Otherwise, respect the ViewModel's fallback to OpenStreetMap
                    if (!mapKeyLoadError) {
                        viewModel.setSelectedMapTileSource(MapTileSource.fromString(savedTileSource!!))
                    }

                    // Use initial values if provided, otherwise load from preferences
                    val showBeaconDb = initialBeaconDbEnabled ?: preferences.getBoolean(
                        getBeaconDbCoverageKey(mapContext),
                        getDefaultBeaconDbCoverage(mapContext)
                    )
                    viewModel.setShowBeaconDbCoverage(showBeaconDb)

                    val showTowers = initialShowTowers ?: preferences.getBoolean(
                        getTowersLayerKey(mapContext),
                        getDefaultShowTowers(mapContext)
                    )
                    viewModel.setShowTowersLayer(showTowers)

                    onDispose { }
                }

                // decide which style URL to use:
                val styleUrl = remember(mapTilerKey, mapKeyLoadError, selectedTileSource) {
                    when {
                        selectedTileSource == MapTileSource.OPENSTREETMAP -> {
                            // Always use OSM if explicitly selected
                            darkMap.value = false
                            "https://raw.githubusercontent.com/christianrowlands/ns-map-style-uri/refs/heads/main/openstreetmap.json"
                        }

                        selectedTileSource == MapTileSource.OPENFREEMAP -> {
                            // Use OpenFreeMap if explicitly selected
                            darkMap.value = false
                            "https://tiles.openfreemap.org/styles/liberty"
                        }

                        mapKeyLoadError || mapTilerKey.isNullOrEmpty() -> {
                            // Fallback to OSM if MapTiler key unavailable
                            darkMap.value = false
                            "https://raw.githubusercontent.com/christianrowlands/ns-map-style-uri/refs/heads/main/openstreetmap.json"
                        }

                        else -> {
                            // Use MapTiler
                            darkMap.value = true
                            "https://api.maptiler.com/maps/$mapId/style.json?key=$mapTilerKey"
                        }
                    }
                }

                val iconMap = remember(darkMap.value) {
                    if (darkMap.value) {
                        mapOf(
                            KEY_TOWER_ICON to R.drawable.ic_cell_tower_map_dark,
                            KEY_SERVING_CELL_ICON to R.drawable.ic_cell_tower_map_serving_dark,
                            KEY_SEARCH_TOWER_ICON to R.drawable.ic_cell_tower_search_dark
                        ).toImmutableMap()
                    } else {
                        mapOf(
                            KEY_TOWER_ICON to R.drawable.ic_cell_tower_map_light,
                            KEY_SERVING_CELL_ICON to R.drawable.ic_cell_tower_map_serving_light,
                            KEY_SEARCH_TOWER_ICON to R.drawable.ic_cell_tower_search_dark
                        ).toImmutableMap()
                    }
                }
                MapLibreMap(
                    styleUri = styleUrl,
                    modifier = Modifier.fillMaxSize(),
                    paddingInsets = paddingInsets,
                    images = iconMap,
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        compassEnabled = true,
                        rotationGesturesEnabled = true,
                        scrollGesturesEnabled = true,
                        tiltGesturesEnabled = false,
                        zoomGesturesEnabled = true
                    ),
                    locationSettings = DefaultMapLocationSettings,
                    onMapReady = { mapView, map, style ->
                        viewModel.initMapLibre(mapView, map, style)
                    },
                    onMyLocationChanged = viewModel::updateMyLocation,
                    onTowerClick = { tower ->
                        selectedTower = tower
                        showTowerInfoDialog = true
                    },
                ) {
                    // 1) Pull your tower wrappers from the VM…
                    val towers by viewModel.towers.collectAsStateWithLifecycle()
                    val towerWrapperList = towers.toList()

                    // 2) Pull the “serving cell” IDs so we can highlight them
                    val servingCellInfo by viewModel.servingCells.collectAsStateWithLifecycle()
                    val servingIds =
                        if (selectedSimIndex != -1 && servingCellInfo.containsKey(selectedSimIndex)) {
                            // Show only the selected SIM's serving cell
                            servingCellInfo[selectedSimIndex]?.let {
                                setOf(CellularUtils.getTowerId(it))
                            } ?: emptySet()
                        } else {
                            // Show all serving cells
                            servingCellInfo.values
                                .map { CellularUtils.getTowerId(it) }
                                .toSet()
                        }

                    // Check if towers layer should be shown
                    if (showTowersLayer) {
                        // 3) One single call to TowerSymbols
                        TowerSymbols(
                            towerWrapperList = towerWrapperList,
                            servingIds = servingIds
                        )

                        // Display search result coverage circle first (behind the icon)
                        val searchedTowerCoverage by viewModel.searchedTowerCoverage.collectAsStateWithLifecycle()
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

                        // Display search result icon on top
                        searchedTower?.let { tower ->
                            SearchResultSymbols(
                                searchedTower = tower
                            )
                        }

                        // Render serving cell lines first (continuous dashed line)
                        servingCellLines.forEach { lineData ->
                            LineString(
                                state = rememberLineStringState(
                                    points = listOf(lineData.startPoint, lineData.endPoint),
                                    color = colorResource(R.color.serving_cell_line),
                                    width = 3f,
                                    dashArray = listOf(5f, 3f) // Dashed line
                                )
                            )
                        }

                        // Then render distance symbols on top of lines
                        servingCellLines.forEach { lineData ->
                            DistanceSymbol(
                                startPoint = lineData.startPoint,
                                endPoint = lineData.endPoint,
                                distanceMeters = lineData.distanceMeters
                            )
                        }

                        // Render serving cell coverage circles
                        val displayCoverage =
                            PreferenceUtils.displayServingCellCoverageOnMap(context)
                        if (displayCoverage) {
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

                    // Render survey tracks if provided (independent of tower layer visibility)
                    surveyTracks?.forEach { track ->
                        if (track.points.size >= 2) {
                            LineString(
                                state = rememberLineStringState(
                                    points = track.points,
                                    color = track.color,
                                    width = 4f,
                                    dashArray = null // Solid line for tracks
                                )
                            )
                        }
                    }

                    // Handle BeaconDB overlay
                    val showBeaconDbCoverage by viewModel.showBeaconDbCoverage.collectAsStateWithLifecycle()
                    DisposableEffect(showBeaconDbCoverage) {
                        if (showBeaconDbCoverage) {
                            // Add BeaconDB coverage layer
                            viewModel.addBeaconDbCoverageLayer()
                        } else {
                            // Remove BeaconDB coverage layer
                            viewModel.removeBeaconDbCoverageLayer()
                        }
                        onDispose { }
                    }
                }

                TopAppBarOverlay(statusBarHeight)

                // Top area - only show back button if not in Survey Monitor context
                if (mapContext != MapContext.SURVEY_MONITOR) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = statusBarHeight + 4.dp, start = 16.dp)
                    ) {
                        MapButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back button",
                            onClick = { onBackButtonPressed() },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Bottom button bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = paddingInsets.calculateBottomPadding())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            modifier = Modifier,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = CircleShape,
                            shadowElevation = 6.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(4.dp)
                            ) {
                                LocationButton(
                                    isFollowing = cameraPositionState.cameraMode == CameraMode.TRACKING,
                                    toggleFollowMe = {
                                        if (cameraPositionState.cameraMode == CameraMode.TRACKING) {
                                            cameraPositionState.cameraMode = CameraMode.NONE
                                        } else {
                                            viewModel.getMyLocation()?.let { location ->
                                                val currentZoom = viewModel.getCurrentZoom()
                                                // Apply minimum zoom threshold
                                                val targetZoom =
                                                    kotlin.math.max(
                                                        currentZoom,
                                                        MINIMUM_LOCATION_ZOOM
                                                    )

                                                cameraPositionState.position =
                                                    CameraPosition.Builder()
                                                        .target(location)
                                                        .zoom(targetZoom)
                                                        .build()
                                            }
                                            // Enable tracking mode after setting position
                                            cameraPositionState.cameraMode = CameraMode.TRACKING
                                        }
                                    })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = MaterialTheme.shapes.large,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Info button (leftmost)
                            MapButton(
                                iconRes = R.drawable.ic_info,
                                contentDescription = "About Tower Map",
                                onClick = { showInfoDialog = true },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            // Settings button
                            MapButton(
                                iconRes = R.drawable.ic_settings,
                                contentDescription = "Tower Map Settings",
                                onClick = { onNavigateToTowerMapSettings() },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            // Filters button (center, larger) - only show when towers layer is enabled
                            if (showTowersLayer) {
                                MapButton(
                                    iconRes = R.drawable.ic_filter,
                                    contentDescription = "Filters",
                                    onClick = { showFiltersDialog = true },
                                    isLarge = true,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Search button - only show when towers layer is enabled
                            if (showTowersLayer) {
                                MapButton(
                                    iconRes = R.drawable.ic_search_24,
                                    contentDescription = "Search Tower",
                                    onClick = { showSearchDialog = true },
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }

                            // Layers button (rightmost)
                            MapButton(
                                iconRes = R.drawable.ic_layers,
                                contentDescription = "Map Layers",
                                onClick = { showLayersDialog = true },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (mapContext != MapContext.TOWER_MAP) {
                        // Add extra space for Survey Monitor context because we are not extending the map to the very bottom
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Serving cell info on the left side near the top
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            top = statusBarHeight + 70.dp,
                            start = 0.dp
                        )
                ) {
                    // Display the serving cell info for the selected SIM card
                    if (servingCells.isNotEmpty()) {
                        if (servingCells.size == 1) {
                            ServingCellInfoDisplay(
                                servingCells.values.first(),
                                servingCellSignals.values.first()
                            )
                        } else {
                            if (selectedSimIndex == -1) {
                                // Default to the first key if a SIM card has not been selected
                                selectedSimIndex = servingCells.keys.first()
                                viewModel.setSelectedSimSubscriptionId(selectedSimIndex)
                            }
                            ServingCellInfoDisplay(
                                servingCells[selectedSimIndex],
                                servingCellSignals[selectedSimIndex]
                            )
                        }
                    }

                    if (servingCells.size > 1) {
                        // Only show the SIM card selection drop down if there is more than one option
                        Spacer(modifier = Modifier.height(6.dp))
                        SimCardDropdown(servingCells, selectedSimIndex) { newIndex ->
                            selectedSimIndex = newIndex
                            viewModel.setSelectedSimSubscriptionId(newIndex)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Show toast if we failed to fetch the map key and user's preference was MapTiler
                DisposableEffect(mapKeyLoadError, selectedTileSource) {
                    // Check if key failed AND initial preference was MapTiler but we fell back to OSM
                    val savedTileSource = preferences.getString(
                        NetworkSurveyConstants.PROPERTY_SELECTED_MAP_TILE_SOURCE,
                        MapTileSource.MAPTILER.name
                    )
                    val initialPreferenceWasMapTiler =
                        MapTileSource.fromString(savedTileSource!!) == MapTileSource.MAPTILER

                    if ((mapKeyLoadError || mapTilerKey.isNullOrEmpty()) &&
                        initialPreferenceWasMapTiler &&
                        selectedTileSource == MapTileSource.OPENSTREETMAP
                    ) {
                        Toast.makeText(
                            context,
                            "Could not load map API key; using fallback tiles.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onDispose { }
                }
            }
        }

        if (showInfoDialog) {
            TowerMapInfoDialog(onDismiss = { showInfoDialog = false })
        }

        if (showTowerInfoDialog && selectedTower != null) {
            TowerInfoDialog(
                tower = selectedTower!!,
                onDismiss = {
                    showTowerInfoDialog = false
                    selectedTower = null
                }
            )
        }

        if (showLayersDialog) {
            val currentTileSource by viewModel.selectedMapTileSource.collectAsStateWithLifecycle()
            val showBeaconDbCoverage by viewModel.showBeaconDbCoverage.collectAsStateWithLifecycle()
            val mapKeyLoadError by viewModel.mapKeyLoadError.collectAsState()
            val mapTilerKey by viewModel.mapTilerKey.collectAsState()

            MapLayersDialog(
                currentTileSource = currentTileSource,
                showBeaconDbCoverage = showBeaconDbCoverage,
                showTowersLayer = showTowersLayer,
                onSetTileSource = { source ->
                    val previousSource = currentTileSource
                    viewModel.setSelectedMapTileSource(source)
                    // Save preference
                    preferences.edit {
                        putString(
                            getMapTileSourceKey(mapContext),
                            source.name
                        )
                    }
                    // Show error toast when switching from OSM to MapTiler with failed key
                    if (previousSource == MapTileSource.OPENSTREETMAP &&
                        source == MapTileSource.MAPTILER &&
                        (mapKeyLoadError || mapTilerKey.isNullOrEmpty())
                    ) {
                        Toast.makeText(
                            context,
                            "Could not load map API key; using fallback tiles.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onSetShowBeaconDbCoverage = { show ->
                    viewModel.setShowBeaconDbCoverage(show)
                    // Save preference
                    preferences.edit {
                        putBoolean(getBeaconDbCoverageKey(mapContext), show)
                    }
                },
                onSetShowTowersLayer = { show ->
                    viewModel.setShowTowersLayer(show)
                    // Save preference
                    preferences.edit {
                        putBoolean(getTowersLayerKey(mapContext), show)
                    }
                },
                onDismiss = { showLayersDialog = false }
            )
        }

        if (showSearchDialog) {
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
                onSearch = { mcc, mnc, area, cid ->
                    viewModel.searchForTower(mcc, mnc, area, cid)
                },
                onDismiss = { showSearchDialog = false },
                isSearching = isSearchInProgress,
                searchError = searchError,
                hasSearchResult = searchedTower != null,
                onClearSearchResult = viewModel::clearSearchResult
            )
        }

        if (showFiltersDialog) {
            val context = LocalContext.current
            CombinedFiltersBottomSheet(
                currentPlmn = currentPlmnFilter,
                currentRadio = radio,
                currentSource = currentSource,
                onSetPlmnFilter = { mcc, mnc ->
                    viewModel.setPlmnFilter(Plmn(mcc, mnc))
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
                onDismiss = { showFiltersDialog = false }
            )
        }
    }

    if (missingApiKey) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                text = "Missing the API Key. Please report this bug at https://github.com/christianrowlands/android-network-survey/issues/new/choose",
                color = MaterialTheme.colorScheme.surface,
                softWrap = true,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    } else {
        if (showTowersLayer && isZoomedOutTooFar) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Zoom in farther to see towers", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, softWrap = true,
                    textAlign = TextAlign.Center
                )
            }
        } else if (showTowersLayer && noTowersFound) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "No towers found in the area", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, softWrap = true,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!isMapInitializing && isLoadingInProgress) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarHeight)
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun TopAppBarOverlay(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.Black.copy(alpha = 0.25f))
    ) {
    }
}

@Composable
fun SimCardDropdown(
    servingCells: HashMap<Int, ServingCellInfo>,
    selectedSimIndex: Int,
    onSimSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val simOptions = servingCells.keys.toList() // Get SIM card indices

    // Dropdown button for selecting SIM card
    Button(onClick = { expanded = true }) {
        Text(text = "SIM Card $selectedSimIndex")
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        simOptions.forEachIndexed { _, simIndex ->
            DropdownMenuItem(
                text = { Text(text = "SIM Card $simIndex") },
                onClick = {
                    onSimSelected(simIndex)
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun ServingCellInfoDisplay(cellInfo: ServingCellInfo?, servingSignalInfo: ServingSignalInfo?) {
    Column(
        modifier = Modifier
            .wrapContentWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (cellInfo != null && cellInfo.servingCell != null) {
            val servingCell = cellInfo.servingCell
            val record = servingCell.cellularRecord

            // Technology badge at the top
            Surface(
                color = when (servingCell.cellularProtocol) {
                    CellularProtocol.NR -> Color(0xFFA855F7)
                    CellularProtocol.LTE -> Color(0xFF009688)
                    CellularProtocol.UMTS -> Color(0xFF2196F3)
                    CellularProtocol.CDMA -> Color(0xFF795548)
                    CellularProtocol.GSM -> Color(0xFFF97316)
                    else -> MaterialTheme.colorScheme.secondary
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = servingCell.cellularProtocol.toString(),
                    color = Color.White,
                    fontSize = 12.nonScaledSp,
                    lineHeight = 24.nonScaledSp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Signal info with vertical layout
            if (servingSignalInfo != null) {
                when (servingCell.cellularProtocol) {
                    CellularProtocol.GSM -> {
                        VerticalMetric("RSSI", servingSignalInfo.signalOne.toString())
                    }

                    CellularProtocol.CDMA -> {
                        VerticalMetric("ECIO", servingSignalInfo.signalOne.toString())
                    }

                    CellularProtocol.UMTS -> {
                        VerticalMetric("RSSI", servingSignalInfo.signalOne.toString())
                        VerticalMetric("RSCP", servingSignalInfo.signalTwo.toString())
                    }

                    CellularProtocol.LTE -> {
                        VerticalMetric("RSRP", servingSignalInfo.signalOne.toString())
                        VerticalMetric("RSRQ", servingSignalInfo.signalTwo.toString())
                    }

                    CellularProtocol.NR -> {
                        VerticalMetric("SS-RSRP", servingSignalInfo.signalOne.toString())
                        VerticalMetric("SS-RSRQ", servingSignalInfo.signalTwo.toString())
                    }

                    else -> {}
                }
            }

            // Cell ID info with vertical layout
            when (record) {
                is GsmRecord -> {
                    VerticalMetric("MCC", record.data.mcc.value.toString())
                    VerticalMetric("MNC", record.data.mnc.value.toString())
                    VerticalMetric("LAC", record.data.lac.value.toString())
                    VerticalMetric("CID", record.data.ci.value.toString())
                }

                is CdmaRecord -> {
                    VerticalMetric("SID", record.data.sid.value.toString())
                    VerticalMetric("NID", record.data.nid.value.toString())
                    VerticalMetric("BSID", record.data.bsid.value.toString())
                }

                is UmtsRecord -> {
                    VerticalMetric("MCC", record.data.mcc.value.toString())
                    VerticalMetric("MNC", record.data.mnc.value.toString())
                    VerticalMetric("LAC", record.data.lac.value.toString())
                    VerticalMetric("CID", record.data.cid.value.toString())
                }

                is LteRecord -> {
                    VerticalMetric("MCC", record.data.mcc.value.toString())
                    VerticalMetric("MNC", record.data.mnc.value.toString())
                    VerticalMetric("TAC", record.data.tac.value.toString())
                    VerticalMetric("ECI", record.data.eci.value.toString())
                }

                is NrRecord -> {
                    VerticalMetric("MCC", record.data.mcc.value.toString())
                    VerticalMetric("MNC", record.data.mnc.value.toString())
                    VerticalMetric("TAC", record.data.tac.value.toString())
                    VerticalMetric("NCI", record.data.nci.value.toString())
                }

                else -> {}
            }
        } else {
            Text(
                "No serving cell",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.nonScaledSp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VerticalMetric(label: String, value: String) {
    Column(
        modifier = Modifier
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 10.nonScaledSp,
            lineHeight = 12.nonScaledSp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.nonScaledSp,
            lineHeight = 20.nonScaledSp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = (-2).dp)  // Pull value up slightly to reduce gap
        )
    }
}

/**
 * Reusable map button with press animation.
 */
@Composable
fun MapButton(
    iconRes: Int? = null,
    icon: ImageVector? = null,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    require(iconRes != null || icon != null) { "Either iconRes or icon must be provided" }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        label = "button_scale"
    )

    if (isLarge) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier
                .size(64.dp)
                .scale(scale),
            containerColor = containerColor.copy(alpha = 0.95f),
            contentColor = contentColor,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp,
                hoveredElevation = 10.dp
            ),
            interactionSource = interactionSource
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = iconRes!!),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    } else {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier
                .size(48.dp)
                .scale(scale),
            containerColor = containerColor.copy(alpha = 0.95f),
            contentColor = contentColor,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp,
                hoveredElevation = 8.dp
            ),
            interactionSource = interactionSource
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = iconRes!!),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Location tracking button with two states: following and not following.
 */
@Composable
fun LocationButton(
    isFollowing: Boolean,
    toggleFollowMe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        label = "location_button_scale"
    )

    FloatingActionButton(
        onClick = toggleFollowMe,
        modifier = modifier
            .size(48.dp)
            .scale(scale),
        containerColor = if (isFollowing)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = if (isFollowing)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (isFollowing) 8.dp else 6.dp,
            pressedElevation = 10.dp,
            hoveredElevation = 9.dp
        ),
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(
                id = if (isFollowing)
                    R.drawable.ic_my_location
                else
                    R.drawable.ic_location_not_following
            ),
            contentDescription = if (isFollowing)
                "Stop Following"
            else
                "Follow My Location",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun TowerMapInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Tower Map Information")
        },
        text = {
            Box {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = """
                    The tower locations are sourced from various database, for example OpenCelliD ( https://opencellid.org ).
                    
                    Please note that these locations may not be accurate as they are generated from crowd-sourced data and based on survey results. The tower locations are provided for your convenience, but they should not be relied upon for precise accuracy. We recommend verifying tower locations through additional sources if accuracy is critical.
                """.trimIndent()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun PlmnFilterDialog(
    currentPlmn: Plmn,
    onSetPlmnFilter: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var mccInput by remember { mutableStateOf(currentPlmn.mcc.toString()) }
    var mncInput by remember { mutableStateOf(currentPlmn.mnc.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Set PLMN Filter")
        },
        text = {
            Box {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = """
                        A PLMN (Public Land Mobile Network) is a network uniquely identified by a Mobile Country Code (MCC) and a Mobile Network Code (MNC). In other words, a PLMN identifies a specific cellular provider. 
                        
                        This filter allows you to display towers for a specific cellular provider.
                    """.trimIndent()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = if (mccInput == "0") "" else mccInput,
                        onValueChange = { mccInput = it },
                        label = { Text("MCC") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            if (mccInput.isNotEmpty() && mccInput != "0") {
                                IconButton(onClick = { mccInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear MCC"
                                    )
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (mncInput == "0") "" else mncInput,
                        onValueChange = { mncInput = it },
                        label = { Text("MNC") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            if (mncInput.isNotEmpty() && mncInput != "0") {
                                IconButton(onClick = { mncInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear MNC"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mcc = mccInput.toIntOrNull() ?: 0
                    val mnc = mncInput.toIntOrNull() ?: 0
                    onSetPlmnFilter(mcc, mnc)
                    onDismiss()
                }
            ) {
                Text("Set Filter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersDialog(
    currentTileSource: MapTileSource,
    showBeaconDbCoverage: Boolean,
    showTowersLayer: Boolean,
    onSetTileSource: (MapTileSource) -> Unit,
    onSetShowBeaconDbCoverage: (Boolean) -> Unit,
    onSetShowTowersLayer: (Boolean) -> Unit,
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
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Map Layers",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Map Tile Source",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            MapTileSource.entries.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (source == currentTileSource),
                            onClick = { onSetTileSource(source) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (source == currentTileSource),
                        onClick = { onSetTileSource(source) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = source.displayName)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Map Overlays",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = showTowersLayer,
                        onClick = { onSetShowTowersLayer(!showTowersLayer) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showTowersLayer,
                    onCheckedChange = onSetShowTowersLayer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Towers")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = showBeaconDbCoverage,
                        onClick = { onSetShowBeaconDbCoverage(!showBeaconDbCoverage) }
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = showBeaconDbCoverage,
                    onCheckedChange = onSetShowBeaconDbCoverage
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "BeaconDB Coverage")
            }
        }
    }
}

/**
 * Helper function to robustly dismiss the keyboard in ModalBottomSheet.
 * Uses multiple approaches due to known issues with keyboard dismissal in bottom sheets.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberKeyboardDismisser(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current

    return remember(focusManager, keyboardController, view) {
        {
            // 1. Clear focus first (removes cursor from TextFields)
            focusManager.clearFocus()

            // 2. Hide keyboard using Compose API
            keyboardController?.hide()

            // 3. Fallback: Use Android's InputMethodManager for extra reliability
            try {
                val activity = context as? android.app.Activity
                if (activity != null) {
                    val imm =
                        activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                as? android.view.inputmethod.InputMethodManager
                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                }
            } catch (_: Exception) {
                // Silently fail if we can't get the activity or IMM
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CellSearchBottomSheet(
    mccValue: String,
    mncValue: String,
    areaValue: String,
    cidValue: String,
    onMccChange: (String) -> Unit,
    onMncChange: (String) -> Unit,
    onAreaChange: (String) -> Unit,
    onCidChange: (String) -> Unit,
    onClearAll: () -> Unit,
    onSearch: (mcc: Int, mnc: Int, area: Int, cid: Long) -> Unit,
    onDismiss: () -> Unit,
    isSearching: Boolean = false,
    searchError: String? = null,
    hasSearchResult: Boolean = false,
    onClearSearchResult: () -> Unit = {}
) {
    val bottomSheetState = rememberModalBottomSheetState()
    val dismissKeyboard = rememberKeyboardDismisser()
    val focusManager = LocalFocusManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cell Tower Search",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        dismissKeyboard()
                        onClearAll()
                    },
                    enabled = mccValue.isNotEmpty() || mncValue.isNotEmpty() ||
                            areaValue.isNotEmpty() || cidValue.isNotEmpty()
                ) {
                    Text("Clear All")
                }
            }

            Text(
                text = "Enter the cell tower parameters to search for it on the map.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
            )

            // First row: MCC and MNC
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = mccValue,
                    onValueChange = onMccChange,
                    label = { Text("MCC") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Right) }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = mccValue.isNotEmpty() && mccValue.toIntOrNull() == null
                )

                OutlinedTextField(
                    value = mncValue,
                    onValueChange = onMncChange,
                    label = { Text("MNC") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = mncValue.isNotEmpty() && mncValue.toIntOrNull() == null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second row: LAC/TAC and CID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = areaValue,
                    onValueChange = onAreaChange,
                    label = { Text("LAC/TAC") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Right) }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = areaValue.isNotEmpty() && areaValue.toIntOrNull() == null
                )

                OutlinedTextField(
                    value = cidValue,
                    onValueChange = onCidChange,
                    label = { Text("CID") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            dismissKeyboard()
                            // Optionally trigger search if all fields are valid
                            val mcc = mccValue.toIntOrNull()
                            val mnc = mncValue.toIntOrNull()
                            val area = areaValue.toIntOrNull()
                            val cid = cidValue.toLongOrNull()
                            if (mcc != null && mnc != null && area != null && cid != null) {
                                onSearch(mcc, mnc, area, cid)
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = cidValue.isNotEmpty() && cidValue.toLongOrNull() == null
                )
            }

            searchError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Clear Result button - shown only when there's a search result
                if (hasSearchResult) {
                    OutlinedButton(
                        onClick = onClearSearchResult,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Result",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Result")
                    }
                }

                // Search button
                Button(
                    onClick = {
                        // Dismiss keyboard first
                        dismissKeyboard()

                        val mcc = mccValue.toIntOrNull()
                        val mnc = mncValue.toIntOrNull()
                        val area = areaValue.toIntOrNull()
                        val cid = cidValue.toLongOrNull()

                        if (mcc != null && mnc != null && area != null && cid != null) {
                            onSearch(mcc, mnc, area, cid)
                        }
                    },
                    modifier = if (hasSearchResult) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    enabled = !isSearching &&
                            mccValue.toIntOrNull() != null &&
                            mncValue.toIntOrNull() != null &&
                            areaValue.toIntOrNull() != null &&
                            cidValue.toLongOrNull() != null
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Search")
                    }
                }
            }
        }
    }
}

@Composable
fun TowerSourceSelectionDialog(
    currentSource: TowerSource,
    onSetSource: (TowerSource) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf(currentSource) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Select Tower Data Source")
        },
        text = {
            Box {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = """
                        Select a data source to display tower information. Each source provides data from different origins:
                        
                        - OpenCelliD: Crowdsourced tower data from around the world.
                        - BTSearch: Poland specific tower database.
                    """.trimIndent()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    TowerSource.entries.forEach { source ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (source == selectedSource),
                                    onClick = { selectedSource = source }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (source == selectedSource),
                                onClick = { selectedSource = source }
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = source.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSetSource(selectedSource)
                    onDismiss()
                }
            ) {
                Text("Set Source")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CombinedFiltersBottomSheet(
    currentPlmn: Plmn,
    currentRadio: String,
    currentSource: TowerSource,
    onSetPlmnFilter: (Int, Int) -> Unit,
    onSetRadioType: (String) -> Unit,
    onSetTowerSource: (TowerSource) -> Unit,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()
    val dismissKeyboard = rememberKeyboardDismisser()
    val focusManager = LocalFocusManager.current

    var mccInput by remember { mutableStateOf(currentPlmn.mcc.toString()) }
    var mncInput by remember { mutableStateOf(currentPlmn.mnc.toString()) }
    var selectedRadio by remember { mutableStateOf(currentRadio) }
    var selectedSource by remember { mutableStateOf(currentSource) }

    val radioOptions = listOf(
        CellularProtocol.GSM.name,
        CellularProtocol.CDMA.name,
        CellularProtocol.UMTS.name,
        CellularProtocol.LTE.name,
        CellularProtocol.NR.name
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // PLMN Filter Section
            Text(
                text = "PLMN Filter",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Filter towers by specific cellular provider (MCC/MNC)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (mccInput == "0") "" else mccInput,
                    onValueChange = { mccInput = it },
                    label = { Text("MCC") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Right) }
                    ),
                    trailingIcon = {
                        if (mccInput.isNotEmpty() && mccInput != "0") {
                            IconButton(onClick = { mccInput = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear MCC"
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = if (mncInput == "0") "" else mncInput,
                    onValueChange = { mncInput = it },
                    label = { Text("MNC") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { dismissKeyboard() }
                    ),
                    trailingIcon = {
                        if (mncInput.isNotEmpty() && mncInput != "0") {
                            IconButton(onClick = { mncInput = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear MNC"
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Protocol Selection Section
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Filter towers by cellular protocol type",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            radioOptions.forEach { protocol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (protocol == selectedRadio),
                            onClick = { selectedRadio = protocol }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (protocol == selectedRadio),
                        onClick = { selectedRadio = protocol }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = protocol)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tower Source Section
            Text(
                text = "Tower Data Source",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Select which database to use for tower locations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            TowerSource.entries.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (source == selectedSource),
                            onClick = { selectedSource = source }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (source == selectedSource),
                        onClick = { selectedSource = source }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = source.displayName)
                        Text(
                            text = when (source) {
                                TowerSource.OpenCelliD -> "Crowdsourced tower data from around the world"
                                TowerSource.BTSearch -> "Poland specific tower database"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply button
            Button(
                onClick = {
                    dismissKeyboard()
                    val mcc = mccInput.toIntOrNull() ?: 0
                    val mnc = mncInput.toIntOrNull() ?: 0
                    onSetPlmnFilter(mcc, mnc)
                    onSetRadioType(selectedRadio)
                    onSetTowerSource(selectedSource)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Filters")
            }
        }
    }
}
