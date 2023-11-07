package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.AGC_DB;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.ALTITUDE_STD_DEV_M;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.CARRIER_FREQ_HZ;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.CLOCK_OFFSET;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.CN0_DB_HZ;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.CONSTELLATION;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.GROUP_NUMBER;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.HDOP;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.LATITUDE_STD_DEV_M;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.LONGITUDE_STD_DEV_M;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.SPACE_VEHICLE_ID;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.SPEED;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.UNDULATION_M;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.USED_IN_SOLUTION;
import static com.craxiom.networksurvey.constants.csv.GnssCsvConstants.VDOP;

import android.os.Looper;

import com.craxiom.messaging.GnssRecord;
import com.craxiom.messaging.GnssRecordData;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IGnssSurveyRecordListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in GNSS survey records and logging them to a CSV file.
 */
public class GnssCsvLogger extends CsvRecordLogger implements IGnssSurveyRecordListener
{
    public GnssCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.GNSS_FILE_NAME_PREFIX, true);
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER,
                CONSTELLATION, SPACE_VEHICLE_ID, CARRIER_FREQ_HZ, CLOCK_OFFSET, USED_IN_SOLUTION,
                UNDULATION_M, LATITUDE_STD_DEV_M, LONGITUDE_STD_DEV_M, ALTITUDE_STD_DEV_M, AGC_DB,
                CN0_DB_HZ, HDOP, VDOP, GROUP_NUMBER};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.1.0"};
    }

    @Override
    public void onGnssSurveyRecord(GnssRecord record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the GNSS record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the GNSS record values that can be written out as a CSV
     * row.
     */
    private String[] convertToObjectArray(GnssRecord record)
    {
        GnssRecordData data = record.getData();

        return new String[]{
                data.getDeviceTime(),
                String.valueOf(data.getLatitude()),
                String.valueOf(data.getLongitude()),
                String.valueOf(data.getAltitude()),
                String.valueOf(data.getSpeed()),
                String.valueOf(data.getAccuracy()),
                data.getMissionId(),
                String.valueOf(data.getRecordNumber()),
                data.getConstellation().toString(),
                data.hasSpaceVehicleId() ? String.valueOf(data.getSpaceVehicleId().getValue()) : "",
                data.hasCarrierFreqHz() ? String.valueOf(data.getCarrierFreqHz().getValue()) : "",
                data.hasClockOffset() ? String.valueOf(data.getClockOffset().getValue()) : "",
                data.hasUsedInSolution() ? String.valueOf(data.getUsedInSolution().getValue()) : "",
                data.hasUndulationM() ? String.valueOf(data.getUndulationM().getValue()) : "",
                data.hasLatitudeStdDevM() ? String.valueOf(data.getLatitudeStdDevM().getValue()) : "",
                data.hasLongitudeStdDevM() ? String.valueOf(data.getLongitudeStdDevM().getValue()) : "",
                data.hasAltitudeStdDevM() ? String.valueOf(data.getAltitudeStdDevM().getValue()) : "",
                data.hasAgcDb() ? String.valueOf(data.getAgcDb().getValue()) : "",
                data.hasCn0DbHz() ? String.valueOf(data.getCn0DbHz().getValue()) : "",
                data.hasHdop() ? String.valueOf(data.getHdop().getValue()) : "",
                data.hasVdop() ? String.valueOf(data.getVdop().getValue()) : ""
        };
    }
}
