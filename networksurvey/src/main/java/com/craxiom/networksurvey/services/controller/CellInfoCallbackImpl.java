package com.craxiom.networksurvey.services.controller;

import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

import timber.log.Timber;

/**
 * Implementation of CellInfoCallbackWrapper for API 29+ devices.
 * This class wraps the actual TelephonyManager.CellInfoCallback and delegates calls to it.
 * <p>
 * This class should only be instantiated on devices running API 29 or higher.
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class CellInfoCallbackImpl implements CellInfoCallbackWrapper
{

    private final CellInfoCallbackListener listener;
    private final TelephonyManager.CellInfoCallback androidCallback;

    /**
     * Interface for receiving cell info updates.
     * This separates the callback logic from the Android-specific callback class.
     */
    public interface CellInfoCallbackListener
    {
        void onCellInfoReceived(@NonNull List<CellInfo> cellInfo);

        void onCellInfoError(int errorCode, @Nullable Throwable detail);
    }

    /**
     * Creates a new CellInfoCallbackImpl with the given listener.
     *
     * @param listener The listener to receive callback events
     */
    public CellInfoCallbackImpl(@NonNull CellInfoCallbackListener listener)
    {
        this.listener = listener;

        // Create the actual Android callback
        androidCallback = new TelephonyManager.CellInfoCallback()
        {
            @Override
            public void onCellInfo(@NonNull List<CellInfo> cellInfo)
            {
                CellInfoCallbackImpl.this.onCellInfo(cellInfo);
            }

            @Override
            public void onError(int errorCode, @Nullable Throwable detail)
            {
                // Don't call super.onError() on Android 10 due to framework bug with ParcelableException
                // https://issuetracker.google.com/issues/141438333
                if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q)
                {
                    try
                    {
                        super.onError(errorCode, detail);
                    } catch (Throwable t)
                    {
                        Timber.e(t, "Error calling super.onError(), likely due to framework bug");
                    }
                }
                CellInfoCallbackImpl.this.onError(errorCode, detail);
            }
        };
    }

    @Override
    public void onCellInfo(List<CellInfo> cellInfo)
    {
        listener.onCellInfoReceived(cellInfo);
    }

    @Override
    public void onError(int errorCode, @Nullable Throwable detail)
    {
        Timber.w(detail, "Received an error from the Telephony Manager when requesting a cell info update; errorCode=%s", errorCode);
        listener.onCellInfoError(errorCode, detail);
    }

    @Override
    @NonNull
    public Object getAndroidCallback()
    {
        return androidCallback;
    }
}