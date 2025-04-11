package com.craxiom.networksurvey.services;

import static com.craxiom.networksurvey.util.GpsTestUtil.getGnssTimeoutIntervalMs;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssAutomaticGainControl;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.BluetoothRecordData;
import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.GnssRecordData;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteBandwidth;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.messaging.PhoneState;
import com.craxiom.messaging.PhoneStateData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.messaging.WifiBeaconRecord;
import com.craxiom.messaging.WifiBeaconRecordData;
import com.craxiom.messaging.bluetooth.SupportedTechnologies;
import com.craxiom.messaging.gnss.Constellation;
import com.craxiom.messaging.phonestate.Domain;
import com.craxiom.messaging.phonestate.NetworkType;
import com.craxiom.messaging.phonestate.SimState;
import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.messaging.wifi.Standard;
import com.craxiom.messaging.wifi.WifiBandwidth;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.constants.CdmaMessageConstants;
import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.craxiom.networksurvey.constants.GnssMessageConstants;
import com.craxiom.networksurvey.constants.GsmMessageConstants;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.NrMessageConstants;
import com.craxiom.networksurvey.constants.UmtsMessageConstants;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICdrEventListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.logging.db.DbUploadStore;
import com.craxiom.networksurvey.model.CdrEvent;
import com.craxiom.networksurvey.model.CdrEventType;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.model.ConstellationFreqKey;
import com.craxiom.networksurvey.model.NrRecordWrapper;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.services.controller.CellularController;
import com.craxiom.networksurvey.util.FormatUtils;
import com.craxiom.networksurvey.util.LocationUtils;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.NsUtils;
import com.craxiom.networksurvey.util.ParserUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.craxiom.networksurvey.util.WifiUtils;
import com.google.common.base.Strings;
import com.google.protobuf.BoolValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import timber.log.Timber;

/**
 * Responsible for consuming {@link CellInfo} objects, converting them to records specific to a protocol, and then notifying any listeners
 * of the new record.
 *
 * @since 0.0.2
 */
public class SurveyRecordProcessor
{
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final String MISSION_ID_PREFIX = "NS ";
    private static final int UNSET_TX_POWER_LEVEL = 127;

    /**
     * It seems that every once in a while the RSSI value for a bluetooth record will be set to 127.
     * I'm not sure why this is, but we will just ignore those records.
     */
    private static final int UNSET_RSSI = 127;
    private static final int MAX_CDR_LOCATION_WAIT_TIME = 5_000;

    private final Object cellInfoProcessingLock = new Object();
    private final Object activityUpdateLock = new Object();

    private final GpsListener gpsListener;
    private final Set<ICellularSurveyRecordListener> cellularSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<IWifiSurveyRecordListener> wifiSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<IBluetoothSurveyRecordListener> bluetoothSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<IGnssSurveyRecordListener> gnssSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<ICdrEventListener> cdrListeners = new CopyOnWriteArraySet<>();
    private final Set<IDeviceStatusListener> deviceStatusListeners = new CopyOnWriteArraySet<>();
    private volatile NetworkSurveyActivity networkSurveyActivity;

    private DbUploadStore uploadDbSink;

    private final ExecutorService executorService;
    private final String deviceId;
    private final String missionId;
    private final Context context;

    private int recordNumber = 1;
    private int groupNumber = 0; // This will be incremented to 1 the first time it is used.

    private int wifiRecordNumber = 1;
    private int bluetoothRecordNumber = 1;

    private int gnssRecordNumber = 1;
    private int gnssGroupNumber = 0; // This will be incremented to 1 the first time it is used.

    private int phoneStateRecordNumber = 1;

    private long lastGnssLogTimeMs;
    private int gnssScanRateMs;

    private int currentCallState = TelephonyManager.CALL_STATE_IDLE;
    private CdrEvent currentCdrCellIdentity = new CdrEvent(CdrEventType.LOCATION_UPDATE, "", "", CellularController.DEFAULT_SUBSCRIPTION_ID);

    /**
     * Creates a new processor that can consume the raw survey records in Android format and convert them to the
     * protobuf defined formats.
     *
     * @param gpsListener The GPS Listener that is used to retrieve the latest location.
     * @param deviceId    The Device ID associated with this phone.
     * @param context     The context that is used to get the app's default shared preferences.
     */
    SurveyRecordProcessor(GpsListener gpsListener, String deviceId, Context context, ExecutorService executorService)
    {
        this.gpsListener = gpsListener;
        this.deviceId = deviceId;
        this.executorService = executorService;
        this.context = context;

        missionId = MISSION_ID_PREFIX + deviceId + " " + DATE_TIME_FORMATTER.format(LocalDateTime.now());

        gnssScanRateMs = PreferenceUtils.getScanRatePreferenceMs(NetworkSurveyConstants.PROPERTY_GNSS_SCAN_INTERVAL_SECONDS,
                NetworkSurveyConstants.DEFAULT_GNSS_SCAN_INTERVAL_SECONDS, context);
    }

    void registerCellularSurveyRecordListener(ICellularSurveyRecordListener surveyRecordListener)
    {
        cellularSurveyRecordListeners.add(surveyRecordListener);
    }

    void unregisterCellularSurveyRecordListener(ICellularSurveyRecordListener surveyRecordListener)
    {
        cellularSurveyRecordListeners.remove(surveyRecordListener);
    }

    void registerWifiSurveyRecordListener(IWifiSurveyRecordListener surveyRecordListener)
    {
        wifiSurveyRecordListeners.add(surveyRecordListener);
    }

    void unregisterWifiSurveyRecordListener(IWifiSurveyRecordListener surveyRecordListener)
    {
        wifiSurveyRecordListeners.remove(surveyRecordListener);
    }

    void registerBluetoothSurveyRecordListener(IBluetoothSurveyRecordListener surveyRecordListener)
    {
        bluetoothSurveyRecordListeners.add(surveyRecordListener);
    }

    void unregisterBluetoothSurveyRecordListener(IBluetoothSurveyRecordListener surveyRecordListener)
    {
        bluetoothSurveyRecordListeners.remove(surveyRecordListener);
    }

    /**
     * Adds a listener that will be notified of new GNSS Survey records whenever this class processes a new GNSS record.
     *
     * @param surveyRecordListener The listener to add.
     * @since 0.3.0
     */
    void registerGnssSurveyRecordListener(IGnssSurveyRecordListener surveyRecordListener)
    {
        gnssSurveyRecordListeners.add(surveyRecordListener);
    }

    /**
     * Removes a listener of GNSS records.
     *
     * @param surveyRecordListener The listener to remove.
     * @since 0.3.0
     */
    void unregisterGnssSurveyRecordListener(IGnssSurveyRecordListener surveyRecordListener)
    {
        gnssSurveyRecordListeners.remove(surveyRecordListener);
    }

    /**
     * Adds a listener that will be notified of new CDR events whenever this class processes a new event.
     *
     * @param listener The listener to add.
     * @since 1.11
     */
    void registerCdrEventListener(ICdrEventListener listener)
    {
        cdrListeners.add(listener);
    }

    /**
     * Removes a listener of CDR events.
     *
     * @param listener The listener to remove.
     * @since 1.11
     */
    void unregisterCdrEventListener(ICdrEventListener listener)
    {
        cdrListeners.remove(listener);
    }

    /**
     * Adds a listener that will be notified of new device status messages.
     *
     * @param deviceStatusListener The listener to add.
     * @since 1.1.0
     */
    void registerDeviceStatusListener(IDeviceStatusListener deviceStatusListener)
    {
        deviceStatusListeners.add(deviceStatusListener);
    }

    /**
     * Removes a listener of Device Status messages.
     *
     * @param deviceStatusListener The listener to remove.
     * @since 1.1.0
     */
    void unregisterDeviceStatusListener(IDeviceStatusListener deviceStatusListener)
    {
        deviceStatusListeners.remove(deviceStatusListener);
    }

    /**
     * Adds a sink for the local database that does not follow the typical listener lifecycle.
     * More specifically, when the last regular listener is removed then the survey service will
     * be shutdown, but this DB sink will always want to consume records if they are being created
     * and will not prevent the survey service from being shutdown.
     */
    public synchronized void addDbSink(DbUploadStore dbSink)
    {
        registerCellularSurveyRecordListener(dbSink);
        registerWifiSurveyRecordListener(dbSink);
        uploadDbSink = dbSink;
    }

    public synchronized void removeDbSink()
    {
        unregisterCellularSurveyRecordListener(uploadDbSink);
        unregisterWifiSurveyRecordListener(uploadDbSink);
        uploadDbSink = null;
    }

    public synchronized boolean isDbSinkSet()
    {
        return uploadDbSink != null;
    }

    /**
     * Whenever the UI is visible, we need to pass information to it so it can be displayed to the user.
     *
     * @param networkSurveyActivity The activity that is now visible to the user.
     */
    void onUiVisible(NetworkSurveyActivity networkSurveyActivity)
    {
        synchronized (activityUpdateLock)
        {
            this.networkSurveyActivity = networkSurveyActivity;
        }
    }

    /**
     * The UI is no longer visible, so don't send any updates to the UI.
     */
    void onUiHidden()
    {
        synchronized (activityUpdateLock)
        {
            networkSurveyActivity = null;
        }
    }

    /**
     * @return True if either the UI or a listener needs this survey record processor.  False if the UI is hidden and
     * there are not any listeners.
     * <p>
     * Need to synchronize because of the usage of cellularDbSink.
     */
    synchronized boolean isBeingUsed()
    {
        return networkSurveyActivity != null
                || !cellularSurveyRecordListeners.isEmpty()
                || !wifiSurveyRecordListeners.isEmpty()
                || !bluetoothSurveyRecordListeners.isEmpty()
                || !gnssSurveyRecordListeners.isEmpty()
                || !cdrListeners.isEmpty()
                || !deviceStatusListeners.isEmpty()
                || uploadDbSink != null;
    }

    /**
     * @return True if there are any registered Cellular survey record listeners, false otherwise.
     * @noinspection BooleanMethodIsAlwaysInverted
     * @since 0.3.0
     */
    boolean isCellularBeingUsed()
    {
        return !cellularSurveyRecordListeners.isEmpty() || networkSurveyActivity != null;
    }

    /**
     * @return True if there are any registered Wi-Fi survey record listeners, false otherwise.
     */
    boolean isWifiBeingUsed()
    {
        return !wifiSurveyRecordListeners.isEmpty();
    }

