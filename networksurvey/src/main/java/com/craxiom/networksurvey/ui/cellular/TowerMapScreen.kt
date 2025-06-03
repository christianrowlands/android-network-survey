package com.craxiom.networksurvey.ui.cellular

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import com.craxiom.networksurvey.ui.cellular.model.INITIAL_ZOOM
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.ServingSignalInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerMapLibreViewModel
import com.craxiom.networksurvey.ui.cellular.model.TowerSource
import com.craxiom.networksurvey.ui.cellular.towermap.CameraMode
import com.craxiom.networksurvey.ui.cellular.towermap.Circle
import com.craxiom.networksurvey.ui.cellular.towermap.DefaultMapLocationSettings
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_SERVING_CELL_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.KEY_TOWER_ICON
import com.craxiom.networksurvey.ui.cellular.towermap.LineString
import com.craxiom.networksurvey.ui.cellular.towermap.MapLibreMap
import com.craxiom.networksurvey.ui.cellular.towermap.MapUiSettings
import com.craxiom.networksurvey.ui.cellular.towermap.TowerInfoDialog
import com.craxiom.networksurvey.ui.cellular.towermap.TowerSymbols
import com.craxiom.networksurvey.ui.cellular.towermap.rememberCameraPositionState
import com.craxiom.networksurvey.ui.cellular.towermap.rememberCircleState
import com.craxiom.networksurvey.ui.cellular.towermap.rememberLineStringState
import com.craxiom.networksurvey.util.CellularUtils
import com.craxiom.networksurvey.util.PreferenceUtils
import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import timber.log.Timber


/**
 * Creates the map view for displaying the tower locations. The tower locations are pulled from the
 * NS backend.
 */
