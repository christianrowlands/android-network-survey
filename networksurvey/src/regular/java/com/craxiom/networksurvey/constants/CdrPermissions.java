package com.craxiom.networksurvey.constants;

import android.Manifest;
import android.os.Build;

public class CdrPermissions
{
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
