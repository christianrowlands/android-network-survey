package com.craxiom.networksurvey.services.controller;

import android.os.Build;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Listener for telephony display info and service state changes. It tracks the override network
 * type (for the status-bar branding badge), the display network type (the actual camped-on RAT),
 * and caches the latest {@link ServiceState} so the cellular UI can read the full per-domain
 * registration table without a per-scan blocking binder call.
 * <p>
 * This class is separated from CellularController to prevent class loading issues on devices below
 * API 31. Only instantiate it when Build.VERSION.SDK_INT >= Build.VERSION_CODES.S.
 */
@RequiresApi(api = Build.VERSION_CODES.S)
public class OverrideNetworkTypeListener extends TelephonyCallback
        implements TelephonyCallback.DisplayInfoListener, TelephonyCallback.ServiceStateListener
{
    // Today the writer (telephony callback) and readers share the same single-threaded executor,
    // so volatile is future-proofing for consistency with serviceState rather than a live race.
    private volatile int overrideNetworkType = -1;
    private volatile int displayNetworkType = -1;
    private volatile ServiceState serviceState = null;

    @Override
    public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo)
    {
        overrideNetworkType = telephonyDisplayInfo.getOverrideNetworkType();
        displayNetworkType = telephonyDisplayInfo.getNetworkType();
    }

    @Override
    public void onServiceStateChanged(@NonNull ServiceState serviceState)
    {
        this.serviceState = serviceState;
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

    /**
     * Gets the actual packet switched network type the device is camped on, as reported by the
     * platform for display purposes. This is the value the override network type is layered on top
     * of, so the two are only meaningful together.
     *
     * @return The display network type, or -1 if not set
     */
    public int getDisplayNetworkType()
    {
        return displayNetworkType;
    }

    /**
     * Gets the most recently reported {@link ServiceState} for this subscription.
     *
     * @return The cached service state, or null if none has been reported yet.
     */
    @Nullable
    public ServiceState getServiceState()
    {
        return serviceState;
    }
}