package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.WifiClientCsvConstants.FRAME_TYPES;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.BSSID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.CHANNEL;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.FREQ_MHZ;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SIGNAL_STRENGTH;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SNR;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SOURCE_ADDRESS;

import com.craxiom.messaging.WifiClientRecord;
import com.craxiom.messaging.WifiClientRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.csv.WifiClientCsvConstants;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Logs the Wi-Fi clients an NS Scout heard to a {@code craxiom-wifi-client-} CSV file, one row per
 * client per window. The columns are the NS Messaging {@code WifiClientRecord} fields.
 */
public class WifiClientCsvLogger extends ScoutClientCsvLogger
{
    public WifiClientCsvLogger(NetworkSurveyService networkSurveyService)
    {
        super(networkSurveyService, NetworkSurveyConstants.WIFI_CLIENT_FILE_NAME_PREFIX);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY, MISSION_ID, RECORD_NUMBER,
                SOURCE_ADDRESS, BSSID, CHANNEL, FREQ_MHZ, SIGNAL_STRENGTH, SNR, FRAME_TYPES,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{WifiClientCsvConstants.CSV_VERSION_COMMENT};
    }

    @Override
    public void onWifiClientRecords(List<WifiClientRecord> clientRecords)
    {
        writeRows(clientRecords, this::toRow);
    }

    String[] toRow(WifiClientRecord record)
    {
        final WifiClientRecordData data = record.getData();
        return row(commonValues(data.getDeviceTime(), data.getLatitude(), data.getLongitude(), data.getAltitude(),
                        data.getSpeed(), data.getAccuracy(), data.getMissionId(), data.getRecordNumber()),
                data.getSourceAddress(),
                data.getBssid(),
                text(data.hasChannel(), data.getChannel()),
                text(data.hasFrequencyMhz(), data.getFrequencyMhz()),
                text(data.hasSignalStrength(), data.getSignalStrength()),
                text(data.hasSnr(), data.getSnr()),
                data.getFrameTypesList().stream().map(Enum::name).collect(Collectors.joining(";")),
                data.getDeviceSerialNumber(),
                locationAge(data.getLocationAge()));
    }
}
