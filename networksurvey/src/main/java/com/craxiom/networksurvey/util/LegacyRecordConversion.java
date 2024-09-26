package com.craxiom.networksurvey.util;

import static com.craxiom.networksurvey.util.NsUtils.getEpochFromRfc3339;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.DeviceStatusData;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.messaging.Error;
import com.craxiom.networksurvey.messaging.LteBandwidth;

/**
 * Utility methods to help with converting the newer survey record protobuf objects to the old format. This class should
 * only exist for a short amount of time while usages of the old protobuf records are removed from other code bases.
 *
 * @since 0.2.0
 */
@SuppressWarnings("deprecation")
public final class LegacyRecordConversion
{
    private LegacyRecordConversion()
    {
    }

    /**
     * Converts the newer Device Status protobuf object to the older version.
     *
     * @param deviceStatus The newer Device Status object.
     * @return The old Device Status object format.
     */
    public static com.craxiom.networksurvey.messaging.DeviceStatus convertDeviceStatus(DeviceStatus deviceStatus)
    {
        final com.craxiom.networksurvey.messaging.DeviceStatus.Builder builder = com.craxiom.networksurvey.messaging.DeviceStatus.newBuilder();
        final DeviceStatusData data = deviceStatus.getData();
        builder.setDeviceSerialNumber(data.getDeviceSerialNumber());
        builder.setDeviceTime(getEpochFromRfc3339(data.getDeviceTime()));
        builder.setLatitude(data.getLatitude());
        builder.setLongitude(data.getLongitude());
        builder.setAltitude(data.getAltitude());
        builder.setDeviceName((data.getDeviceName()));

        if (data.hasBatteryLevelPercent())
        {
            builder.setBatteryLevelPercent(data.getBatteryLevelPercent().getValue());
        }
        if (data.hasError())
        {
            builder.setError(Error.newBuilder().setErrorMessage(data.getError().getErrorMessage()).build());
        }

        return builder.build();
    }

    /**
     * Converts the newer GSM record protobuf object to the older version.
     *
     * @param gsmRecord The newer GSM record.
     * @return The old GSM record object format.
     */
    public static com.craxiom.networksurvey.messaging.GsmRecord convertGsmRecord(GsmRecord gsmRecord)
    {
        final com.craxiom.networksurvey.messaging.GsmRecord.Builder builder = com.craxiom.networksurvey.messaging.GsmRecord.newBuilder();
        final GsmRecordData data = gsmRecord.getData();
        builder.setDeviceSerialNumber(data.getDeviceSerialNumber());
        builder.setDeviceTime(getEpochFromRfc3339(data.getDeviceTime()));
        builder.setLatitude(data.getLatitude());
        builder.setLongitude(data.getLongitude());
        builder.setAltitude(data.getAltitude());
        builder.setMissionId(data.getMissionId());
        builder.setRecordNumber(data.getRecordNumber());
        builder.setGroupNumber(data.getGroupNumber());
        builder.setDeviceName((data.getDeviceName()));

        if (data.hasMcc()) builder.setMcc(data.getMcc());
        if (data.hasMnc()) builder.setMnc(data.getMnc());
        if (data.hasLac()) builder.setLac(data.getLac());
        if (data.hasCi()) builder.setCi(data.getCi());
        if (data.hasArfcn()) builder.setArfcn(data.getArfcn());
        if (data.hasBsic()) builder.setBsic(data.getBsic());
        if (data.hasSignalStrength()) builder.setSignalStrength(data.getSignalStrength());
        if (data.hasTa()) builder.setTa(data.getTa());
        if (data.hasServingCell()) builder.setServingCell(data.getServingCell());
        builder.setProvider(data.getProvider());

        return builder.build();
    }

    /**
     * Converts the newer CDMA record protobuf object to the older version.
     *
     * @param cdmaRecord The newer CDMA record.
     * @return The old CDMA record object format.
     */
    public static com.craxiom.networksurvey.messaging.CdmaRecord convertCdmaRecord(CdmaRecord cdmaRecord)
    {
        final com.craxiom.networksurvey.messaging.CdmaRecord.Builder builder = com.craxiom.networksurvey.messaging.CdmaRecord.newBuilder();
        final CdmaRecordData data = cdmaRecord.getData();
        builder.setDeviceSerialNumber(data.getDeviceSerialNumber());
        builder.setDeviceTime(getEpochFromRfc3339(data.getDeviceTime()));
        builder.setLatitude(data.getLatitude());
        builder.setLongitude(data.getLongitude());
        builder.setAltitude(data.getAltitude());
        builder.setMissionId(data.getMissionId());
        builder.setRecordNumber(data.getRecordNumber());
        builder.setGroupNumber(data.getGroupNumber());
        builder.setDeviceName((data.getDeviceName()));

        if (data.hasSid()) builder.setSid(data.getSid());
        if (data.hasNid()) builder.setNid(data.getNid());
        if (data.hasZone()) builder.setZone(data.getZone());
        if (data.hasBsid()) builder.setBsid(data.getBsid());
        if (data.hasChannel()) builder.setChannel(data.getChannel());
        if (data.hasPnOffset()) builder.setPnOffset(data.getPnOffset());
        if (data.hasSignalStrength()) builder.setSignalStrength(data.getSignalStrength());
        if (data.hasEcio()) builder.setEcio(data.getEcio());
        if (data.hasServingCell()) builder.setServingCell(data.getServingCell());
        builder.setProvider(data.getProvider());

        return builder.build();
    }