@Composable
internal fun TowerMapScreen(
    viewModel: TowerMapLibreViewModel = viewModel(),
    onBackButtonPressed: () -> Unit,
    onNavigateToTowerMapSettings: () -> Unit
) {
    val paddingInsets by viewModel.paddingInsets.collectAsStateWithLifecycle()

    val isLoadingInProgress by viewModel.isLoadingInProgress.collectAsStateWithLifecycle()
    val isZoomedOutTooFar by viewModel.isZoomedOutTooFar.collectAsStateWithLifecycle()
    val radio by viewModel.selectedRadioType.collectAsStateWithLifecycle()
    val currentPlmnFilter by viewModel.plmnFilter.collectAsStateWithLifecycle()
    val currentSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val noTowersFound by viewModel.noTowersFound.collectAsStateWithLifecycle()
    val isMapInitializing by viewModel.isMapInitializing.collectAsStateWithLifecycle()

    val missingApiKey = BuildConfig.NS_API_KEY.isEmpty()

    val servingCells by viewModel.servingCells.collectAsStateWithLifecycle()
    var selectedSimIndex by remember { mutableIntStateOf(-1) }
    val servingCellSignals by viewModel.servingSignals.collectAsStateWithLifecycle()

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

    val cameraPositionState = rememberCameraPositionState {
        // FIXME Is this redundant because there is similar logic in the view model?
        viewModel.lastQueriedBounds.value?.let { bounds ->
            position = CameraPosition.Builder()
                .target(LatLng(bounds.center.latitude, bounds.center.longitude))
                .zoom(INITIAL_ZOOM)
                .build()
        }
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
                var darkMap = remember { mutableStateOf(false) }

                // decide which style URL to use:
                val styleUrl = remember(mapTilerKey, mapKeyLoadError) {
                    if (mapKeyLoadError || mapTilerKey.isNullOrEmpty()) {
                        // generic (OSM) raster‐tiles style
                        darkMap.value = false
                        "https://raw.githubusercontent.com/christianrowlands/ns-map-style-uri/refs/heads/main/openstreetmap.json"
                    } else {
                        darkMap.value = true
                        "https://api.maptiler.com/maps/$mapId/style.json?key=$mapTilerKey"
                    }
                }

                val iconMap = remember(darkMap.value) {
                    if (darkMap.value) {
                        mapOf(
                            KEY_TOWER_ICON to R.drawable.ic_cell_tower_map_dark,
                            KEY_SERVING_CELL_ICON to R.drawable.ic_cell_tower_map_serving_dark
                        ).toImmutableMap()
                    } else {
                        mapOf(
                            KEY_TOWER_ICON to R.drawable.ic_cell_tower_map_light,
                            KEY_SERVING_CELL_ICON to R.drawable.ic_cell_tower_map_serving_light
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
                    //val towers = towerWrappers.map { it.tower }
                    val towerWrapperList =
                        towers.toList() // TODO This will be expensive if called often

                    // 2) Pull the “serving cell” IDs so we can highlight them
                    val servingCellInfo by viewModel.servingCells.collectAsStateWithLifecycle()
                    val servingIds = servingCellInfo.values
                        .map { CellularUtils.getTowerId(it) }
                        .toSet()

                    // 3) One single call to TowerSymbols
                    TowerSymbols(
                        towerWrapperList = towerWrapperList,
                        servingIds = servingIds
                    )

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
                    val servingCellCoverage by viewModel.servingCellCoverage.collectAsStateWithLifecycle()
                    val coverageCircleColor = getCoverageCircleColor()
                    servingCellCoverage.forEach { coverageData ->
                        Circle(
                            state = rememberCircleState(
                                center = coverageData.center,
                                radiusMeters = coverageData.radiusMeters,
                                //fillColor = colorResource(R.color.serving_cell_coverage).copy(alpha = 0.1f),
                                strokeColor = coverageCircleColor,
                                strokeWidth = 2f
                            )
                        )
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
                                                viewModel.setSelectedRadioType(label)
                                                viewModel.viewModelScope.launch {
                                                    viewModel.runTowerQuery()
                                                }
                                            }
                                            expanded = false
                                        })
                                }
                            }
                        }
                    }

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

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            vertical = paddingInsets.calculateBottomPadding(),
                            horizontal = 12.dp
                        )
                ) {
                    Column {
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
                                cameraPositionState.cameraMode = if (cameraPositionState.cameraMode == CameraMode.TRACKING) {
                                    CameraMode.NONE
                                } else {
                                    CameraMode.TRACKING
                                }
                            })

                        Spacer(modifier = Modifier.height(44.dp))
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
                        }
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
                            }
                            ServingCellInfoDisplay(
                                servingCells[selectedSimIndex],
                                servingCellSignals[selectedSimIndex]
                            )
                        }
                    }
                }

                // show a banner if we failed to fetch
                // FIXME Move this somewhere it is not covered up by the top bar
                if (mapKeyLoadError || mapTilerKey.isNullOrEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Red)
                            .padding(4.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Text(
                            "Could not load map API key; using fallback tiles.",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
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
                    viewModel.viewModelScope.launch {
                        viewModel.runTowerQuery()
                    }
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
                        viewModel.viewModelScope.launch {
                            viewModel.runTowerQuery()
                        }
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
        if (isZoomedOutTooFar) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Zoom in farther to see towers", fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface, softWrap = true,
                    textAlign = TextAlign.Center
                )
            }
        } else if (noTowersFound) {
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
                    
                    Legend:
                    - Purple: Your Current Serving Cell
                    - Blue: Non-Serving Cells
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
 * Gets the color resource ID for the coverage circle based on user preference.
 */
@Composable
private fun getCoverageCircleColor(): Color {
    val context = LocalContext.current
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val selectedColor = preferences.getString(NetworkSurveyConstants.PROPERTY_MAP_COVERAGE_CIRCLE_COLOR, NetworkSurveyConstants.DEFAULT_COVERAGE_CIRCLE_COLOR)

    return when (selectedColor) {
        "red" -> colorResource(R.color.coverage_circle_red)
        "green" -> colorResource(R.color.coverage_circle_green)
        "orange" -> colorResource(R.color.coverage_circle_orange)
        "purple" -> colorResource(R.color.coverage_circle_purple)
        "yellow" -> colorResource(R.color.coverage_circle_yellow)
        "cyan" -> colorResource(R.color.coverage_circle_cyan)
        "white" -> colorResource(R.color.coverage_circle_white)
        else -> colorResource(R.color.serving_cell_dark) // Default blue
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
