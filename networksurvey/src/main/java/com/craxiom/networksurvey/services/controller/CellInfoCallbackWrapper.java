package com.craxiom.networksurvey.services.controller;

import android.telephony.CellInfo;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Wrapper interface for cell info callbacks that provides type safety while avoiding
 * class loading issues on devices below API 29.
 * <p>
 * This interface allows us to maintain type safety in our collections without directly
 * referencing TelephonyManager.CellInfoCallback which is only available on API 29+.
 */
public interface CellInfoCallbackWrapper
{

    /**
     * Called when cell info is available.
     *
     * @param cellInfo List of current cell information
     */
    void onCellInfo(List<CellInfo> cellInfo);

    /**
     * Called when an error occurs during cell info update.
     *
     * @param errorCode The error code
     * @param detail    Additional error details
     */
    void onError(int errorCode, @Nullable Throwable detail);

    /**
     * Gets the underlying Android callback object for use with TelephonyManager APIs.
     * This will return the actual TelephonyManager.CellInfoCallback on API 29+ devices.
     *
     * @return The Android callback object, or null if not available
     */
    @Nullable
    Object getAndroidCallback();
}