package com.craxiom.networksurvey;

import android.annotation.SuppressLint;
import android.location.Location;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import com.craxiom.networksurvey.listeners.ISurveyRecordListener;
import com.craxiom.networksurvey.messaging.CdmaRecord;
import com.craxiom.networksurvey.messaging.GsmRecord;
import com.craxiom.networksurvey.messaging.LteBandwidth;
import com.craxiom.networksurvey.messaging.LteRecord;
import com.craxiom.networksurvey.messaging.UmtsRecord;
import com.google.protobuf.BoolValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Responsible for consuming {@link CellInfo} objects, converting them to records specific to a protocol, and then notifying any listeners
 * of the new record.
 *
 * @since 0.0.2
 */
class SurveyRecordProcessor
{
    private static final SimpleDateFormat formatFilenameFriendlyTime = new SimpleDateFormat("YYYYMMdd-HHmmss", Locale.US);
    private static final String MISSION_ID_PREFIX = "NS ";

    private final String LOG_TAG = SurveyRecordProcessor.class.getSimpleName();

    private final GpsListener gpsListener;
    private final NetworkSurveyActivity networkSurveyActivity;
    private final List<ISurveyRecordListener> surveyRecordListeners = new CopyOnWriteArrayList<>();

    private final String deviceId;
    private final String missionId;

    private int recordNumber = 0;
    private int groupNumber = -1; // This will be incremented to 0 the first time it is used.

    SurveyRecordProcessor(GpsListener gpsListener, NetworkSurveyActivity networkSurveyActivity, String deviceId)
    {
        this.gpsListener = gpsListener;
        this.networkSurveyActivity = networkSurveyActivity;

        this.deviceId = deviceId;
        missionId = MISSION_ID_PREFIX + deviceId + " " + formatFilenameFriendlyTime.format(System.currentTimeMillis());
    }

    void registerSurveyRecordListener(ISurveyRecordListener surveyRecordListener)
    {
        surveyRecordListeners.add(surveyRecordListener);
    }

    void unregisterSurveyRecordListener(ISurveyRecordListener surveyRecordListener)
    {
        surveyRecordListeners.remove(surveyRecordListener);
    }

    /**
     * Process the updated list of {@link CellInfo} objects from the {@link TelephonyManager}.  This list is converted to the appropriate ProtoBuf defined
     * survey records and any listeners are notified of the new records.
     * <p>
     * This method is synchronized to make sure that we are only processing one list of Cell Info objects at a time.
     *
     * @param allCellInfo The List of {@link CellInfo} records to convert to survey records.
     */
    synchronized void onCellInfoUpdate(List<CellInfo> allCellInfo) throws SecurityException
    {
        try
        {
            if (allCellInfo != null && allCellInfo.size() > 0)
            {
                groupNumber++; // Group all the records found in this scan iteration.

                for (CellInfo cellInfo : allCellInfo)
                {
                    // If there is a serving cell that is not an LTE cell, then clear the UI TODO Change this once we support other technologies like GSM
                    final boolean isServingCell = cellInfo.isRegistered();
                    if (isServingCell && !(cellInfo instanceof CellInfoLte)) updateUi(LteRecord.getDefaultInstance());

                    if (isServingCell) updateCurrentTechnologyUi(cellInfo);

                    processCellInfo(cellInfo);
                }
            } else
            {
                updateUi(LteRecord.getDefaultInstance());
                updateCurrentTechnologyUi(null);
            }
        } catch (Exception e)
        {
            Log.e(LOG_TAG, "Unable to display and log an LTE Survey Record", e);
            updateUi(LteRecord.getDefaultInstance());
            updateCurrentTechnologyUi(null);
        }
    }

