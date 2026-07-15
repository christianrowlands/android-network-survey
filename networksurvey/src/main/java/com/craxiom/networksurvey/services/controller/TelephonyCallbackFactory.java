package com.craxiom.networksurvey.services.controller;

import android.os.Build;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import timber.log.Timber;

/**
 * Factory class for creating TelephonyCallback instances safely on devices that support them.
 * This factory ensures that TelephonyCallback classes are only loaded on API 31+ devices.
 */
public class TelephonyCallbackFactory
{

    /**
     * Creates an OverrideNetworkTypeListener if the device supports TelephonyCallback (API 31+).
     *
     * @return A new OverrideNetworkTypeListener instance, or null if not supported
     */
    @Nullable
    public static Object createOverrideNetworkTypeListener()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        {
            try
            {
                return createOverrideNetworkTypeListenerInternal();
            } catch (NoClassDefFoundError | NoSuchMethodError e)
            {
                Timber.w("TelephonyCallback not available on this device");
            }
        }
        return null;
    }

    /**
     * Internal method to create the listener - separated to prevent class loading on older devices.
     * This method should only be called on API 31+ devices.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private static TelephonyCallback createOverrideNetworkTypeListenerInternal()
    {
        return new OverrideNetworkTypeListener();
    }

    /**
     * Gets the override network type from a listener object if it's an OverrideNetworkTypeListener.
     *
     * @param listener The listener object
     * @return The override network type, or -1 if not available
     */
    public static int getOverrideNetworkType(@Nullable Object listener)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listener instanceof OverrideNetworkTypeListener)
        {
            return getOverrideNetworkTypeInternal((OverrideNetworkTypeListener) listener);
        }
        return -1;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static int getOverrideNetworkTypeInternal(OverrideNetworkTypeListener listener)
    {
        return listener.getOverrideNetworkType();
    }

    /**
     * Gets the display network type from a listener object if it's an OverrideNetworkTypeListener.
     *
     * @param listener The listener object
     * @return The display network type, or -1 if not available
     */
    public static int getDisplayNetworkType(@Nullable Object listener)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listener instanceof OverrideNetworkTypeListener)
        {
            return getDisplayNetworkTypeInternal((OverrideNetworkTypeListener) listener);
        }
        return -1;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static int getDisplayNetworkTypeInternal(OverrideNetworkTypeListener listener)
    {
        return listener.getDisplayNetworkType();
    }

    /**
     * Gets the cached {@link ServiceState} from a listener object if it's an
     * OverrideNetworkTypeListener.
     *
     * @param listener The listener object
     * @return The cached service state, or null if not available
     */
    @Nullable
    public static ServiceState getServiceState(@Nullable Object listener)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listener instanceof OverrideNetworkTypeListener)
        {
            return getServiceStateInternal((OverrideNetworkTypeListener) listener);
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static ServiceState getServiceStateInternal(OverrideNetworkTypeListener listener)
    {
        return listener.getServiceState();
    }

    /**
     * Creates a CellInfoCallbackWrapper if the device supports CellInfoCallback (API 29+).
     *
     * @param listener The listener to receive callback events
     * @return A new CellInfoCallbackWrapper instance, or null if not supported
     */
    @Nullable
    public static CellInfoCallbackWrapper createCellInfoCallback(@NonNull CellInfoCallbackImpl.CellInfoCallbackListener listener)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            try
            {
                return createCellInfoCallbackInternal(listener);
            } catch (NoClassDefFoundError | NoSuchMethodError e)
            {
                Timber.w("CellInfoCallback not available on this device");
            }
        }
        return null;
    }

    /**
     * Internal method to create the cell info callback - separated to prevent class loading on older devices.
     * This method should only be called on API 29+ devices.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static CellInfoCallbackWrapper createCellInfoCallbackInternal(@NonNull CellInfoCallbackImpl.CellInfoCallbackListener listener)
    {
        return new CellInfoCallbackImpl(listener);
    }

    /**
     * Gets the Android callback object from a CellInfoCallbackWrapper for use with TelephonyManager.
     *
     * @param wrapper The callback wrapper
     * @return The Android TelephonyManager.CellInfoCallback object, or null if not available
     */
    @Nullable
    public static TelephonyManager.CellInfoCallback getAndroidCellInfoCallback(@Nullable CellInfoCallbackWrapper wrapper)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wrapper != null)
        {
            Object callback = wrapper.getAndroidCallback();
            if (callback instanceof TelephonyManager.CellInfoCallback)
            {
                return (TelephonyManager.CellInfoCallback) callback;
            }
        }
        return null;
    }
}