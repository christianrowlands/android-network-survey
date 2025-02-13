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

import android.os.Looper;

import com.craxiom.messaging.WifiBeaconRecordData;
import com.craxiom.messaging.wifi.CipherSuite;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IWifiSurveyRecordListener;
import com.craxiom.networksurvey.model.WifiRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import timber.log.Timber;

/**
 * Responsible for taking in Wi-Fi survey records and logging them to a CSV file.
 */
public class WifiCsvLogger extends CsvRecordLogger implements IWifiSurveyRecordListener
{
    public WifiCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
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
    public synchronized void onWifiBeaconSurveyRecords(List<WifiRecordWrapper> wifiBeaconRecords)
    {
        wifiBeaconRecords.forEach(wrapper -> {
            try
            {
                writeCsvRecord(convertToObjectArray(wrapper), false);
            } catch (IOException e)
            {
                Timber.e(e, "Could not log the Wi-Fi record to the CSV file");
            }
        });

        try
        {
            printer.flush();
        } catch (IOException e)
        {
            Timber.e(e, "Could not flush the Wi-Fi records to the CSV file");
        }
    }

    /**
     * @return A String array that contains the Wi-Fi record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(WifiRecordWrapper wrapper)
    {
        WifiBeaconRecordData data = wrapper.getWifiBeaconRecord().getData();

        final List<CipherSuite> cipherSuitesList = data.getCipherSuitesList();
        String cipherSuites = "";
        if (!cipherSuitesList.isEmpty())
        {
            cipherSuites = cipherSuitesList.stream().map(Enum::toString)
                    .collect(Collectors.joining(";"));
        }

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
                "", // Service Set Type, not supported by NS
                data.getSsid(),
                "", // Supported Rates, not supported by NS
                "", // Extended Supported Rates, not supported by NS
                cipherSuites,
                "", // AKM Suites, not supported by NS
                data.getEncryptionType().toString(),
                data.hasWps() ? String.valueOf(data.getWps().getValue()) : "",
                data.hasChannel() ? String.valueOf(data.getChannel().getValue()) : "",
                data.hasFrequencyMhz() ? String.valueOf(data.getFrequencyMhz().getValue()) : "",
                data.hasSignalStrength() ? String.valueOf(data.getSignalStrength().getValue()) : "",
                data.hasSnr() ? String.valueOf(data.getSnr().getValue()) : "",
                "", // Node Type, not supported by NS
                data.getStandard().toString(),
                data.hasPasspoint() ? String.valueOf(data.getPasspoint().getValue()) : "",
                data.getBandwidth().toString(),
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge())
        };
    }
}
