package com.craxiom.networksurvey.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * Helper for the {@code android.permission.ACCESS_LOCAL_NETWORK} runtime permission introduced
 * (and made mandatory) for apps targeting Android 17 / API level 37. The permission gates any
 * socket traffic to local-network addresses, which Network Survey needs for gRPC and MQTT brokers
 * that users self-host on a LAN.
 *
 * <p>The permission is part of the {@code NEARBY_DEVICES} permission group, so users who have
 * already granted Bluetooth or nearby-Wi-Fi permissions will be auto-granted with no dialog. The
 * request itself is made by {@code SurveyPermissionFlow} before anything connects.</p>
 */
public final class LocalNetworkPermissionUtil
{
    public static final String ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";

    private LocalNetworkPermissionUtil()
    {
    }

    /**
     * @return True when this Android version needs {@code ACCESS_LOCAL_NETWORK} to reach a server on
     * the local network and the app does not have it yet.
     */
    public static boolean isRequired(Context context)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
                && ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED;
    }
}
