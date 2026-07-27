package com.craxiom.networksurvey.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.craxiom.networksurvey.BuildConfig;

import java.util.Arrays;
import java.util.List;

import timber.log.Timber;

/**
 * Debug only diagnostics for the Android telephony values that feed the Voice Network, Data
 * Network, and Override Network fields on the cellular details screen.
 * <p>
 * This class exists because those three fields do not come from the same place, even though the
 * calling code treats them as if they do. {@link TelephonyManager#getDataNetworkType()} reads the
 * packet switched registration and understands the IWLAN (Wi-Fi calling) transport, while
 * {@link TelephonyManager#getVoiceNetworkType()} reads only the circuit switched registration on
 * the cellular transport and reports {@link TelephonyManager#NETWORK_TYPE_UNKNOWN} when that one
 * registration is missing. Dumping the full registration table makes it possible to tell those
 * cases apart instead of guessing.
 * <p>
 * Every method here is a no-op outside of a debug build.
 */
public final class TelephonyDiagnostics
{
    private static final String LOG_PREFIX = "NS-DIAG";

    private TelephonyDiagnostics()
    {
    }

    /**
     * Logs a snapshot of everything the platform is willing to tell us about the current cellular
     * registration, at the moment a cellular scan produced the values that are about to be pushed
     * to the UI.
     * <p>
     * Call this immediately before handing the network type strings to the survey record processor
     * so that the log lines up one to one with what the user sees on screen.
     *
     * @param context             Used for the runtime permission checks.
     * @param telephonyManager    The subscription specific telephony manager the values were read from.
     * @param subscriptionId      The subscription (SIM) the snapshot belongs to.
     * @param overrideNetworkType The override network type as cached by the display info callback,
     *                            or -1 if it has not been reported yet.
     * @param displayNetworkType  The display network type as cached by the display info callback,
     *                            or -1 if it has not been reported yet.
     */
    public static void logNetworkTypeSnapshot(Context context, TelephonyManager telephonyManager,
                                              int subscriptionId, int overrideNetworkType,
                                              int displayNetworkType)
    {
        if (!BuildConfig.DEBUG) return;
        if (context == null || telephonyManager == null) return;

        try
        {
            logSnapshot(context, telephonyManager, subscriptionId, overrideNetworkType, displayNetworkType);
        } catch (Throwable t)
        {
            // Never let a diagnostic take down a scan
            Timber.e(t, "%s Could not log the telephony snapshot", LOG_PREFIX);
        }
    }

    // READ_PHONE_STATE and ACCESS_COARSE_LOCATION are both checked below before any call that needs
    // them, and logNetworkTypeSnapshot catches anything that still escapes.
    @SuppressLint("MissingPermission")
    private static void logSnapshot(Context context, TelephonyManager telephonyManager,
                                    int subscriptionId, int overrideNetworkType, int displayNetworkType)
    {
        final boolean hasPhoneStatePermission = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
        final boolean hasCoarseLocationPermission = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        Timber.i("%s ===== subscriptionId=%d readPhoneState=%b coarseLocation=%b", LOG_PREFIX,
                subscriptionId, hasPhoneStatePermission, hasCoarseLocationPermission);

        if (!hasPhoneStatePermission)
        {
            Timber.i("%s READ_PHONE_STATE is denied, so the network types cannot be read", LOG_PREFIX);
            return;
        }

        final int voiceNetworkType = telephonyManager.getVoiceNetworkType();
        final int dataNetworkType = telephonyManager.getDataNetworkType();

        Timber.i("%s voiceNetworkType=%d (%s)", LOG_PREFIX, voiceNetworkType,
                CalculationUtils.getNetworkType(voiceNetworkType));
        Timber.i("%s dataNetworkType=%d (%s)", LOG_PREFIX, dataNetworkType,
                CalculationUtils.getNetworkType(dataNetworkType));
        Timber.i("%s overrideNetworkType=%d (%s)", LOG_PREFIX, overrideNetworkType,
                CalculationUtils.getOverrideNetworkType(overrideNetworkType));
        Timber.i("%s displayNetworkType=%d (%s)", LOG_PREFIX, displayNetworkType,
                CalculationUtils.getNetworkType(displayNetworkType));

        if (!hasCoarseLocationPermission)
        {
            Timber.i("%s ACCESS_COARSE_LOCATION is denied, so the ServiceState cannot be read", LOG_PREFIX);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            logCellConnectionStatuses(context, telephonyManager);
        }

        final ServiceState serviceState = telephonyManager.getServiceState();
        if (serviceState == null)
        {
            Timber.w("%s The ServiceState is null. The subscription may be inactive.", LOG_PREFIX);
            return;
        }

        logServiceState(serviceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            logRegistrationTable(serviceState);
        } else
        {
            Timber.i("%s The registration table needs API 30, this device is API %d, so the reason"
                    + " for the voice network type cannot be determined", LOG_PREFIX, Build.VERSION.SDK_INT);
        }

        Timber.v("%s raw ServiceState: %s", LOG_PREFIX, serviceState);
    }

