package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CellularCsvConstants.SLOT;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.ARFCN;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.BSIC;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.CI;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.GROUP_NUMBER;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.LAC;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.MCC;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.MNC;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.PROVIDER;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.SERVING_CELL;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.SIGNAL_STRENGTH;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.GsmCsvConstants.TA;

import android.os.Looper;

import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in GSM survey records and logging them to a CSV file.
 */
public class GsmCsvLogger extends CsvRecordLogger implements ICellularSurveyRecordListener
{
    public GsmCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.GSM_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER, GROUP_NUMBER,
                MCC, MNC, LAC, CI, ARFCN, BSIC, SIGNAL_STRENGTH, TA, SERVING_CELL, PROVIDER, SLOT,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.3.0"};
    }

    @Override
    public synchronized void onGsmSurveyRecord(GsmRecord record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the GSM record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the GSM record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(GsmRecord record)
    {
        GsmRecordData data = record.getData();

        return new String[]{
                data.getDeviceTime(),
                trimToSixDecimalPlaces(data.getLatitude()),
                trimToSixDecimalPlaces(data.getLongitude()),
                roundToTwoDecimalPlaces(data.getAltitude()),
                roundToTwoDecimalPlaces(data.getSpeed()),
                roundToTwoDecimalPlaces(data.getAccuracy()),
                data.getMissionId(),
                String.valueOf(data.getRecordNumber()),
                String.valueOf(data.getGroupNumber()),
                data.hasMcc() ? String.valueOf(data.getMcc().getValue()) : "",
                data.hasMnc() ? String.valueOf(data.getMnc().getValue()) : "",
                data.hasLac() ? String.valueOf(data.getLac().getValue()) : "",
                data.hasCi() ? String.valueOf(data.getCi().getValue()) : "",
                data.hasArfcn() ? String.valueOf(data.getArfcn().getValue()) : "",
                data.hasBsic() ? String.valueOf(data.getBsic().getValue()) : "",
                data.hasSignalStrength() ? String.valueOf(data.getSignalStrength().getValue()) : "",
                data.hasTa() ? String.valueOf(data.getTa().getValue()) : "",
                data.hasServingCell() ? String.valueOf(data.getServingCell().getValue()) : "",
                data.getProvider(),
                data.hasSlot() ? String.valueOf(data.getSlot().getValue()) : "",
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge())
        };
    }
}
