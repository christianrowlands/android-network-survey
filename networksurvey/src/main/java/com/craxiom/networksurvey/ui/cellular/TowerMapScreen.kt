package com.craxiom.networksurvey.ui.cellular

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
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
import com.craxiom.networksurvey.ui.cellular.model.CustomLocationOverlay
import com.craxiom.networksurvey.ui.cellular.model.FollowMyLocationChangeListener
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.ServingSignalInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.craxiom.networksurvey.ui.cellular.model.TowerMarker
import com.google.gson.annotations.SerializedName
import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import timber.log.Timber
import java.util.Collections
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


const val INITIAL_ZOOM: Double = 15.0
const val MIN_ZOOM_LEVEL = 13.0
const val MAX_AREA_SQ_METERS = 400_000_000.0

private const val MAX_TOWERS_ON_MAP = 5000

/**
 * Creates the map view for displaying the tower locations. The tower locations are pulled from the
 * NS backend.
 */
@Composable
internal fun TowerMapScreen(viewModel: TowerMapViewModel = viewModel()) {

    val isLoadingInProgress by viewModel.isLoadingInProgress.collectAsStateWithLifecycle()
    val isZoomedOutTooFar by viewModel.isZoomedOutTooFar.collectAsStateWithLifecycle()
    val radio by viewModel.selectedRadioType.collectAsStateWithLifecycle()
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

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {

                // Button to show the DropdownMenu
                Button(
                    onClick = { expanded = true },
                ) {
                    Text(text = radio)
                }

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
                                        runTowerQuery(viewModel)
                                    }
                                }
                                expanded = false
                            })
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
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
                                painter = painterResource(id = R.drawable.ic_my_location),
                                contentDescription = "My Location",
                                modifier = Modifier.size(24.dp)
                            )
                            Button(
                                onClick = { goToMyLocation(viewModel) },
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
                    .padding(16.dp)
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
                Text(text = "Zoom in farther to see the towers", fontWeight = FontWeight.Bold)
            }
        } else if (noTowersFound) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 8.dp)
            ) {
                Text(text = "No towers found in the area", fontWeight = FontWeight.Bold)
            }
        }

        if (isLoadingInProgress) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
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
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val mapView = MapView(context)
            viewModel.mapView = mapView
            viewModel.gpsMyLocationProvider = GpsMyLocationProvider(mapView.context)
            viewModel.towerOverlayGroup = RadiusMarkerClusterer(context)
            viewModel.towerOverlayGroup.setMaxClusteringZoomLevel(14)

            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
            mapView.setMultiTouchControls(true)

            // I pulled the idea for setting this from: https://github.com/osmdroid/osmdroid/wiki/Important-notes-on-using-osmdroid-in-your-app#changing-the-loading-tile-grid-colors
            mapView.overlayManager.tilesOverlay.loadingBackgroundColor = android.R.color.black
            mapView.overlayManager.tilesOverlay.loadingLineColor =
                context.getColor(R.color.colorPrimary)

            val mapController = mapView.controller
            mapController.setZoom(INITIAL_ZOOM)

            addDefaultOverlays(
                context,
                mapView,
                viewModel.gpsMyLocationProvider,
                viewModel,
                followMyLocationChangeListener
            )
            mapView.overlays.add(viewModel.towerOverlayGroup)
            mapView.overlays.add(viewModel.servingCellLinesOverlayGroup)

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

            mapView
        },
        update = {
            viewModel.recreateOverlaysFromTowerData(it)
        }
    )
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
        Text(
            text = "Serving Cell Info",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (cellInfo != null) {
            val servingCell = cellInfo.servingCell ?: return Text(
                "No serving cell found",
                color = MaterialTheme.colorScheme.surface
            )
            val record = servingCell.cellularRecord

            // Display technology and signal strengths based on CellularRecord
            Text(
                "Technology: ${servingCell.cellularProtocol}",
                color = MaterialTheme.colorScheme.surface
            )
            if (servingSignalInfo != null) {
                Text(
                    servingSignalInfo.toString(),
                    color = MaterialTheme.colorScheme.surface
                )
            }

            val servingCellDisplayString = getServingCellDisplayString(record)
            Text(servingCellDisplayString, color = MaterialTheme.colorScheme.surface)
        } else {
            Text("No serving cell info available", color = MaterialTheme.colorScheme.surface)
        }
    }
}

/**
 * Moves the map view to the user's current location.
 */
