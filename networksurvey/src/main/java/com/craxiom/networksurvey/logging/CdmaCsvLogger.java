package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.BSID;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.CHANNEL;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.ECIO;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.GROUP_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.NID;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.PN_OFFSET;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.PROVIDER;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.SERVING_CELL;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.SID;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.SIGNAL_STRENGTH;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.CdmaCsvConstants.ZONE;
import static com.craxiom.networksurvey.constants.csv.CellularCsvConstants.SLOT;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.DEVICE_SERIAL_NUMBER;
import static com.craxiom.networksurvey.constants.csv.CsvConstants.LOCATION_AGE;

import android.os.Looper;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.CdmaRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in CDMA survey records and logging them to a CSV file.
 */
public class CdmaCsvLogger extends CsvRecordLogger implements ICellularSurveyRecordListener
{
    public CdmaCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.CDMA_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER, GROUP_NUMBER,
                SID, NID, ZONE, BSID, CHANNEL, PN_OFFSET, SIGNAL_STRENGTH, ECIO, SERVING_CELL, PROVIDER, SLOT,
                DEVICE_SERIAL_NUMBER, LOCATION_AGE};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.3.0"};
    }

    @Override
    public synchronized void onCdmaSurveyRecord(CdmaRecord record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the CDMA record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the CDMA record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(CdmaRecord record)
    {
        CdmaRecordData data = record.getData();

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
                data.hasSid() ? String.valueOf(data.getSid().getValue()) : "",
                data.hasNid() ? String.valueOf(data.getNid().getValue()) : "",
                data.hasZone() ? String.valueOf(data.getZone().getValue()) : "",
                data.hasBsid() ? String.valueOf(data.getBsid().getValue()) : "",
                data.hasChannel() ? String.valueOf(data.getChannel().getValue()) : "",
                data.hasPnOffset() ? String.valueOf(data.getPnOffset().getValue()) : "",
                data.hasSignalStrength() ? String.valueOf(data.getSignalStrength().getValue()) : "",
                data.hasEcio() ? String.valueOf(data.getEcio().getValue()) : "",
                data.hasServingCell() ? String.valueOf(data.getServingCell().getValue()) : "",
                data.getProvider(),
                data.hasSlot() ? String.valueOf(data.getSlot().getValue()) : "",
                data.getDeviceSerialNumber(),
                data.getLocationAge() == 0 ? "" : String.valueOf(data.getLocationAge())
        };
    }
}
