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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.messaging.CdmaRecord
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.UmtsRecord
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.Plmn
import com.craxiom.networksurvey.ui.cellular.model.FollowMyLocationChangeListener
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.ServingSignalInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.craxiom.networksurvey.ui.cellular.model.TowerSource
import com.craxiom.networksurvey.util.PreferenceUtils
import com.google.gson.annotations.SerializedName
import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


const val INITIAL_ZOOM: Double = 15.0
const val MIN_ZOOM_LEVEL = 13.0
const val MAX_AREA_SQ_METERS = 400_000_000.0

/**
 * Creates the map view for displaying the tower locations. The tower locations are pulled from the
 * NS backend.
 */
@Composable
internal fun TowerMapScreen(
    viewModel: TowerMapViewModel = viewModel(),
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
    var isFollowing by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPlmnDialog by remember { mutableStateOf(false) }
    var showTowerSourceDialog by remember { mutableStateOf(false) }

    val statusBarHeight = paddingInsets.calculateTopPadding()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            OsmdroidMapView(viewModel, object :
                FollowMyLocationChangeListener {
                override fun onFollowMyLocationChanged(enabled: Boolean) {
                    isFollowing = enabled
                }
            })

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
                                            viewModel.towers.value.clear()
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
                    .padding(vertical = paddingInsets.calculateBottomPadding(), horizontal = 16.dp)
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
                        isFollowing = isFollowing,
                        toggleFollowMe = {
                            if (viewModel.myLocationOverlay == null) return@CircleButtonWithLine

                            val currentIsFollowing =
                                viewModel.myLocationOverlay!!.isFollowLocationEnabled
                            isFollowing = !currentIsFollowing
                            toggleFollowMe(viewModel, isFollowing)
                        })
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(vertical = paddingInsets.calculateBottomPadding(), horizontal = 16.dp)
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
        }

        if (showInfoDialog) {
            TowerMapInfoDialog(onDismiss = { showInfoDialog = false })
        }

        if (showPlmnDialog) {
            PlmnFilterDialog(
                currentPlmn = currentPlmnFilter,
                onSetPlmnFilter = { mcc, mnc ->
                    viewModel.setPlmnFilter(Plmn(mcc, mnc))
                    viewModel.towers.value.clear()
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
                        viewModel.towers.value.clear()
                        viewModel.viewModelScope.launch {
                            viewModel.runTowerQuery()
                        }
                    }
                },
                onDismiss = { showTowerSourceDialog = false }
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

        if (isLoadingInProgress) {
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
internal fun OsmdroidMapView(
    viewModel: TowerMapViewModel,
    followMyLocationChangeListener: FollowMyLocationChangeListener
) {
    val localContext = LocalContext.current
    val mapView = remember {
        val mapView = MapView(localContext)
        viewModel.followMyLocationChangeListener = followMyLocationChangeListener
        viewModel.initMapView(mapView)
        mapView
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            mapView.apply {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.zoomController.display.setMarginPadding(.75f, .5f)
                mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
                mapView.setMultiTouchControls(true)

                // I pulled the idea for setting this from: https://github.com/osmdroid/osmdroid/wiki/Important-notes-on-using-osmdroid-in-your-app#changing-the-loading-tile-grid-colors
                mapView.overlayManager.tilesOverlay.loadingBackgroundColor = android.R.color.black
                mapView.overlayManager.tilesOverlay.loadingLineColor =
                    context.getColor(R.color.colorPrimary)

                val mapController = mapView.controller
                mapController.setZoom(INITIAL_ZOOM)

                // Listener to detect when map movement stops
                mapView.addMapListener(DelayedMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        runListener(mapView, viewModel)
                        return true
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        runListener(mapView, viewModel)
                        return true
                    }
                }, 400))
            }
        },
        update = {
            viewModel.recreateOverlaysFromTowerData(it)
        }
    )
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

/**
 * Toggles the option to continuously move the map view to the user's current location.
 */
private fun toggleFollowMe(viewModel: TowerMapViewModel, newIsFollowing: Boolean) {
    if (viewModel.myLocationOverlay == null) return

    if (newIsFollowing) {
        viewModel.myLocationOverlay?.enableFollowLocation()
    } else {
        viewModel.myLocationOverlay?.disableFollowLocation()
    }
}

/**
 * The listener that is called when the map is idle. This is where we will load the towers for the
 * current map view.
 */
private fun runListener(
    mapView: MapView,
    viewModel: TowerMapViewModel
) {
    Timber.d("Map is idle")

    val bounds = mapView.boundingBox

    if (viewModel.lastQueriedBounds.value != null && viewModel.lastQueriedBounds.value == bounds) {
        Timber.d("The bounds have not changed, so we do not need to load the towers")
        return
    }

    val area = calculateArea(bounds)
    if (mapView.zoomLevelDouble >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
        viewModel.setIsZoomedOutTooFar(false)
        viewModel.setLastQueriedBounds(bounds)
        Timber.d("The zoom level is appropriate to show the towers")

        viewModel.viewModelScope.launch {
            viewModel.runTowerQuery()
        }
    } else {
        viewModel.setIsLoadingInProgress(false)
        viewModel.setIsZoomedOutTooFar(true)
        Timber.d(
            "The zoom level is too high or the area is too large to show the towers %s",
            area.toBigDecimal().toPlainString()
        )
    }
}

/**
 * Calculates the area of the bounding box in square meters.
 */
private fun calculateArea(bounds: BoundingBox): Double {
    val earthRadius = 6371000.0 // meters

    val latDistance = Math.toRadians(bounds.latNorth - bounds.latSouth)
    val lngDistance = Math.toRadians(bounds.lonEast - bounds.lonWest)

    val a = sin(latDistance / 2) * sin(latDistance / 2) +
            cos(Math.toRadians(bounds.latSouth)) * cos(Math.toRadians(bounds.latNorth)) *
            sin(lngDistance / 2) * Math.sin(lngDistance / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    val width = earthRadius * c
    val height = width // Approximation as we're not accounting for changes in radius with latitude

    return width * height // area in square meters
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

// The API definition for the NS Tower Service
interface Api {
    @GET("cells/area")
    suspend fun getTowers(
        @Query("bbox") bbox: String,
        @Query("radio") radio: String,
        @Query("source") source: String
    ): Response<TowerResponse>

    @GET("cells/area")
    suspend fun getTowers(
        @Query("bbox") bbox: String,
        @Query("radio") radio: String,
        @Query("mcc") mcc: Int,
        @Query("mnc") mnc: Int,
        @Query("source") source: String
    ): Response<TowerResponse>
}

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("x-api-key", BuildConfig.NS_API_KEY)
            .build()
        chain.proceed(newRequest)
    }
    .build()

val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://network-survey-gateway-2z7o328z.uc.gateway.dev/")
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val nsApi: Api = retrofit.create(Api::class.java)

/**
 * The data class that represents a tower from the NS backend. Needs to stay in sync with the API.
 */
data class Tower(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("mcc") val mcc: Int,
    @SerializedName("mnc") val mnc: Int,
    @SerializedName("area") val area: Int,
    @SerializedName("cid") val cid: Long,
    @SerializedName("unit") val unit: Int,
    @SerializedName("average_signal") val averageSignal: Int,
    @SerializedName("range") val range: Int,
    @SerializedName("samples") val samples: Int,
    @SerializedName("changeable") val changeable: Int,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("radio") val radio: String,
    @SerializedName("source") val source: String
)

/**
 * The data class that represents the response from the NS backend when fetching towers.
 */
data class TowerResponse(
    val count: Int,
    val cells: List<Tower>
)
