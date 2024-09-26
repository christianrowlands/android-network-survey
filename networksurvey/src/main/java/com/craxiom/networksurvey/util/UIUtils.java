/*
 * Copyright (C) 2015-2018 University of South  Florida, Sean J. Barbeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.craxiom.networksurvey.util;

import android.content.Context;
import android.location.Location;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;
import com.google.android.material.chip.Chip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for processing user interface elements.
 * <p>
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */

public class UIUtils
{
    public static final String COORDINATE_LATITUDE = "lat";
    public static final String COORDINATE_LONGITUDE = "lon";

    /**
     * Returns true if the fragment is attached to the activity, or false if it is not attached
     *
     * @param f fragment to be tested
     * @return true if the fragment is attached to the activity, or false if it is not attached
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isFragmentAttached(Fragment f)
    {
        return f.getActivity() != null && f.isAdded();
    }

    /**
     * Returns a human-readable description of the time-to-first-fix, such as "38 sec"
     *
     * @param ttff time-to-first fix, in milliseconds
     * @return a human-readable description of the time-to-first-fix, such as "38 sec"
     */
    public static String getTtffString(int ttff)
    {
        if (ttff == 0)
        {
            return "";
        } else
        {
            return TimeUnit.MILLISECONDS.toSeconds(ttff) + " sec";
        }
    }

    /**
     * Returns the provided latitude or longitude value in Degrees Minutes Seconds (DMS) format
     *
     * @param coordinate latitude or longitude to convert to DMS format
     * @return the provided latitude or longitude value in Degrees Minutes Seconds (DMS) format
     */
    public static String getDMSFromLocation(Context context, double coordinate, String latOrLon)
    {
        BigDecimal loc = new BigDecimal(coordinate);
        BigDecimal degrees = loc.setScale(0, RoundingMode.DOWN);
        BigDecimal minTemp = loc.subtract(degrees).multiply((new BigDecimal(60))).abs();
        BigDecimal minutes = minTemp.setScale(0, RoundingMode.DOWN);
        BigDecimal seconds = minTemp.subtract(minutes).multiply(new BigDecimal(60)).setScale(2, RoundingMode.HALF_UP);

        String hemisphere;
        int outputString;
        if (latOrLon.equals(COORDINATE_LATITUDE))
        {
            hemisphere = (coordinate < 0 ? "S" : "N");
            outputString = R.string.gps_lat_dms_value;
        } else
        {
            hemisphere = (coordinate < 0 ? "W" : "E");
            outputString = R.string.gps_lon_dms_value;
        }

        return context.getString(outputString, hemisphere, degrees.abs().intValue(), minutes.intValue(), seconds.floatValue());
    }

    /**
     * Returns the provided latitude or longitude value in Decimal Degree Minutes (DDM) format
     *
     * @param coordinate latitude or longitude to convert to DDM format
     * @param latOrLon   lat or lon to format hemisphere
     * @return the provided latitude or longitude value in Decimal Degree Minutes (DDM) format
     */
    public static String getDDMFromLocation(Context context, double coordinate, String latOrLon)
    {
        BigDecimal loc = new BigDecimal(coordinate);
        BigDecimal degrees = loc.setScale(0, RoundingMode.DOWN);
        BigDecimal minutes = loc.subtract(degrees).multiply((new BigDecimal(60))).abs().setScale(3, RoundingMode.HALF_UP);
        String hemisphere;
        int outputString;
        if (latOrLon.equals(COORDINATE_LATITUDE))
        {
            hemisphere = (coordinate < 0 ? "S" : "N");
            outputString = R.string.gps_lat_ddm_value;
        } else
        {
            hemisphere = (coordinate < 0 ? "W" : "E");
            outputString = R.string.gps_lon_ddm_value;
        }
        return context.getString(outputString, hemisphere, degrees.abs().intValue(), minutes.floatValue());
    }

    /**
     * Converts the provide value in meters to the corresponding value in feet
     *
     * @param meters value in meters to convert to feet
     * @return the provided meters value converted to feet
     */
    public static double toFeet(double meters)
    {
        return meters * 1000d / 25.4d / 12d;
    }

    /**
     * Converts the provide value in meters per second to the corresponding value in kilometers per hour
     *
     * @param metersPerSecond value in meters per second to convert to kilometers per hour
     * @return the provided meters per second value converted to kilometers per hour
     */
    public static float toKilometersPerHour(float metersPerSecond)
    {
        return metersPerSecond * 3600f / 1000f;
    }

    /**
     * Converts the provide value in meters per second to the corresponding value in miles per hour
     *
     * @param metersPerSecond value in meters per second to convert to miles per hour
     * @return the provided meters per second value converted to miles per hour
     */
    public static float toMilesPerHour(float metersPerSecond)
    {
        return toKilometersPerHour(metersPerSecond) / 1.6093440f;
    }

    /**
     * Returns the provided location based on the provided coordinate format, and sets the provided Views (locationValue, chips) accordingly if views are provided,
     * and returns the string value.
     *
     * @param location              location to be formatted
     * @param locationValue         View to be set with the selected coordinateFormat
     * @param includeAltitude       true if altitude should be included, false if it should not
     * @param chipDecimalDegrees    View to be set as checked if "dd" is the coordinateFormat
     * @param chipDMS               View to be set as checked if "dms" is the coordinateFormat
     * @param chipDegreesDecimalMin View to be set as checked if "ddm" is the coordinateFormat
     * @param coordinateFormat      dd, dms, or ddm
     * @return the provided location based on the provided coordinate format
     */
    public static String formatLocationForDisplay(Location location, TextView locationValue, boolean includeAltitude, Chip chipDecimalDegrees, Chip chipDMS, Chip chipDegreesDecimalMin, String coordinateFormat)
    {
        String formattedLocation;
        switch (coordinateFormat)
        {
            // Constants below must match string values in do_not_translate.xml
            case "dd":
                // Decimal degrees
                formattedLocation = NsUtils.createLocationShare(location, includeAltitude);
                if (chipDecimalDegrees != null)
                {
                    chipDecimalDegrees.setChecked(true);
                }
                break;

            case "dms":
                // Degrees minutes seconds
                formattedLocation = NsUtils.createLocationShare(getDMSFromLocation(Application.get(), location.getLatitude(), COORDINATE_LATITUDE),
                        getDMSFromLocation(Application.get(), location.getLongitude(), COORDINATE_LONGITUDE),
                        (location.hasAltitude() && includeAltitude) ? Double.toString(location.getAltitude()) : null);
                if (chipDMS != null)
                {
                    chipDMS.setChecked(true);
                }
                break;

            case "ddm":
                // Degrees decimal minutes
                formattedLocation = NsUtils.createLocationShare(getDDMFromLocation(Application.get(), location.getLatitude(), COORDINATE_LATITUDE),
                        getDDMFromLocation(Application.get(), location.getLongitude(), COORDINATE_LONGITUDE),
                        (location.hasAltitude() && includeAltitude) ? Double.toString(location.getAltitude()) : null);
                if (chipDegreesDecimalMin != null)
                {
                    chipDegreesDecimalMin.setChecked(true);
                }
                break;

            default:
                // Decimal degrees
                formattedLocation = NsUtils.createLocationShare(location, includeAltitude);
                if (chipDecimalDegrees != null)
                {
                    chipDecimalDegrees.setChecked(true);
                }
                break;
        }
        if (locationValue != null)
        {
            locationValue.setText(formattedLocation);
        }
        return formattedLocation;
    }
}