    /**
     * Given a {@link CellInfo} record, convert it to the appropriate ProtoBuf defined message.  Then, notify any listeners so it can be written to a log file
     * and/or sent to any servers if those services are enabled.
     *
     * @param cellInfo The Cell Info object with the details.
     * @since 0.0.5
     */
    private void processCellInfo(CellInfo cellInfo)
    {
        final boolean isServingCell = cellInfo.isRegistered();
        final boolean isLteRecord = cellInfo instanceof CellInfoLte;

        // We only want to take the time to process a record if we are going to do something with it.  Currently, that means logging,
        // sending to a server, or updating the UI with the latest LTE information.
        if (!surveyRecordListeners.isEmpty() || (isServingCell && isLteRecord && this.networkSurveyActivity.isNetworkDetailsVisible()))
        {
            if (isLteRecord)
            {
                final LteRecord lteSurveyRecord = generateLteSurveyRecord((CellInfoLte) cellInfo);
                if (lteSurveyRecord == null)
                {
                    Log.w(LOG_TAG, "Could not generate a Server LteRecord from the CellInfoLte");
                    if (isServingCell) updateUi(LteRecord.getDefaultInstance());
                    return;
                }

                if (isServingCell) updateUi(lteSurveyRecord);
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
            }
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

        final GsmRecord.Builder recordBuilder = GsmRecord.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                recordBuilder.setLatitude(lastKnownLocation.getLatitude());
                recordBuilder.setLongitude(lastKnownLocation.getLongitude());
                recordBuilder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        recordBuilder.setDeviceSerialNumber(deviceId);
        recordBuilder.setDeviceTime(System.currentTimeMillis());
        recordBuilder.setMissionId(missionId);
        recordBuilder.setRecordNumber(recordNumber++);
        recordBuilder.setGroupNumber(groupNumber);
        recordBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoGsm.isRegistered()).build());
        if (provider != null) recordBuilder.setProvider(provider.toString());

        // Even though the Android Javadocs indicate that an unset value is represented by Integer.MAX_VALUE, I found that a -1 is sometimes used for TA and CID.
        // I also found that 0 is used as unset for MCC, MNC, LAC, ARFCN, and BSIC.

