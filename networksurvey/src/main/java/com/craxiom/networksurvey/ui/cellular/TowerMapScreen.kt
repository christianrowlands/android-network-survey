package com.craxiom.networksurvey.ui.cellular

import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
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


const val INITIAL_ZOOM: Double = 14.0
const val MIN_ZOOM_LEVEL = 7.0
const val MAX_AREA_SQ_METERS = 400_000_000.0
const val ICON_TOWER = "communications-tower"

private const val MAX_TOWERS_ON_MAP = 5000

/**
 * Creates the map view for displaying the tower locations. The tower locations are pulled from the
 * NS backend.
 */
@Composable
internal fun TowerMapScreen(viewModel: TowerMapViewModel = viewModel()) {

    val isLoadingInProgress by viewModel.isLoadingInProgress.collectAsStateWithLifecycle()
    val isZoomedOutTooFar by viewModel.isZoomedOutTooFar.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        OsmdroidMapView(viewModel)
    }

    if (isZoomedOutTooFar) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp)
        ) {
            Text(text = "Zoom in farther to see the towers", fontWeight = FontWeight.Bold)
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
    //}
}

@Composable
internal fun OsmdroidMapView(viewModel: TowerMapViewModel) {
    var points by remember {
        mutableStateOf<LinkedHashSet<Marker>>(LinkedHashSet())
    }

    val radio by viewModel.selectedRadioType.collectAsStateWithLifecycle()

    // TODO Set the radio via the serving cell info

    val options = listOf("GSM", "CDMA", "UMTS", "LTE", "NR")
    var expanded by remember { mutableStateOf(false) }
    val mapView = MapView(LocalContext.current)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
                mapView.setMultiTouchControls(true)

                // I pulled the idea for setting this from: https://github.com/osmdroid/osmdroid/wiki/Important-notes-on-using-osmdroid-in-your-app#changing-the-loading-tile-grid-colors
                mapView.overlayManager.tilesOverlay.loadingBackgroundColor = android.R.color.black;
                mapView.overlayManager.tilesOverlay.loadingLineColor =
                    context.getColor(R.color.colorPrimary)

                val mapController = mapView.controller
                mapController.setZoom(INITIAL_ZOOM)
                val startPoint = GeoPoint(35.410, -80.854);
                mapController.setCenter(startPoint);

                val mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                mLocationOverlay.enableMyLocation()
                mapView.overlays.add(mLocationOverlay)

                val compassOverlay =
                    CompassOverlay(context, InternalCompassOrientationProvider(context), mapView)
                compassOverlay.enableCompass()
                mapView.overlays.add(compassOverlay)

                // Listener to detect when map movement stops
                mapView.addMapListener(DelayedMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        runListener(mapView, viewModel, points)
                        return true
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        runListener(mapView, viewModel, points)
                        return true
                    }
                }, 400))

                mapView
            },
            update = { mapView ->
                Timber.i("Updating the map view!!!")
                // TODO This is not working, it is being called too often. There must be a better way to trigger an update on changes
                runListener(mapView, viewModel, points)
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            options.forEach { label ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        if (viewModel.selectedRadioType.value != label) {
                            Timber.i("The Selected radio type changed to $label")
                            viewModel.setSelectedRadioType(label)
                            points.clear()
                        }
                        expanded = false
                    })
            }
        }

        // Button to show the DropdownMenu
        Button(
            onClick = { expanded = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(text = radio)
        }
    }

    // TODO I need to figure out a way to call mapView.onPause() and mapView.onResume()
}

/**
 * The listener that is called when the map is idle. This is where we will load the towers for the
 * current map view.
 */
private fun runListener(
    mapView: MapView,
    viewModel: TowerMapViewModel,
    points: LinkedHashSet<Marker>
) {
    Timber.d("Map is idle")

    val bounds = mapView.boundingBox

    if (viewModel.lastQueriedBounds.value != null && viewModel.lastQueriedBounds.value == bounds) {
        Timber.d("The bounds have not changed, so we do not need to load the towers")
        return
    }

    val area = calculateArea(bounds)
    if (mapView.zoomLevelDouble >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
        viewModel.setIsLoadingInProgress(true)
        viewModel.setIsZoomedOutTooFar(false)
        viewModel.setLastQueriedBounds(bounds)
        Timber.d("The zoom level is appropriate to show the towers")

        viewModel.viewModelScope.launch {
            val towerPoints = loadTowers(bounds, viewModel)
            Timber.d("Loaded ${towerPoints.size} towers")

            // TODO Add a text overlay if no towers are found

            towerPoints.forEach {
                val towerMarker = Marker(mapView)
                towerMarker.setPosition(GeoPoint(it.lat, it.lon))
                towerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                // TODO towerMarker.infoWindow = InfoWindow(view, mapView)
                val towerDrawable = getDrawable(mapView.context, R.drawable.ic_cellular)
                towerDrawable!!.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        R.color.colorPrimary, BlendModeCompat.SRC_ATOP
                    )
                towerMarker.icon = towerDrawable
                // TODO Set an onClick listener to display the tower details
                towerMarker.title = getCgiString(it)
                // FIXME Checking !points.contains(towerMarker) is not the right way to do this
                //  because we can have multiple towers at the same location. Override the equals and hashCode to fix this
                if (points.size >= MAX_TOWERS_ON_MAP && !points.contains(towerMarker)) {
                    points.remove(points.first())
                }
                points.add(towerMarker)
            }

            mapView.overlays.clear() // TODO is this the right way to do it?

            Timber.i("Adding %s points to the map", points.size)
            points.forEach { mapView.overlays.add(it) } // FIXME I am not actually clearing the map, which means we can end up with more than MAX_TOWERS_ON_MAP

            mapView.invalidate()

            viewModel.setIsLoadingInProgress(false)
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
private suspend fun loadTowers(
    bounds: BoundingBox,
    viewModel: TowerMapViewModel
): List<Tower> {
    return suspendCancellableCoroutine { continuation ->
        try {
            viewModel.viewModelScope.launch {
                // Format the bounding box coordinates to the required "bbox" string format
                val bbox =
                    "${bounds.latSouth},${bounds.lonWest},${bounds.latNorth},${bounds.lonEast}"
                val response = nsApi.getTowers(bbox, viewModel.selectedRadioType.value)

                // Process the response
                if (response.code() == 204) {
                    // No towers found, return an empty list
                    Timber.w("No towers found; raw: ${response.raw()}") // TODO Delete me
                    continuation.resume(Collections.emptyList(), onCancellation = null)
                    Collections.emptyList<GeoPoint>()
                } else if (response.isSuccessful && response.body() != null) {
                    Timber.i("Successfully loaded towers")
                    val towerData = response.body()!!

                    continuation.resume(towerData.cells, onCancellation = {
                        Timber.e("The tower data fetch was cancelled")
                    })
                } else {
                    Timber.w("Failed to load towers; raw: ${response.raw()}") // TODO Delete me
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

/**
 * Returns a string representation of the Cell Global Identifier (CGI) for the given tower.
 */
private fun getCgiString(tower: Tower): String {
    return "${tower.radio}: ${tower.mcc}/${tower.mnc}/${tower.area}/${tower.cid}"
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
    @SerializedName("radio") val radio: String
)

/**
 * The data class that represents the response from the NS backend when fetching towers.
 */
data class TowerResponse(
    val count: Int,
    val cells: List<Tower>
)