private fun goToMyLocation(viewModel: TowerMapViewModel) {
    val lastKnownLocation = viewModel.gpsMyLocationProvider.lastKnownLocation
    if (lastKnownLocation == null) {
        Timber.w("The last known location is null")
        return
    }
    viewModel.mapView.controller.animateTo(
        GeoPoint(
            lastKnownLocation.latitude,
            lastKnownLocation.longitude
        )
    )
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
 * Adds the default overlays to the map view such as the my location overlay.
 */
private fun addDefaultOverlays(
    context: Context,
    mapView: MapView,
    gpsMyLocationProvider: GpsMyLocationProvider,
    viewModel: TowerMapViewModel,
    followMyLocationChangeListener: FollowMyLocationChangeListener
) {
    val locationConsumer = IMyLocationConsumer { location, _ ->
        viewModel.myLocation = location
        viewModel.drawServingCellLine()
    }

    viewModel.myLocationOverlay =
        CustomLocationOverlay(
            gpsMyLocationProvider,
            mapView,
            locationConsumer,
            followMyLocationChangeListener
        )
    val locationOverlay: MyLocationNewOverlay = viewModel.myLocationOverlay!!

    val icon = AppCompatResources.getDrawable(context, R.drawable.ic_location_pin)?.toBitmap()
    if (icon != null) {
        locationOverlay.setPersonIcon(icon)
        locationOverlay.setPersonAnchor(0.5f, .8725f)
    }

    val directionIcon =
        AppCompatResources.getDrawable(context, R.drawable.ic_navigation)?.toBitmap()
    if (icon != null) {
        locationOverlay.setDirectionIcon(directionIcon)
        locationOverlay.setDirectionAnchor(0.5f, 0.5f)
    }

    locationOverlay.enableMyLocation()
    mapView.overlays.add(locationOverlay)
}

/**
 * Runs the tower query to get the towers from the back end for the current map view.
 */
private suspend fun runTowerQuery(viewModel: TowerMapViewModel) {
    viewModel.setIsLoadingInProgress(true)

    Timber.i("Running the towerQuery")

    val towerPoints = getTowersFromServer(viewModel)
    Timber.d("Loaded ${towerPoints.size} towers")

    val towers = viewModel.towers.value

    towerPoints.forEach {
        val towerMarker = TowerMarker(viewModel.mapView, it)

        if (towers.size >= MAX_TOWERS_ON_MAP) {
            val towerToRemove = towers.first()
            towers.remove(towerToRemove)
            towerToRemove.destroy()
        }

        if (towers.contains(towerMarker)) {
            towers.remove(towerMarker)
        }

        towers.add(towerMarker)
    }

    viewModel.setNoTowersFound(towers.isEmpty())

    viewModel.setIsLoadingInProgress(false)
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
    viewModel.setMapZoomLevel(mapView.zoomLevelDouble)
    if (mapView.zoomLevelDouble >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
        viewModel.setIsZoomedOutTooFar(false)
        viewModel.setLastQueriedBounds(bounds)
        Timber.d("The zoom level is appropriate to show the towers")

        viewModel.viewModelScope.launch {
            runTowerQuery(viewModel)
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
 * Loads the towers from the NS backend for the given bounding box.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun getTowersFromServer(
    viewModel: TowerMapViewModel
): List<Tower> {
    return suspendCancellableCoroutine { continuation ->
        try {
            viewModel.viewModelScope.launch {
                try {
                    val bounds = viewModel.lastQueriedBounds.value ?: return@launch

                    // Format the bounding box coordinates to the required "bbox" string format
                    val bbox =
                        "${bounds.latSouth},${bounds.lonWest},${bounds.latNorth},${bounds.lonEast}"
                    val response = nsApi.getTowers(bbox, viewModel.selectedRadioType.value)

                    // Process the response
                    if (response.code() == 204) {
                        // No towers found, return an empty list
                        Timber.w("No towers found; raw: ${response.raw()}")
                        continuation.resume(Collections.emptyList(), onCancellation = null)
                        Collections.emptyList<GeoPoint>()
                    } else if (response.isSuccessful && response.body() != null) {
                        Timber.i("Successfully loaded towers")
                        val towerData = response.body()!!

                        continuation.resume(towerData.cells, onCancellation = {
                            Timber.e("The tower data fetch was cancelled")
                        })
                    } else {
                        Timber.w("Failed to load towers; raw: ${response.raw()}")
                        continuation.resume(Collections.emptyList(), onCancellation = null)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch towers")
                    continuation.resume(Collections.emptyList(), onCancellation = null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch towers")
            continuation.resume(Collections.emptyList(), onCancellation = null)
        }
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


// The API definition for the NS Tower Service
interface Api {
    @GET("cells/area")
    suspend fun getTowers(
        @Query("bbox") bbox: String,
        @Query("radio") radio: String
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