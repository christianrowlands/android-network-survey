package com.craxiom.networksurvey.constants;

import android.Manifest;
import android.os.Build;

/**
 * The extra permissions that the Google Play Store won't approve for Network Survey. More
 * specifically, the SMS and CALL_LOG permissions were rejected by the Play Store review process.
 * <p>
 * This custom "flavor" of Network Survey can be built and installed using the APK for anyone that
 * wants to log SMS events and the "other" phone number for call events to CDR CSV files.
 *
 * @since 1.11
 */
public class CdrPermissions
{
    public static final String[] CDR_REQUIRED_PERMISSIONS;
    public static final String[] CDR_OPTIONAL_PERMISSIONS = {
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS};

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
