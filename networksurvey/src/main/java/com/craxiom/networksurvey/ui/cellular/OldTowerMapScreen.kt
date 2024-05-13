package com.craxiom.networksurvey.ui.cellular

private const val MAX_TOWERS_ON_MAP = 5000

/**
 * Creates the map view for displaying the tower locations. The tower locations are pulled from the
 * NS backend.
 */
/*@OptIn(MapboxExperimental::class)
@Composable
internal fun OldTowerMapScreen(viewModel: TowerMapViewModel = viewModel()) {
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
}*/