package com.craxiom.networksurvey.ui.cellular.model

import com.craxiom.networksurvey.logging.db.dao.SurveyedPointDao
import com.craxiom.networksurvey.logging.db.dao.SurveyedPointFilter
import com.craxiom.networksurvey.logging.db.dao.SurveyedPointQueries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import timber.log.Timber

/**
 * The features the survey points layer should draw, plus what the legend needs: the PLMNs in
 * view (for the provider style expression) and the top identities in view for the data-driven
 * legends. [coarse] is true when the points were grouped into grid cells for a zoomed-out view.
 */
data class SurveyedPointsData(
    val features: FeatureCollection,
    val coarse: Boolean,
    val plmns: Set<String>,
    val legend: List<SurveyedPointLegendKey>,
    val legendMore: Int,
)

/**
 * Feeds the "My survey points" layer. Re-queries the surveyed point table whenever the layer
 * is enabled and the viewport, the table, or the display options change, so the map stays live
 * while the user stands still and flips points the moment an upload completes.
 *
 * Zoomed in, every point in view is returned with every mode's property, so a mode switch is a
 * style change only. Zoomed out, points are grouped into a lattice whose spacing is a fixed
 * number of screen pixels; the grouping depends on the mode, so a mode switch requeries.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SurveyedPointsController(private val scope: CoroutineScope) {

    private data class Viewport(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
        val widthPx: Int
    )

    /** Everything that narrows the query. Grouped so the flow combine stays within arity. */
    private data class FilterSettings(
        val kind: SurveyedPointKind,
        val time: SurveyedPointTimeFilter,
        val source: SurveyedPointSourceFilter,
        val upload: SurveyedPointUploadFilter,
        val missionId: String?,
    )

    private data class Settings(
        val mode: SurveyedPointColorMode,
        val filters: FilterSettings,
    )

    private class Request(val dao: SurveyedPointDao, val viewport: Viewport, val settings: Settings)

    private val daoFlow = MutableStateFlow<SurveyedPointDao?>(null)
    private val enabled = MutableStateFlow(false)
    private val viewport = MutableStateFlow<Viewport?>(null)

    private val _colorMode = MutableStateFlow(SurveyedPointColorMode.SIGNAL)
    val colorMode: StateFlow<SurveyedPointColorMode> = _colorMode.asStateFlow()

    private val _kind = MutableStateFlow(SurveyedPointKind.CELLULAR)
    val kind: StateFlow<SurveyedPointKind> = _kind.asStateFlow()

    private val _timeFilter = MutableStateFlow(SurveyedPointTimeFilter.ANY)
    val timeFilter: StateFlow<SurveyedPointTimeFilter> = _timeFilter.asStateFlow()

    private val _sourceFilter = MutableStateFlow(SurveyedPointSourceFilter.BOTH)
    val sourceFilter: StateFlow<SurveyedPointSourceFilter> = _sourceFilter.asStateFlow()

    private val _uploadFilter = MutableStateFlow(SurveyedPointUploadFilter.ANY)
    val uploadFilter: StateFlow<SurveyedPointUploadFilter> = _uploadFilter.asStateFlow()

    private val _data = MutableStateFlow<SurveyedPointsData?>(null)
    val data = _data.asStateFlow()

    /** Live row count for the layer summary; zero until a DAO is attached. */
    val count: Flow<Int> = daoFlow.flatMapLatest { dao -> dao?.observeCount() ?: flowOf(0) }

    /** Live row count per kind, so the kind toggle only offers kinds that have something to show. */
    val countByKind: Flow<Map<Int, Int>> = daoFlow.flatMapLatest { dao ->
        dao?.observeCountByKind()?.map { counts -> counts.associate { it.kind to it.count } }
            ?: flowOf(emptyMap())
    }

    /** The pipelines with rows, so the source filter only appears when both exist. */
    val sources: Flow<List<Int>> =
        daoFlow.flatMapLatest { dao -> dao?.observeSources() ?: flowOf(emptyList()) }

    /**
     * The mission behind "Latest NS Analytics survey", or null when no NS Analytics survey has
     * written rows.
     */
    val latestMission: StateFlow<String?> = daoFlow
        .flatMapLatest { dao -> dao?.observeLatestNsAnalyticsMissionId() ?: flowOf(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * When the mission behind "Latest NS Analytics survey" first wrote a point, for the filter's
     * subtext. Derived from [latestMission] rather than from the table so the lookup runs once per
     * survey instead of on every insert; it must stay declared after [latestMission].
     */
    val latestMissionStart: StateFlow<Long?> = combine(daoFlow, latestMission) { dao, id -> dao to id }
        .mapLatest { (dao, id) ->
            if (dao == null || id == null) null
            else withContext(Dispatchers.IO) { runCatching { dao.missionStartTime(id) }.getOrNull() }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private var queryJob: Job? = null

    /** True while a derived default kind is pending and no explicit choice has been made. */
    @Volatile
    private var defaultKindPending = false

    init {
        val filters = combine(
            _kind,
            _timeFilter,
            _sourceFilter,
            _uploadFilter,
            latestMission
        ) { kind, time, source, upload, mission ->
            FilterSettings(kind, time, source, upload, mission)
        }
        val settings = combine(_colorMode, filters) { mode, f -> Settings(mode, f) }
        scope.launch {
            combine(daoFlow, enabled, viewport, settings) { dao, on, vp, st ->
                if (dao != null && on && vp != null) Request(dao, vp, st) else null
            }
                .flatMapLatest { request ->
                    // Room re-emits on every write to the table, including upload marking.
                    if (request == null) flowOf(null) else request.dao.observeMaxId()
                        .map { request }
                }
                .debounce(DEBOUNCE_MS)
                .collect { request ->
                    if (request == null) {
                        queryJob?.cancel()
                        _data.value = null
                    } else {
                        runQuery(request)
                    }
                }
        }
    }

    /** Attaches the table; safe to call more than once. */
    fun attach(dao: SurveyedPointDao) {
        daoFlow.value = dao
    }

    fun setEnabled(on: Boolean) {
        enabled.value = on
    }

    /** Called on camera idle with the visible bounds and the map width in pixels. */
    fun setViewport(bounds: LatLngBounds, widthPx: Int) {
        viewport.value = Viewport(
            bounds.latitudeSouth,
            bounds.longitudeWest,
            bounds.latitudeNorth,
            bounds.longitudeEast,
            widthPx
        )
    }

    /** Sets the hue meaning; a cellular-only mode pulls the kind to cellular. */
    fun setColorMode(mode: SurveyedPointColorMode) {
        _colorMode.value = mode
        if (mode.cellularOnly) _kind.value = SurveyedPointKind.CELLULAR
    }

    /** Sets the kind drawn; leaving cellular drops a cellular-only mode back to signal strength. */
    fun setKind(kind: SurveyedPointKind) {
        defaultKindPending = false
        _kind.value = kind
        if (kind != SurveyedPointKind.CELLULAR && _colorMode.value.cellularOnly) _colorMode.value =
            SurveyedPointColorMode.SIGNAL
    }

    fun setTimeFilter(filter: SurveyedPointTimeFilter) {
        _timeFilter.value = filter
    }

    fun setSourceFilter(filter: SurveyedPointSourceFilter) {
        _sourceFilter.value = filter
    }

    fun setUploadFilter(filter: SurveyedPointUploadFilter) {
        _uploadFilter.value = filter
    }

    /**
     * Picks the kind with rows in the last hour (cellular first) when the user has never chosen
     * one, so a Wi-Fi-only walk shows its points without a trip to the options sheet.
     */
    fun applyDefaultKind() {
        val dao = daoFlow.value ?: return
        defaultKindPending = true
        scope.launch(Dispatchers.IO) {
            val recent = try {
                dao.kindsSince(System.currentTimeMillis() - SurveyedPointTimeFilter.LAST_HOUR.windowMs)
            } catch (e: Exception) {
                return@launch
            }
            // A choice made while the query ran wins over the derived default
            if (!defaultKindPending) return@launch
            SurveyedPointKind.entries.firstOrNull { it.mask in recent }
                ?.let { if (it != _kind.value) setKind(it) }
            defaultKindPending = false
        }
    }

    /** Loads the rows behind a tap, applying the current filters to a coarse cell. */
    suspend fun resolve(tap: SurveyedPointTap): SurveyedPointSelection? =
        withContext(Dispatchers.IO) {
            val dao = daoFlow.value ?: return@withContext null
            try {
                when (tap) {
                    is SurveyedPointTap.Points -> SurveyedPointSelection(dao.byIds(tap.ids), null)
                    is SurveyedPointTap.Cell -> {
                        val f = currentFilter()
                        val south = tap.latKey * tap.step - 90.0
                        val west = tap.lonKey * tap.step - 180.0
                        val points = dao.inBoundsRecent(
                            south, west, south + tap.step, west + tap.step,
                            f.kinds, f.since, f.missionId, f.sources, f.uploadState,
                            AGGREGATE_LIMIT
                        )
                        SurveyedPointSelection(points, tap.count)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load the tapped survey points")
                null
            }
        }

    private fun currentFilter(): SurveyedPointFilter = filterFor(
        FilterSettings(
            _kind.value,
            _timeFilter.value,
            _sourceFilter.value,
            _uploadFilter.value,
            latestMission.value
        )
    )

    private fun filterFor(settings: FilterSettings): SurveyedPointFilter {
        val since = when (settings.time) {
            SurveyedPointTimeFilter.ANY, SurveyedPointTimeFilter.LATEST_SURVEY -> 0L
            else -> System.currentTimeMillis() - settings.time.windowMs
        }
        val mission =
            if (settings.time == SurveyedPointTimeFilter.LATEST_SURVEY) settings.missionId else null
        return SurveyedPointFilter(
            kinds = settings.kind.mask,
            since = since,
            missionId = mission,
            sources = settings.source.mask,
            uploadState = settings.upload.state
        )
    }

    private fun runQuery(request: Request) {
        queryJob?.cancel()
        queryJob = scope.launch(Dispatchers.IO) {
            val vp = request.viewport
            val settings = request.settings
            val filter = filterFor(settings.filters)
            val degreesPerPx = longitudeSpan(vp.west, vp.east) / vp.widthPx.coerceAtLeast(1)
            val step = degreesPerPx * TARGET_SPACING_PX
            val coarse = step > INDIVIDUAL_STEP_DEGREES
            val now = System.currentTimeMillis()
            val legend = SurveyedPointLegendBuilder(settings.mode)
            val category = settings.mode.category

            val features: List<Feature> = try {
                // A view across the antimeridian becomes two longitude ranges
                longitudeRanges(vp.west, vp.east).flatMap { (west, east) ->
                    when {
                        !coarse -> request.dao
                            .inBoundsWhere(
                                vp.south,
                                west,
                                vp.north,
                                east,
                                filter.kinds,
                                filter.since,
                                filter.missionId,
                                filter.sources,
                                filter.uploadState
                            )
                            .map { point ->
                                legend.add(point); SurveyedPointFeatures.fromPoint(
                                point,
                                now
                            )
                            }

                        category != null -> request.dao
                            .coarseDominant(
                                SurveyedPointQueries.dominant(
                                    category,
                                    step,
                                    vp.south,
                                    west,
                                    vp.north,
                                    east,
                                    filter
                                )
                            )
                            .map { cell ->
                                legend.add(
                                    cell.category,
                                    cell.count
                                ); SurveyedPointFeatures.fromCategoryCell(cell, category, step, now)
                            }

                        else -> request.dao
                            .coarse(
                                step,
                                vp.south,
                                west,
                                vp.north,
                                east,
                                filter.kinds,
                                filter.since,
                                filter.missionId,
                                filter.sources,
                                filter.uploadState
                            )
                            .map { cell -> SurveyedPointFeatures.fromCell(cell, step, now) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load survey points")
                return@launch
            }

            ensureActive()
            _data.value = SurveyedPointsData(
                features = FeatureCollection.fromFeatures(features),
                coarse = coarse,
                plmns = legend.plmns,
                legend = legend.top(LEGEND_LIMIT),
                legendMore = (legend.size - LEGEND_LIMIT).coerceAtLeast(0),
            )
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 500L
        private const val AGGREGATE_LIMIT = 200
        private const val LEGEND_LIMIT = 8

        /**
         * The longitude ranges to query for a viewport. MapLibre reports a view across the
         * antimeridian either with a longitude past 180 (or below -180) or as an inverted box,
         * and a single BETWEEN cannot express that, so such a view is split at the dateline.
         */
        fun longitudeRanges(west: Double, east: Double): List<Pair<Double, Double>> {
            if (longitudeSpan(west, east) >= 360.0) return listOf(-180.0 to 180.0)
            val w = wrap(west)
            val e = wrap(east)
            return if (w <= e) listOf(w to e) else listOf(w to 180.0, -180.0 to e)
        }

        /** Width of the viewport in degrees, correct across the antimeridian and capped at 360. */
        fun longitudeSpan(west: Double, east: Double): Double {
            val span = east - west
            return when {
                span >= 360.0 -> 360.0
                span < 0.0 -> span + 360.0
                else -> span
            }
        }

        private fun wrap(longitude: Double): Double {
            var value = longitude
            while (value > 180.0) value -= 360.0
            while (value < -180.0) value += 360.0
            return value
        }

        /** Screen distance between grouped marks when zoomed out. */
        const val TARGET_SPACING_PX = 12

        /**
         * Below this lattice spacing (about 25 m) individual points are drawn instead of cells,
         * which happens from roughly zoom 17 upward.
         */
        private const val INDIVIDUAL_STEP_DEGREES = 25.0 / 111_320.0
    }
}
