package com.craxiom.networksurvey.ui.cellular.model

import android.location.Location
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class CustomLocationOverlay(
    myLocationProvider: IMyLocationProvider, mapView: MapView,
    private val locationConsumer: IMyLocationConsumer,
    private val followMyLocationChangeListener: FollowMyLocationChangeListener
) :
    MyLocationNewOverlay(myLocationProvider, mapView) {

    override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
        super.onLocationChanged(location, source)
        locationConsumer.onLocationChanged(location, source)
    }

    override fun onTouchEvent(event: MotionEvent?, mapView: MapView?): Boolean {
        val wasFollowing = isFollowLocationEnabled
        val result = super.onTouchEvent(event, mapView)
        val isFollowing = isFollowLocationEnabled
        if (wasFollowing != isFollowing) {
            followMyLocationChangeListener.onFollowMyLocationChanged(isFollowing)
        }

        return result
    }
}

interface FollowMyLocationChangeListener {
    fun onFollowMyLocationChanged(enabled: Boolean)
}