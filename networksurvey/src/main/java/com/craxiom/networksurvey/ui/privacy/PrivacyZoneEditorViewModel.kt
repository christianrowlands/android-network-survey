package com.craxiom.networksurvey.ui.privacy

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.craxiom.networksurvey.logging.db.CommunityQueueScrubber
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneSource
import com.craxiom.networksurvey.privacy.PrivacyZoneStore
import com.craxiom.networksurvey.ui.common.MapPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * The zone editor's form.
 *
 * @property isNew        True when the editor adds a zone rather than changing a saved one.
 * @property name         What the user typed; a blank name is saved as the default name.
 * @property center       The map's center, where the zone goes; null until the map has a place.
 * @property radiusMeters The radius, from [PrivacyZone.MIN_RADIUS_METERS] to [PrivacyZone.MAX_RADIUS_METERS].
 * @property centeredOnFix True once the center came from the phone's location (or the user moved
 *                        the map), so a late first fix no longer moves the map.
 */
data class PrivacyZoneEditorForm(
    val isNew: Boolean,
    val name: String = "",
    val center: MapPoint? = null,
    val radiusMeters: Int = PrivacyZone.DEFAULT_RADIUS_METERS,
    val centeredOnFix: Boolean = false,
)

/** How the editor ended, so the page can close. */
enum class PrivacyZoneEditorResult { SAVED, DELETED, FAILED }

