package com.craxiom.networksurvey.listeners;

import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.LocationListener;

/**
 * Defines the GPS events that listeners will be notified about.
 * <p>
 * This interface is originally from the GPS Test open source Android app.
 * https://github.com/barbeau/gpstest/blob/master/GPSTest/src/main/java/com/android/gpstest/GpsTestListener.java
 */
public interface IGpsTestListener extends LocationListener
{

    default void gpsStart()
    {
    }

    default void gpsStop()
    {
    }

    @Deprecated
    default void onGpsStatusChanged(int event, GpsStatus status)
    {
    }

    default void onGnssFirstFix(int ttffMillis)
    {
    }

    default void onSatelliteStatusChanged(GnssStatus status)
    {
    }

    default void onGnssStarted()
    {
    }

    default void onGnssStopped()
    {
    }

    default void onGnssMeasurementsReceived(GnssMeasurementsEvent event)
    {
    }

    default void onOrientationChanged(double orientation, double tilt)
    {
    }

    default void onNmeaMessage(String message, long timestamp)
    {
    }
}