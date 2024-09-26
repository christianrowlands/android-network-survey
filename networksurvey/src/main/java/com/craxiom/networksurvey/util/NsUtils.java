package com.craxiom.networksurvey.util;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;

import com.craxiom.networksurvey.Application;
import com.craxiom.networksurvey.R;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import timber.log.Timber;

/**
 * A collection of general purpose util methods for use throughout the app.
 */
public class NsUtils
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

    /**
     * @return This device's phone number. Used for populating the phone number in CDRs.
     */
    public static String getMyPhoneNumber(Context context, TelephonyManager telephonyManager)
    {
        try
        {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            {
                return "";
            }
            String line1Number = telephonyManager.getLine1Number();
            return line1Number == null ? "" : line1Number;
        } catch (SecurityException e)
        {
            Timber.e(e, "Could not get the phone number because of a permissions issue");
            return "";
        }
    }

    /**
     * Removes leading and trailing characters ("[" and "]") from the provided input, respectively.
     * If the input size is less than 2, then an empty string is returned
     *
     * @param input The input string to trim
     * @return the input string with leading and trailing characters ("[" and "]") from the provided input, respectively
     */
    public static String trimEnds(String input)
    {
        if (input.length() < 2)
        {
            return "";
        }
        return input.substring(1, input.length() - 1);
    }

    /**
     * Replaces the term "NAVSTAR" with "GPS" for the provided input
     *
     * @param input
     * @return the input string with the term "NAVSTAR" replaced with "GPS"
     */
    public static String replaceNavstar(String input)
    {
        return input.replace("NAVSTAR", "GPS");
    }

    /**
     * Serializes the provided two-dimensional array of doubles to a String
     * (for example, for logging GnssAntennaInfo to CSV files). Example:
     * [11.22 33.44 55.66 77.88; 10.2 30.4 50.6 70.8; 12.2 34.4 56.6 78.8]
     *
     * @param data an array to be serialized
     * @return the serialized version of the provided array as a String
     */
    public static String serialize(double[][] data)
    {
        StringBuilder builder = new StringBuilder(70); // Based on Pixel 5 GnssAntennaInfo
        builder.append("[");
        for (double[] i : data)
        {
            for (double j : i)
            {
                builder.append(j);
                builder.append(" ");
            }
            builder.replace(builder.length() - 1, builder.length(), "; ");
        }
        builder.replace(builder.length() - 2, builder.length(), "]");
        return builder.toString();
    }

    /**
     * @return The NS App version name (with any flavor suffix), or an empty string if it could not be determined.
     */
    public static String getAppVersionName(Context context)
    {
        try
        {
            final PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            final String versionSuffix = context.getString(R.string.version_suffix);
            return info.versionName + versionSuffix;
        } catch (PackageManager.NameNotFoundException e)
        {
            return "";
        }
    }
}
