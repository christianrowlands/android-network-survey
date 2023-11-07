package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CellularCsvConstants.SERVING_CELL;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.EARFCN;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.ECI;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.GROUP_NUMBER;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.LTE_BANDWIDTH;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.MCC;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.MNC;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.PCI;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.PROVIDER;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.RSRP;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.RSRQ;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.TA;
import static com.craxiom.networksurvey.constants.csv.LteCsvConstants.TAC;

import android.os.Looper;

import com.craxiom.messaging.LteBandwidth;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.LteRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in LTE survey records and logging them to a CSV file.
 */
public class LteCsvLogger extends CsvRecordLogger implements ICellularSurveyRecordListener
{
    public LteCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.LTE_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER, GROUP_NUMBER,
                MCC, MNC, TAC, ECI, EARFCN, PCI, RSRP, RSRQ, TA, SERVING_CELL, LTE_BANDWIDTH, PROVIDER};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.1.0"};
    }

    @Override
    public synchronized void onLteSurveyRecord(LteRecord record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the LTE record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the LTE record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(LteRecord record)
    {
        LteRecordData data = record.getData();

        LteBandwidth lteBandwidth = data.getLteBandwidth();

        return new String[]{
                data.getDeviceTime(),
                String.valueOf(data.getLatitude()),
                String.valueOf(data.getLongitude()),
                String.valueOf(data.getAltitude()),
                String.valueOf(data.getSpeed()),
                String.valueOf(data.getAccuracy()),
                data.getMissionId(),
                String.valueOf(data.getRecordNumber()),
                String.valueOf(data.getGroupNumber()),
                data.hasMcc() ? String.valueOf(data.getMcc().getValue()) : "",
                data.hasMnc() ? String.valueOf(data.getMnc().getValue()) : "",
                data.hasTac() ? String.valueOf(data.getTac().getValue()) : "",
                data.hasEci() ? String.valueOf(data.getEci().getValue()) : "",
                data.hasEarfcn() ? String.valueOf(data.getEarfcn().getValue()) : "",
                data.hasPci() ? String.valueOf(data.getPci().getValue()) : "",
                data.hasRsrp() ? String.valueOf(data.getRsrp().getValue()) : "",
                data.hasRsrq() ? String.valueOf(data.getRsrq().getValue()) : "",
                data.hasTa() ? String.valueOf(data.getTa().getValue()) : "",
                data.hasServingCell() ? String.valueOf(data.getServingCell().getValue()) : "",
                lteBandwidth == LteBandwidth.UNRECOGNIZED ? "" : lteBandwidth.name(),
                data.getProvider()};
    }
}