/**
 * The full-screen zone editor: a new zone (no [ZONE_ID_ARG]) or a saved one. The map's center is
 * the zone's center, so panning the map moves the zone; the radius comes from a slider. While the
 * circle changes, the number of community records it would remove from the upload queue is counted
 * again ([WaitingUploadCounter]), a short moment after the user stops moving it. The unsaved form
 * is kept in the [SavedStateHandle], so it survives the process being killed in the background.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PrivacyZoneEditorViewModel internal constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val store: PrivacyZoneStore,
    private val counter: WaitingUploadCounter,
    lastKnownLocation: () -> MapPoint?,
    ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application,
        savedStateHandle,
        PrivacyZoneStore.getInstance(application),
        WaitingUploadCounter { latitude, longitude, radiusMeters ->
            CommunityQueueScrubber.countQueuedInside(
                application,
                PrivacyZone(id = DRAFT_ZONE_ID, name = "", latitude = latitude, longitude = longitude, radiusMeters = radiusMeters),
            )
        },
        { lastKnownLocation(application) },
        Dispatchers.IO,
    )

    /** The zone being changed, or null for a new zone. */
    val zoneId: String? = savedStateHandle.get<String>(ZONE_ID_ARG)?.takeIf { it.isNotBlank() }

    private val existing: PrivacyZone? = zoneId?.let(store::zone)

    private val _form = MutableStateFlow(restoredForm() ?: initialForm(lastKnownLocation))

    /** The form as the user has it now. */
    val form: StateFlow<PrivacyZoneEditorForm> = _form.asStateFlow()

    /** The saved zones other than this one, drawn on the editor's map for context. */
    val otherZones: StateFlow<List<PrivacyZone>> = store.zones
        .map { zones -> zones.filterNot { it.id == zoneId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.zones.value.filterNot { it.id == zoneId })

    private val _result = MutableStateFlow<PrivacyZoneEditorResult?>(null)

    /** Set once the zone is saved or deleted (the page then closes), or when saving failed. */
    val result: StateFlow<PrivacyZoneEditorResult?> = _result.asStateFlow()

    /**
     * The map's zoom when it last moved, kept so a rotation (or a restored process) reopens the map
     * where it was; null until the map has been framed around the circle.
     */
    var cameraZoom: Double?
        get() = savedStateHandle.get<Double>(KEY_ZOOM)
        set(value) {
            savedStateHandle[KEY_ZOOM] = value
        }

    /** The name the zone was saved with (for the delete confirmation), or null for a new zone. */
    val savedName: String? = existing?.name

    /** How many records waiting to upload to the community databases this circle would remove. */
    val waitingCount: StateFlow<Int> = _form
        .map { form -> form.center?.let { WaitingCircle(it, form.radiusMeters) } }
        .distinctUntilChanged()
        .debounce(COUNT_DEBOUNCE_MS)
        .mapLatest { circle ->
            if (circle == null) return@mapLatest 0
            try {
                counter.countInside(circle.center.latitude, circle.center.longitude, circle.radiusMeters)
            } catch (e: Exception) {
                Timber.w(e, "Could not count the community records inside the zone")
                0
            }
        }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** True when the zone being edited no longer exists (deleted elsewhere), or a new one cannot be added. */
    val unavailable: Boolean = (zoneId != null && existing == null) || (zoneId == null && !store.canAdd())

    fun setName(name: String) = edit { it.copy(name = name.take(MAX_NAME_LENGTH)) }

    fun setRadius(radiusMeters: Int) = edit { it.copy(radiusMeters = PrivacyZone.clampRadius(radiusMeters)) }

    /** The map moved: its center is the zone's center. [byUser] is true for a pan or zoom gesture. */
    fun onCenterChanged(center: MapPoint, byUser: Boolean) = edit {
        it.copy(center = center, centeredOnFix = it.centeredOnFix || byUser)
    }

    /**
     * Seeds the center when the editor opened with none (no location and a new zone): the place the
     * Map tab last showed.
     *
     * @return True if the form now has a center.
     */
    fun seedCenter(center: MapPoint?): Boolean {
        if (center != null) edit { if (it.center == null) it.copy(center = center) else it }
        return _form.value.center != null
    }

    /**
     * @return The place to move the map to for the phone's first fix: only for a new zone that has
     * not been centered on a fix or moved by the user yet. Marks the form centered.
     */
    fun takeFirstFix(location: MapPoint): MapPoint? {
        val current = _form.value
        if (!current.isNew || current.centeredOnFix) return null
        edit { current.copy(center = location, centeredOnFix = true) }
        return location
    }

    /** Saves the zone at the map's center; a blank name is saved as [defaultName]. */
    fun save(defaultName: String) {
        val current = _form.value
        val center = current.center ?: run {
            _result.value = PrivacyZoneEditorResult.FAILED
            return
        }
        val zone = PrivacyZone(
            id = zoneId ?: PrivacyZoneStore.newId(),
            name = current.name.trim().ifEmpty { defaultName },
            latitude = center.latitude,
            longitude = center.longitude,
            radiusMeters = current.radiusMeters,
            source = existing?.source ?: PrivacyZoneSource.USER,
        )
        _result.value = if (store.save(zone) != null) PrivacyZoneEditorResult.SAVED else PrivacyZoneEditorResult.FAILED
    }

    /** Deletes the zone being edited. */
    fun delete() {
        zoneId?.let(store::remove)
        _result.value = PrivacyZoneEditorResult.DELETED
    }

    /** Clears a failed save, once the page has said so. */
    fun consumeFailure() {
        if (_result.value == PrivacyZoneEditorResult.FAILED) _result.value = null
    }

    /**
     * @param nameFor The default name for a number ("Privacy zone 3").
     * @return The name a zone saved without one gets: the next number after the zones saved so far,
     * moved past any number another zone already carries, so a delete never leads to the same
     * default name twice.
     */
    fun defaultName(nameFor: (Int) -> String): String {
        val others = store.zones.value.filterNot { it.id == zoneId }
        val taken = others.mapTo(mutableSetOf()) { it.name }
        var number = others.size + 1
        while (nameFor(number) in taken) number++
        return nameFor(number)
    }

    /** Changes the form and keeps the change in the saved state. */
    private inline fun edit(change: (PrivacyZoneEditorForm) -> PrivacyZoneEditorForm) {
        _form.update(change)
        val form = _form.value
        savedStateHandle[KEY_NAME] = form.name
        savedStateHandle[KEY_LATITUDE] = form.center?.latitude
        savedStateHandle[KEY_LONGITUDE] = form.center?.longitude
        savedStateHandle[KEY_RADIUS] = form.radiusMeters
        savedStateHandle[KEY_CENTERED] = form.centeredOnFix
    }

    /** @return The unsaved form kept before the process was killed, or null when there is none. */
    private fun restoredForm(): PrivacyZoneEditorForm? {
        val radius = savedStateHandle.get<Int>(KEY_RADIUS) ?: return null
        val latitude = savedStateHandle.get<Double>(KEY_LATITUDE)
        val longitude = savedStateHandle.get<Double>(KEY_LONGITUDE)
        return PrivacyZoneEditorForm(
            isNew = zoneId == null,
            name = savedStateHandle.get<String>(KEY_NAME).orEmpty(),
            center = if (latitude != null && longitude != null) MapPoint(latitude, longitude) else null,
            radiusMeters = PrivacyZone.clampRadius(radius),
            centeredOnFix = savedStateHandle.get<Boolean>(KEY_CENTERED) ?: false,
        )
    }

    private fun initialForm(lastKnownLocation: () -> MapPoint?): PrivacyZoneEditorForm {
        existing?.let { zone ->
            return PrivacyZoneEditorForm(
                isNew = false,
                name = zone.name,
                center = MapPoint(zone.latitude, zone.longitude),
                radiusMeters = zone.radiusMeters,
                centeredOnFix = true,
            )
        }
        val fix = lastKnownLocation()
        return PrivacyZoneEditorForm(isNew = true, center = fix, centeredOnFix = fix != null)
    }

    /** The circle the waiting count is for. */
    private data class WaitingCircle(val center: MapPoint, val radiusMeters: Int)

    companion object {
        /** The navigation argument that names the zone to edit; absent for a new zone. */
        const val ZONE_ID_ARG = "zoneId"

        /** The id of the circle being drawn, which is not a saved zone. */
        private const val DRAFT_ZONE_ID = "draft"

        // The unsaved form, in the saved state
        private const val KEY_NAME = "privacyZoneName"
        private const val KEY_LATITUDE = "privacyZoneLatitude"
        private const val KEY_LONGITUDE = "privacyZoneLongitude"
        private const val KEY_RADIUS = "privacyZoneRadius"
        private const val KEY_CENTERED = "privacyZoneCentered"
        private const val KEY_ZOOM = "privacyZoneZoom"

        /** The longest name the field takes. */
        const val MAX_NAME_LENGTH = 40

        /** Waits this long after the circle stops changing before counting again. */
        internal const val COUNT_DEBOUNCE_MS = 400L
        private const val STOP_TIMEOUT_MS = 5_000L

        /** A fix older than this is not used to place a new zone. */
        private const val MAX_LAST_KNOWN_AGE_MS = 10 * 60 * 1000L

        /**
         * @return The freshest last known location from the phone's providers, if it is recent and
         * location permission is granted; null otherwise.
         */
        @SuppressLint("MissingPermission")
        fun lastKnownLocation(context: Context): MapPoint? {
            if (!hasLocationPermission(context)) return null
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = buildList {
                add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            }
            val newest: Location? = providers.mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: Exception) {
                    Timber.d(e, "No last known location from %s", provider)
                    null
                }
            }.maxByOrNull { it.time }
            return newest
                ?.takeIf { System.currentTimeMillis() - it.time <= MAX_LAST_KNOWN_AGE_MS }
                ?.let { MapPoint(it.latitude, it.longitude) }
        }
    }
}