    /**
     * Logs each cached cell's connection status alongside its registration state. This is the
     * device evidence for how well this vendor populates
     * {@link CellInfo#getCellConnectionStatus()}: the 5G NSA data leg should report
     * SECONDARY_SERVING while unregistered, and OEMs that report UNKNOWN for every cell are the
     * reason the absence of a status must never be treated as meaningful.
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    // ACCESS_FINE_LOCATION is checked before the getAllCellInfo call
    @SuppressLint("MissingPermission")
    private static void logCellConnectionStatuses(Context context, TelephonyManager telephonyManager)
    {
        final boolean hasFineLocationPermission = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFineLocationPermission)
        {
            Timber.i("%s ACCESS_FINE_LOCATION is denied, so the cell connection statuses cannot be read", LOG_PREFIX);
            return;
        }

        final List<CellInfo> allCellInfo = telephonyManager.getAllCellInfo();
        if (allCellInfo == null || allCellInfo.isEmpty())
        {
            Timber.i("%s No cached CellInfo is available for connection statuses", LOG_PREFIX);
            return;
        }

        for (CellInfo cellInfo : allCellInfo)
        {
            final int connectionStatus = cellInfo.getCellConnectionStatus();
            Timber.i("%s cell type=%s registered=%b connectionStatus=%d (%s)", LOG_PREFIX,
                    cellInfo.getClass().getSimpleName(), cellInfo.isRegistered(),
                    connectionStatus, getConnectionStatusName(connectionStatus));
        }
    }

    /**
     * @return A human readable name for a {@link CellInfo} {@code CONNECTION_*} constant.
     */
    private static String getConnectionStatusName(int connectionStatus)
    {
        return switch (connectionStatus)
        {
            case CellInfo.CONNECTION_NONE -> "NONE";
            case CellInfo.CONNECTION_PRIMARY_SERVING -> "PRIMARY_SERVING";
            case CellInfo.CONNECTION_SECONDARY_SERVING -> "SECONDARY_SERVING";
            case CellInfo.CONNECTION_UNKNOWN -> "UNKNOWN";
            default -> "UNRECOGNIZED";
        };
    }

    /**
     * Logs the service state wide values. Note that {@link ServiceState#getState()} is the VOICE
     * service state, which is a different thing from the voice network type and is worth seeing
     * alongside it.
     */
    // getOperatorAlphaLong and getOperatorNumeric need a location permission. The only caller
    // verifies ACCESS_COARSE_LOCATION before getting this far.
    @SuppressLint("MissingPermission")
    private static void logServiceState(ServiceState serviceState)
    {
        Timber.i("%s serviceState voiceServiceState=%d (%s) roaming=%b manualSelection=%b operator=%s (%s)",
                LOG_PREFIX, serviceState.getState(), getServiceStateName(serviceState.getState()),
                serviceState.getRoaming(), serviceState.getIsManualSelection(),
                serviceState.getOperatorAlphaLong(), serviceState.getOperatorNumeric());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            Timber.i("%s serviceState duplexMode=%s channelNumber=%d cellBandwidths=%s",
                    LOG_PREFIX, getDuplexModeName(serviceState.getDuplexMode()),
                    serviceState.getChannelNumber(), Arrays.toString(serviceState.getCellBandwidths()));
        }

