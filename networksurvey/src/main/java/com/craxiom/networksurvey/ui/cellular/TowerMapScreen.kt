package com.craxiom.networksurvey.ui.cellular

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
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
import com.craxiom.networksurvey.ui.cellular.model.MapTileSource
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.ServingSignalInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerMapLibreViewModel
import com.craxiom.networksurvey.ui.cellular.model.TowerSource
import com.craxiom.networksurvey.ui.cellular.towermap.CameraMode
import com.craxiom.networksurvey.ui.cellular.towermap.Circle
import com.craxiom.networksurvey.ui.cellular.towermap.DefaultMapLocationSettings
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
import com.google.protobuf.GeneratedMessage
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
    val showTowersLayer by viewModel.showTowersLayer.collectAsStateWithLifecycle()
    val searchedTower by viewModel.searchedTower.collectAsStateWithLifecycle()
    val isSearchInProgress by viewModel.isSearchInProgress.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val searchMccInput by viewModel.searchMccInput.collectAsStateWithLifecycle()
    val searchMncInput by viewModel.searchMncInput.collectAsStateWithLifecycle()
    val searchAreaInput by viewModel.searchAreaInput.collectAsStateWithLifecycle()
    val searchCidInput by viewModel.searchCidInput.collectAsStateWithLifecycle()

    val options = listOf(
        CellularProtocol.GSM.name,
        CellularProtocol.CDMA.name,
        CellularProtocol.UMTS.name,
        CellularProtocol.LTE.name,
        CellularProtocol.NR.name
    )
    var expanded by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPlmnDialog by remember { mutableStateOf(false) }
    var showTowerSourceDialog by remember { mutableStateOf(false) }
    var showTowerInfoDialog by remember { mutableStateOf(false) }
    var selectedTower by remember { mutableStateOf<Tower?>(null) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

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
                var darkMap = remember { mutableStateOf(false) }

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

                        // Render serving cell lines
                        val servingCellLines by viewModel.servingCellLines.collectAsStateWithLifecycle()
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

                Column {
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = statusBarHeight + 4.dp, end = 16.dp)
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(8.dp))

                            // Only show back button if not in Survey Monitor context
                            if (mapContext != MapContext.SURVEY_MONITOR) {
                                IconButton(onClick = { onBackButtonPressed() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back button",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .padding(0.dp)
                                            .background(color = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(onClick = { showInfoDialog = true }) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "About Cellular Tower Map",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .padding(0.dp)
                                        .background(color = MaterialTheme.colorScheme.onSurface)
                                )
                            }

                            // Only show tower-related controls when towers layer is enabled
                            if (showTowersLayer) {
                                Spacer(modifier = Modifier.width(16.dp))

                                Button(
                                    onClick = { showPlmnDialog = true },
                                ) {
                                    val buttonText =
                                        if (currentPlmnFilter.isSet()) currentPlmnFilter.toString() else "PLMN Filter"
                                    Text(
                                        text = buttonText,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.nonScaledSp,
                                        lineHeight = 14.nonScaledSp,
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { expanded = true },
                                ) {
                                    Text(
                                        text = radio, color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.nonScaledSp,
                                        lineHeight = 14.nonScaledSp,
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                        ) {
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                options.forEach { label ->
                                    DropdownMenuItem(
                                        text = { Text(text = label) },
                                        onClick = {
                                            if (viewModel.selectedRadioType.value != label) {
                                                Timber.i("The Selected radio type changed to $label")
                                                viewModel.setSelectedRadioType(
                                                    label,
                                                    isManualSelection = true
                                                )
                                            }
                                            expanded = false
                                        })
                                }
                            }
                        }
                    }

                    // Only show tower source button when towers layer is enabled
                    if (showTowersLayer) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 16.dp)
                        ) {
                            Button(
                                onClick = { showTowerSourceDialog = true },
                            ) {
                                val buttonText = currentSource.displayName
                                Text(
                                    text = buttonText,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.nonScaledSp,
                                    lineHeight = 14.nonScaledSp,
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            vertical = paddingInsets.calculateBottomPadding(),
                            horizontal = 12.dp
                        )
                ) {
                    Column {
                        // Search button at the top when towers layer is enabled
                        if (showTowersLayer) {
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_search_24),
                                        contentDescription = "Search Tower",
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Button(
                                        onClick = { showSearchDialog = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent
                                        )
                                    ) {}
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Layers button
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_layers),
                                    contentDescription = "Map Layers",
                                    modifier = Modifier.size(24.dp)
                                )
                                Button(
                                    onClick = { showLayersDialog = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent
                                    )
                                ) {}
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_settings),
                                    contentDescription = "Tower Map Settings",
                                    modifier = Modifier.size(24.dp)
                                )
                                Button(
                                    onClick = { onNavigateToTowerMapSettings.invoke() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent
                                    )
                                ) {}
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_my_location),
                                    contentDescription = "My Location",
                                    modifier = Modifier.size(24.dp)
                                )
                                Button(
                                    onClick = { viewModel.goToMyLocation() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent
                                    )
                                ) {}
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        CircleButtonWithLine(
                            isFollowing = cameraPositionState.cameraMode == CameraMode.TRACKING,
                            toggleFollowMe = {
                                // Toggle camera mode directly on the state
                                cameraPositionState.cameraMode =
                                    if (cameraPositionState.cameraMode == CameraMode.TRACKING) {
                                        CameraMode.NONE
                                    } else {
                                        CameraMode.TRACKING
                                    }
                            })

                        if (mapContext == MapContext.TOWER_MAP) {
                            Spacer(modifier = Modifier.height(44.dp))
                        } else {
                            // Add extra space for Survey Monitor context because we are not extending the map to the very bottom
                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            vertical = paddingInsets.calculateBottomPadding(),
                            horizontal = 16.dp
                        )
                ) {
                    if (servingCells.size > 1) {
                        // Only show the drop down if there is more than one option
                        SimCardDropdown(servingCells, selectedSimIndex) { newIndex ->
                            selectedSimIndex = newIndex
                            viewModel.setSelectedSimSubscriptionId(newIndex)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

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

        if (showPlmnDialog) {
            PlmnFilterDialog(
                currentPlmn = currentPlmnFilter,
                onSetPlmnFilter = { mcc, mnc ->
                    viewModel.setPlmnFilter(Plmn(mcc, mnc))
                },
                onDismiss = { showPlmnDialog = false }
            )
        }

        if (showTowerSourceDialog) {
            val context = LocalContext.current
            TowerSourceSelectionDialog(
                currentSource = currentSource,
                onSetSource = { source ->
                    if (source != currentSource) {
                        viewModel.setTowerSource(source)
                        PreferenceUtils.setLastSelectedTowerSource(context, source)
                    }
                },
                onDismiss = { showTowerSourceDialog = false }
            )
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
                    color = MaterialTheme.colorScheme.surface, softWrap = true,
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
            .background(Color(0xA6EEEEEE))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        val fontSize = 14.nonScaledSp
        val lineHeight = 20.nonScaledSp
        Text(
            text = "Serving Cell Info",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface,
            fontSize = fontSize,
            lineHeight = lineHeight
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (cellInfo != null) {
            val servingCell = cellInfo.servingCell ?: return Text(
                "No serving cell found",
                color = MaterialTheme.colorScheme.surface,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
            val record = servingCell.cellularRecord

            // Display technology and signal strengths based on CellularRecord
            Text(
                "Technology: ${servingCell.cellularProtocol}",
                color = MaterialTheme.colorScheme.surface,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
            if (servingSignalInfo != null) {
                Text(
                    servingSignalInfo.toString(),
                    color = MaterialTheme.colorScheme.surface,
                    fontSize = fontSize,
                    lineHeight = lineHeight
                )
            }

            val servingCellDisplayString = getServingCellDisplayString(record)
            Text(
                servingCellDisplayString, color = MaterialTheme.colorScheme.surface,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
        } else {
            Text(
                "No serving cell info available", color = MaterialTheme.colorScheme.surface,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
        }
    }
}


private fun getServingCellDisplayString(message: GeneratedMessage): String {
    return when (message) {
        is GsmRecord -> {
            "MCC: ${message.data.mcc.value}\nMNC: ${message.data.mnc.value}\nLAC: ${message.data.lac.value}\nCellId: ${message.data.ci.value}"
        }

        is CdmaRecord -> {
            "SID: ${message.data.sid.value}\nNID: ${message.data.nid.value}\nBSID: ${message.data.bsid.value}"
        }

        is UmtsRecord -> {
            "MCC: ${message.data.mcc.value}\nMNC: ${message.data.mnc.value}\nLAC: ${message.data.lac.value}\nCellId: ${message.data.cid.value}"
        }

        is LteRecord -> {
            "MCC: ${message.data.mcc.value}\nMNC: ${message.data.mnc.value}\nTAC: ${message.data.tac.value}\nECI: ${message.data.eci.value}"
        }

        is NrRecord -> {
            "MCC: ${message.data.mcc.value}\nMNC: ${message.data.mnc.value}\nTAC: ${message.data.tac.value}\nNCI: ${message.data.nci.value}"
        }

        else -> {
            "Unknown Protocol"
        }
    }
}

@Composable
fun CircleButtonWithLine(
    isFollowing: Boolean,
    toggleFollowMe: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Image(
                painter = painterResource(id = if (isFollowing) R.drawable.ic_follow_me_enabled else R.drawable.ic_follow_me_disabled),
                contentDescription = "Follow Me",
                modifier = Modifier.size(24.dp)
            )
            Button(
                onClick = { toggleFollowMe() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                )
            ) {}
        }
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
