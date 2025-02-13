package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CellularCsvConstants.SLOT;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.CID;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.ECNO;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.GROUP_NUMBER;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.LAC;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.MCC;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.MNC;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.PROVIDER;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.PSC;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.RSCP;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.SERVING_CELL;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.SIGNAL_STRENGTH;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.UmtsCsvConstants.UARFCN;

import android.os.Looper;

import com.craxiom.messaging.UmtsRecord;
import com.craxiom.messaging.UmtsRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in UMTS survey records and logging them to a CSV file.
 */
public class UmtsCsvLogger extends CsvRecordLogger implements ICellularSurveyRecordListener
{
    public UmtsCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.UMTS_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER, GROUP_NUMBER,
                MCC, MNC, LAC, CID, UARFCN, PSC, RSCP, ECNO, SIGNAL_STRENGTH, SERVING_CELL, PROVIDER, SLOT,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.3.0"};
    }

    @Override
    public synchronized void onUmtsSurveyRecord(UmtsRecord record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the UMTS record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the UMTS record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(UmtsRecord record)
    {
        UmtsRecordData data = record.getData();

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
                data.hasCid() ? String.valueOf(data.getCid().getValue()) : "",
                data.hasUarfcn() ? String.valueOf(data.getUarfcn().getValue()) : "",
                data.hasPsc() ? String.valueOf(data.getPsc().getValue()) : "",
                data.hasRscp() ? String.valueOf(data.getRscp().getValue()) : "",
                data.hasEcno() ? String.valueOf(data.getEcno().getValue()) : "",
                data.hasSignalStrength() ? String.valueOf(data.getSignalStrength().getValue()) : "",
                data.hasServingCell() ? String.valueOf(data.getServingCell().getValue()) : "",
                data.getProvider(),
                data.hasSlot() ? String.valueOf(data.getSlot().getValue()) : "",
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge())
        };
    }
}