        // ServiceState.isSearching() is API 30, despite the other ServiceState getters above being API 28
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            Timber.i("%s serviceState searching=%b", LOG_PREFIX, serviceState.isSearching());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        {
            Timber.i("%s serviceState nonTerrestrialNetwork=%b", LOG_PREFIX,
                    serviceState.isUsingNonTerrestrialNetwork());
        }
    }

    /**
     * Logs one line per network registration. The rows are keyed by (domain, transport):
     * CS over WWAN is circuit switched voice, PS over WWAN is cellular data, and PS over WLAN is
     * the ePDG tunnel used for Wi-Fi calling. Which rows exist, and what access network technology
     * each one carries, is what determines the values shown on the cellular details screen.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private static void logRegistrationTable(ServiceState serviceState)
    {
        final List<NetworkRegistrationInfo> registrationInfoList = serviceState.getNetworkRegistrationInfoList();
        if (registrationInfoList.isEmpty())
        {
            Timber.i("%s The ServiceState has no NetworkRegistrationInfo rows", LOG_PREFIX);
            return;
        }

        for (NetworkRegistrationInfo registrationInfo : registrationInfoList)
        {
            final int accessNetworkTechnology = registrationInfo.getAccessNetworkTechnology();

            Timber.i("%s   NRI domain=%s transport=%s registered=%b searching=%b roaming=%b rat=%d (%s) registeredPlmn=%s services=%s",
                    LOG_PREFIX,
                    CalculationUtils.getDomainName(registrationInfo.getDomain()),
                    CalculationUtils.getTransportTypeName(registrationInfo.getTransportType()),
                    isRegistered(registrationInfo),
                    isSearching(registrationInfo),
                    isRoaming(registrationInfo),
                    accessNetworkTechnology,
                    CalculationUtils.getNetworkType(accessNetworkTechnology),
                    registrationInfo.getRegisteredPlmn(),
                    getServiceTypeNames(registrationInfo.getAvailableServices()));
        }
    }

    /**
     * The isNetworkX() getters report the true modem state and are preferred, but they were only
     * added in API 34. The deprecated isX() getters go back to API 30 and report the same thing
     * except that a carrier config or resource overlay may have overridden the value.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    @SuppressWarnings("deprecation")
    private static boolean isRegistered(NetworkRegistrationInfo registrationInfo)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? registrationInfo.isNetworkRegistered()
                : registrationInfo.isRegistered();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @SuppressWarnings("deprecation")
    private static boolean isSearching(NetworkRegistrationInfo registrationInfo)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? registrationInfo.isNetworkSearching()
                : registrationInfo.isSearching();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @SuppressWarnings("deprecation")
    private static boolean isRoaming(NetworkRegistrationInfo registrationInfo)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? registrationInfo.isNetworkRoaming()
                : registrationInfo.isRoaming();
    }

    private static String getServiceStateName(int state)
    {
        return switch (state)
        {
            case ServiceState.STATE_IN_SERVICE -> "IN_SERVICE";
            case ServiceState.STATE_OUT_OF_SERVICE -> "OUT_OF_SERVICE";
            case ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY";
            case ServiceState.STATE_POWER_OFF -> "POWER_OFF";
            default -> "UNRECOGNIZED(" + state + ")";
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private static String getDuplexModeName(int duplexMode)
    {
        return switch (duplexMode)
        {
            case ServiceState.DUPLEX_MODE_FDD -> "FDD";
            case ServiceState.DUPLEX_MODE_TDD -> "TDD";
            case ServiceState.DUPLEX_MODE_UNKNOWN -> "UNKNOWN";
            default -> "UNRECOGNIZED(" + duplexMode + ")";
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static String getServiceTypeNames(List<Integer> availableServices)
    {
        if (availableServices == null || availableServices.isEmpty()) return "[]";

        final StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < availableServices.size(); i++)
        {
            if (i > 0) builder.append(",");
            builder.append(getServiceTypeName(availableServices.get(i)));
        }
        return builder.append("]").toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static String getServiceTypeName(int serviceType)
    {
        return switch (serviceType)
        {
            case NetworkRegistrationInfo.SERVICE_TYPE_VOICE -> "VOICE";
            case NetworkRegistrationInfo.SERVICE_TYPE_DATA -> "DATA";
            case NetworkRegistrationInfo.SERVICE_TYPE_SMS -> "SMS";
            case NetworkRegistrationInfo.SERVICE_TYPE_VIDEO -> "VIDEO";
            case NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY -> "EMERGENCY";
            case NetworkRegistrationInfo.SERVICE_TYPE_MMS -> "MMS";
            case NetworkRegistrationInfo.SERVICE_TYPE_UNKNOWN -> "UNKNOWN";
            default -> "UNRECOGNIZED(" + serviceType + ")";
        };
    }
}
