package com.craxiom.networksurvey.services.controller;

import android.os.Build;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Listener for telephony display info changes, specifically for tracking the override network type.
 * This class is separated from CellularController to prevent class loading issues on devices below API 31.
 * Only instantiate this class when Build.VERSION.SDK_INT >= Build.VERSION_CODES.S.
 */
@RequiresApi(api = Build.VERSION_CODES.S)
public class OverrideNetworkTypeListener extends TelephonyCallback implements TelephonyCallback.DisplayInfoListener
{
    private int overrideNetworkType = -1;

    @Override
    public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo)
    {
        overrideNetworkType = telephonyDisplayInfo.getOverrideNetworkType();
    }

    /**
     * Gets the current override network type.
     *
     * @return The override network type, or -1 if not set
     */
    public int getOverrideNetworkType()
    {
        return overrideNetworkType;
    }
}