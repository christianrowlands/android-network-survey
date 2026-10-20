package com.craxiom.networksurvey.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.ui.cellular.SurveyedPointFilterChips
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.TowerMapSheets
import com.craxiom.networksurvey.ui.cellular.TowerMapUiState
import com.craxiom.networksurvey.ui.cellular.model.INITIAL_ZOOM
import com.craxiom.networksurvey.ui.cellular.model.MINIMUM_LOCATION_ZOOM
import com.craxiom.networksurvey.ui.cellular.model.TowerMapLibreViewModel
import com.craxiom.networksurvey.ui.cellular.rememberSurveyedPointsSettings
import com.craxiom.networksurvey.ui.cellular.rememberTowerMapUiState
import com.craxiom.networksurvey.ui.cellular.towermap.CameraMode
import com.craxiom.networksurvey.ui.cellular.towermap.SurveyedPointsPill
import com.craxiom.networksurvey.ui.cellular.towermap.rememberCameraPositionState
import com.craxiom.networksurvey.ui.common.ServiceConnectionHandler
import com.craxiom.networksurvey.ui.common.findComponentActivity
import com.craxiom.networksurvey.ui.common.openLocationSettings
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.signals.SignalsViewModel
import com.craxiom.networksurvey.ui.signals.SimLabels
import com.craxiom.networksurvey.ui.theme.NsTheme
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

/** The world view, for a first launch with no saved viewport and no location yet. */
private const val WORLD_ZOOM = 1.0

/**
 * The Map tab: the map, with the serving-cell card, the location banner and the buttons around
 * it. It binds the survey service while it shows and feeds the map through [MapTabViewModel].
 */
@Composable
fun MapTabScreen(sharedViewModel: SharedViewModel) {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val mapTabViewModel: MapTabViewModel = viewModel(viewModelStoreOwner = activity)
    val signalsViewModel: SignalsViewModel = viewModel(viewModelStoreOwner = activity)
    // Lives with the Map tab's back stack entry, so it is kept across tab visits
    val towerViewModel: TowerMapLibreViewModel = viewModel()

    val camera = rememberCameraPositionState { rawPosition = mapTabViewModel.initialCamera().toCameraPosition() }
    val uiState = rememberTowerMapUiState(camera)

    MapTabEffects(mapTabViewModel, signalsViewModel, towerViewModel, sharedViewModel, uiState)

    val servingCells by mapTabViewModel.servingCells.collectAsStateWithLifecycle()
    val selectedSim by mapTabViewModel.selectedSim.collectAsStateWithLifecycle()
    val newTowers by mapTabViewModel.newTowers.collectAsStateWithLifecycle()
    val locationEnabled by mapTabViewModel.locationEnabled.collectAsStateWithLifecycle()
    val servingCellLines by towerViewModel.servingCellLines.collectAsStateWithLifecycle()
    val showTowersLayer by towerViewModel.showTowersLayer.collectAsStateWithLifecycle()
    val showSurveyPoints by towerViewModel.showSurveyPoints.collectAsStateWithLifecycle()

    // Looking up a SIM's label asks the system, so only when the set of SIMs changes
    val simIds = servingCells.keys.sorted()
    val sims = remember(simIds) { simIds.map { SimChoice(it, SimLabels.forSubscription(context, it)) } }
    val card = ServingCellSummary.from(
        info = selectedSim?.let { servingCells[it] },
        distanceMeters = servingCellLines.firstOrNull { it.subscriptionId == selectedSim }?.distanceMeters,
        newTowers = newTowers,
    )?.let { summary ->
        ServingCellCardState(
            summary = summary,
            sims = sims,
        )
    }
    // Read on every recomposition to catch a change made in Map settings
    val showZoom = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(NetworkSurveyConstants.PROPERTY_MAP_SHOW_ZOOM_CONTROLS, false)

    val actions = remember(mapTabViewModel, towerViewModel, uiState) {
        MapTabScreenActions(context, mapTabViewModel, towerViewModel, sharedViewModel, uiState)
    }

    MapTabChrome(
        state = MapChromeState(
            card = card,
            locationOff = !locationEnabled,
            controlsVisible = uiState.surveyedSelection == null,
            searchAvailable = showTowersLayer,
            filtersAvailable = showTowersLayer || showSurveyPoints,
            showZoom = showZoom,
            following = camera.cameraMode == CameraMode.TRACKING,
        ),
        actions = actions,
        surveyPoints = { SurveyPointsChrome(towerViewModel, uiState) },
    ) { contentPadding ->
        TowerMapScreen(towerViewModel, uiState, selectedSim, contentPadding)
    }

    TowerMapSheets(towerViewModel, uiState)
}

