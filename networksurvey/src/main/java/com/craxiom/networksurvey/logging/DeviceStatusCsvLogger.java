package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.BATTERY_LEVEL_PERCENT;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.GNSS_ACCURACY;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.GNSS_ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.GNSS_LATITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.GNSS_LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.NETWORK_ACCURACY;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.NETWORK_ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.NETWORK_LATITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.NETWORK_LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.DeviceStatusCsvConstants.SPEED;

import android.os.Looper;

import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.DeviceStatusData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in device status records and logging them to a CSV file.
 */
public class DeviceStatusCsvLogger extends CsvRecordLogger implements IDeviceStatusListener
{
    public DeviceStatusCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.DEVICESTATUS_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                BATTERY_LEVEL_PERCENT, GNSS_LATITUDE, GNSS_LONGITUDE, GNSS_ALTITUDE, GNSS_ACCURACY,
                NETWORK_LATITUDE, NETWORK_LONGITUDE, NETWORK_ALTITUDE, NETWORK_ACCURACY,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.4.0"};
    }

    @Override
    public void onDeviceStatus(DeviceStatus record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the Device Status record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the Device Status record values that can be written out
     * as a CSV row.
     */
    private String[] convertToObjectArray(DeviceStatus record)
    {
        DeviceStatusData data = record.getData();

        // Yes, I know that 0.0 is a valid location, but I am filtering on 0.0 anyway
        double gnssLatitude = data.getGnssLatitude();
        double gnssLongitude = data.getGnssLongitude();
        boolean hasGnssLocation = gnssLatitude != 0d && gnssLongitude != 0d;

        double networkLatitude = data.getNetworkLatitude();
        double networkLongitude = data.getNetworkLongitude();
        boolean hasNetworkLocation = networkLatitude != 0d && networkLongitude != 0d;

        return new String[]{
                data.getDeviceTime(),
                trimToSixDecimalPlaces(data.getLatitude()),
                trimToSixDecimalPlaces(data.getLongitude()),
                roundToTwoDecimalPlaces(data.getAltitude()),
                roundToTwoDecimalPlaces(data.getSpeed()),
                roundToTwoDecimalPlaces(data.getAccuracy()),
                data.hasBatteryLevelPercent() ? String.valueOf(data.getBatteryLevelPercent().getValue()) : "",

                hasGnssLocation ? trimToSixDecimalPlaces(gnssLatitude) : "",
                hasGnssLocation ? trimToSixDecimalPlaces(gnssLongitude) : "",
                hasGnssLocation ? roundToTwoDecimalPlaces(data.getGnssAltitude()) : "",
                hasGnssLocation ? roundToTwoDecimalPlaces(data.getGnssAccuracy()) : "",

                hasNetworkLocation ? trimToSixDecimalPlaces(networkLatitude) : "",
                hasNetworkLocation ? trimToSixDecimalPlaces(networkLongitude) : "",
                hasNetworkLocation ? roundToTwoDecimalPlaces(data.getNetworkAltitude()) : "",
                hasNetworkLocation ? roundToTwoDecimalPlaces(data.getNetworkAccuracy()) : "",
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge())
        };
    }
}
