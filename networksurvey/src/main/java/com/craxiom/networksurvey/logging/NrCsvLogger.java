package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CellularCsvConstants.SLOT;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.CSI_RSRP;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.CSI_RSRQ;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.CSI_SINR;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.GROUP_NUMBER;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.MCC;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.MNC;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.NARFCN;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.NCI;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.PCI;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.PROVIDER;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.SERVING_CELL;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.SS_RSRP;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.SS_RSRQ;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.SS_SINR;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.TA;
import static com.craxiom.networksurvey.constants.csv.NrCsvConstants.TAC;

import android.os.Looper;

import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.NrRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in 5G NR survey records and logging them to a CSV file.
 */
public class NrCsvLogger extends CsvRecordLogger implements ICellularSurveyRecordListener
{
    public NrCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.NR_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER, GROUP_NUMBER,
                MCC, MNC, TAC, NCI, NARFCN, PCI, SS_RSRP, SS_RSRQ, SS_SINR, CSI_RSRP, CSI_RSRQ, CSI_SINR, TA, SERVING_CELL, PROVIDER, SLOT,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.3.0"};
    }

    @Override
    public synchronized void onNrSurveyRecord(NrRecord record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the NR record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the NR record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(NrRecord record)
    {
        NrRecordData data = record.getData();

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
                data.hasTac() ? String.valueOf(data.getTac().getValue()) : "",
                data.hasNci() ? String.valueOf(data.getNci().getValue()) : "",
                data.hasNarfcn() ? String.valueOf(data.getNarfcn().getValue()) : "",
                data.hasPci() ? String.valueOf(data.getPci().getValue()) : "",
                data.hasSsRsrp() ? String.valueOf(data.getSsRsrp().getValue()) : "",
                data.hasSsRsrq() ? String.valueOf(data.getSsRsrq().getValue()) : "",
                data.hasSsSinr() ? String.valueOf(data.getSsSinr().getValue()) : "",
                data.hasCsiRsrp() ? String.valueOf(data.getCsiRsrp().getValue()) : "",
                data.hasCsiRsrq() ? String.valueOf(data.getCsiRsrq().getValue()) : "",
                data.hasCsiSinr() ? String.valueOf(data.getCsiSinr().getValue()) : "",
                data.hasTa() ? String.valueOf(data.getTa().getValue()) : "",
                data.hasServingCell() ? String.valueOf(data.getServingCell().getValue()) : "",
                data.getProvider(),
                data.hasSlot() ? String.valueOf(data.getSlot().getValue()) : "",
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge())
        };
    }
}
