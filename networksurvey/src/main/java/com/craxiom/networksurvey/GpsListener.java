package com.craxiom.networksurvey;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

/**
 * A GPS Listener that is registered with the Android Location Service so that we are notified of Location updates.
 * <p>
 * This code was modeled after the WiGLE Wi-Fi App GPSListener:
 * https://github.com/wiglenet/wigle-wifi-wardriving/blob/master/wiglewifiwardriving/src/main/java/net/wigle/wigleandroid/listener/GPSListener.java
 *
 * @since 0.0.1
 */
public class GpsListener implements LocationListener
{
    private static final String LOG_TAG = GpsListener.class.getSimpleName();
    private static final float MIN_DISTANCE_ACCURACY = 32f; // This is the number that WiGLE Wi-Fi uses.

    private NetworkSurveyActivity networkSurveyActivity;

    private Location latestLocation;

    GpsListener(NetworkSurveyActivity networkSurveyActivity)
    {
        this.networkSurveyActivity = networkSurveyActivity;
    }

    @Override
    public void onLocationChanged(Location location)
    {
        updateLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {

    }

    @Override
    public void onProviderEnabled(String provider)
    {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) Log.i(LOG_TAG, "Location Provider (" + provider + ") has been enabled");
    }

    @Override
    public void onProviderDisabled(String provider)
    {
        if (Log.isLoggable(LOG_TAG, Log.INFO)) Log.i(LOG_TAG, "Location Provider (" + provider + ") has been disabled");
    }

    Location getLatestLocation()
    {
        return latestLocation;
    }

    private void updateLocation(Location newLocation)
    {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) Log.d(LOG_TAG, "Received an updated location: " + newLocation.toString());

        if (newLocation != null && LocationManager.GPS_PROVIDER.equals(newLocation.getProvider())
                && newLocation.getAccuracy() <= MIN_DISTANCE_ACCURACY)
        {
            latestLocation = newLocation;
        }
    }
}
