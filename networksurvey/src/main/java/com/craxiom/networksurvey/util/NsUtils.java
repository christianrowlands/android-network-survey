package com.craxiom.networksurvey.util;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    public static String getDeviceModel()
    {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    /**
     * @return True if there is a network connection available, false if it is not reachable.
     */
    public static boolean isNetworkAvailable(Context context)
    {
        if (hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE))
        {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activeNetwork = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (capabilities != null)
            {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                {
                    return true;
                }
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                {
                    return true;
                }
                return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            }
        }
        return false;
    }

    /**
     * @return True if the phone is on a network that isn't metered (typically Wi-Fi), the kind an
     * upload limited to Wi-Fi waits for.
     */
    public static boolean isOnUnmeteredNetwork(Context context)
    {
        if (!hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) return false;

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    public static boolean hasPermission(Context context, String permission)
    {
        return (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Extracts the MCC portion from a PLMN string in "MCC-MNC" format.
     *
     * @param plmn The PLMN string (e.g., "310-01").
     * @return The MCC string, or an empty string if the input is null or invalid.
     */
    public static String extractMccFromPlmn(String plmn)
    {
        if (plmn == null) return "";
        String[] parts = plmn.split("-");
        return parts[0];
    }

    /**
     * Extracts the MNC portion from a PLMN string in "MCC-MNC" format, preserving leading zeros.
     *
     * @param plmn The PLMN string (e.g., "310-01").
     * @return The MNC string with leading zeros preserved, or an empty string if the input is
     * null or does not contain a hyphen separator.
     */
    public static String extractMncFromPlmn(String plmn)
    {
        if (plmn == null) return "";
        String[] parts = plmn.split("-");
        return parts.length > 1 ? parts[1] : "";
    }

    /**
     * Extracts the MNC portion from a PLMN string in "MCC-MNC" format, preserving leading zeros.
     * Returns null instead of empty string when the MNC is not available.
     *
     * @param plmn The PLMN string (e.g., "310-01"), or null.
     * @return The MNC string with leading zeros preserved, or null if the input is null or
     * does not contain a hyphen separator.
     */
    @Nullable
    public static String extractMncFromPlmnOrNull(String plmn)
    {
        if (plmn == null) return null;
        String[] parts = plmn.split("-");
        return parts.length > 1 ? parts[1] : null;
    }

    /**
     * Extracts MCC and MNC as strings from a PLMN field, falling back to integer values.
     * This preserves MNC leading zeros when the PLMN field is available.
     *
     * @param hasPlmn Whether the PLMN field is present in the record.
     * @param plmn    The PLMN string (e.g., "310-01"), may be null.
     * @param mccInt  Fallback integer MCC value when PLMN is not available.
     * @param mncInt  Fallback integer MNC value when PLMN is not available.
     * @return A two-element array [mcc, mnc] as strings.
     */
    public static String[] extractMccMncStrings(boolean hasPlmn, String plmn, int mccInt, int mncInt)
    {
        if (hasPlmn && plmn != null)
        {
            return new String[]{extractMccFromPlmn(plmn), extractMncFromPlmn(plmn)};
        }
        return new String[]{String.valueOf(mccInt), String.valueOf(mncInt)};
    }
}
