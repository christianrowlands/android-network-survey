package com.craxiom.networksurvey.logging;

import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.ACCURACY;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.ALTITUDE;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.DEVICE_TIME;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.LATITUDE;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.LONGITUDE;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.MISSION_ID;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.NETWORK_REGISTRATION;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.RECORD_NUMBER;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.SIM_OPERATOR;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.SIM_STATE;
import static com.craxiom.networksurvey.constants.csv.PhoneStateCsvConstants.SPEED;

import android.os.Looper;

import com.craxiom.messaging.DeviceStatus;
import com.craxiom.messaging.PhoneState;
import com.craxiom.messaging.PhoneStateData;
import com.craxiom.messaging.phonestate.SimState;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.IDeviceStatusListener;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in the phone state change records and logging them to a CSV file.
 */
public class PhoneStateCsvLogger extends CsvRecordLogger implements IDeviceStatusListener
{
    private final JsonFormat.Printer jsonFormatter;

    public PhoneStateCsvLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.PHONESTATE_FILE_NAME_PREFIX, true);

        jsonFormatter = JsonFormat.printer().preservingProtoFieldNames().omittingInsignificantWhitespace();
    }

    @Override
    String[] getHeaders()
    {
        return new String[]{DEVICE_TIME, LATITUDE, LONGITUDE, ALTITUDE, SPEED, ACCURACY,
                MISSION_ID, RECORD_NUMBER,
                SIM_STATE, SIM_OPERATOR, NETWORK_REGISTRATION};
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.1.0"};
    }

    @Override
    public void onDeviceStatus(DeviceStatus deviceStatus)
    {
        // no-op
    }

    @Override
    public synchronized void onPhoneState(PhoneState record)
    {
        try
        {
            writeCsvRecord(convertToObjectArray(record), true);
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the Phone State record to the CSV file");
        }
    }

    /**
     * @return A String array that contains the Phone State record values that can be written out
     * as a CSV row.
     */
    private String[] convertToObjectArray(PhoneState record)
    {
        PhoneStateData data = record.getData();
        String networkRegistrationJson = new Gson().toJson(data.getNetworkRegistrationInfoList());

        String jsonMessage = this.jsonFormatter.print(data.getNetworkRegistrationInfoList());

        String simState = "";
        try
        {
            simState = readSimState(data);
        } catch (JsonProcessingException e)
        {
            // noop
        }

        return new String[]{
                data.getDeviceTime(),
                String.valueOf(data.getLatitude()),
                String.valueOf(data.getLongitude()),
                String.valueOf(data.getAltitude()),
                String.valueOf(data.getSpeed()),
                String.valueOf(data.getAccuracy()),
                data.getMissionId(),
                String.valueOf(data.getRecordNumber()),
                simState,
                data.getSimOperator(),
                networkRegistrationJson,
        };
    }

    /**
     * @param data The {@link PhoneStateData} from which to read the sim state value.
     * @return A String representation of the sim state enum, removing the leading and trailing double-quotes.
     * @throws JsonProcessingException If the {@link SimState} enum cannot be parsed into a String.
     */
    @Nullable
    private String readSimState(PhoneStateData data) throws JsonProcessingException
    {
        String simState = new ObjectMapper().writeValueAsString(data.getSimState());

        if (!Strings.isNullOrEmpty(simState))
        {
            simState = simState.replace("\"", "");
        }

        return simState;
    }
}
