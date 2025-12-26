package com.craxiom.networksurvey;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import timber.log.Timber;

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
    public static final long LOCATION_AGE_THRESHOLD_MS = 60_000L;
    private final Set<LocationListener> listeners = new CopyOnWriteArraySet<>();

    private Location latestLocation;
    private Runnable gnssTimeoutCallback;

    /**
     * Adds a callback so that the the caller can check for a GNSS timeout. This will be called whenever a new location
     * is received. This works out nicely because the timeout can start counting down once we know we have a good GPS
     * fix, and then if the consumer does not receive any raw GNSS measurements in a predetermined amount of time then
     * we can assume raw GNSS measurement is not supported on this device.
     *
     * @param callbackRunnable The runnable to execute anytime a location update is received.
     * @since 0.4.0
     */
    public void addGnssTimeoutCallback(Runnable callbackRunnable)
    {
        gnssTimeoutCallback = callbackRunnable;
    }

    /**
     * Removes the GNSS timeout callback.
     */
    public void clearGnssTimeoutCallback()
    {
        gnssTimeoutCallback = null;
    }

    /**
     * Registers a listener for notifications when different location events occur.
     *
     * @param listener The location listener to register.
     * @since 1.6.0
     */
    public void registerListener(LocationListener listener)
    {
        if (listener != null)
        {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters a location listener.
     *
     * @param listener The listener to unregister.
     * @since 1.6.0
     */
    public void unregisterListener(LocationListener listener)
    {
        if (listener != null)
        {
            listeners.remove(listener);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        updateLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        for (LocationListener listener : listeners)
        {
            try
            {
                listener.onStatusChanged(provider, status, extras);
            } catch (Throwable t)
            {
                Timber.e(t, "Unable to notify a LocationListener because of an exception");
            }
        }
    }

    @Override
    public void onProviderEnabled(@NonNull String provider)
    {
        Timber.i("Location Provider (%s) has been enabled", provider);

        for (LocationListener listener : listeners)
        {
            try
            {
                listener.onProviderEnabled(provider);
            } catch (Throwable t)
            {
                Timber.e(t, "Unable to notify a LocationListener because of an exception");
            }
        }
    }

    @Override
    public void onProviderDisabled(@NonNull String provider)
    {
        Timber.i("Location Provider (%s) has been disabled", provider);

        if (LocationManager.GPS_PROVIDER.equals(provider)) updateLocation(null);

        for (LocationListener listener : listeners)
        {
            try
            {
                listener.onProviderDisabled(provider);
            } catch (Throwable t)
            {
                Timber.e(t, "Unable to notify a LocationListener because of an exception");
            }
        }
    }

    public synchronized Location getLatestLocation()
    {
        if (latestLocation == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            long locationTimeAge = latestLocation.getElapsedRealtimeAgeMillis();
            if (locationTimeAge > LOCATION_AGE_THRESHOLD_MS)
            {
                // Don't return stale locations as it will mess up the data
                return null;
            } else
            {
                return latestLocation;
            }
        } else
        {
            return latestLocation;
        }
    }

    /**
     * Updates the cached location with the newly provided location.
     *
     * @param newLocation The newly provided location.
     */
    private synchronized void updateLocation(Location newLocation)
    {
        latestLocation = newLocation;

        if (newLocation != null)
        {
            if (gnssTimeoutCallback != null)
            {
                gnssTimeoutCallback.run();
            }
        }

        for (LocationListener listener : listeners)
        {
            try
            {
                listener.onLocationChanged(newLocation);
            } catch (Throwable t)
            {
                Timber.e(t, "Unable to notify a LocationListener because of an exception");
            }
        }
    }
}
