package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.ADDRESS_TYPE;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.CHANNEL;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.COMPANY_ID;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.DESTINATION_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.DEVICE_CLASS;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.OTA_DEVICE_NAME;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.SERVICE_UUIDS;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.SIGNAL_STRENGTH;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.SOURCE_ADDRESS;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.SUPPORTED_TECHNOLOGIES;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.TECHNOLOGY;
import static com.craxiom.networksurvey.constants.csv.BluetoothCsvConstants.TX_POWER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;

import android.os.Looper;

import com.craxiom.messaging.BluetoothRecord;
import com.craxiom.messaging.BluetoothRecordData;
import com.craxiom.messaging.bluetooth.AddressType;
import com.craxiom.messaging.bluetooth.SupportedTechnologies;
import com.craxiom.messaging.bluetooth.Technology;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;
import java.util.List;

import timber.log.Timber;

/**
 * Responsible for taking in Bluetooth survey records and logging them to a CSV file.
 */
public class BluetoothCsvLogger extends CsvRecordLogger implements IBluetoothSurveyRecordListener
{
    public BluetoothCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.BLUETOOTH_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER,
                SOURCE_ADDRESS, DESTINATION_ADDRESS, SIGNAL_STRENGTH, TX_POWER, TECHNOLOGY,
                SUPPORTED_TECHNOLOGIES, OTA_DEVICE_NAME, CHANNEL,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE, ADDRESS_TYPE, DEVICE_CLASS, SERVICE_UUIDS, COMPANY_ID};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.3.0"};
    }

    @Override
    public synchronized void onBluetoothSurveyRecord(BluetoothRecord bluetoothRecord)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(bluetoothRecord), false);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the Bluetooth record to the CSV file");
        }
    }

    @Override
    public synchronized void onBluetoothSurveyRecords(List<BluetoothRecord> bluetoothRecords)
    {
        bluetoothRecords.forEach(record -> {
            try
            {
                writeCsvRecord(convertToObjectArray(record), false);
            } catch (IOException e)
            {
                Timber.e(e, "Could not log the Bluetooth record to the CSV file");
            }
        });

        try
        {
            printer.flush();
        } catch (IOException e)
        {
            Timber.e(e, "Could not flush the Bluetooth records to the CSV file");
        }
    }

    /**
     * @return A String array that contains the LTE record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(BluetoothRecord record)
    {
        BluetoothRecordData data = record.getData();

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
                data.hasSignalStrength() ? String.valueOf(data.getSignalStrength().getValue()) : "",
                data.hasTxPower() ? String.valueOf(data.getTxPower().getValue()) : "",
                data.getTechnology() == Technology.UNRECOGNIZED ? "" : data.getTechnology().name(),
                data.getSupportedTechnologies() == SupportedTechnologies.UNRECOGNIZED ? "" : data.getSupportedTechnologies().name(),
                data.getOtaDeviceName(),
                data.hasChannel() ? String.valueOf(data.getChannel().getValue()) : "",
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge()),
                data.getAddressType() == AddressType.UNRECOGNIZED ? "" : data.getAddressType().name(),
                data.getDeviceClass(),
                // Convert the list of service UUIDs to a semicolon-separated string
                String.join(";", data.getServiceUuidsList()),
                data.getCompanyId(),
        };
    }
}