    /**
     * Converts the newer UMTS record protobuf object to the older version.
     *
     * @param umtsRecord The newer UMTS record.
     * @return The old UMTS record object format.
     */
    public static com.craxiom.networksurvey.messaging.UmtsRecord convertUmtsRecord(UmtsRecord umtsRecord)
    {
        final com.craxiom.networksurvey.messaging.UmtsRecord.Builder builder = com.craxiom.networksurvey.messaging.UmtsRecord.newBuilder();
        final UmtsRecordData data = umtsRecord.getData();
        builder.setDeviceSerialNumber(data.getDeviceSerialNumber());
        builder.setDeviceTime(getEpochFromRfc3339(data.getDeviceTime()));
        builder.setLatitude(data.getLatitude());
        builder.setLongitude(data.getLongitude());
        builder.setAltitude(data.getAltitude());
        builder.setMissionId(data.getMissionId());
        builder.setRecordNumber(data.getRecordNumber());
        builder.setGroupNumber(data.getGroupNumber());
        builder.setDeviceName((data.getDeviceName()));

        if (data.hasMcc()) builder.setMcc(data.getMcc());
        if (data.hasMnc()) builder.setMnc(data.getMnc());
        if (data.hasLac()) builder.setLac(data.getLac());
        if (data.hasCid()) builder.setCi(data.getCid());
        if (data.hasUarfcn()) builder.setUarfcn(data.getUarfcn());
        if (data.hasPsc()) builder.setPsc(data.getPsc());
        if (data.hasRscp()) builder.setRscp(data.getRscp());
        if (data.hasSignalStrength()) builder.setSignalStrength(data.getSignalStrength());
        if (data.hasServingCell()) builder.setServingCell(data.getServingCell());
        builder.setProvider(data.getProvider());

        return builder.build();
    }

    /**
     * Converts the newer LTE record protobuf object to the older version.
     *
     * @param lteRecord The newer LTE record.
     * @return The old LTE record object format.
     */
    public static com.craxiom.networksurvey.messaging.LteRecord convertLteRecord(LteRecord lteRecord)
    {
        final com.craxiom.networksurvey.messaging.LteRecord.Builder builder = com.craxiom.networksurvey.messaging.LteRecord.newBuilder();
        final LteRecordData data = lteRecord.getData();
        builder.setDeviceSerialNumber(data.getDeviceSerialNumber());
        builder.setDeviceTime(getEpochFromRfc3339(data.getDeviceTime()));
        builder.setLatitude(data.getLatitude());
        builder.setLongitude(data.getLongitude());
        builder.setAltitude(data.getAltitude());
        builder.setMissionId(data.getMissionId());
        builder.setRecordNumber(data.getRecordNumber());
        builder.setGroupNumber(data.getGroupNumber());
        builder.setDeviceName((data.getDeviceName()));

        if (data.hasMcc()) builder.setMcc(data.getMcc());
        if (data.hasMnc()) builder.setMnc(data.getMnc());
        if (data.hasTac()) builder.setTac(data.getTac());
        if (data.hasEci()) builder.setCi(data.getEci());
        if (data.hasEarfcn()) builder.setEarfcn(data.getEarfcn());
        if (data.hasPci()) builder.setPci(data.getPci());
        if (data.hasRsrp()) builder.setRsrp(data.getRsrp());
        if (data.hasRsrq()) builder.setRsrq(data.getRsrq());
        if (data.hasTa()) builder.setTa(data.getTa());
        if (data.hasServingCell()) builder.setServingCell(data.getServingCell());
        builder.setLteBandwidth(LteBandwidth.forNumber(data.getLteBandwidth().getNumber())); // Same enums, just different packages
        builder.setProvider(data.getProvider());

        return builder.build();
    }
}