    /**
     * @return True if there are any registered Bluetooth survey record listeners, false otherwise.
     * @since 1.0.0
     */
    boolean isBluetoothBeingUsed()
    {
        return !bluetoothSurveyRecordListeners.isEmpty();
    }

    /**
     * @return True if there are any registered GNSS survey record listeners, false otherwise.
     * @since 0.3.0
     */
    boolean isGnssBeingUsed()
    {
        return !gnssSurveyRecordListeners.isEmpty();
    }

    /**
     * @return True if there are any registered CDR event listeners, false otherwise.
     * @since 1.11
     */
    boolean isCdrBeingUsed()
    {
        return !cdrListeners.isEmpty();
    }

    /**
     * @return True if there are any registered Device Status message listeners, false otherwise.
     * @noinspection BooleanMethodIsAlwaysInverted
     * @since 1.1.0
     */
    boolean isDeviceStatusBeingUsed()
    {
        return !deviceStatusListeners.isEmpty();
    }

    /**
     * Process the updated list of {@link CellInfo} objects from the {@link TelephonyManager}.  This list is converted to the appropriate ProtoBuf defined
     * survey records and any listeners are notified of the new records.
     *
     * @param allCellInfo         The List of {@link CellInfo} records to convert to survey records.
     * @param dataNetworkType     The data network type (e.g. "LTE"), which might be different than the voice network type.
     * @param voiceNetworkType    The voice network type (e.g. "LTE").
     * @param subscriptionId      The subscription ID (aka SIM ID) associated with the cell info records.
     *                            This allows for multi-sim support in NS.
     * @param networkOperatorName The name of the network operator since it is sometimes not available in the CellInfo.
     * @param signalStrength      The signal strength object that contains the SNR value since it is sometimes not
     *                            available in the CellInfo object.
     * @param overrideNetworkType The network type that the provider has specified to override the actual network type
     *                            with. This is use for marketing purposes to show 5G when the network is really 4G.
     */
    public void onCellInfoUpdate(List<CellInfo> allCellInfo, String dataNetworkType, String voiceNetworkType,
                                 int subscriptionId, String networkOperatorName, SignalStrength signalStrength,
                                 String overrideNetworkType) throws SecurityException
    {
        // synchronized to make sure that we are only processing one list of Cell Info objects at a time.
        synchronized (cellInfoProcessingLock)
        {
            try
            {
                notifyNetworkTypeListeners(dataNetworkType, voiceNetworkType, subscriptionId, overrideNetworkType);

                if (allCellInfo != null && !allCellInfo.isEmpty())
                {
                    groupNumber++; // Group all the records found in this scan iteration.
                    final List<CellularRecordWrapper> cellularRecords = new ArrayList<>(allCellInfo.size());

                    for (CellInfo cellInfo : allCellInfo)
                    {
                        final CellularRecordWrapper cellularRecord = processCellInfo(cellInfo, subscriptionId, networkOperatorName, signalStrength);
                        if (cellularRecord != null) cellularRecords.add(cellularRecord);
                    }

                    // processCellInfo notifies listeners of the individual records, but we also
                    // want to notify the batch listeners (eg. the UI) of the entire batch.
                    notifyCellularListeners(cellularRecords, subscriptionId);
                } else
                {
                    notifyCellularListeners(Collections.emptyList(), subscriptionId);
                }
            } catch (Exception e)
            {
                Timber.e(e, "Unable to display and log Survey Record(s)");
                notifyCellularListeners(Collections.emptyList(), subscriptionId);
            }
        }
    }

    /**
     * Notification for when a new set of Wi-Fi scan results are available to process.
     *
     * @param apScanResults The list of results coming from the Android wifi scanning API.
     * @since 0.1.2
     */
    public void onWifiScanUpdate(List<ScanResult> apScanResults)
    {
        execute(() -> processAccessPoints(apScanResults));
    }

    /**
     * Notification for when a new single Bluetooth Classic scan result is available to process.
     *
     * @param device The Bluetooth device object associated with the scan.
     * @param rssi   The RSSI value associated with the scan.
     * @since 1.0.0
     */
    public void onBluetoothClassicScanUpdate(BluetoothDevice device, int rssi)
    {
        execute(() -> processBluetoothClassicResult(device, rssi));
    }

    /**
     * Notification for when a new single Bluetooth scan result is available to process.
     *
     * @param result A single Bluetooth scan result coming from the Android Bluetooth scanning API.
     * @since 1.0.0
     */
    public void onBluetoothScanUpdate(android.bluetooth.le.ScanResult result)
    {
        execute(() -> processBluetoothResult(result));
    }

    /**
     * Notification for when a new set of Bluetooth scan results are available to process.
     *
     * @param results The list of results coming from the Android Bluetooth scanning API.
     * @since 1.0.0
     */
    public void onBluetoothScanUpdate(List<android.bluetooth.le.ScanResult> results)
    {
        execute(() -> processBluetoothResults(results));
    }

    /**
     * Notification for when the latest set of GNSS measurements are available to process.
     *
     * @param event The latest set of GNSS measurements.
     * @since 0.3.0
     */
    public void onGnssMeasurements(GnssMeasurementsEvent event)
    {
        execute(() -> processGnssMeasurements(event));
    }

    /**
     * Notification for when the latest device status is available to process.
     *
     * @param deviceStatus The latest device status.
     * @since 1.1.0
     */
    void onDeviceStatus(DeviceStatus deviceStatus)
    {
        execute(() -> notifyDeviceStatusListeners(deviceStatus));
    }

    /**
     * Notification that the cellular service state has changed.
     *
     * @param serviceState     The new service state.
     * @param telephonyManager The Android telephony manager to get some more details from.
     * @since 1.4.0
     */
    @SuppressLint({"NewApi", "ObsoleteSdkInt"})
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void onServiceStateChanged(ServiceState serviceState, TelephonyManager telephonyManager, int subscriptionId)
    {
        notifyPhoneStateListeners(createPhoneStateMessage(telephonyManager, subscriptionId, serviceState,
                builder -> {
                    // The documentation indicates the getNetworkRegistrationInfoList method was added in API level 30,
                    // but I found it works for API level 29 as well. I filed a bug: https://issuetracker.google.com/issues/190809962
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    {
                        serviceState.getNetworkRegistrationInfoList()
                                .forEach(info -> builder.addNetworkRegistrationInfo(ParserUtils.convertNetworkInfo(info)));
                    }
                }));
    }

    public void onCdrServiceStateChanged(ServiceState serviceState, TelephonyManager telephonyManager, int subscriptionId)
    {
        CdrEvent cdrEvent = new CdrEvent(CdrEventType.LOCATION_UPDATE, "", "", subscriptionId);
        setCellInfo(cdrEvent, serviceState);

        if (currentCdrCellIdentity.locationAreaChanged(cdrEvent))
        {
            currentCdrCellIdentity = cdrEvent;
            finishCdrEvent(cdrEvent);
        }
    }

    /**
     * Handles creating and sending a {@link CdrEvent} to any listeners.
     *
     * @param state            The new state.
     * @param otherPhoneNumber The phone number. Only present if the READ_CALL_LOG permission is granted.
     * @param telephonyManager Used to get the cell identity of the current cell.
     * @param myPhoneNumber    This device's phone number. Only present if the READ_PHONE_NUMBERS permission is granted.
     */
    public void onCallStateChanged(int state, String otherPhoneNumber, TelephonyManager telephonyManager, String myPhoneNumber, int subscriptionId)
    {
        Timber.d("Current call state=%s, new call state=%s", currentCallState, state);
        CdrEvent cdrEvent = null;
        switch (state)
        {
            case TelephonyManager.CALL_STATE_IDLE: // Hangup
                break;

            case TelephonyManager.CALL_STATE_OFFHOOK: // Outgoing
                // The state has to transition from idle to offhook for it to be an outgoing call. The offhook state
                // will also be set on an incoming call, but it will transition from ringing to offhook.
                if (currentCallState == TelephonyManager.CALL_STATE_IDLE)
                {
                    cdrEvent = new CdrEvent(CdrEventType.OUTGOING_CALL, myPhoneNumber, otherPhoneNumber, subscriptionId);
                    setCellInfo(cdrEvent, telephonyManager);
                }
                break;

            case TelephonyManager.CALL_STATE_RINGING: // Incoming
                cdrEvent = new CdrEvent(CdrEventType.INCOMING_CALL, otherPhoneNumber, myPhoneNumber, subscriptionId);
                setCellInfo(cdrEvent, telephonyManager);
                break;

            default:
                break;
        }

        currentCallState = state;
        finishCdrEvent(cdrEvent);
    }

    public void onSmsEvent(CdrEventType smsEventType, String originatingAddress, TelephonyManager telephonyManager,
                           String destinationAddress, int subscriptionId)
    {
        if (cdrListeners.isEmpty()) return;

        Timber.d("onSmsEvent outgoingAddress=%s, destinationAddress=%s", originatingAddress, destinationAddress);
        CdrEvent cdrEvent = new CdrEvent(smsEventType, originatingAddress, destinationAddress, subscriptionId);
        setCellInfo(cdrEvent, telephonyManager);
        finishCdrEvent(cdrEvent);
    }

    /**
     * This javadoc has been taken and modified from
     * {@link android.telephony.PhoneStateListener#onRegistrationFailed(CellIdentity, String, int, int, int)}.
     * <p>
     * Report that Registration or a Location/Routing/Tracking Area update has failed.
     *
     * <p>Indicate whenever a registration procedure, including a location, routing, or tracking
     * area update fails. This includes procedures that do not necessarily result in a change of
     * the modem's registration status.
     *
     * @param cellIdentity        the CellIdentity, which must include the globally unique identifier
     *                            for the cell (for example, all components of the CGI or ECGI).
     * @param domain              DOMAIN_CS, DOMAIN_PS or both in case of a combined procedure.
     * @param causeCode           the primary failure cause code of the procedure.
     *                            For GSM/UMTS (MM), values are in TS 24.008 Sec 10.5.95
     *                            For GSM/UMTS (GMM), values are in TS 24.008 Sec 10.5.147
     *                            For LTE (EMM), cause codes are TS 24.301 Sec 9.9.3.9
     *                            For NR (5GMM), cause codes are TS 24.501 Sec 9.11.3.2
     *                            Integer.MAX_VALUE if this value is unused.
     * @param additionalCauseCode the cause code of any secondary/combined procedure if appropriate.
     *                            For UMTS, if a combined attach succeeds for PS only, then the GMM cause code shall be
     *                            included as an additionalCauseCode. For LTE (ESM), cause codes are in
     *                            TS 24.301 9.9.4.4. Integer.MAX_VALUE if this value is unused.
     * @since 1.4.0
     */
    void onRegistrationFailed(@NonNull CellIdentity cellIdentity, int domain,
                              int causeCode, int additionalCauseCode, TelephonyManager telephonyManager,
                              int subscriptionId, ServiceState serviceState)
    {
        notifyPhoneStateListeners(createPhoneStateMessage(telephonyManager, subscriptionId, serviceState,
                builder -> builder.addNetworkRegistrationInfo(ParserUtils.convertNetworkInfo(cellIdentity, domain, causeCode))));
    }

