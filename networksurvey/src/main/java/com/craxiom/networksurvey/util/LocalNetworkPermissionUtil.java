package com.craxiom.networksurvey.util;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import timber.log.Timber;

/**
 * Helper for the {@code android.permission.ACCESS_LOCAL_NETWORK} runtime permission introduced
 * (and made mandatory) for apps targeting Android 17 / API level 37. The permission gates any
 * socket traffic to local-network addresses, which Network Survey needs for gRPC and MQTT brokers
 * that users self-host on a LAN.
 *
 * <p>The permission is part of the {@code NEARBY_DEVICES} permission group, so users who have
 * already granted Bluetooth or nearby-Wi-Fi permissions will be auto-granted with no dialog.</p>
 */
public final class LocalNetworkPermissionUtil
{
    public static final String ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";

    private static final int LOCAL_NETWORK_PERMISSION_REQUEST_ID = 40;

    private LocalNetworkPermissionUtil()
    {
    }

    /**
     * Callers may safely ignore the return value. The grant outcome is intentionally not handled
     * via {@code onRequestPermissionsResult}: the connect proceeds regardless, and a denied user
     * simply experiences a TCP timeout for LAN hosts (public hosts work either way).
     *
     * @return {@code true} when the runtime permission is not required (older OS) or has already
     * been granted; {@code false} after a request has been issued.
     */
    public static boolean ensureLocalNetworkPermission(Activity activity)
    {
        if (activity == null) return true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return true;

        if (ContextCompat.checkSelfPermission(activity, ACCESS_LOCAL_NETWORK)
                == PackageManager.PERMISSION_GRANTED)
        {
            return true;
        }

        Timber.i("Requesting ACCESS_LOCAL_NETWORK permission for LAN broker connectivity");
        ActivityCompat.requestPermissions(activity,
                new String[]{ACCESS_LOCAL_NETWORK},
                LOCAL_NETWORK_PERMISSION_REQUEST_ID);
        return false;
    }
}
