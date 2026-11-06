package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.BSSID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.CHANNEL;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.DESTINATION_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.FREQ_MHZ;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.NODE_TYPE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SIGNAL_STRENGTH;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SNR;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SOURCE_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SSID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.STANDARD;

import com.craxiom.messaging.WifiProbeRequestRecord;
import com.craxiom.messaging.WifiProbeRequestRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.csv.WifiClientCsvConstants;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.util.List;

/**
 * Logs the probe requests an NS Scout heard to a {@code craxiom-wifi-probe-} CSV file, one row per
 * client and requested network name per window. The columns are the NS Messaging
 * {@code WifiProbeRequestRecord} fields.
 */
public class WifiProbeCsvLogger extends ScoutClientCsvLogger
{
    public WifiProbeCsvLogger(NetworkSurveyService networkSurveyService)
    {
        super(networkSurveyService, NetworkSurveyConstants.WIFI_PROBE_FILE_NAME_PREFIX);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY, MISSION_ID, RECORD_NUMBER,
                SOURCE_ADDRESS, DESTINATION_ADDRESS, BSSID, SSID, CHANNEL, FREQ_MHZ, SIGNAL_STRENGTH, SNR, NODE_TYPE, STANDARD,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{WifiClientCsvConstants.CSV_VERSION_COMMENT};
    }

    @Override
    public void onWifiProbeRequestRecords(List<WifiProbeRequestRecord> probeRecords)
    {
        writeRows(probeRecords, this::toRow);
    }

    String[] toRow(WifiProbeRequestRecord record)
    {
        final WifiProbeRequestRecordData data = record.getData();
        return row(commonValues(data.getDeviceTime(), data.getLatitude(), data.getLongitude(), data.getAltitude(),
                        data.getSpeed(), data.getAccuracy(), data.getMissionId(), data.getRecordNumber()),
                data.getSourceAddress(),
                data.getDestinationAddress(),
                data.getBssid(),
                data.getSsid(),
                text(data.hasChannel(), data.getChannel()),
                text(data.hasFrequencyMhz(), data.getFrequencyMhz()),
                text(data.hasSignalStrength(), data.getSignalStrength()),
                text(data.hasSnr(), data.getSnr()),
                data.getNodeType().toString(),
                data.getStandard().toString(),
                data.getDeviceSerialNumber(),
                locationAge(data.getLocationAge()));
    }
}
