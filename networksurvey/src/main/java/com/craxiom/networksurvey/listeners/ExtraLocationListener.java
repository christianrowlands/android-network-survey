package com.craxiom.networksurvey.listeners;

import android.location.Location;
import android.location.LocationListener;

import androidx.annotation.NonNull;

import java.util.List;

import timber.log.Timber;

/**
 * Handles listening for locations from the "extra" location listeners that are added if the user
 * selects the "ALL" option for the location provider in the settings. The ALL option provides a way
 * for the user to have all the providers grab a location and have them written to the device status
 * message so that the locations can be compared for further analysis.
 */
public class ExtraLocationListener implements LocationListener
{
    private final String selectedProvider;
    private Location latestLocation;

    public ExtraLocationListener(String provider)
    {
        selectedProvider = provider;
    }

    @Override
    public void onLocationChanged(@NonNull Location location)
    {
        Timber.i("In the %s extra location provider and saw a location update from the %s provider", selectedProvider, location.getProvider());
        if (selectedProvider.equals(location.getProvider()))
        {
            latestLocation = location;
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

    public Location getLatestLocation()
    {
        return latestLocation;
    }

    public String getProvider()
    {
        return selectedProvider;
    }
}