        if (mcc != Integer.MAX_VALUE && mcc != 0) recordBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        if (mnc != Integer.MAX_VALUE && mnc != 0) recordBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        if (lac != Integer.MAX_VALUE && lac != 0) recordBuilder.setLac(Int32Value.newBuilder().setValue(lac).build());
        if (cid != Integer.MAX_VALUE && cid != -1) recordBuilder.setCi(Int32Value.newBuilder().setValue(cid).build());
        if (arfcn != Integer.MAX_VALUE && arfcn != 0) recordBuilder.setArfcn(Int32Value.newBuilder().setValue(arfcn).build());
        if (bsic != Integer.MAX_VALUE && bsic != 0) recordBuilder.setBsic(Int32Value.newBuilder().setValue(bsic).build());
        if (signalStrength != Integer.MAX_VALUE) recordBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());
        if (timingAdvance != Integer.MAX_VALUE && timingAdvance != -1) recordBuilder.setTa(Int32Value.newBuilder().setValue(timingAdvance).build());

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

        final CdmaRecord.Builder recordBuilder = CdmaRecord.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                recordBuilder.setLatitude(lastKnownLocation.getLatitude());
                recordBuilder.setLongitude(lastKnownLocation.getLongitude());
                recordBuilder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        recordBuilder.setDeviceSerialNumber(deviceId);
        recordBuilder.setDeviceTime(System.currentTimeMillis());
        recordBuilder.setMissionId(missionId);
        recordBuilder.setRecordNumber(recordNumber++);
        recordBuilder.setGroupNumber(groupNumber);
        recordBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoCdma.isRegistered()).build());
        if (provider != null) recordBuilder.setProvider(provider.toString());

        if (sid != Integer.MAX_VALUE) recordBuilder.setSid(Int32Value.newBuilder().setValue(sid).build());
        if (nid != Integer.MAX_VALUE) recordBuilder.setNid(Int32Value.newBuilder().setValue(nid).build());
        if (bsid != Integer.MAX_VALUE) recordBuilder.setBsid(Int32Value.newBuilder().setValue(bsid).build());
        if (signalStrength != Integer.MAX_VALUE) recordBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());
        if (ecio != Integer.MAX_VALUE) recordBuilder.setEcio(FloatValue.newBuilder().setValue(ecioFloat).build());

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

        final UmtsRecord.Builder recordBuilder = UmtsRecord.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                recordBuilder.setLatitude(lastKnownLocation.getLatitude());
                recordBuilder.setLongitude(lastKnownLocation.getLongitude());
                recordBuilder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        recordBuilder.setDeviceSerialNumber(deviceId);
        recordBuilder.setDeviceTime(System.currentTimeMillis());
        recordBuilder.setMissionId(missionId);
        recordBuilder.setRecordNumber(recordNumber++);
        recordBuilder.setGroupNumber(groupNumber);
        recordBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoWcdma.isRegistered()).build());
        if (provider != null) recordBuilder.setProvider(provider.toString());

        if (mcc != Integer.MAX_VALUE) recordBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        if (mnc != Integer.MAX_VALUE) recordBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        if (lac != Integer.MAX_VALUE) recordBuilder.setLac(Int32Value.newBuilder().setValue(lac).build());
        if (ci != Integer.MAX_VALUE) recordBuilder.setCi(Int32Value.newBuilder().setValue(ci).build());
        if (uarfcn != Integer.MAX_VALUE) recordBuilder.setUarfcn(Int32Value.newBuilder().setValue(uarfcn).build());
        if (psc != Integer.MAX_VALUE) recordBuilder.setPsc(Int32Value.newBuilder().setValue(psc).build());
        if (signalStrength != Integer.MAX_VALUE) recordBuilder.setSignalStrength(FloatValue.newBuilder().setValue(signalStrength).build());

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

        final LteRecord.Builder lteRecordBuilder = LteRecord.newBuilder();

        if (gpsListener != null)
        {
            @SuppressLint("MissingPermission") final Location lastKnownLocation = gpsListener.getLatestLocation();
            if (lastKnownLocation != null)
            {
                lteRecordBuilder.setLatitude(lastKnownLocation.getLatitude());
                lteRecordBuilder.setLongitude(lastKnownLocation.getLongitude());
                lteRecordBuilder.setAltitude((float) lastKnownLocation.getAltitude());
            }
        }

        lteRecordBuilder.setDeviceSerialNumber(deviceId);
        lteRecordBuilder.setDeviceTime(System.currentTimeMillis());
        lteRecordBuilder.setMissionId(missionId);
        lteRecordBuilder.setRecordNumber(recordNumber++);
        lteRecordBuilder.setGroupNumber(groupNumber);
        lteRecordBuilder.setServingCell(BoolValue.newBuilder().setValue(cellInfoLte.isRegistered()).build());
        if (provider != null) lteRecordBuilder.setProvider(provider.toString());

        if (mcc != Integer.MAX_VALUE) lteRecordBuilder.setMcc(Int32Value.newBuilder().setValue(mcc).build());
        if (mnc != Integer.MAX_VALUE) lteRecordBuilder.setMnc(Int32Value.newBuilder().setValue(mnc).build());
        if (tac != Integer.MAX_VALUE) lteRecordBuilder.setTac(Int32Value.newBuilder().setValue(tac).build());
        if (ci != Integer.MAX_VALUE) lteRecordBuilder.setCi(Int32Value.newBuilder().setValue(ci).build());
        if (earfcn != Integer.MAX_VALUE) lteRecordBuilder.setEarfcn(Int32Value.newBuilder().setValue(earfcn).build());
        if (pci != Integer.MAX_VALUE) lteRecordBuilder.setPci(Int32Value.newBuilder().setValue(pci).build());
        if (rsrp != Integer.MAX_VALUE) lteRecordBuilder.setRsrp(FloatValue.newBuilder().setValue(rsrp).build());
        if (rsrq != Integer.MAX_VALUE) lteRecordBuilder.setRsrq(FloatValue.newBuilder().setValue(rsrq).build());
        if (timingAdvance != Integer.MAX_VALUE) lteRecordBuilder.setTa(Int32Value.newBuilder().setValue(timingAdvance).build());

        setBandwidth(lteRecordBuilder, cellIdentity);

        return lteRecordBuilder.build();
    }

    /**
     * Sets the LTE bandwidth on the record if it is valid, and if the current android version supports it.
     *
     * @param lteRecordBuilder The builder to set the bandwidth on.
     * @param cellIdentity     The {@link CellIdentityLte} to pull the bandwidth from.
     */
    private void setBandwidth(LteRecord.Builder lteRecordBuilder, CellIdentityLte cellIdentity)
    {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            final int bandwidth = cellIdentity.getBandwidth();
            if (bandwidth != Integer.MAX_VALUE)
            {
                LteBandwidth lteBandwidth = null;
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
        if (arfcn == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The ARFCN is required to build a GSM Survey Record.");
            return false;
        }

        if (bsic == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The BSIC is required to build a GSM Survey Record.");
            return false;
        }

        if (signalStrength == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The Signal Strength is required to build a GSM Survey Record.");
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
            Log.v(LOG_TAG, "The Signal Strength is required to build a CDMA Survey Record.");
            return false;
        }

        if (ecio == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The Ec/Io is required to build a CDMA Survey Record.");
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
        if (uarfcn == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The UARFCN is required to build a UMTS Survey Record.");
            return false;
        }

        if (psc == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The PSC is required to build a UMTS Survey Record.");
            return false;
        }

        if (signalStrength == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The Signal Strength is required to build a UMTS Survey Record.");
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
        if (earfcn == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The EARFCN is required to build an LTE Survey Record.");
            return false;
        }

        if (pci == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The PCI is required to build an LTE Survey Record.");
            return false;
        }

        if (rsrp == Integer.MAX_VALUE)
        {
            Log.v(LOG_TAG, "The RSRP is required to build an LTE Survey Record.");
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
        for (ISurveyRecordListener listener : surveyRecordListeners)
        {
            try
            {
                listener.onGsmSurveyRecord(gsmRecord);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to notify a Survey Record Listener because of an exception", e);
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
        for (ISurveyRecordListener listener : surveyRecordListeners)
        {
            try
            {
                listener.onCdmaSurveyRecord(cdmaRecord);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to notify a Survey Record Listener because of an exception", e);
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
        for (ISurveyRecordListener listener : surveyRecordListeners)
        {
            try
            {
                listener.onUmtsSurveyRecord(umtsRecord);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to notify a Survey Record Listener because of an exception", e);
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
        for (ISurveyRecordListener listener : surveyRecordListeners)
        {
            try
            {
                listener.onLteSurveyRecord(lteRecord);
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Unable to notify a Survey Record Listener because of an exception", e);
            }
        }
    }

    /**
     * Sets the provided String as the current technology in the UI.
     *
     * @param servingCellInfo The {@link CellInfo} for the current serving cell.
     */
    private void updateCurrentTechnologyUi(CellInfo servingCellInfo)
    {
        if (!networkSurveyActivity.isNetworkDetailsVisible())
        {
            Log.v(LOG_TAG, "Skipping updating the Current Technology UI because it is not visible");
            return;
        }

        final String currentTechnology;
        if (servingCellInfo instanceof CellInfoLte)
        {
            currentTechnology = "LTE";
        } else if (servingCellInfo instanceof CellInfoWcdma)
        {
            currentTechnology = "UMTS";
        } else if (servingCellInfo instanceof CellInfoCdma)
        {
            currentTechnology = "CDMA";
        } else if (servingCellInfo instanceof CellInfoGsm)
        {
            currentTechnology = "GSM";
        } else
        {
            currentTechnology = "Unknown";
        }
        setText(R.id.current_technology, R.string.current_technology_label, currentTechnology);
    }

    private void updateUi(LteRecord lteSurveyRecord)
    {
        if (!networkSurveyActivity.isNetworkDetailsVisible())
        {
            Log.v(LOG_TAG, "Skipping updating the Network Details UI because it is not visible");
            return;
        }

        final String provider = lteSurveyRecord.getProvider();
        setText(R.id.carrier, R.string.carrier_label, provider != null ? provider : "");

        setText(R.id.mcc, R.string.mcc_label, lteSurveyRecord.hasMcc() ? String.valueOf(lteSurveyRecord.getMcc().getValue()) : "");
        setText(R.id.mnc, R.string.mnc_label, lteSurveyRecord.hasMnc() ? String.valueOf(lteSurveyRecord.getMnc().getValue()) : "");
        setText(R.id.tac, R.string.tac_label, lteSurveyRecord.hasTac() ? String.valueOf(lteSurveyRecord.getTac().getValue()) : "");

        if (lteSurveyRecord.hasCi())
        {
            final int ci = lteSurveyRecord.getCi().getValue();
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
        ((TextView) networkSurveyActivity.findViewById(textViewId)).setText(networkSurveyActivity.getString(stringResourceId, text));
    }

    /**
     * Checks to make sure the location is not null, and then updates the appropriate UI elements.
     *
     * @param lteRecord The LTE Record to check and see if it has a valid location.
     */
    private void checkAndSetLocation(LteRecord lteRecord)
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
