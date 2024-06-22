package com.craxiom.networksurvey.ui.cellular.model

import android.location.Location
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class CustomLocationOverlay(myLocationProvider: IMyLocationProvider, mapView: MapView, private val locationConsumer: IMyLocationConsumer) :
    MyLocationNewOverlay(myLocationProvider, mapView) {

    override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
        super.onLocationChanged(location, source)
        locationConsumer.onLocationChanged(location, source)
    }
}