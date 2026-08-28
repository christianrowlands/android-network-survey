package com.craxiom.networksurvey.listeners;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.craxiom.networksurvey.util.LocationDiagnostics;

import java.util.List;

/**
 * Handles listening for locations from the "extra" location listeners that are added if the user
 * selects the "ALL" option for the location provider in the settings. The ALL option provides a way
 * for the user to have all the providers grab a location and have them written to the device status
 * message so that the locations can be compared for further analysis.
 */
public class ExtraLocationListener implements LocationListener
{
    private final String selectedProvider;
    private volatile Location latestLocation;

    public ExtraLocationListener(String provider)
    {
        selectedProvider = provider;
    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        if (selectedProvider.equals(location.getProvider()))
        {
            latestLocation = location;

            // Debug only. These are the raw per provider fixes, so this is where a NETWORK_PROVIDER
            // fix can be inspected to see whether Wi-Fi or cell towers produced it.
            LocationDiagnostics.logLocationExtras(location);
        }
    }

    @Override
    public void onLocationChanged(@NonNull List<Location> locations)
    {
        LocationListener.super.onLocationChanged(locations);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider)
    {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider)
    {
        if (selectedProvider.equals(provider))
        {
            latestLocation = null;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
        // No-op
    }

    public Location getLatestLocation()
    {
        return latestLocation;
    }

    public String getProvider()
    {
        return selectedProvider;
    }
}
