package com.craxiom.networksurvey.ui.survey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.craxiom.networksurvey.ui.survey.model.LocationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Keeps the Survey screen's location line current: listens to the service's primary location
 * listener for fixes, and re-checks the permission and the location setting whenever the screen
 * resumes (the user may have changed either in system settings).
 */
class SurveyLocationTracker(private val context: Context) : LocationListener {

    private val mutableStatus = MutableStateFlow<LocationStatus>(LocationStatus.Finding)
    val status: StateFlow<LocationStatus> = mutableStatus.asStateFlow()

    private var gateway: SurveyServiceGateway? = null

    /** Starts listening to [newGateway]'s fixes, or stops listening when it is null. */
    fun attach(newGateway: SurveyServiceGateway?) {
        gateway?.unregisterLocationListener(this)
        gateway = newGateway
        newGateway?.registerLocationListener(this)
        refresh(newGateway?.latestLocation())
    }

    /** Re-checks the permission and the location setting, keeping the last fix if there is one. */
    fun refresh(latest: Location? = gateway?.latestLocation()) {
        mutableStatus.value = when {
            !hasPermission() -> LocationStatus.PermissionNeeded
            !isLocationEnabled() -> LocationStatus.ProviderOff
            latest != null -> fixed(latest)
            else -> LocationStatus.Finding
        }
    }

    override fun onLocationChanged(location: Location) {
        if (hasPermission()) mutableStatus.value = fixed(location)
    }

    override fun onProviderEnabled(provider: String) = refresh()

    override fun onProviderDisabled(provider: String) = refresh()

    private fun fixed(location: Location) = LocationStatus.Fixed(location.accuracy.roundToInt())

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }
}
