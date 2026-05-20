package com.craxiom.networksurvey.util;

import android.content.Context;
import android.location.Location;

import com.craxiom.networksurvey.R;

import java.text.DecimalFormat;

/**
 * Shared utility for computing location status state and formatting location display values.
 * Used by the Compose dashboard and NetworkDetailsFragment to eliminate duplicated logic.
 *
 * @since 1.50
 */
public final class LocationStatusHelper
{
    private LocationStatusHelper()
    {
    }

    /**
     * Represents the current state of the location fix for UI display.
     */
    public enum LocationState
    {
        FIX,
        SEARCHING,
        GPS_DISABLED,
        NO_GPS,
        MISSING_PERMISSION
    }

    /**
     * Determines the current location state based on available information.
     *
     * @param location        The latest location, or null if unavailable.
     * @param hasPermission   Whether location permission is granted.
     * @param providerEnabled Whether the GPS provider is enabled.
     * @param hasGps          Whether the device has a GPS provider.
     * @return The current {@link LocationState}.
     */
    public static LocationState determineState(Location location, boolean hasPermission,
                                               boolean providerEnabled, boolean hasGps)
    {
        if (!hasPermission) return LocationState.MISSING_PERMISSION;
        if (!hasGps) return LocationState.NO_GPS;
        if (!providerEnabled) return LocationState.GPS_DISABLED;

        if (location == null) return LocationState.SEARCHING;

        return LocationState.FIX;
    }

    /**
     * Returns the color resource ID for the location status icon based on the current state.
     * Green for any fix, yellow for searching, red for error states.
     */
    public static int getIconColorRes(LocationState state)
    {
        switch (state)
        {
            case FIX:
                return R.color.rssi_green;
            case SEARCHING:
                return R.color.rssi_yellow;
            case GPS_DISABLED:
            case NO_GPS:
            case MISSING_PERMISSION:
            default:
                return R.color.rssi_red;
        }
    }

    /**
     * Returns the user-facing status text for the given location state.
     */
    public static String getStatusText(Context context, LocationState state)
    {
        switch (state)
        {
            case FIX:
                return context.getString(R.string.location_status_fixed);
            case SEARCHING:
                return context.getString(R.string.location_status_searching);
            case GPS_DISABLED:
                return context.getString(R.string.location_status_gps_disabled);
            case NO_GPS:
                return context.getString(R.string.location_status_no_gps);
            case MISSING_PERMISSION:
                return context.getString(R.string.location_status_missing_permission);
            default:
                return context.getString(R.string.location_status_searching);
        }
    }

    /**
     * Returns formatted accuracy text (e.g., "Acc: 4.6 m") using the user's preferred units.
     *
     * @return The formatted accuracy string, or an empty string if location is null.
     */
    public static String getAccuracyText(Context context, Location location)
    {
        if (location == null) return "";
        String formatted = MeasurementFormatter.formatAccuracy(context, location.getAccuracy());
        return context.getString(R.string.location_status_accuracy, formatted);
    }

    /**
     * Returns formatted details text (e.g., "35.4177, -80.8837   Alt: 192 m").
     *
     * @return The formatted details string, or an empty string if location is null.
     */
    public static String getDetailsText(Context context, Location location, DecimalFormat locationFormat)
    {
        if (location == null) return "";
        String latLon = locationFormat.format(location.getLatitude()) + ", "
                + locationFormat.format(location.getLongitude());
        String altitude = MeasurementFormatter.formatAltitude(context, location.getAltitude());
        return context.getString(R.string.location_details_format, latLon, altitude);
    }

    /**
     * Returns true if accuracy text should be displayed (only when we have a fix).
     */
    public static boolean shouldShowAccuracy(LocationState state)
    {
        return state == LocationState.FIX;
    }

    /**
     * Returns true if the details row (lat/lon + altitude) should be displayable.
     */
    public static boolean shouldShowDetails(LocationState state)
    {
        return state == LocationState.FIX;
    }
}
