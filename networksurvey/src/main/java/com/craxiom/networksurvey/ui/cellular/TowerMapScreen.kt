package com.craxiom.networksurvey.ui.cellular

import android.content.Context
import android.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craxiom.networksurvey.BuildConfig
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.google.gson.annotations.SerializedName
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotationGroup
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.toCameraOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Collections


const val ZOOM: Double = 9.0
const val MIN_ZOOM_LEVEL = 7.0
const val MAX_AREA_SQ_METERS = 400_000_000.0
const val ICON_TOWER = "communications-tower"

private const val MAX_TOWERS_ON_MAP = 5000

/**
 * Creates the map view for displaying the tower locations. The tower locations are pulled from the
 * NS backend.
 */
@OptIn(MapboxExperimental::class)
@Composable
internal fun TowerMapScreen(viewModel: TowerMapViewModel = viewModel()) {
    MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN

    var isLoadingInProgress by remember {
        mutableStateOf(true)
    }
    var isZoomedOutTooFar by remember {
        mutableStateOf(false)
    }
    var points by remember {
        mutableStateOf<LinkedHashSet<Point>>(LinkedHashSet())
    }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(-80.854, 35.410))
            zoom(ZOOM)
        }
    }
    val context = LocalContext.current
    var towerMapView: MapView? = null
    var lastQueriedBounds by remember { mutableStateOf<CoordinateBounds?>(null) }
    var lastJob by remember { mutableStateOf<Job?>(null) }

    // ExampleScaffold { TODO Look into the ExampleScaffold from the example app
    MapboxMap(
        Modifier.fillMaxSize(),
        mapViewportState = mapViewportState,
        style = {
            MapStyle(style = Style.LIGHT)
            //MapStyle(style = Style.SATELLITE_STREETS)
        }
    ) {
        MapEffect(Unit) { mapView ->
            towerMapView = mapView
            mapView.mapboxMap.subscribeMapIdle { cameraPosition ->
                Timber.d("Map is idle at: $cameraPosition")
                val bounds =
                    towerMapView!!.mapboxMap.coordinateBoundsForCamera(towerMapView!!.mapboxMap.cameraState.toCameraOptions())

                if (lastQueriedBounds != null && lastQueriedBounds!! == bounds) {
                    Timber.d("The bounds have not changed, so we do not need to load the towers")
                    return@subscribeMapIdle
                }

                val area = calculateArea(bounds)
                if (mapView.mapboxMap.cameraState.zoom >= MIN_ZOOM_LEVEL && area <= MAX_AREA_SQ_METERS) {
                    isLoadingInProgress = true
                    isZoomedOutTooFar = false
                    lastQueriedBounds = bounds
                    Timber.d("The zoom level is appropriate to show the towers")

                    lastJob?.cancel() // Cancel the previous job if it is still running
                    lastJob = viewModel.viewModelScope.launch {
                        val towerPoints = loadTowers(bounds, viewModel)
                        Timber.d("Loaded ${towerPoints.size} towers")

                        // TODO Add a text overlay if no towers are found

                        towerPoints.forEach {
                            val newPoint: Point = it
                            // FIXME Checking !points.contains(newPoint) is not the right way to do this
                            //  because we can have multiple towers at the same location
                            if (points.size >= MAX_TOWERS_ON_MAP && !points.contains(newPoint)) {
                                points.remove(points.first())
                            }
                            points.add(newPoint)
                        }

                        isLoadingInProgress = false
                    }
                } else {
                    isLoadingInProgress = false
                    isZoomedOutTooFar = true
                    Timber.d(
                        "The zoom level is too high or the area is too large to show the towers %s",
                        area.toBigDecimal().toPlainString()
                    )
                }
            }
        }

        PointAnnotationGroup(
            annotations = points.map {
                PointAnnotationOptions()
                    .withPoint(it)
                    .withIconImage(ICON_TOWER)
                    .withIconSize(1.5)
            },
            annotationConfig = AnnotationConfig(
                annotationSourceOptions = AnnotationSourceOptions(
                    clusterOptions = ClusterOptions(
                        textColorExpression = Expression.color(Color.YELLOW),
                        textColor = Color.BLACK, // Will not be applied as textColorExpression has been set
                        textSize = 20.0,
                        circleRadiusExpression = literal(25.0),
                        colorLevels = listOf(
                            Pair(100, Color.RED),
                            Pair(50, Color.BLUE),
                            Pair(0, Color.GREEN) // TODO Update these clustering options
                        )
                    )
                )
            ),
            onClick = {
                Toast.makeText(
                    context,
                    "Clicked on Point Annotation Cluster: $it",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        )
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

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun loadTowers(
    bounds: CoordinateBounds,
    viewModel: TowerMapViewModel
): List<Point> {
    return suspendCancellableCoroutine { continuation ->
        try {
            viewModel.viewModelScope.launch {
                // Format the bounding box coordinates to the required "bbox" string format
                val bbox =
                    "${bounds.southwest.latitude()},${bounds.southwest.longitude()},${bounds.northeast.latitude()},${bounds.northeast.longitude()}"
                val response = nsApi.getTowers(bbox)

                // Process the response
                if (response.code() == 204) {
                    // No towers found, return an empty list
                    Timber.w("No towers found; raw: ${response.raw()}") // TODO Delete me
                    continuation.resume(Collections.emptyList(), onCancellation = null)
                    Collections.emptyList<Point>()
                } else if (response.isSuccessful && response.body() != null) {
                    Timber.i("Successfully loaded towers")
                    val towerData = response.body()!!
                    val mappedPoints = towerData.cells.map {
                        //Timber.i("Tower lat: ${it.lat}, lon: ${it.lon}, mcc: ${it.mcc}, mnc: ${it.mnc}, area: ${it.area}, cid: ${it.cid}, unit: ${it.unit}, averageSignal: ${it.averageSignal}, range: ${it.range}, samples: ${it.samples}, changeable: ${it.changeable}, createdAt: ${it.createdAt}, updatedAt: ${it.updatedAt}, radio: ${it.radio}")
                        Point.fromLngLat(it.lon, it.lat)
                    }

                    continuation.resume(mappedPoints, onCancellation = {
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
 * Load the string content from net
 *
 * @param url the url of the file to load
 */
fun loadStringFromNet(context: Context, url: String): String? {
    val client = OkHttpClient.Builder()
        .cache(Cache(File(context.externalCacheDir.toString(), "cache"), 10 * 1024 * 1024L))
        .build()
    val request: Request = Request.Builder()
        .url(url)
        .build()

    return try {
        val response = client.newCall(request).execute()
        val inputStream = BufferedInputStream(response.body?.byteStream())
        val rd = BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8")))
        val sb = StringBuilder()
        rd.forEachLine {
            sb.append(it)
        }
        sb.toString()
    } catch (e: IOException) {
        Timber.e("Unable to download $url")
        null
    }
}

private fun calculateArea(bounds: CoordinateBounds): Double {
    val earthRadius = 6371000.0 // meters

    val latDistance = Math.toRadians(bounds.northeast.latitude() - bounds.southwest.latitude())
    val lngDistance = Math.toRadians(bounds.northeast.longitude() - bounds.southwest.longitude())

    val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
            Math.cos(Math.toRadians(bounds.southwest.latitude())) * Math.cos(Math.toRadians(bounds.northeast.latitude())) *
            Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    val width = earthRadius * c
    val height = width // Approximation as we're not accounting for changes in radius with latitude

    return width * height // area in square meters
}

// The API definition for the NS Tower Service
interface Api {
    @GET("cells/area")
    suspend fun getTowers(@Query("bbox") bbox: String): Response<TowerResponse>
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

data class TowerResponse(
    val count: Int,
    val cells: List<Tower>
)