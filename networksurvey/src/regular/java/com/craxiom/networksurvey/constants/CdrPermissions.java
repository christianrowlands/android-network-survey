package com.craxiom.networksurvey.constants;

import android.Manifest;
import android.os.Build;

/**
 * The CDR permissions the Google Play build may ask for. Play does not approve the SMS and call
 * log permissions, so this build logs no SMS or MMS events and no other party's phone number.
 */
public class CdrPermissions
{
    /** False because this build cannot read SMS and MMS events. */
    public static final boolean LOGS_TEXT_MESSAGES = false;

    public static final String[] CDR_REQUIRED_PERMISSIONS;
    public static final String[] CDR_OPTIONAL_PERMISSIONS = {
            Manifest.permission.READ_PHONE_NUMBERS};

    static
    {
        // Android 13+ (SDK 33) requires permission for push notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            CDR_REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.POST_NOTIFICATIONS};
        } else
        {
            CDR_REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE};
        }
    }
}
