/*
 * Copyright (C) 2019 Sean J. Barbeau
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.location.Location;
import android.text.TextUtils;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import timber.log.Timber;

/**
 * Originally from the GPS Test open source Android app.  https://github.com/barbeau/gpstest
 */
public class IOUtils
{
    /**
     * Return an ISO 8601 combined date and time string for specified date/time.
     *
     * @param date The date object to use when generating the timestamp.
     * @return String with format {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} (e.g. "2020-08-19T18:13:22.548+00:00")
     * @since 0.2.1
     */
    public static String getRfc3339String(ZonedDateTime date)
    {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(date);
    }

    /**
     * Converts an RFC3339 formatted timestamp to Unix Epoch time. More specifically, it converts a date time string in
     * the {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} format to Unix Epoch time.
     *
     * @param dateTimeString The date time string in {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} format.
     * @return The Unix Epoch time in milliseconds.
     * @since 0.2.1
     */
    public static long getEpochFromRfc3339(String dateTimeString)
    {
        try
        {
            return ZonedDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception e)
        {
            Timber.e(e, "Could not convert the String date/time to Epoch");
            return 0;
        }
    }

    /**
     * Copies the provided location string to the clipboard.
     *
     * @param location the location string to copy to the clipboard.
     */
    public static void copyToClipboard(String location)
    {
        ClipboardManager clipboard = (ClipboardManager) Application.get().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null)
        {
            ClipData clip = ClipData.newPlainText(Application.get().getString(R.string.pref_file_location_output_title), location);
            clipboard.setPrimaryClip(clip);
        }
    }

    /**
     * Returns a string to be shared as plain text (e.g., via clipboard).
     *
     * @param location        The Location to convert to a String so it can be shared.
     * @param includeAltitude true if altitude should be included in the output, false if it should
     *                        not.  If the location doesn't have an altitude this variable has no effect.
     * @return a string to be shared as plain text (e.g., via clipboard).
     */
    public static String createLocationShare(Location location, boolean includeAltitude)
    {
        if (location == null)
        {
            return null;
        }
        String locationString = location.getLatitude() + "," + location.getLongitude();
        if (location.hasAltitude() && includeAltitude)
        {
            locationString += "," + location.getAltitude();
        }
        return locationString;
    }

    /**
     * Returns a string to be shared as plain text (e.g., via clipboard) based on the provided
     * pre-formatted latitude, longitude, and (optionally) altitude
     *
     * @return a string to be shared as plain text (e.g., via clipboard) based on the provided
     * * pre-formatted latitude, longitude, and (optionally) altitude
     */
    public static String createLocationShare(String latitude, String longitude, String altitude)
    {
        String locationString = latitude + "," + longitude;
        if (!TextUtils.isEmpty(altitude))
        {
            locationString += "," + altitude;
        }
        return locationString;
    }
}
