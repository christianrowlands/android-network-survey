package com.craxiom.networksurvey.services;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.Build;
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
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

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
import com.craxiom.messaging.phonestate.SimState;
import com.craxiom.messaging.wifi.EncryptionType;
import com.craxiom.networksurvey.BuildConfig;
import com.craxiom.networksurvey.CalculationUtils;
import com.craxiom.networksurvey.GpsListener;
import com.craxiom.networksurvey.NetworkSurveyActivity;
import com.craxiom.networksurvey.R;
import com.craxiom.networksurvey.constants.BluetoothMessageConstants;
import com.craxiom.networksurvey.constants.CdmaMessageConstants;
import com.craxiom.networksurvey.constants.DeviceStatusMessageConstants;
import com.craxiom.networksurvey.constants.GnssMessageConstants;
import com.craxiom.networksurvey.constants.GsmMessageConstants;
import com.craxiom.networksurvey.constants.LteMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.UmtsMessageConstants;
import com.craxiom.networksurvey.constants.WifiBeaconMessageConstants;
import com.craxiom.networksurvey.fragments.NetworkDetailsFragment;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.util.IOUtils;
import com.craxiom.networksurvey.util.ParserUtils;
import com.craxiom.networksurvey.util.PreferenceUtils;
import com.craxiom.networksurvey.util.WifiCapabilitiesUtils;
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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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

    private final Object cellInfoProcessingLock = new Object();
    private final Object activityUpdateLock = new Object();

    private final GpsListener gpsListener;
    private final Set<ICellularSurveyRecordListener> cellularSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<IWifiSurveyRecordListener> wifiSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<IBluetoothSurveyRecordListener> bluetoothSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<IGnssSurveyRecordListener> gnssSurveyRecordListeners = new CopyOnWriteArraySet<>();
    private final Set<IDeviceStatusListener> deviceStatusListeners = new CopyOnWriteArraySet<>();
    private volatile NetworkSurveyActivity networkSurveyActivity;

    private final ExecutorService executorService;
    private final String deviceId;
    private final String missionId;

    private int recordNumber = 1;
    private int groupNumber = 0; // This will be incremented to 1 the first time it is used.

    private int wifiRecordNumber = 1;
    private int bluetoothRecordNumber = 1;

    private int gnssRecordNumber = 1;
    private int gnssGroupNumber = 0; // This will be incremented to 1 the first time it is used.

    private int phoneStateRecordNumber = 1;

    private long lastGnssLogTimeMs;
    private int gnssScanRateMs;

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
     */
    boolean isBeingUsed()
    {
        return networkSurveyActivity != null
                || !cellularSurveyRecordListeners.isEmpty()
                || !wifiSurveyRecordListeners.isEmpty()
                || !bluetoothSurveyRecordListeners.isEmpty()
                || !gnssSurveyRecordListeners.isEmpty()
                || !deviceStatusListeners.isEmpty();
    }

    /**
     * @return True if there are any registered Cellular survey record listeners, false otherwise.
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
     * @return True if there are any registered Device Status message listeners, false otherwise.
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
     * @param allCellInfo The List of {@link CellInfo} records to convert to survey records.
     */
    void onCellInfoUpdate(List<CellInfo> allCellInfo, String currentTechnology) throws SecurityException
    {
        // synchronized to make sure that we are only processing one list of Cell Info objects at a time.
        synchronized (cellInfoProcessingLock)
        {
            try
            {
                /* Timber.v("currentTechnology=%s", currentTechnology);
                Timber.v("allCellInfo: ");
                allCellInfo.forEach(cellInfo -> Timber.v(cellInfo.toString()));*/
                updateCurrentTechnologyUi(currentTechnology);

                if (allCellInfo != null && !allCellInfo.isEmpty())
                {
                    groupNumber++; // Group all the records found in this scan iteration.

                    allCellInfo.forEach(this::processCellInfo);
                } else
                {
                    updateUi(LteRecord.getDefaultInstance().getData());
                }
            } catch (Exception e)
            {
                Timber.e(e, "Unable to display and log an LTE Survey Record");
                updateUi(LteRecord.getDefaultInstance().getData());
            }
        }
    }

    /**
     * Notification for when a new set of Wi-Fi scan results are available to process.
     *
     * @param apScanResults The list of results coming from the Android wifi scanning API.
     * @since 0.1.2
     */
    void onWifiScanUpdate(List<ScanResult> apScanResults)
    {
        /*Timber.v("SCAN RESULTS:");
        apScanResults.forEach(scanResult -> Timber.v(scanResult.toString()));
        Timber.v("");*/

        execute(() -> processAccessPoints(apScanResults));
    }

    /**
     * Notification for when a new single Bluetooth Clasic scan result is available to process.
     *
     * @param device The Bluetooth device object associated with the scan.
     * @param rssi   The RSSI value associated with the scan.
     * @since 1.0.0
     */
    void onBluetoothClassicScanUpdate(BluetoothDevice device, int rssi)
    {
        execute(() -> processBluetoothClassicResult(device, rssi));
    }

    /**
     * Notification for when a new single Bluetooth scan result is available to process.
     *
     * @param result A single Bluetooth scan result coming from the Android Bluetooth scanning API.
     * @since 1.0.0
     */
    void onBluetoothScanUpdate(android.bluetooth.le.ScanResult result)
    {
        execute(() -> processBluetoothResult(result));
    }

    /**
     * Notification for when a new set of Bluetooth scan results are available to process.
     *
     * @param results The list of results coming from the Android Bluetooth scanning API.
     * @since 1.0.0
     */
    void onBluetoothScanUpdate(List<android.bluetooth.le.ScanResult> results)
    {
        /*Timber.v("SCAN RESULTS:");
        results.forEach(scanResult -> Timber.v(scanResult.toString()));
        Timber.v("");*/

        execute(() -> processBluetoothResults(results));
    }

    /**
     * Notification for when the latest set of GNSS measurements are available to process.
     *
     * @param event The latest set of GNSS measurements.
     * @since 0.3.0
     */
    void onGnssMeasurements(GnssMeasurementsEvent event)
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
    @RequiresApi(api = Build.VERSION_CODES.R)
    void onServiceStateChanged(ServiceState serviceState, TelephonyManager telephonyManager)
    {
        notifyPhoneStateListeners(createPhoneStateMessage(telephonyManager,
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
                              int causeCode, int additionalCauseCode, TelephonyManager telephonyManager)
    {
        notifyPhoneStateListeners(createPhoneStateMessage(telephonyManager,
                builder -> builder.addNetworkRegistrationInfo(ParserUtils.convertNetworkInfo(cellIdentity, domain, causeCode))));
    }

    private PhoneState createPhoneStateMessage(TelephonyManager telephonyManager, Consumer<PhoneStateData.Builder> networkRegistrationInfoFunction)
    {
        final PhoneStateData.Builder dataBuilder = PhoneStateData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));

        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(phoneStateRecordNumber++);

        dataBuilder.setSimState(SimState.forNumber(telephonyManager.getSimState()));
        dataBuilder.setSimOperator(telephonyManager.getSimOperator());

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
     * @param cellInfo The Cell Info object with the details.
     * @since 0.0.5
     */
    private void processCellInfo(CellInfo cellInfo)
    {
        final boolean isServingCell = cellInfo.isRegistered();
        final boolean isLteRecord = cellInfo instanceof CellInfoLte;

        // We only want to take the time to process a record if we are going to do something with it.  Currently, that
        // means logging, sending to a server, or updating the UI with the latest LTE information.
        if (!cellularSurveyRecordListeners.isEmpty() || (isServingCell && isLteRecord && NetworkDetailsFragment.visible.get()))
        {
            if (isLteRecord)
            {
                final LteRecord lteSurveyRecord = generateLteSurveyRecord((CellInfoLte) cellInfo);
                if (lteSurveyRecord == null)
                {
                    Timber.w("Could not generate a Server LteRecord from the CellInfoLte");
                    if (isServingCell) updateUi(LteRecord.getDefaultInstance().getData());
                    return;
                }

                if (isServingCell) updateUi(lteSurveyRecord.getData());
                notifyLteRecordListeners(lteSurveyRecord);
            } else if (cellInfo instanceof CellInfoGsm)
            {
                final GsmRecord gsmRecord = generateGsmSurveyRecord((CellInfoGsm) cellInfo);
                if (gsmRecord != null) notifyGsmRecordListeners(gsmRecord);
            } else if (cellInfo instanceof CellInfoCdma)
            {
                final CdmaRecord cdmaRecord = generateCdmaSurveyRecord((CellInfoCdma) cellInfo);
                if (cdmaRecord != null) notifyCdmaRecordListeners(cdmaRecord);
            } else if (cellInfo instanceof CellInfoWcdma)
            {
                final UmtsRecord umtsRecord = generateUmtsSurveyRecord((CellInfoWcdma) cellInfo);
                if (umtsRecord != null) notifyUmtsRecordListeners(umtsRecord);
            } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo instanceof CellInfoNr)
            {
                // TODO: 8/20/2021 Do we want to update UI as well for 5G?
                final NrRecord nrRecord = generateNrSurveyRecord((CellInfoNr) cellInfo);
                if (nrRecord != null) notifyNrRecordListeners(nrRecord);
            }

        }
    }

    /**
     * Given a group of 802.11 scan results, create the protobuf objects from it and notify any listeners.
     *
     * @param apScanResults The list of Scan Results.
     * @since 0.1.2
     */
    private void processAccessPoints(List<ScanResult> apScanResults)
    {
        final List<WifiRecordWrapper> wifiBeaconRecords = apScanResults.stream()
                .map(this::generateWiFiBeaconSurveyRecord)
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
        notifyBluetoothRecordListeners(generateBluetoothSurveyRecord(device, rssi, UNSET_TX_POWER_LEVEL));
    }

    /**
     * Given a single Bluetooth scan result, create the protobuf object from it and notify any listeners.
     *
     * @param result The Scan Results.
     * @since 1.0.0
     */
    private void processBluetoothResult(android.bluetooth.le.ScanResult result)
    {
        notifyBluetoothRecordListeners(generateBluetoothSurveyRecord(result));
    }

    /**
     * Given a group of Bluetooth scan results, create the protobuf objects from it and notify any listeners.
     *
     * @param results The list of Scan Results.
     * @since 1.0.0
     */
    private void processBluetoothResults(List<android.bluetooth.le.ScanResult> results)
    {
        final List<BluetoothRecord> bluetoothRecords = results.stream()
                .map(this::generateBluetoothSurveyRecord)
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

        gnssGroupNumber++; // Group all the records found in this scan iteration.

        for (final GnssMeasurement gnssMeasurement : gnssMeasurements)
        {
            final GnssRecord gnssRecord = generateGnssSurveyRecord(gnssMeasurement);
            notifyGnssRecordListeners(gnssRecord);
        }
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
    private GsmRecord generateGsmSurveyRecord(CellInfoGsm cellInfoGsm)
    {
        final CellIdentityGsm cellIdentity = cellInfoGsm.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int lac = cellIdentity.getLac();
        final int cid = cellIdentity.getCid();
        final int arfcn = cellIdentity.getArfcn();
        final int bsic = cellIdentity.getBsic();

        CharSequence provider = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
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
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoGsm.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        // Even though the Android Javadocs indicate that an unset value is represented by Integer.MAX_VALUE, I found that a -1 is sometimes used for TA and CID.
        // I also found that 0 is used as unset for MCC, MNC, LAC, ARFCN, and BSIC.

        if (mcc != Integer.MAX_VALUE && mcc != 0) dataBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        if (mnc != Integer.MAX_VALUE && mnc != 0) dataBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        if (lac != Integer.MAX_VALUE && lac != 0) dataBuilder.setLac(Int32Value.newBuilder().setValue(lac).build());
        if (cid != Integer.MAX_VALUE && cid != -1) dataBuilder.setCi(Int32Value.newBuilder().setValue(cid).build());

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
    private CdmaRecord generateCdmaSurveyRecord(CellInfoCdma cellInfoCdma)
    {
        final CellIdentityCdma cellIdentity = cellInfoCdma.getCellIdentity();
        final int sid = cellIdentity.getSystemId();
        final int nid = cellIdentity.getNetworkId();
        final int bsid = cellIdentity.getBasestationId();
        // TODO also get the Base Latitude and Longitude

        CharSequence provider = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
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
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoCdma.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        if (sid != Integer.MAX_VALUE) dataBuilder.setSid(Int32Value.newBuilder().setValue(sid).build());
        if (nid != Integer.MAX_VALUE) dataBuilder.setNid(Int32Value.newBuilder().setValue(nid).build());
        if (bsid != Integer.MAX_VALUE) dataBuilder.setBsid(Int32Value.newBuilder().setValue(bsid).build());

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
    private UmtsRecord generateUmtsSurveyRecord(CellInfoWcdma cellInfoWcdma)
    {
        final CellIdentityWcdma cellIdentity = cellInfoWcdma.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int lac = cellIdentity.getLac();
        final int ci = cellIdentity.getCid();
        final int uarfcn = cellIdentity.getUarfcn();
        final int psc = cellIdentity.getPsc();

        CharSequence provider = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            provider = cellIdentity.getOperatorAlphaLong();
        }

        CellSignalStrengthWcdma cellSignalStrengthUmts = cellInfoWcdma.getCellSignalStrength();
        final int signalStrength = cellSignalStrengthUmts.getDbm();

        // Validate that the required fields are present before proceeding further
        if (!validateUmtsFields(uarfcn, psc, signalStrength)) return null;

        final UmtsRecordData.Builder dataBuilder = UmtsRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoWcdma.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        if (mcc != Integer.MAX_VALUE) dataBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        if (mnc != Integer.MAX_VALUE) dataBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        if (lac != Integer.MAX_VALUE) dataBuilder.setLac(Int32Value.newBuilder().setValue(lac).build());
        if (ci != Integer.MAX_VALUE) dataBuilder.setCid(Int32Value.newBuilder().setValue(ci).build());

        dataBuilder.setUarfcn(Int32Value.newBuilder().setValue(uarfcn).build());
        dataBuilder.setPsc(Int32Value.newBuilder().setValue(psc).build());
        dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());

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
    private LteRecord generateLteSurveyRecord(CellInfoLte cellInfoLte)
    {
        final CellIdentityLte cellIdentity = cellInfoLte.getCellIdentity();
        final int mcc = cellIdentity.getMcc();
        final int mnc = cellIdentity.getMnc();
        final int tac = cellIdentity.getTac();
        final int ci = cellIdentity.getCi();
        final int earfcn = cellIdentity.getEarfcn();
        final int pci = cellIdentity.getPci();

        CharSequence provider = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            provider = cellIdentity.getOperatorAlphaLong();
        }

        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
        final int rsrp = cellSignalStrengthLte.getRsrp();
        final int rsrq = cellSignalStrengthLte.getRsrq();
        final int timingAdvance = cellSignalStrengthLte.getTimingAdvance();

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
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoLte.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        if (mcc != Integer.MAX_VALUE) dataBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        if (mnc != Integer.MAX_VALUE) dataBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        if (tac != Integer.MAX_VALUE) dataBuilder.setTac(Int32Value.newBuilder().setValue(tac).build());
        if (ci != Integer.MAX_VALUE) dataBuilder.setEci(Int32Value.newBuilder().setValue(ci).build());

        dataBuilder.setEarfcn(Int32Value.newBuilder().setValue(earfcn).build());
        dataBuilder.setPci(Int32Value.newBuilder().setValue(pci).build());
        dataBuilder.setRsrp(FloatValue.newBuilder().setValue(rsrp).build());

        if (rsrq != Integer.MAX_VALUE) dataBuilder.setRsrq(FloatValue.newBuilder().setValue(rsrq).build());
        if (timingAdvance != Integer.MAX_VALUE)
        {
            dataBuilder.setTa(Int32Value.newBuilder().setValue(timingAdvance).build());
        }

        setBandwidth(dataBuilder, cellIdentity);

        final LteRecord.Builder recordBuilder = LteRecord.newBuilder();
        recordBuilder.setMessageType(LteMessageConstants.LTE_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Given a {@link CellInfoNr} object, pull out the values and generate a {@link NrRecord}.
     *
     * @param cellInfoNr The object that contains the NR(5G) Cell info.  This can be a serving cell, or a neighbor cell.
     * @return The survey record.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private NrRecord generateNrSurveyRecord(CellInfoNr cellInfoNr)
    {
        // safe to cast as per: https://developer.android.com/reference/android/telephony/CellInfoNr#getCellIdentity()
        final CellIdentityNr cellIdentity = (CellIdentityNr) cellInfoNr.getCellIdentity();

        final int nrarfcn = cellIdentity.getNrarfcn();
        final int pci = cellIdentity.getPci();
        final int tac = cellIdentity.getTac();
        final long nci = cellIdentity.getNci();
        // TODO: 8/20/2021 Do we want any other values from cell identity?
        // Strings of Mcc and Mnc?


        // can't extract this to method due to API limitations
        CharSequence provider = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
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

        // TODO: 8/20/2021 validate fields
        if (!validateNrFields()) return null;

        final NrRecordData.Builder dataBuilder = NrRecordData.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                dataBuilder.setLatitude(lastKnownLocation.getLatitude());
                dataBuilder.setLongitude(lastKnownLocation.getLongitude());
                dataBuilder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(recordNumber++);
        dataBuilder.setGroupNumber(groupNumber);
        dataBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoNr.isRegistered()).build());
        if (provider != null) dataBuilder.setProvider(provider.toString());

        Function<Integer, FloatValue> getFloat = i -> FloatValue.newBuilder().setValue(i).build();
        Function<Integer, Int32Value> getInt32 = i -> Int32Value.newBuilder().setValue(i).build();
        Predicate<Integer> isAvail = i -> i != CellInfo.UNAVAILABLE;
        // TODO: 8/20/2021 Should this setter be nrarfcn?
        // vals from CellIdentity
        if(isAvail.test(nrarfcn)) dataBuilder.setNarfcn(getInt32.apply(nrarfcn));
        if(isAvail.test(pci)) dataBuilder.setPci(getInt32.apply(pci));
        if(isAvail.test(tac)) dataBuilder.setTac(getInt32.apply(tac));
        if(nci != CellInfo.UNAVAILABLE_LONG) dataBuilder.setNci(Int64Value.newBuilder().setValue(nci).build());

        // vals from CellSignalStrength
        if(isAvail.test(csiRsrp)) dataBuilder.setCsiRsrp(getFloat.apply(csiRsrp));
        if(isAvail.test(csiRsrq)) dataBuilder.setCsiRsrq(getFloat.apply(csiRsrq));
        if(isAvail.test(csiSinr)) dataBuilder.setCsiSinr(getFloat.apply(csiSinr));

        if(isAvail.test(ssRsrp)) dataBuilder.setSsRsrp(getFloat.apply(ssRsrp));
        if(isAvail.test(ssRsrq)) dataBuilder.setSsRsrq(getFloat.apply(ssRsrq));
        if(isAvail.test(ssSinr)) dataBuilder.setSsSinr(getFloat.apply(ssSinr));

        // TODO: 8/20/2021 Do we care about setting bandwidth?
        final NrRecord.Builder recordBuilder = NrRecord.newBuilder();
        // TODO: 8/20/2021 impl constants
        //recordBuilder.setMessageType()
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Pull out the appropriate values from the {@link ScanResult}, and create a {@link WifiBeaconRecord}.
     *
     * @param apScanResult The scan result to pull the Wi-Fi data from.
     * @return The Wi-Fi record to send to any listeners.
     * @since 0.1.2
     */
    private WifiRecordWrapper generateWiFiBeaconSurveyRecord(ScanResult apScanResult)
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
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(wifiRecordNumber++);

        dataBuilder.setBssid(bssid);
        dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());

        final String ssid = apScanResult.SSID;
        if (ssid != null) dataBuilder.setSsid(ssid);

        final short channel = WifiBeaconMessageConstants.convertFrequencyToChannelNumber(apScanResult.frequency);
        if (channel != -1) dataBuilder.setChannel(Int32Value.newBuilder().setValue(channel).build());

        final int frequency = apScanResult.frequency;
        if (frequency != -1 && frequency != 0)
        {
            dataBuilder.setFrequencyMhz(Int32Value.newBuilder().setValue(frequency).build());
        }

        final String capabilities = apScanResult.capabilities;
        if (capabilities != null && !capabilities.isEmpty())
        {
            // TODO At some point it would be nice to add the Cipher Suites and AKM Suites, but I can't seem to get
            //  enough information for that.

            final EncryptionType encryptionType = WifiCapabilitiesUtils.getEncryptionType(capabilities);
            if (encryptionType != EncryptionType.UNKNOWN) dataBuilder.setEncryptionType(encryptionType);

            dataBuilder.setWps(BoolValue.newBuilder().setValue(WifiCapabilitiesUtils.supportsWps(capabilities)).build());
        }

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
    private BluetoothRecord generateBluetoothSurveyRecord(android.bluetooth.le.ScanResult result)
    {
        return generateBluetoothSurveyRecord(result.getDevice(), result.getRssi(), result.getTxPower());
    }

    /**
     * Pull out the appropriate values, and create a {@link BluetoothRecord}.
     *
     * @return The Bluetooth record to send to any listeners.
     * @since 1.0.0
     */
    private BluetoothRecord generateBluetoothSurveyRecord(BluetoothDevice device, int rssi, int txPowerLevel)
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
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
        dataBuilder.setMissionId(missionId);
        dataBuilder.setRecordNumber(bluetoothRecordNumber++);

        dataBuilder.setSourceAddress(sourceAddress);
        dataBuilder.setSignalStrength(FloatValue.newBuilder().setValue(rssi).build());

        // The TX Power seems to never be set (a value of 127 indicates unset). However, I am including
        // the code here in case it starts being populated in a future version of Android, or if a specific phone model
        // reports it.
        if (txPowerLevel != UNSET_TX_POWER_LEVEL)
        {
            dataBuilder.setTxPower(FloatValue.newBuilder().setValue(txPowerLevel).build());
        }

        final String otaDeviceName = device.getName();
        if (otaDeviceName != null) dataBuilder.setOtaDeviceName(otaDeviceName);

        final SupportedTechnologies supportedTech = BluetoothMessageConstants.getSupportedTechnologies(device.getType());
        if (supportedTech != SupportedTechnologies.UNKNOWN) dataBuilder.setSupportedTechnologies(supportedTech);

        final BluetoothRecord.Builder recordBuilder = BluetoothRecord.newBuilder();
        recordBuilder.setMessageType(BluetoothMessageConstants.BLUETOOTH_RECORD_MESSAGE_TYPE);
        recordBuilder.setVersion(BuildConfig.MESSAGING_API_VERSION);
        recordBuilder.setData(dataBuilder);

        return recordBuilder.build();
    }

    /**
     * Pull out the appropriate values from the {@link GnssMeasurement}, and create a {@link GnssRecord}.
     *
     * @param gnss The GNSS measurement object to pull the data from.
     * @return The GNSS record to send to any listeners.
     * @since 0.3.0
     */
    private GnssRecord generateGnssSurveyRecord(GnssMeasurement gnss)
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
            }
        }

        dataBuilder.setDeviceSerialNumber(deviceId);
        dataBuilder.setDeviceTime(IOUtils.getRfc3339String(ZonedDateTime.now()));
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

        // TODO dataBuilder.setClockOffset(FloatValue.newBuilder().setValue());
        // TODO Can get this from the Satellite Status Changed call dataBuilder.setUsedInSolution(FloatValue.newBuilder().setValue());
        // TODO dataBuilder.setUndulationM(FloatValue.newBuilder().setValue());

        if (gnss.hasAutomaticGainControlLevelDb())
        {
            dataBuilder.setAgcDb(FloatValue.newBuilder().setValue((float) gnss.getAutomaticGainControlLevelDb()));
        }

        dataBuilder.setCn0DbHz(FloatValue.newBuilder().setValue((float) gnss.getCn0DbHz()));

        // TODO dataBuilder.setHdop(FloatValue.newBuilder().setValue());
        // TODO dataBuilder.setVdop(FloatValue.newBuilder().setValue());

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
                LteBandwidth lteBandwidth = null;
                //noinspection SwitchStatementWithoutDefaultBranch
                switch (bandwidth)
                {
                    case 1_400:
                        lteBandwidth = LteBandwidth.MHZ_1_4;
                        break;

                    case 3_000:
                        lteBandwidth = LteBandwidth.MHZ_3;
                        break;

                    case 5_000:
                        lteBandwidth = LteBandwidth.MHZ_5;
                        break;

                    case 10_000:
                        lteBandwidth = LteBandwidth.MHZ_10;
                        break;

                    case 15_000:
                        lteBandwidth = LteBandwidth.MHZ_15;
                        break;

                    case 20_000:
                        lteBandwidth = LteBandwidth.MHZ_20;
                        break;
                }

                if (lteBandwidth != null) lteRecordBuilder.setLteBandwidth(lteBandwidth);
            }
        }
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
    private boolean validateUmtsFields(int uarfcn, int psc, int signalStrength)
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

        if (signalStrength == Integer.MAX_VALUE)
        {
            Timber.v("The Signal Strength is required to build a UMTS Survey Record.");
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

    private boolean validateNrFields()
    {
        // TODO: 8/20/2021 required fields?
        
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
     * @param nrRecord  The new NR Survey Record to send to the listeners
     */
    private void notifyNrRecordListeners(NrRecord nrRecord)
    {
        // TODO: 8/20/2021 do we care about logging null records?
        if (nrRecord == null) return;

        cellularSurveyRecordListeners.forEach(l -> {
            try
            {
                l.onNrSurveyRecord(nrRecord);
            } catch (Exception e)
            {
                Timber.e(e, "Unable to notify a Cellular Survey Record Listener because of an exception %s", e.getMessage());
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

    /**
     * Sets the provided String as the current technology in the UI.
     *
     * @param currentTechnology The {@link String} for the current serving cell.
     */
    private void updateCurrentTechnologyUi(String currentTechnology)
    {
        if (!NetworkDetailsFragment.visible.get())
        {
            Timber.v("Skipping updating the Current Technology UI because it is not visible");
            return;
        }

        // Clear out the UI if the technology is not LTE
        if (!NetworkSurveyConstants.LTE.equals(currentTechnology) && !NetworkSurveyConstants.LTE_CA.equals(currentTechnology))
        {
            updateUi(LteRecord.getDefaultInstance().getData());
        }

        synchronized (activityUpdateLock)
        {
            if (networkSurveyActivity != null && NetworkDetailsFragment.visible.get())
            {
                networkSurveyActivity.runOnUiThread(() -> setText(R.id.current_technology, R.string.current_technology_label, currentTechnology));
            }
        }
    }

    /**
     * Updates the UI with the information from the latest survey record.
     *
     * @param lteSurveyRecord The latest LTE serving cell record.
     */
    private void updateUi(LteRecordData lteSurveyRecord)
    {
        synchronized (activityUpdateLock)
        {
            if (networkSurveyActivity == null || !NetworkDetailsFragment.visible.get())
            {
                Timber.v("Skipping updating the Network Details UI because it is not visible");
                return;
            }

            networkSurveyActivity.runOnUiThread(() -> {

                final String provider = lteSurveyRecord.getProvider();
                setText(R.id.carrier, R.string.carrier_label, provider != null ? provider : "");

                setText(R.id.mcc, R.string.mcc_label, lteSurveyRecord.hasMcc() ? String.valueOf(lteSurveyRecord.getMcc().getValue()) : "");
                setText(R.id.mnc, R.string.mnc_label, lteSurveyRecord.hasMnc() ? String.valueOf(lteSurveyRecord.getMnc().getValue()) : "");
                setText(R.id.tac, R.string.tac_label, lteSurveyRecord.hasTac() ? String.valueOf(lteSurveyRecord.getTac().getValue()) : "");

                if (lteSurveyRecord.hasEci())
                {
                    final int ci = lteSurveyRecord.getEci().getValue();
                    setText(R.id.cid, R.string.cid_label, String.valueOf(ci));

                    // The Cell Identity is 28 bits long. The first 20 bits represent the Macro eNodeB ID. The last 8 bits
                    // represent the sector.  Strip off the last 8 bits to get the Macro eNodeB ID.
                    int eNodebId = CalculationUtils.getEnodebIdFromCellId(ci);
                    setText(R.id.enbId, R.string.enb_id_label, String.valueOf(eNodebId));

                    int sectorId = CalculationUtils.getSectorIdFromCellId(ci);
                    setText(R.id.sectorId, R.string.sector_id_label, String.valueOf(sectorId));
                } else
                {
                    setText(R.id.cid, R.string.cid_label, "");
                    setText(R.id.enbId, R.string.enb_id_label, "");
                    setText(R.id.sectorId, R.string.sector_id_label, "");
                }

                setText(R.id.earfcn, R.string.earfcn_label, lteSurveyRecord.hasEarfcn() ? String.valueOf(lteSurveyRecord.getEarfcn().getValue()) : "");

                if (lteSurveyRecord.hasPci())
                {
                    final int pci = lteSurveyRecord.getPci().getValue();
                    int primarySyncSequence = CalculationUtils.getPrimarySyncSequence(pci);
                    int secondarySyncSequence = CalculationUtils.getSecondarySyncSequence(pci);
                    setText(R.id.pci, R.string.pci_label, pci + " (" + primarySyncSequence + "/" + secondarySyncSequence + ")");
                } else
                {
                    setText(R.id.pci, R.string.pci_label, "");
                }

                setText(R.id.bandwidth, R.string.bandwidth_label, LteMessageConstants.getLteBandwidth(lteSurveyRecord.getLteBandwidth()));

                checkAndSetLocation(lteSurveyRecord);

                setText(R.id.rsrp, R.string.rsrp_label, lteSurveyRecord.hasRsrp() ? String.valueOf(lteSurveyRecord.getRsrp().getValue()) : "");
                setText(R.id.rsrq, R.string.rsrq_label, lteSurveyRecord.hasRsrq() ? String.valueOf(lteSurveyRecord.getRsrq().getValue()) : "");
                setText(R.id.ta, R.string.ta_label, lteSurveyRecord.hasTa() ? String.valueOf(lteSurveyRecord.getTa().getValue()) : "");
            });
        }
    }

    /**
     * Sets the provided text on the TextView with the provided Text View ID.
     *
     * @param textViewId       The ID that is used to lookup the TextView.
     * @param stringResourceId The resource ID for the String to populate.
     * @param text             The text to set on the text view.
     */
    private void setText(int textViewId, int stringResourceId, String text)
    {
        if (networkSurveyActivity == null || !NetworkDetailsFragment.visible.get()) return;

        final View viewById = networkSurveyActivity.findViewById(textViewId);
        if (viewById != null) ((TextView) viewById).setText(networkSurveyActivity.getString(stringResourceId, text));
    }

    /**
     * Checks to make sure the location is not null, and then updates the appropriate UI elements.
     *
     * @param lteRecord The LTE Record to check and see if it has a valid location.
     */
    private void checkAndSetLocation(LteRecordData lteRecord)
    {
        final double latitude = lteRecord.getLatitude();
        final double longitude = lteRecord.getLongitude();
        if (latitude == 0 && longitude == 0)
        {
            setText(R.id.latitude, R.string.latitude_label, "");
            setText(R.id.longitude, R.string.longitude_label, "");
        } else
        {
            setText(R.id.latitude, R.string.latitude_label, String.format(Locale.US, "%.7f", latitude));
            setText(R.id.longitude, R.string.longitude_label, String.format(Locale.US, "%.7f", longitude));
        }
    }
}
