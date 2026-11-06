package com.craxiom.networksurvey.logging;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IWifiClientSurveyRecordListener;
import com.craxiom.messaging.WifiClientRecord;
import com.craxiom.messaging.WifiProbeRequestRecord;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;

import java.util.List;

/**
 * The shared part of the Wi-Fi clients and probe request CSV loggers: the columns every row starts
 * with (time, place, Mission ID, record number) and how optional values are written (empty when not
 * reported).
 * <p>
 * Each logger is an {@link IWifiClientSurveyRecordListener} that keeps only its own kind of record.
 */
abstract class ScoutClientCsvLogger extends CsvRecordLogger implements IWifiClientSurveyRecordListener
{
    ScoutClientCsvLogger(NetworkSurveyService networkSurveyService, String fileNamePrefix)
    {
        super(networkSurveyService, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME, fileNamePrefix, true);
    }

    @Override
    public void onWifiClientRecords(List<WifiClientRecord> clientRecords)
    {
    }

    @Override
    public void onWifiProbeRequestRecords(List<WifiProbeRequestRecord> probeRecords)
    {
    }

    /**
     * @return The columns every row starts with, formatted like the other NS CSV files.
     */
    String[] commonValues(String deviceTime, double latitude, double longitude, float altitude, float speed,
                          int accuracy, String missionId, int recordNumber)
    {
        return new String[]{
                deviceTime,
                trimToSixDecimalPlaces(latitude),
                trimToSixDecimalPlaces(longitude),
                roundToTwoDecimalPlaces(altitude),
                roundToTwoDecimalPlaces(speed),
                roundToTwoDecimalPlaces(accuracy),
                missionId,
                String.valueOf(recordNumber)};
    }

    /**
     * @return The common columns followed by the rest, as one row.
     */
    static String[] row(String[] common, String... rest)
    {
        final String[] all = new String[common.length + rest.length];
        System.arraycopy(common, 0, all, 0, common.length);
        System.arraycopy(rest, 0, all, common.length, rest.length);
        return all;
    }

    static String text(boolean present, Int32Value value)
    {
        return present ? String.valueOf(value.getValue()) : "";
    }

    static String text(boolean present, FloatValue value)
    {
        return present ? String.valueOf(value.getValue()) : "";
    }

    /**
     * @return The location age in milliseconds, empty when unknown (zero).
     */
    static String locationAge(int locationAge)
    {
        return locationAge == 0 ? "" : String.valueOf(locationAge);
    }
}
