package com.craxiom.networksurvey.ui.cellular.towermap

import android.location.Location
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.parcelize.Parcelize
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap

/**
 * State object to control and observe the MapLibre camera.
 */
class CameraPositionState(
    position: CameraPosition = CameraPosition.Builder().build(),
    cameraMode: CameraMode = CameraMode.NONE
) {
    /** Whether the camera is moving (pan/zoom/rotate). */
    var isMoving by mutableStateOf(false)
        internal set

    /** Reason for latest camera movement. */
    var cameraMoveStartedReason by mutableStateOf(CameraMoveStartedReason.NO_MOVEMENT_YET)
        internal set

    /** Last known user location. */
    var location by mutableStateOf<Location?>(null)
        internal set

    /** Current saved camera position. */
    internal var rawPosition by mutableStateOf(position)

    var position: CameraPosition
        get() = rawPosition
        set(value) {
            synchronized(lock) {
                map?.moveCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                        value
                    )
                )
            }
        }

    internal var rawCameraMode by mutableStateOf(cameraMode)
    var cameraMode: CameraMode
        get() = rawCameraMode
        set(value) {
            synchronized(lock) {
                map?.locationComponent?.cameraMode = value.toInternal()
                rawCameraMode = value
            }
        }

    // Underlying MapLibreMap, set by updater
    private var map: MapLibreMap? by mutableStateOf(null)
    private val lock = Any()

    internal fun setMap(map: MapLibreMap?) {
        synchronized(lock) {
            if (this.map != null && map != null) {
                throw IllegalStateException("CameraPositionState may only bind to one Map at a time.")
            }
            this.map = map
            if (map != null) {
                map.moveCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                        rawPosition
                    )
                )
                map.locationComponent.cameraMode = rawCameraMode.toInternal()
            } else {
                isMoving = false
            }
        }
    }

    companion object {
        /** Saver for CameraPositionState. */
        val Saver: Saver<CameraPositionState, SaveableCameraPositionState> = Saver(
            save = { SaveableCameraPositionState(it.position, it.cameraMode.toInternal()) },
            restore = { CameraPositionState(it.position, CameraMode.fromInternal(it.cameraMode)) }
        )
    }
}

/** Create and remember a [CameraPositionState]. */
@Composable
fun rememberCameraPositionState(
    key: String? = null,
    init: CameraPositionState.() -> Unit = {}
): CameraPositionState = rememberSaveable(key, saver = CameraPositionState.Saver) {
    CameraPositionState().apply(init)
}

/** Local provider for camera state. */
internal val LocalCameraPositionState = staticCompositionLocalOf { CameraPositionState() }

// Dummy enums for CameraMode & Reason, map to MapLibre internals.
enum class CameraMode {
    NONE, TRACKING;

    fun toInternal(): Int = when (this) {
        NONE -> org.maplibre.android.location.modes.CameraMode.NONE
        TRACKING -> org.maplibre.android.location.modes.CameraMode.TRACKING
    }

    companion object {
        fun fromInternal(value: Int): CameraMode = when (value) {
            org.maplibre.android.location.modes.CameraMode.TRACKING -> TRACKING
            else -> NONE
        }
    }
}

enum class CameraMoveStartedReason(val internal: Int) {
    GESTURE(1), API(2), UNKNOWN(0), NO_MOVEMENT_YET(-1);

    companion object {
        fun fromInt(i: Int) = values().find { it.internal == i } ?: UNKNOWN
    }
}

/** Placeholder for saveable state. */
@Parcelize
data class SaveableCameraPositionState(
    val position: CameraPosition,
    val cameraMode: Int
) : Parcelable
