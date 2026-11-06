package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.AKM_SUITES;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.BANDWIDTH;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.BEACON_INTERVAL;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.BSSID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.CHANNEL;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.CIPHER_SUITES;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.DESTINATION_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.ENCRYPTION_TYPE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.EXT_SUPPORTED_RATES;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.FREQ_MHZ;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.NODE_TYPE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.PASSPOINT;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SERVICE_SET_TYPE;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SIGNAL_STRENGTH;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SNR;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SOURCE_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SSID;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.STANDARD;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.SUPPORTED_RATES;
import static com.craxiom.networksurvey.constants.csv.WifiCsvConstants.WPA;

import com.craxiom.messaging.WifiBeaconRecordData;
import com.craxiom.messaging.wifi.NodeType;
import com.craxiom.messaging.wifi.ServiceSetType;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible for taking in Wi-Fi survey records and logging them to a CSV file.
 * <p>
 * The phone's own scans leave the service set type, rates, AKM suites, beacon interval, SNR and
 * node type blank; records from an NS Scout fill them.
 */
public class WifiCsvLogger extends CsvRecordLogger implements IWifiSurveyRecordListener
{
    public WifiCsvLogger(NetworkSurveyService networkSurveyService)
    {
        super(networkSurveyService, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.WIFI_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER,
                SOURCE_ADDRESS, DESTINATION_ADDRESS, BSSID, BEACON_INTERVAL, SERVICE_SET_TYPE, SSID,
                SUPPORTED_RATES, EXT_SUPPORTED_RATES, CIPHER_SUITES, AKM_SUITES, ENCRYPTION_TYPE,
                WPA, CHANNEL, FREQ_MHZ, SIGNAL_STRENGTH, SNR, NODE_TYPE, STANDARD, PASSPOINT, BANDWIDTH,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.4.0"};
    }

    @Override
    public void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        writeRows(wifiBeaconRecords, this::convertToObjectArray);
    }

    /**
     * @return A String array that contains the Wi-Fi record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(WifiRecordWrapper wrapper)
    {
        WifiBeaconRecordData data = wrapper.getWifiBeaconRecord().getData();

        final String cipherSuites = joinNames(data.getCipherSuitesList());
        final String akmSuites = joinNames(data.getAkmSuitesList());
        final ServiceSetType serviceSetType = data.getServiceSetType();
        final NodeType nodeType = data.getNodeType();

        return new String[]{
                data.getDeviceTime(),
                trimToSixDecimalPlaces(data.getLatitude()),
                trimToSixDecimalPlaces(data.getLongitude()),
                roundToTwoDecimalPlaces(data.getAltitude()),
                roundToTwoDecimalPlaces(data.getSpeed()),
                roundToTwoDecimalPlaces(data.getAccuracy()),
                data.getMissionId(),
                String.valueOf(data.getRecordNumber()),
                data.getSourceAddress(),
                data.getDestinationAddress(),
                data.getBssid(),
                data.hasBeaconInterval() ? String.valueOf(data.getBeaconInterval().getValue()) : "",
                serviceSetType == ServiceSetType.UNKNOWN || serviceSetType == ServiceSetType.UNRECOGNIZED ? "" : serviceSetType.name(),
                data.getSsid(),
                data.getSupportedRates(),
                data.getExtendedSupportedRates(),
                cipherSuites,
                akmSuites,
                data.getEncryptionType().toString(),
                data.hasWps() ? String.valueOf(data.getWps().getValue()) : "",
                data.hasChannel() ? String.valueOf(data.getChannel().getValue()) : "",
                data.hasFrequencyMhz() ? String.valueOf(data.getFrequencyMhz().getValue()) : "",
                data.hasSignalStrength() ? String.valueOf(data.getSignalStrength().getValue()) : "",
                data.hasSnr() ? String.valueOf(data.getSnr().getValue()) : "",
                nodeType == NodeType.UNKNOWN || nodeType == NodeType.UNRECOGNIZED ? "" : nodeType.name(),
                data.getStandard().toString(),
                data.hasPasspoint() ? String.valueOf(data.getPasspoint().getValue()) : "",
                data.getBandwidth().toString(),
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge())
        };
    }

    /**
     * @return The enum names joined with ";", empty for none.
     */
    private static String joinNames(List<? extends Enum<?>> values)
    {
        return values.stream().map(Enum::toString).collect(Collectors.joining(";"));
    }
}