    private PhoneState createPhoneStateMessage(TelephonyManager telephonyManager, int subscriptionId,
                                               ServiceState serviceState, Consumer<PhoneStateData.Builder> networkRegistrationInfoFunction)
    {
        final PhoneStateData.Builder dataBuilder = PhoneStateData.newBuilder();
        final ZonedDateTime deviceTime = ZonedDateTime.now();
        final long elapsedTimeMillis = SystemClock.elapsedRealtime();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));

        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(phoneStateRecordNumber++);

        dataBuilder.setSimState(SimState.forNumber(telephonyManager.getSimState()));
        dataBuilder.setSimOperator(telephonyManager.getSimOperator());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        {
            dataBuilder.setNonTerrestrialNetwork(BoolValue.of(serviceState.isUsingNonTerrestrialNetwork()));
        }

        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subscriptionId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
        {
            dataBuilder.setSlot(Int32Value.of(subscriptionId));
        }

        networkRegistrationInfoFunction.accept(dataBuilder);

        final PhoneState.Builder messageBuilder = PhoneState.newBuilder();
        messageBuilder.setMessageType(DeviceStatusMessageConstants.PHONE_STATE_MESSAGE_TYPE);
        messageBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        messageBuilder.setData(dataBuilder);

        return messageBuilder.build();
    }

    /**
     * Sets the GNSS scan interval so that we can control how often this processor creates {@link GnssRecord}s from the
     * incoming GNSS events.
     *
     * @param gnssScanIntervalMs The new GNSS Scan rate in milliseconds.
     * @since 0.3.0
     */
    void setGnssScanRateMs(int gnssScanIntervalMs)
    {
        gnssScanRateMs = gnssScanIntervalMs;
    }

    /**
     * Given a {@link CellInfo} record, convert it to the appropriate ProtoBuf defined message.  Then, notify any
     * listeners so it can be written to a log file and/or sent to any servers if those services are enabled.
     *
     * @param cellInfo       The Cell Info object with the details.
     * @param subscriptionId The subscription ID (aka SIM ID) associated with the cell info record.
     * @since 0.0.5
     */
    private CellularRecordWrapper processCellInfo(CellInfo cellInfo, int subscriptionId, String networkOperatorName, SignalStrength signalStrength)
    {
        // We only want to take the time to process a record if we are going to do something with it.  Currently, that
        // means logging, sending to a server, or updating the UI with the latest LTE information.
        if (!cellularSurveyRecordListeners.isEmpty())
        {
            final String carrierName = getCarrierName(cellInfo, networkOperatorName);
            final ZonedDateTime deviceTime = ZonedDateTime.now();
            final long elapsedTimeMillis = SystemClock.elapsedRealtime();

            if (cellInfo instanceof CellInfoLte)
            {
                final LteRecord lteSurveyRecord = generateLteSurveyRecord((CellInfoLte) cellInfo, subscriptionId, carrierName, signalStrength, deviceTime, elapsedTimeMillis);
                if (lteSurveyRecord != null)
                {
                    notifyLteRecordListeners(lteSurveyRecord);
                    return new CellularRecordWrapper(CellularProtocol.LTE, lteSurveyRecord);
                }
            } else if (cellInfo instanceof CellInfoGsm)
            {
                final GsmRecord gsmRecord = generateGsmSurveyRecord((CellInfoGsm) cellInfo, subscriptionId, carrierName, deviceTime, elapsedTimeMillis);
                if (gsmRecord != null)
                {
                    notifyGsmRecordListeners(gsmRecord);
                    return new CellularRecordWrapper(CellularProtocol.GSM, gsmRecord);
                }
            } else if (cellInfo instanceof CellInfoCdma)
            {
                final CdmaRecord cdmaRecord = generateCdmaSurveyRecord((CellInfoCdma) cellInfo, subscriptionId, carrierName, deviceTime, elapsedTimeMillis);
                if (cdmaRecord != null)
                {
                    notifyCdmaRecordListeners(cdmaRecord);
                    return new CellularRecordWrapper(CellularProtocol.CDMA, cdmaRecord);
                }
            } else if (cellInfo instanceof CellInfoWcdma)
            {
                final UmtsRecord umtsRecord = generateUmtsSurveyRecord((CellInfoWcdma) cellInfo, subscriptionId, carrierName, deviceTime, elapsedTimeMillis);
                if (umtsRecord != null)
                {
                    notifyUmtsRecordListeners(umtsRecord);
                    return new CellularRecordWrapper(CellularProtocol.UMTS, umtsRecord);
                }
            } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo instanceof CellInfoNr)
            {
                final NrRecordWrapper nrRecordWrapper = generateNrSurveyRecord((CellInfoNr) cellInfo, subscriptionId, carrierName, deviceTime, elapsedTimeMillis);
                if (nrRecordWrapper != null)
                {
                    notifyNrRecordListeners((NrRecord) nrRecordWrapper.cellularRecord);
                    return nrRecordWrapper;
                }
            }
        }

        return null;
    }

    /**
     * Tries to get the carrier name from the provided cellInfo object.  If the carrier name is not available from the
     * cellInfo object, then the provided network operator name is used.
     */
    private String getCarrierName(CellInfo cellInfo, String networkOperatorName)
    {
        String carrierName = "";
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            CharSequence operatorAlphaLong = cellInfo.getCellIdentity().getOperatorAlphaLong();
            if (operatorAlphaLong != null)
            {
                carrierName = operatorAlphaLong.toString().trim();
            }
        }

        if (carrierName.isEmpty() && networkOperatorName != null)
        {
            carrierName = networkOperatorName;
        }

        return carrierName;
    }

    /**
     * Given a group of 802.11 scan results, create the protobuf objects from it and notify any listeners.
     *
     * @param apScanResults The list of Scan Results.
     * @since 0.1.2
     */
    private void processAccessPoints(List<ScanResult> apScanResults)
    {
        final ZonedDateTime deviceTime = ZonedDateTime.now();
        final long elapsedTimeMillis = SystemClock.elapsedRealtime();
        final List<WifiRecordWrapper> wifiBeaconRecords = apScanResults.stream()
                .map(apScanResult -> generateWiFiBeaconSurveyRecord(apScanResult, deviceTime, elapsedTimeMillis))
                .collect(Collectors.toList());
        notifyWifiBeaconRecordListeners(wifiBeaconRecords);
    }

    /**
     * Given a Bluetooth classic scan result, create the protobuf objects from it and notify any listeners.
     *
     * @param device The Bluetooth device object associated with the scan.
     * @param rssi   The RSSI value associated with the scan.
     * @since 1.0.0
     */
    private void processBluetoothClassicResult(BluetoothDevice device, int rssi)
    {
        notifyBluetoothRecordListeners(generateBluetoothSurveyRecord(null, device, rssi, UNSET_TX_POWER_LEVEL, ZonedDateTime.now(), SystemClock.elapsedRealtime()));
    }

    /**
     * Given a single Bluetooth scan result, create the protobuf object from it and notify any listeners.
     *
     * @param result The Scan Results.
     * @since 1.0.0
     */
    private void processBluetoothResult(android.bluetooth.le.ScanResult result)
    {
        notifyBluetoothRecordListeners(generateBluetoothSurveyRecord(result, ZonedDateTime.now(), SystemClock.elapsedRealtime()));
    }

    /**
     * Given a group of Bluetooth scan results, create the protobuf objects from it and notify any listeners.
     *
     * @param results The list of Scan Results.
     * @since 1.0.0
     */
    private void processBluetoothResults(List<android.bluetooth.le.ScanResult> results)
    {
        final ZonedDateTime deviceTime = ZonedDateTime.now();
        final long elapsedTimeMillis = SystemClock.elapsedRealtime();
        final List<BluetoothRecord> bluetoothRecords = results.stream()
                .map(scanResult -> generateBluetoothSurveyRecord(scanResult, deviceTime, elapsedTimeMillis))
                .collect(Collectors.toList());
        notifyBluetoothRecordListeners(bluetoothRecords);
    }

    /**
     * Given a {@link GnssMeasurementsEvent}, convert it to the appropriate ProtoBuf defined message.  Then,
     * notify any listeners so it can be written to a log file and/or sent to any servers if those services are enabled.
     * <p>
     * This method does nothing if the user preference defined GNSS Scan Interval time has not elapsed since the last
     * log time.
     *
     * @param event The event that contains all the GNSS measurement information.
     * @since 0.3.0
     */
    private void processGnssMeasurements(GnssMeasurementsEvent event)
    {
        // Ideally we would tell the Android OS that we only want GNSS Measurement Events every n seconds, but since
        // there does not seem to be any option for that we simply ignore any updates until the interval has been reached
        if (lastGnssLogTimeMs + gnssScanRateMs > System.currentTimeMillis()) return;

        lastGnssLogTimeMs = System.currentTimeMillis();

        final Collection<GnssMeasurement> gnssMeasurements = event.getMeasurements();

        final Map<ConstellationFreqKey, Float> agcMap = new HashMap<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
        {
            Collection<GnssAutomaticGainControl> gnssAgcs = event.getGnssAutomaticGainControls();
            //Timber.i("GnssAutomaticGainControls length=%s", (long) gnssAgcs.size());
            //gnssAgcs.forEach(g -> Timber.i("GnssAutomaticGainControl: Constellation=%s, CarrierFreq=%s, AGC=%s", GnssMessageConstants.getProtobufConstellation(g.getConstellationType()), g.getCarrierFrequencyHz(), g.getLevelDb()));

            for (GnssAutomaticGainControl agc : gnssAgcs)
            {
                ConstellationFreqKey key = new ConstellationFreqKey(agc.getConstellationType(), agc.getCarrierFrequencyHz());
                agcMap.put(key, (float) agc.getLevelDb());
            }
        }

        gnssGroupNumber++; // Group all the records found in this scan iteration.

        final ZonedDateTime deviceTime = ZonedDateTime.now();
        final long elapsedTimeMillis = SystemClock.elapsedRealtime();
        for (final GnssMeasurement gnssMeasurement : gnssMeasurements)
        {
            final GnssRecord gnssRecord = generateGnssSurveyRecord(gnssMeasurement, agcMap, deviceTime, elapsedTimeMillis);
            notifyGnssRecordListeners(gnssRecord);
        }
    }

    /**
     * Generates an empty GNSS message in cases where the Location Provider is enabled, we are given
     * permissions to access the device location, but we don't receive a location update within the GNSS Timeout
     * interval as defined in {@link com.craxiom.networksurvey.util.GpsTestUtil#getGnssTimeoutIntervalMs(long)}.
     *
     * @since 1.8.0
     */
    public void checkForMissedGnssMeasurement()
    {
        if (isLocationAllowed() && lastGnssLogTimeMs < System.currentTimeMillis() - getGnssTimeoutIntervalMs(gnssScanRateMs))
        {
            Timber.d("Generating an empty GNSS message");
            final GnssRecord gnssRecord = generateEmptyGnssSurveyRecord();
            notifyGnssRecordListeners(gnssRecord);
        }
    }

    /**
     * @return True if the {@link Manifest.permission#ACCESS_FINE_LOCATION} permission has been granted and location
     * provider is enabled.  False otherwise.
     */
    private boolean isLocationAllowed()
    {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Wraps the execute command for the executor service in a try catch to prevent the app from crashing if something
     * goes wrong with submitting the runnable. The most common crash I am seeing seems to be from the executor service
     * shutting down but some scan results are coming in. Hopefully that is the only case because otherwise we are
     * losing some survey results.
     *
     * @param runnable The runnable to execute on the executor service.
     * @since 1.5.0
     */
    private void execute(Runnable runnable)
    {
        try
        {
            executorService.execute(runnable);
        } catch (Throwable t)
        {
            Timber.w(t, "Could not submit to the executor service");
        }
    }

    /**
     * Given a {@link CellInfoGsm} object, pull out the values and generate a {@link GsmRecord}.
     *
     * @param cellInfoGsm The object that contains the GSM Cell info.  This can be a serving cell or a neighbor cell.
     * @return The survey record.
     */
    private GsmRecord generateGsmSurveyRecord(CellInfoGsm cellInfoGsm, int subscriptionId, String carrierName, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        final CellIdentityGsm cellIdentity = cellInfoGsm.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int lac = cellIdentity.getLac();
        final int cid = cellIdentity.getCid();
        final int arfcn = cellIdentity.getArfcn();
        final int bsic = cellIdentity.getBsic();

        CharSequence provider = null;
        if (!carrierName.isEmpty())
        {
            provider = carrierName;
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            provider = cellIdentity.getOperatorAlphaLong();
        }

        CellSignalStrengthGsm cellSignalStrength = cellInfoGsm.getCellSignalStrength();
        final int signalStrength = cellSignalStrength.getDbm();
        final int timingAdvance = cellSignalStrength.getTimingAdvance();

        // Validate that the required fields are present before proceeding further
        if (!validateGsmFields(arfcn, bsic, signalStrength)) return null;

        final GsmRecordData.Builder dataBuilder = GsmRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoGsm.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        // Even though the Android Javadocs indicate that an unset value is represented by Integer.MAX_VALUE, I found that a -1 is sometimes used for TA and CID.
        // I also found that 0 is used as unset for MCC, MNC, LAC, ARFCN, and BSIC.

        if (mcc != Integer.MAX_VALUE && mcc != 0)
        {
            dataBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        }
        if (mnc != Integer.MAX_VALUE && mnc != 0)
        {
            dataBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        }
        if (lac != Integer.MAX_VALUE && lac != 0)
        {
            dataBuilder.setLac(Int32Value.newBuilder().setValue(lac).build());
        }
        if (cid != Integer.MAX_VALUE && cid != -1)
        {
            dataBuilder.setCi(Int32Value.newBuilder().setValue(cid).build());
        }
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subscriptionId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
        {
            dataBuilder.setSlot(Int32Value.newBuilder().setValue(subscriptionId).build());
        }

        dataBuilder.setArfcn(Int32Value.newBuilder().setValue(arfcn).build());
        dataBuilder.setBsic(Int32Value.newBuilder().setValue(bsic).build());
        dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());

        if (timingAdvance != Integer.MAX_VALUE && timingAdvance != -1)
        {
            dataBuilder.setTa(Int32Value.newBuilder().setValue(timingAdvance).build());
        }

        final GsmRecord.Builder recordBuilder = GsmRecord.newBuilder();
        recordBuilder.setMessageType(GsmMessageConstants.GSM_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Given a {@link CellInfoCdma} object, pull out the values and generate a {@link CdmaRecord}.
     *
     * @param cellInfoCdma The object that contains the GSM Cell info.  This can be a serving cell or a neighbor cell.
     * @return The survey record.
     */
    private CdmaRecord generateCdmaSurveyRecord(CellInfoCdma cellInfoCdma, int subscriptionId, String carrierName, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        final CellIdentityCdma cellIdentity = cellInfoCdma.getCellIdentity();
        final int sid = cellIdentity.getSystemId();
        final int nid = cellIdentity.getNetworkId();
        final int bsid = cellIdentity.getBasestationId();

        CharSequence provider = null;
        if (!carrierName.isEmpty())
        {
            provider = carrierName;
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            provider = cellIdentity.getOperatorAlphaLong();
        }

        CellSignalStrengthCdma cellSignalStrength = cellInfoCdma.getCellSignalStrength();
        final int signalStrength = cellSignalStrength.getCdmaDbm();
        final int ecio = cellSignalStrength.getCdmaEcio();

        // Validate that the required fields are present before proceeding further
        if (!validateCdmaFields(signalStrength, ecio)) return null;

        // Convert the Ec/Io to the actual value.  The Android Javadocs indicate:  "Get the CDMA Ec/Io value in dB*10".  So we need to divide by 10.
        final float ecioFloat = ecio / 10.0f;

        final CdmaRecordData.Builder dataBuilder = CdmaRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoCdma.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        if (sid != Integer.MAX_VALUE)
        {
            dataBuilder.setSid(Int32Value.newBuilder().setValue(sid).build());
        }
        if (nid != Integer.MAX_VALUE)
        {
            dataBuilder.setNid(Int32Value.newBuilder().setValue(nid).build());
        }
        if (bsid != Integer.MAX_VALUE)
        {
            dataBuilder.setBsid(Int32Value.newBuilder().setValue(bsid).build());
        }
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subscriptionId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
        {
            dataBuilder.setSlot(Int32Value.newBuilder().setValue(subscriptionId).build());
        }

        dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());
        dataBuilder.setEcio(FloatValue.newBuilder().setValue(ecioFloat).build());

        final CdmaRecord.Builder recordBuilder = CdmaRecord.newBuilder();
        recordBuilder.setMessageType(CdmaMessageConstants.CDMA_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Given a {@link CellInfoWcdma} object, pull out the values and generate an {@link UmtsRecord}.
     *
     * @param cellInfoWcdma The object that contains the UMTS Cell info.  This can be a serving cell, or a neighbor cell.
     * @return The survey record.
     */
    private UmtsRecord generateUmtsSurveyRecord(CellInfoWcdma cellInfoWcdma, int subscriptionId, String carrierName, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        final CellIdentityWcdma cellIdentity = cellInfoWcdma.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int lac = cellIdentity.getLac();
        final int ci = cellIdentity.getCid();
        final int uarfcn = cellIdentity.getUarfcn();
        final int psc = cellIdentity.getPsc();

        CharSequence provider = null;
        if (!carrierName.isEmpty())
        {
            provider = carrierName;
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            provider = cellIdentity.getOperatorAlphaLong();
        }

        final CellSignalStrengthWcdma cellSignalStrengthUmts = cellInfoWcdma.getCellSignalStrength();

        final int signalStrength = ParserUtils.extractIntFromToString(cellSignalStrengthUmts.toString(), ParserUtils.RSSI_KEY);
        final int rscp = ParserUtils.extractIntFromToString(cellSignalStrengthUmts.toString(), ParserUtils.RSCP_KEY);

        // Validate that the required fields are present before proceeding further
        if (!validateUmtsFields(uarfcn, psc)) return null;

        final UmtsRecordData.Builder dataBuilder = UmtsRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoWcdma.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        if (mcc != Integer.MAX_VALUE)
        {
            dataBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        }
        if (mnc != Integer.MAX_VALUE)
        {
            dataBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        }
        if (lac != Integer.MAX_VALUE)
        {
            dataBuilder.setLac(Int32Value.newBuilder().setValue(lac).build());
        }
        if (ci != Integer.MAX_VALUE)
        {
            dataBuilder.setCid(Int32Value.newBuilder().setValue(ci).build());
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
        {
            final int ecNo = cellSignalStrengthUmts.getEcNo();
            if (ecNo != CellInfo.UNAVAILABLE)
            {
                dataBuilder.setEcno(FloatValue.newBuilder().setValue(ecNo).build());
            }
        }

        if (signalStrength != Integer.MAX_VALUE)
        {
            dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());
        }
        if (rscp != Integer.MAX_VALUE)
        {
            dataBuilder.setRscp(FloatValue.newBuilder().setValue(rscp).build());
        }
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subscriptionId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
        {
            dataBuilder.setSlot(Int32Value.newBuilder().setValue(subscriptionId).build());
        }

        dataBuilder.setUarfcn(Int32Value.newBuilder().setValue(uarfcn).build());
        dataBuilder.setPsc(Int32Value.newBuilder().setValue(psc).build());

        final UmtsRecord.Builder recordBuilder = UmtsRecord.newBuilder();
        recordBuilder.setMessageType(UmtsMessageConstants.UMTS_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Given a {@link CellInfoLte} object, pull out the values and generate an {@link LteRecord}.
     *
     * @param cellInfoLte The object that contains the LTE Cell info.  This can be a serving cell, or a neighbor cell.
     * @return The survey record.
     */
    private LteRecord generateLteSurveyRecord(CellInfoLte cellInfoLte, int subscriptionId, String carrierName, SignalStrength signalStrength, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        final CellIdentityLte cellIdentity = cellInfoLte.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int tac = cellIdentity.getTac();
        final int ci = cellIdentity.getCi();
        final int earfcn = cellIdentity.getEarfcn();
        final int pci = cellIdentity.getPci();

        CharSequence provider = null;
        if (!carrierName.isEmpty())
        {
            provider = carrierName;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            provider = cellIdentity.getOperatorAlphaLong();
        }

        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
        final int rsrp = cellSignalStrengthLte.getRsrp();
        final int rsrq = cellSignalStrengthLte.getRsrq();
        final int timingAdvance = cellSignalStrengthLte.getTimingAdvance();
        final int cqi = cellSignalStrengthLte.getCqi();

        int rssi = Integer.MAX_VALUE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            rssi = cellSignalStrengthLte.getRssi();
        }

        // Validate that the required fields are present before proceeding further
        if (!validateLteFields(earfcn, pci, rsrp)) return null;

        final LteRecordData.Builder dataBuilder = LteRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoLte.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        if (mcc != Integer.MAX_VALUE)
        {
            dataBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        }
        if (mnc != Integer.MAX_VALUE)
        {
            dataBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        }
        if (tac != Integer.MAX_VALUE)
        {
            dataBuilder.setTac(Int32Value.newBuilder().setValue(tac).build());
        }
        if (ci != Integer.MAX_VALUE)
        {
            dataBuilder.setEci(Int32Value.newBuilder().setValue(ci).build());
        }

        dataBuilder.setEarfcn(Int32Value.newBuilder().setValue(earfcn).build());
        dataBuilder.setPci(Int32Value.newBuilder().setValue(pci).build());
        dataBuilder.setRsrp(FloatValue.newBuilder().setValue(rsrp).build());

        if (rsrq != Integer.MAX_VALUE)
        {
            dataBuilder.setRsrq(FloatValue.newBuilder().setValue(rsrq).build());
        }
        if (timingAdvance != Integer.MAX_VALUE)
        {
            dataBuilder.setTa(Int32Value.newBuilder().setValue(timingAdvance).build());
        }
        if (rssi != Integer.MAX_VALUE)
        {
            dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(rssi).build());
        }

        // A CQI of 0 is considered "out of range" per 3GPP TS 36.213, and Android will return 0 for
        // neighbor cells.
        if (cqi != Integer.MAX_VALUE && cqi != 0)
        {
            dataBuilder.setCqi(Int32Value.newBuilder().setValue(cqi).build());
        }
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subscriptionId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
        {
            dataBuilder.setSlot(Int32Value.newBuilder().setValue(subscriptionId).build());
        }

        // The signalStrength object is only for the serving cell
        if (cellInfoLte.isRegistered())
        {
            int signalStrengthSnr = getLteRssnr(signalStrength);
            if (signalStrengthSnr != Integer.MIN_VALUE)
            {
                dataBuilder.setSnr(FloatValue.newBuilder().setValue(signalStrengthSnr).build());
            }
        }

        // I can't trust the rssnr value from the cellSignalStrengthLte object because it has always been 0 or 1.
        // Looking at the NetMonster Core source code, they indicate on certain devices the SNR value coming from
        // cellSignalStrengthLte is divided by 10, but the SignalStrength object has the correct value, so we will
        // use that value instead.
        /*int rssnr = cellSignalStrengthLte.getRssnr();
        if (rssnr != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setSnr(FloatValue.newBuilder().setValue(rssnr).build());
        }*/

        setBandwidth(dataBuilder, cellIdentity);

        final LteRecord.Builder recordBuilder = LteRecord.newBuilder();
        recordBuilder.setMessageType(LteMessageConstants.LTE_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    private int getLteRssnr(SignalStrength signalStrengths)
    {
        if (signalStrengths != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
        {
            List<CellSignalStrength> cellSignalStrengths = signalStrengths.getCellSignalStrengths();

            int rssnrValue = Integer.MIN_VALUE; // Initialize with a default invalid value

            for (CellSignalStrength signalStrength : cellSignalStrengths)
            {
                // Check if the CellSignalStrength instance is LTE
                if (signalStrength instanceof CellSignalStrengthLte lteStrength)
                {
                    rssnrValue = lteStrength.getRssnr();

                    break;
                }
            }

            return rssnrValue;
        } else
        {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Given a {@link CellInfoNr} object, pull out the values and generate a {@link NrRecord}.
     *
     * @param cellInfoNr The object that contains the NR(5G) Cell info.  This can be a serving cell, or a neighbor cell.
     * @return The survey record.
     * @since 1.5.0
     */
    @SuppressLint("Range")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private NrRecordWrapper generateNrSurveyRecord(CellInfoNr cellInfoNr, int subscriptionId, String carrierName, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        // safe to cast as per: https://developer.android.com/reference/android/telephony/CellInfoNr#getCellIdentity()
        final CellIdentityNr cellIdentity = (CellIdentityNr) cellInfoNr.getCellIdentity();

        // default to CellInfoNr.UNAVAILABLE for lambdas below and because it's the return value for the other int fields
        final int mcc = ParserUtils.parseInt(cellIdentity.getMccString(), CellInfoNr.UNAVAILABLE);
        final int mnc = ParserUtils.parseInt(cellIdentity.getMncString(), CellInfoNr.UNAVAILABLE);
        final int nrarfcn = cellIdentity.getNrarfcn();
        final int pci = cellIdentity.getPci();
        final int tac = cellIdentity.getTac();
        final long nci = cellIdentity.getNci();
        int[] bands = new int[0];
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
        {
            bands = cellIdentity.getBands();
        }

        CharSequence provider = null;
        if (!carrierName.isEmpty())
        {
            provider = carrierName;
        } else
        {
            provider = cellIdentity.getOperatorAlphaLong();
        }

        CellSignalStrengthNr cellSignalStrength = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();
        final int csiRsrp = cellSignalStrength.getCsiRsrp();
        final int csiRsrq = cellSignalStrength.getCsiRsrq();
        final int csiSinr = cellSignalStrength.getCsiSinr();
        final int ssRsrp = cellSignalStrength.getSsRsrp();
        final int ssRsrq = cellSignalStrength.getSsRsrq();
        final int ssSinr = cellSignalStrength.getSsSinr();

        int timingAdvanceMicros = CellInfo.UNAVAILABLE;
        if (android.os.Build.VERSION.SDK_INT >= 34)
        {
            timingAdvanceMicros = cellSignalStrength.getTimingAdvanceMicros();
        }

        if (!validateNrFields(nrarfcn, pci)) return null;

        final NrRecordData.Builder dataBuilder = NrRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoNr.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        // vals from CellIdentity
        if (mcc != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        }
        if (mnc != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        }
        if (tac != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setTac(Int32Value.newBuilder().setValue(tac).build());
        }
        if (nci != CellInfo.UNAVAILABLE_LONG)
        {
            dataBuilder.setNci(Int64Value.newBuilder().setValue(nci).build());
        }
        if (nrarfcn != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setNarfcn(Int32Value.newBuilder().setValue(nrarfcn).build());
        }
        if (pci != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setPci(Int32Value.newBuilder().setValue(pci).build());
        }

        // vals from CellSignalStrength
        if (ssRsrp != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setSsRsrp(FloatValue.newBuilder().setValue(ssRsrp).build());
        }
        if (ssRsrq != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setSsRsrq(FloatValue.newBuilder().setValue(ssRsrq).build());
        }
        if (ssSinr != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setSsSinr(FloatValue.newBuilder().setValue(ssSinr).build());
        }
        if (csiRsrp != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setCsiRsrp(FloatValue.newBuilder().setValue(csiRsrp).build());
        }
        if (csiRsrq != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setCsiRsrq(FloatValue.newBuilder().setValue(csiRsrq).build());
        }
        if (csiSinr != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setCsiSinr(FloatValue.newBuilder().setValue(csiSinr).build());
        }
        if (timingAdvanceMicros != CellInfo.UNAVAILABLE)
        {
            dataBuilder.setTa(Int32Value.newBuilder().setValue(timingAdvanceMicros).build());
        }
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subscriptionId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
        {
            dataBuilder.setSlot(Int32Value.newBuilder().setValue(subscriptionId).build());
        }

        final NrRecord.Builder recordBuilder = NrRecord.newBuilder();

        recordBuilder.setMessageType(NrMessageConstants.NR_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return new NrRecordWrapper(recordBuilder.build(), bands);
    }

    /**
     * Pull out the appropriate values from the {@link ScanResult}, and create a {@link WifiBeaconRecord}.
     *
     * @param apScanResult The scan result to pull the Wi-Fi data from.
     * @return The Wi-Fi record to send to any listeners.
     * @since 0.1.2
     */
    private WifiRecordWrapper generateWiFiBeaconSurveyRecord(ScanResult apScanResult, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        final String bssid = apScanResult.BSSID;
        final int signalStrength = apScanResult.level;

        // Validate that the required fields are present before proceeding further
        if (!validateWifiBeaconFields(bssid, signalStrength)) return null;

        final WifiBeaconRecordData.Builder dataBuilder = WifiBeaconRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(wifiRecordNumber++);

        dataBuilder.setBssid(bssid);
        dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());

        final String ssid = apScanResult.SSID;
        if (ssid != null) dataBuilder.setSsid(ssid);

        final short channel = WifiBeaconMessageConstants.convertFrequencyToChannelNumber(apScanResult.frequency);
        if (channel != -1)
        {
            dataBuilder.setChannel(Int32Value.newBuilder().setValue(channel).build());
        }

        final int frequency = apScanResult.frequency;
        if (frequency != -1 && frequency != 0)
        {
            dataBuilder.setFrequencyMhz(Int32Value.newBuilder().setValue(frequency).build());
        }

        final String capabilities = apScanResult.capabilities;
        if (capabilities != null && !capabilities.isEmpty())
        {
            final EncryptionType encryptionType = WifiUtils.getEncryptionType(capabilities);
            if (encryptionType != EncryptionType.UNKNOWN)
            {
                dataBuilder.setEncryptionType(encryptionType);
            }

            dataBuilder.setWps(BoolValue.newBuilder().setValue(WifiUtils.supportsWps(capabilities)).build());
        }

        if (apScanResult.isPasspointNetwork())
        {
            dataBuilder.setPasspoint(BoolValue.newBuilder().setValue(true).build());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            setWifiStandard(dataBuilder, apScanResult.getWifiStandard());
        }

        setWifiBandwidth(dataBuilder, apScanResult.channelWidth);

        final WifiBeaconRecord.Builder recordBuilder = WifiBeaconRecord.newBuilder();
        recordBuilder.setMessageType(WifiBeaconMessageConstants.WIFI_BEACON_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return new WifiRecordWrapper(recordBuilder.build(), apScanResult.capabilities);
    }

    /**
     * Pull out the appropriate values from the {@link android.bluetooth.le.ScanResult}, and create a {@link BluetoothRecord}.
     *
     * @param result The scan result to pull the Bluetooth data from.
     * @return The Bluetooth record to send to any listeners.
     * @since 1.0.0
     */
    private BluetoothRecord generateBluetoothSurveyRecord(android.bluetooth.le.ScanResult result, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        return generateBluetoothSurveyRecord(result, result.getDevice(), result.getRssi(), result.getTxPower(), deviceTime, elapsedTimeMillis);
    }

    /**
     * Pull out the appropriate values, and create a {@link BluetoothRecord}.
     *
     * @param scanResult Will be null for classic scan results, but will be present in the new approach.
     * @return The Bluetooth record to send to any listeners.
     * @since 1.0.0
     */
    private BluetoothRecord generateBluetoothSurveyRecord(android.bluetooth.le.ScanResult scanResult, BluetoothDevice device, int rssi, int txPowerLevel, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        final String sourceAddress = device.getAddress();

        // Validate that the required fields are present before proceeding further
        if (!validateBluetoothFields(sourceAddress)) return null;

        final BluetoothRecordData.Builder dataBuilder = BluetoothRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(bluetoothRecordNumber++);

        dataBuilder.setSourceAddress(sourceAddress);

        if (rssi != UNSET_RSSI)
        {
            dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(rssi).build());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        {
            dataBuilder.setAddressType(BluetoothMessageConstants.mapOsAddressTypeToProto(device.getAddressType()));
        }

        // The TX Power seems to never be set (a value of 127 indicates unset). However, I am including
        // the code here in case it starts being populated in a future version of Android, or if a specific phone model
        // reports it.
        if (txPowerLevel != UNSET_TX_POWER_LEVEL)
        {
            dataBuilder.setTxPower(FloatValue.newBuilder().setValue(txPowerLevel).build());
        }

        final ScanRecord scanRecord = scanResult != null ? scanResult.getScanRecord() : null;
        String scanRecordDeviceName = scanRecord != null ? scanRecord.getDeviceName() : "";

        String otaDeviceName = "";
        if (!Strings.isNullOrEmpty(scanRecordDeviceName))
        {
            otaDeviceName = scanRecordDeviceName;
        } else
        {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            {
                otaDeviceName = device.getName();
            } else
            {
                Timber.e("Unable to get the device name, missing BLUETOOTH_CONNECT permission");
            }
        }
        if (otaDeviceName != null) dataBuilder.setOtaDeviceName(otaDeviceName);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        {
            final SupportedTechnologies supportedTech = BluetoothMessageConstants.getSupportedTechnologies(device.getType());
            if (supportedTech != null && supportedTech != SupportedTechnologies.UNKNOWN)
            {
                dataBuilder.setSupportedTechnologies(supportedTech);
            }
        }

        BluetoothClass bluetoothClass = device.getBluetoothClass();
        if (bluetoothClass != null)
        {
            final int deviceClass = bluetoothClass.getDeviceClass();

            // First use the device class, and if it is -1, then use the major device class when setting it on the data builder
            int deviceClassToUse = (deviceClass == 0 || deviceClass == BluetoothClass.Device.Major.UNCATEGORIZED)
                    ? bluetoothClass.getMajorDeviceClass()
                    : deviceClass;
            if (deviceClassToUse != 0 && deviceClassToUse != BluetoothClass.Device.Major.UNCATEGORIZED)
            {
                dataBuilder.setDeviceClass(Integer.toHexString(deviceClassToUse));
            }
        }

        if (scanRecord != null)
        {
            //Timber.i("Bluetooth Advertise Flags: %s", Integer.toHexString(scanRecord.getAdvertiseFlags()));

            List<String> uuid16Services = null;
            List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
            if (null != serviceUuids && !serviceUuids.isEmpty())
            {
                uuid16Services = new ArrayList<>();
                for (ParcelUuid u : serviceUuids)
                {
                    uuid16Services.add(u.getUuid().toString());
                }
            }
            if (uuid16Services != null) dataBuilder.addAllServiceUuids(uuid16Services);

            Integer companyId = null;
            if (null == uuid16Services && scanRecord.getManufacturerSpecificData() != null)
            {
                SparseArray<byte[]> bytesArray = scanRecord.getManufacturerSpecificData();
                for (int i = 0; i < bytesArray.size(); i++)
                {
                    companyId = bytesArray.keyAt(i);
                    //Timber.d("index=%s , companyId=%s", i, Integer.toHexString(companyId));
                }
            }
            if (companyId != null) dataBuilder.setCompanyId(Integer.toHexString(companyId));
        }

        final BluetoothRecord.Builder recordBuilder = BluetoothRecord.newBuilder();
        recordBuilder.setMessageType(BluetoothMessageConstants.BLUETOOTH_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Pull out the appropriate values from the {@link GnssMeasurement}, and create a {@link GnssRecord}.
     *
     * @param gnss   The GNSS measurement object to pull the data from.
     * @param agcMap The map of AGC values keyed by the constellation and frequency.
     * @return The GNSS record to send to any listeners.
     * @since 0.3.0
     */
    private GnssRecord generateGnssSurveyRecord(GnssMeasurement gnss, Map<ConstellationFreqKey, Float> agcMap, ZonedDateTime deviceTime, long elapsedTimeMillis)
    {
        final GnssRecordData.Builder dataBuilder = GnssRecordData.newBuilder();

        if (gpsListener != null)
        {
            final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (lastKnownLocation.hasAccuracy())
                {
                    final FloatValue.Builder accuracy = FloatValue.newBuilder().setValue(lastKnownLocation.getAccuracy());
                    dataBuilder.setLatitudeStdDevM(accuracy);
                    dataBuilder.setLongitudeStdDevM(accuracy);
                }

                if (lastKnownLocation.hasVerticalAccuracy())
                {
                    dataBuilder.setAltitudeStdDevM(FloatValue.newBuilder()
                            .setValue(lastKnownLocation.getVerticalAccuracyMeters()));
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapsedTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(gnssRecordNumber++);
        dataBuilder.setGroupNumber(gnssGroupNumber);
        dataBuilder.setDeviceModel(Build.MODEL);

        final Constellation constellation = GnssMessageConstants.getProtobufConstellation(gnss.getConstellationType());
        if (constellation != Constellation.UNKNOWN) dataBuilder.setConstellation(constellation);

        dataBuilder.setSpaceVehicleId(UInt32Value.newBuilder().setValue(gnss.getSvid()));

        if (gnss.hasCarrierFrequencyHz())
        {
            dataBuilder.setCarrierFreqHz(UInt64Value.newBuilder().setValue((long) gnss.getCarrierFrequencyHz()));
        }

        if (gnss.hasAutomaticGainControlLevelDb())
        {
            dataBuilder.setAgcDb(FloatValue.newBuilder().setValue((float) gnss.getAutomaticGainControlLevelDb()));
        } else
        {
            Float agc = agcMap.get(new ConstellationFreqKey(gnss.getConstellationType(), (long) gnss.getCarrierFrequencyHz()));
            if (agc != null)
            {
                dataBuilder.setAgcDb(FloatValue.newBuilder().setValue(agc));
            }
        }

        dataBuilder.setCn0DbHz(FloatValue.newBuilder().setValue((float) gnss.getCn0DbHz()));

        final GnssRecord.Builder recordBuilder = GnssRecord.newBuilder();
        recordBuilder.setMessageType(GnssMessageConstants.GNSS_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Pull out the appropriate values from the cached location, and create a {@link GnssRecord}.
     *
     * @return The empty GNSS record to send to any listeners.
     * @since 1.8.0
     */
    private GnssRecord generateEmptyGnssSurveyRecord()
    {
        final GnssRecordData.Builder dataBuilder = GnssRecordData.newBuilder();
        final ZonedDateTime deviceTime = ZonedDateTime.now();
        final long elapseTimeMillis = SystemClock.elapsedRealtime();

        if (gpsListener != null)
        {
            final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
                dataBuilder.setAccuracy(MathUtils.roundAccuracy(lastKnownLocation.getAccuracy()));

                if (lastKnownLocation.hasAccuracy())
                {
                    final FloatValue.Builder accuracy = FloatValue.newBuilder().setValue(lastKnownLocation.getAccuracy());
                    dataBuilder.setLatitudeStdDevM(accuracy);
                    dataBuilder.setLongitudeStdDevM(accuracy);
                }

                if (lastKnownLocation.hasVerticalAccuracy())
                {
                    dataBuilder.setAltitudeStdDevM(FloatValue.newBuilder()
                            .setValue(lastKnownLocation.getVerticalAccuracyMeters()));
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                {
                    long elapsedRealtimeAgeMillis = lastKnownLocation.getElapsedRealtimeAgeMillis(elapseTimeMillis);
                    dataBuilder.setLocationAge((int) elapsedRealtimeAgeMillis);
                }

                if (lastKnownLocation.hasSpeed())
                {
                    float speed = FormatUtils.formatSpeed(lastKnownLocation.getSpeed());
                    if (speed != 0f)
                    {
                        dataBuilder.setSpeed(speed);
                    }
                }
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(NsUtils.getRfc3339String(deviceTime));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(gnssRecordNumber++);
        dataBuilder.setGroupNumber(gnssGroupNumber);
        dataBuilder.setDeviceModel(Build.MODEL);

        final GnssRecord.Builder recordBuilder = GnssRecord.newBuilder();
        recordBuilder.setMessageType(GnssMessageConstants.GNSS_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Sets the LTE bandwidth on the record if it is valid, and if the current android version supports it.
     *
     * @param lteRecordBuilder The builder to set the bandwidth on.
     * @param cellIdentity     The {@link CellIdentityLte} to pull the bandwidth from.
     */
    private void setBandwidth(LteRecordData.Builder lteRecordBuilder, CellIdentityLte cellIdentity)
    {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            final int bandwidth = cellIdentity.getBandwidth();
            if (bandwidth != Integer.MAX_VALUE)
            {
                LteBandwidth lteBandwidth = switch (bandwidth)
                {
                    case 1_400 -> LteBandwidth.MHZ_1_4;
                    case 3_000 -> LteBandwidth.MHZ_3;
                    case 5_000 -> LteBandwidth.MHZ_5;
                    case 10_000 -> LteBandwidth.MHZ_10;
                    case 15_000 -> LteBandwidth.MHZ_15;
                    case 20_000 -> LteBandwidth.MHZ_20;
                    default -> null;
                };

                if (lteBandwidth != null) lteRecordBuilder.setLteBandwidth(lteBandwidth);
            }
        }
    }

    /**
     * Sets the Wi-Fi standard on the record if it is valid.
     *
     * @noinspection DuplicateBranchesInSwitch
     */
    private void setWifiStandard(WifiBeaconRecordData.Builder wifiBeaconBuilder, int androidWifiStandard)
    {
        Standard wifiStandard = switch (androidWifiStandard)
        {
            case ScanResult.WIFI_STANDARD_UNKNOWN, ScanResult.WIFI_STANDARD_LEGACY,
                 ScanResult.WIFI_STANDARD_11AD -> Standard.UNKNOWN;
            case ScanResult.WIFI_STANDARD_11N -> Standard.IEEE80211N;
            case ScanResult.WIFI_STANDARD_11AC -> Standard.IEEE80211AC;
            case ScanResult.WIFI_STANDARD_11AX -> Standard.IEEE80211AX;
            case ScanResult.WIFI_STANDARD_11BE -> Standard.IEEE80211BE;
            default -> Standard.UNKNOWN;
        };

        if (wifiStandard != Standard.UNKNOWN) wifiBeaconBuilder.setStandard(wifiStandard);
    }

    /**
     * Sets the Wi-Fi Bandwidth on the record.
     */
    private void setWifiBandwidth(WifiBeaconRecordData.Builder wifiBeaconBuilder, int androidWifiBandwidth)
    {
        WifiBandwidth wifiBandwidth = switch (androidWifiBandwidth)
        {
            case ScanResult.CHANNEL_WIDTH_20MHZ -> WifiBandwidth.MHZ_20;
            case ScanResult.CHANNEL_WIDTH_40MHZ -> WifiBandwidth.MHZ_40;
            case ScanResult.CHANNEL_WIDTH_80MHZ -> WifiBandwidth.MHZ_80;
            case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> WifiBandwidth.MHZ_80_PLUS;
            case ScanResult.CHANNEL_WIDTH_160MHZ -> WifiBandwidth.MHZ_160;
            case ScanResult.CHANNEL_WIDTH_320MHZ -> WifiBandwidth.MHZ_320;
            default -> WifiBandwidth.UNKNOWN;
        };

        if (wifiBandwidth != WifiBandwidth.UNKNOWN) wifiBeaconBuilder.setBandwidth(wifiBandwidth);
    }

    /**
     * @param telephonyManager The manager to use to get the voice network type.
     * @return The Current Network type for voice calls. This method checks the Android permissions
     * first, and returns {@link NetworkType#UNKNOWN} if the permission has not been granted.
     * @since 1.11
     */
    private NetworkType getVoiceNetworkType(TelephonyManager telephonyManager)
    {
        NetworkType networkType = NetworkType.UNKNOWN;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        {
            networkType = NetworkType.forNumber(telephonyManager.getVoiceNetworkType());
        }
        return networkType;
    }

    /**
     * Extracts the current cell information from the service state and sets it on the provided
     * CDR event.
     */
    private void setCellInfo(CdrEvent cdrEvent, TelephonyManager telephonyManager)
    {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.i("Don't have the required permissions for getting the current cell information for the CDR event");
            return;
        }

        setCellInfo(cdrEvent, telephonyManager.getServiceState());
    }

    private void setCellInfo(CdrEvent cdrEvent, ServiceState serviceState)
    {
        if (serviceState == null) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        serviceState.getNetworkRegistrationInfoList().forEach(info -> {
            final Domain domainEnum = DeviceStatusMessageConstants.convertDomain(info.getDomain());
            final NetworkType networkType = NetworkType.forNumber(info.getAccessNetworkTechnology());
            switch (domainEnum)
            {
                case PS:
                    // Ignore Wi-FI Packet Switched info
                    if (networkType == NetworkType.IWLAN)
                    {
                        return;
                    }
                    cdrEvent.setPacketSwitchedInformation(networkType, getCellIdentifier(info));
                    break;

                case CS:
                    cdrEvent.setCircuitSwitchedInformation(networkType, getCellIdentifier(info));
                    break;

                default:
                    Timber.i("Unhandled domain for the current service state domain=%s", info.getDomain());
            }
        });
    }

    /**
     * Pulls the cell identifying information from the provide info object and concatenates it into
     * a single string.
     *
     * @return The cell identity information in a string.
     */
    private String getCellIdentifier(android.telephony.NetworkRegistrationInfo info)
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        {
            return "";
        }

        CellIdentity cellIdentity = info.getCellIdentity();
        if (cellIdentity instanceof CellIdentityNr nr)
        {
            return nr.getMccString() + "-" + nr.getMncString() + "-" + nr.getTac() + "-" + nr.getNci();
        } else if (cellIdentity instanceof CellIdentityLte lte)
        {
            return lte.getMccString() + "-" + lte.getMncString() + "-" + lte.getTac() + "-" + lte.getCi();
        } else if (cellIdentity instanceof CellIdentityWcdma wcdma)
        {
            return wcdma.getMccString() + "-" + wcdma.getMncString() + "-" + wcdma.getLac() + "-" + wcdma.getCid();
        } else if (cellIdentity instanceof CellIdentityCdma cdma)
        {
            return cdma.getSystemId() + "-" + cdma.getNetworkId() + "-" + cdma.getBasestationId();
        } else if (cellIdentity instanceof CellIdentityGsm gsm)
        {
            return gsm.getMccString() + "-" + gsm.getMncString() + "-" + gsm.getLac() + "-" + gsm.getCid();
        }

        return "";
    }

    /**
     * Validates the required fields.
     *
     * @return True if the provided fields are all valid, false if one or more is invalid.
     */
    private boolean validateGsmFields(int arfcn, int bsic, int signalStrength)
    {
        if (arfcn == Integer.MAX_VALUE || arfcn == -1)
        {
            Timber.v("The ARFCN is required to build a GSM Survey Record.");
            return false;
        }

        if (bsic == Integer.MAX_VALUE || bsic == -1)
        {
            Timber.v("The BSIC is required to build a GSM Survey Record.");
            return false;
        }

        if (signalStrength == Integer.MAX_VALUE)
        {
            Timber.v("The Signal Strength is required to build a GSM Survey Record.");
            return false;
        }

        return true;
    }

    /**
     * Validates the required CDMA fields.
     *
     * @return True if the provided fields are all valid, false if one or more is invalid.
     */
    private boolean validateCdmaFields(int signalStrength, int ecio)
    {
        if (signalStrength == Integer.MAX_VALUE)
        {
            Timber.v("The Signal Strength is required to build a CDMA Survey Record.");
            return false;
        }

        if (ecio == Integer.MAX_VALUE)
        {
            Timber.v("The Ec/Io is required to build a CDMA Survey Record.");
            return false;
        }

        return true;
    }

    /**
     * Validates the required fields.
     *
     * @return True if the provided fields are all valid, false if one or more is invalid.
     */
    private boolean validateUmtsFields(int uarfcn, int psc)
    {
        if (uarfcn == Integer.MAX_VALUE || uarfcn == -1)
        {
            Timber.v("The UARFCN is required to build a UMTS Survey Record.");
            return false;
        }

        if (psc == Integer.MAX_VALUE || psc == -1)
        {
            Timber.v("The PSC is required to build a UMTS Survey Record.");
            return false;
        }

        return true;
    }

    /**
     * Validates the required fields.
     *
     * @return True if the provided fields are all valid, false if one or more is invalid.
     */
    private boolean validateLteFields(int earfcn, int pci, int rsrp)
    {
        if (earfcn == Integer.MAX_VALUE || earfcn == -1)
        {
            Timber.v("The EARFCN is required to build an LTE Survey Record.");
            return false;
        }

        if (pci == Integer.MAX_VALUE || pci == -1)
        {
            Timber.v("The PCI is required to build an LTE Survey Record.");
            return false;
        }

        if (rsrp == Integer.MAX_VALUE)
        {
            Timber.v("The RSRP is required to build an LTE Survey Record.");
            return false;
        }

        return true;
    }

    /**
     * Validates the required arguments per:
     * <a href="https://messaging.networksurvey.app/#operation-publish-nr_message">NR Message Requirements</a>
     *
     * @return {@code true} if the provided fields are all valid, false if one or more is invalid.
     * @since 1.5.0
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private boolean validateNrFields(int nrarfcn, int pci)
    {
        if (nrarfcn == CellInfo.UNAVAILABLE)
        {
            Timber.v("NRARFCN is required to build an NR survey record");
            return false;
        }
        if (pci == CellInfo.UNAVAILABLE)
        {
            Timber.v("PCI is required to build an NR survey record");
            return false;
        }

        return true;
    }

    /**
     * Validates the required fields.
     *
     * @return True if the provided fields are all valid, false if one or more is invalid.
     * @since 0.1.2
     */
    private boolean validateWifiBeaconFields(String bssid, int signalStrength)
    {
        if (bssid == null || bssid.isEmpty())
        {
            Timber.v("The BSSID is required to build a Wi-Fi Beacon Survey Record.");
            return false;
        }

        if (signalStrength == Integer.MAX_VALUE)
        {
            Timber.v("The Signal Strength is required to build a Wi-Fi Beacon Survey Record.");
            return false;
        }

        return true;
    }

    /**
     * Validates the required fields.
     *
     * @return True if the provided fields are all valid, false if one or more is invalid.
     * @since 1.0.0
     */
    private boolean validateBluetoothFields(String sourceAddress)
    {
        if (sourceAddress == null || sourceAddress.isEmpty())
        {
            Timber.v("The Source Address is required to build a Bluetooth Survey Record.");
            return false;
        }

        return true;
    }

    /**
     * Notify all the listeners that we have a new GSM Record available.
     *
     * @param gsmRecord The new GSM Survey Record to send to the listeners.
     */
    private void notifyGsmRecordListeners(GsmRecord gsmRecord)
    {
        if (gsmRecord == null) return;
        for (ICellularSurveyRecordListener listener : cellularSurveyRecordListeners)
        {
            try
            {
                listener.onGsmSurveyRecord(gsmRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Cellular Survey Record Listener because of an exception");
            }
        }
    }

    /**
     * Notify all the listeners that we have a new CDMA Record available.
     *
     * @param cdmaRecord The new CDMA Survey Record to send to the listeners.
     */
    private void notifyCdmaRecordListeners(CdmaRecord cdmaRecord)
    {
        if (cdmaRecord == null) return;
        for (ICellularSurveyRecordListener listener : cellularSurveyRecordListeners)
        {
            try
            {
                listener.onCdmaSurveyRecord(cdmaRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Cellular Survey Record Listener because of an exception");
            }
        }
    }

    /**
     * Notify all the listeners that we have a new UMTS Record available.
     *
     * @param umtsRecord The new UMTS Survey Record to send to the listeners.
     */
    private void notifyUmtsRecordListeners(UmtsRecord umtsRecord)
    {
        if (umtsRecord == null) return;
        for (ICellularSurveyRecordListener listener : cellularSurveyRecordListeners)
        {
            try
            {
                listener.onUmtsSurveyRecord(umtsRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Cellular Survey Record Listener because of an exception");
            }
        }
    }

    /**
     * Notify all the listeners that we have a new LTE Record available.
     *
     * @param lteRecord The new LTE Survey Record to send to the listeners.
     */
    private void notifyLteRecordListeners(LteRecord lteRecord)
    {
        if (lteRecord == null) return;
        for (ICellularSurveyRecordListener listener : cellularSurveyRecordListeners)
        {
            try
            {
                listener.onLteSurveyRecord(lteRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Cellular Survey Record Listener because of an exception");
            }
        }
    }

    /**
     * Notify {@link #cellularSurveyRecordListeners} of a new NR record
     *
     * @param nrRecord The new NR Survey Record to send to the listeners
     * @since 1.5.0
     */
    private void notifyNrRecordListeners(NrRecord nrRecord)
    {
        if (nrRecord == null) return;

        cellularSurveyRecordListeners.forEach(l -> {
            try
            {
                l.onNrSurveyRecord(nrRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Cellular Survey Record Listener because of an exception");
            }
        });
    }

    /**
     * Notifies all the listeners of a new batch of cellular records. This batch represents a single scan of the
     * towers this device can see. It can contain multiple technologies (e.g. NR and LTE), which is why it is a list of
     * generic messages and not a specific cellular protocol message.
     *
     * @param cellularRecords The batch of cellular records.
     * @param subscriptionId  The subscription ID (aka SIM ID) that the records are associated with.
     * @since 1.6.0
     */
    private void notifyCellularListeners(List<CellularRecordWrapper> cellularRecords, int subscriptionId)
    {
        cellularSurveyRecordListeners.forEach(l -> {
            try
            {
                l.onCellularBatch(cellularRecords, subscriptionId);
            } catch (Throwable t)
            {
                Timber.e(t, "Unable to notify a Cellular Survey Record Listener because of an exception");
            }
        });

        // Synchronized because the user can turn off the DB sink via the UI, which would set it to null
        synchronized (this)
        {
            if (uploadDbSink != null)
            {
                uploadDbSink.onCellularBatch(cellularRecords, subscriptionId);
            }
        }
    }

    /**
     * Notify {@link #cellularSurveyRecordListeners} of a the current data and voice network types.
     *
     * @param dataNetworkType  The data network type (e.g. "LTE"), which might be different than the voice network type.
     * @param voiceNetworkType The voice network type (e.g. "LTE").
     * @param subscriptionId   The subscription ID (aka SIM ID) that the records are associated with.
     * @since 1.6.0
     */
    private void notifyNetworkTypeListeners(String dataNetworkType, String voiceNetworkType,
                                            int subscriptionId, String overrideNetworkType)
    {
        cellularSurveyRecordListeners.forEach(l -> {
            try
            {
                l.onNetworkType(dataNetworkType, voiceNetworkType, subscriptionId, overrideNetworkType);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Cellular Survey Record Listener because of an exception");
            }
        });
    }

    /**
     * Notify all the listeners that we have a new group of 802.11 Beacon Records available.
     *
     * @param wifiBeaconRecords The new list 802.11 Beacon Survey Records to send to the listeners.
     * @since 0.1.2
     */
    private void notifyWifiBeaconRecordListeners(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        if (wifiBeaconRecords == null || wifiBeaconRecords.isEmpty()) return;

        for (IWifiSurveyRecordListener listener : wifiSurveyRecordListeners)
        {
            try
            {
                listener.onWifiBeaconSurveyRecords(wifiBeaconRecords);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Wi-Fi Survey Record Listener because of an exception");
            }
        }

        // Synchronized because the user can turn off the DB sink via the UI, which would set it to null
        synchronized (this)
        {
            if (uploadDbSink != null)
            {
                uploadDbSink.onWifiBeaconSurveyRecords(wifiBeaconRecords);
            }
        }
    }

    /**
     * Notify all the listeners that we have a new single Bluetooth Record available.
     *
     * @param bluetoothRecord The new Bluetooth Survey Record to send to the listeners.
     * @since 1.0.0
     */
    private void notifyBluetoothRecordListeners(BluetoothRecord bluetoothRecord)
    {
        if (bluetoothRecord == null) return;

        for (IBluetoothSurveyRecordListener listener : bluetoothSurveyRecordListeners)
        {
            try
            {
                listener.onBluetoothSurveyRecord(bluetoothRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Bluetooth Survey Record Listener because of an exception");
            }
        }
    }

    /**
     * Notify all the listeners that we have a new group of Bluetooth Records available.
     *
     * @param bluetoothRecords The new list Bluetooth Survey Records to send to the listeners.
     * @since 1.0.0
     */
    private void notifyBluetoothRecordListeners(List<BluetoothRecord> bluetoothRecords)
    {
        if (bluetoothRecords == null || bluetoothRecords.isEmpty()) return;

        for (IBluetoothSurveyRecordListener listener : bluetoothSurveyRecordListeners)
        {
            try
            {
                listener.onBluetoothSurveyRecords(bluetoothRecords);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Bluetooth Survey Record Listener because of an exception");
            }
        }
    }

    /**
     * Notify all the listeners that we have a new GNSS Record available.
     *
     * @param gnssRecord The new GNSS Survey Record to send to the listeners.
     * @since 0.3.0
     */
    private void notifyGnssRecordListeners(GnssRecord gnssRecord)
    {
        if (gnssRecord == null) return;
        for (IGnssSurveyRecordListener listener : gnssSurveyRecordListeners)
        {
            try
            {
                listener.onGnssSurveyRecord(gnssRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a GNSS Survey Record Listener because of an exception");
            }
        }
    }

    private void finishCdrEvent(CdrEvent cdrEvent)
    {
        if (cdrEvent == null) return;

        setLocationAndNotifyListeners(cdrEvent, context);
    }

    /**
     * Notify all the listeners that we have a new CDR event available.
     *
     * @param cdrEvent The new CDR event to send to the listeners.
     */
    private void notifyCdrListeners(CdrEvent cdrEvent)
    {
        for (ICdrEventListener listener : cdrListeners)
        {
            try
            {
                listener.onCdrEvent(cdrEvent);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a CDR Event Listener because of an exception");
            }
        }
    }

    /**
     * Tries to get the current location and set it on the CDR event. If the current location is not ready, then an
     * async task is created and we try to wait for that to complete before sending the CDR event to listeners. If
     * the task does not finish in {@link #MAX_CDR_LOCATION_WAIT_TIME}, then we send the CDR event to listeners without
     * a location.
     * <p>
     * There is an edge case here where a user could turn off CDR logging while we are waiting for a location. If that
     * happens the CSV logging listener will be removed, so the CDR event would never be notified of the event. This
     * is why I am trying to keep the max wait time low.
     */
    private void setLocationAndNotifyListeners(CdrEvent cdrEvent, Context context)
    {
        // If the regular listener has a location that is not too old, then use that and don't worry about requesting one
        Location listenerLocation = gpsListener.getLatestLocation();
        if (listenerLocation != null)
        {
            long ageMs = SystemClock.elapsedRealtime() - NANOSECONDS.toMillis(listenerLocation.getElapsedRealtimeNanos());
            if (ageMs < 30_000)
            {
                cdrEvent.setLocation(listenerLocation);
                notifyCdrListeners(cdrEvent);
                return;
            }
        }

        // We could not get an existing location, request a new one
        final CancellationSignal cancellationSignal = new CancellationSignal();

        // Schedule a timer task that will cancel the location task if the location is not returned in `n` seconds
        final Timer cancellationTimer = new Timer();
        cancellationTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                notifyCdrListeners(cdrEvent);
                Timber.i("Cancelling the CDR location request because the timeout of %d ms was reached", MAX_CDR_LOCATION_WAIT_TIME);
                cancellationSignal.cancel();
            }
        }, MAX_CDR_LOCATION_WAIT_TIME);

        Consumer<Location> locationConsumer = location -> {
            cancellationTimer.cancel();
            cdrEvent.setLocation(location);
            notifyCdrListeners(cdrEvent);
        };

        requestLocation(context, cancellationSignal, executorService, locationConsumer);
    }

    /**
     * Ask the Android API for this device's location as long as we have the right permissions.
     *
     * @param context            The context to use to check permissions.
     * @param cancellationSignal The cancellation signal that can be used to cancel the location request.
     * @param consumer           The consumer that will be passed the location once it is available.
     */
    private static void requestLocation(Context context,
                                        CancellationSignal cancellationSignal,
                                        ExecutorService executorService,
                                        Consumer<Location> consumer)
    {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Timber.i("Location permissions not granted so the report's sent location could not be populated");
            return;
        }

        final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null)
        {
            final String provider = LocationUtils.getLocationProvider(locationManager);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            {
                locationManager.getCurrentLocation(provider, cancellationSignal, executorService, consumer);
            } else
            {
                consumer.accept(locationManager.getLastKnownLocation(provider));
            }
        }
    }

    /**
     * Notify all the listeners that we have a new Device Status available.
     *
     * @param deviceStatus The new Device Status Message to send to the listeners.
     * @since 1.1.0
     */
    private void notifyDeviceStatusListeners(DeviceStatus deviceStatus)
    {
        if (deviceStatus == null) return;
        for (IDeviceStatusListener listener : deviceStatusListeners)
        {
            try
            {
                listener.onDeviceStatus(deviceStatus);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Device Status Listener because of an exception");
            }
        }
    }

    /**
     * Notify all the listeners that we have a new Phone State available.
     *
     * @param phoneState The new Phone State Message to send to the listeners.
     * @since 1.1.0
     */
    private void notifyPhoneStateListeners(PhoneState phoneState)
    {
        if (phoneState == null) return;
        for (IDeviceStatusListener listener : deviceStatusListeners)
        {
            try
            {
                listener.onPhoneState(phoneState);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Phone State Listener because of an exception");
            }
        }
    }
}