/**
 * Binds the service, attaches the map to [mapTabViewModel], keeps the camera and viewport when the
 * map leaves, and passes the latest serving cell to the other screens.
 */
@Composable
private fun MapTabEffects(
    mapTabViewModel: MapTabViewModel,
    signalsViewModel: SignalsViewModel,
    towerViewModel: TowerMapLibreViewModel,
    sharedViewModel: SharedViewModel,
    uiState: TowerMapUiState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    ServiceConnectionHandler(mapTabViewModel::onServiceChanged)

    // The first visit shows the SIM the Cellular view shows; later visits keep the map's own
    LaunchedEffect(mapTabViewModel) { mapTabViewModel.seedSim(signalsViewModel.viewedSim.value) }

    LaunchedEffect(mapTabViewModel) {
        mapTabViewModel.latestServingCell.collect { info -> info?.let(sharedViewModel::updateLatestServingCellInfo) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapTabViewModel.refreshLocationEnabled()
                Lifecycle.Event.ON_PAUSE -> towerViewModel.saveViewport(context)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(towerViewModel) {
        mapTabViewModel.attach(towerViewModel, sharedViewModel.latestServingCellInfo)
        onDispose {
            mapTabViewModel.detach(towerViewModel)
            uiState.camera.position.toMapCamera()?.let(mapTabViewModel::saveCamera)
            towerViewModel.saveViewport(context)
        }
    }
}

/** The survey points pill and its filter chips, while the layer has points to show. */
@Composable
private fun SurveyPointsChrome(viewModel: TowerMapLibreViewModel, uiState: TowerMapUiState) {
    val showSurveyPoints by viewModel.showSurveyPoints.collectAsStateWithLifecycle()
    val count by viewModel.surveyedPoints.count.collectAsStateWithLifecycle(initialValue = 0)
    val data by viewModel.surveyedPoints.data.collectAsStateWithLifecycle()
    val settings = rememberSurveyedPointsSettings(viewModel)
    if (!showSurveyPoints || count == 0) return

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SurveyedPointsPill(
            mode = settings.mode,
            kind = settings.kind,
            onClick = { uiState.showSurveyedOptions = true },
        )
        SurveyedPointFilterChips(
            selection = settings.filters,
            noneInView = data?.features?.features()?.isEmpty() == true,
            onChange = settings.applyFilters,
            modifier = Modifier.padding(top = NsTheme.spacing.xs)
        )
    }
}

/** The Map tab's buttons, acting on the map's view models and its UI state. */
private class MapTabScreenActions(
    private val context: Context,
    private val mapTabViewModel: MapTabViewModel,
    private val towerViewModel: TowerMapLibreViewModel,
    private val sharedViewModel: SharedViewModel,
    private val uiState: TowerMapUiState,
) : MapTabActions {
    override fun onSearch() {
        uiState.showSearch = true
    }

    override fun onFilters() {
        uiState.showFilters = true
    }

    override fun onLayers() {
        uiState.showLayers = true
    }

    override fun onSettings() = sharedViewModel.triggerNavigationToTowerMapSettings()

    override fun onAbout() {
        uiState.showAbout = true
    }

    override fun onZoomIn() = uiState.camera.zoomIn()

    override fun onZoomOut() = uiState.camera.zoomOut()

    override fun onToggleFollow() {
        val camera = uiState.camera
        if (camera.cameraMode == CameraMode.TRACKING) {
            camera.cameraMode = CameraMode.NONE
            return
        }
        towerViewModel.getMyLocation()?.let { location ->
            camera.position = CameraPosition.Builder()
                .target(location)
                .zoom(maxOf(towerViewModel.getCurrentZoom(), MINIMUM_LOCATION_ZOOM))
                .build()
        }
        // Follow after moving there
        camera.cameraMode = CameraMode.TRACKING
    }

    override fun onTurnOnLocation() = openLocationSettings(context)

    override fun onSelectSim(subscriptionId: Int) = mapTabViewModel.selectSim(subscriptionId)
}

/** The camera to open the map at, or the world view when there is nothing to restore. */
private fun MapCamera?.toCameraPosition(): CameraPosition = if (this == null) {
    CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(WORLD_ZOOM).build()
} else {
    CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(zoom).bearing(bearing).build()
}

/** This camera position as a [MapCamera], or null when it was never placed. */
private fun CameraPosition.toMapCamera(): MapCamera? = target?.let { center ->
    MapCamera(center.latitude, center.longitude, zoom.takeIf { it >= 0 } ?: INITIAL_ZOOM, bearing)
}
