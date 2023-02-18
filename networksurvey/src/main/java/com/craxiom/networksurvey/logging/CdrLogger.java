package com.craxiom.networksurvey.logging;

import android.os.Looper;

import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.listeners.ICdrEventListener;
import com.craxiom.networksurvey.model.CdrEvent;
import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.io.IOException;

import timber.log.Timber;

/**
 * Responsible for taking in CDR Events and logging them to a CSV file.
 *
 * @since 1.11
 */
public class CdrLogger extends CsvRecordLogger implements ICdrEventListener
{
    public CdrLogger(NetworkSurveyService networkSurveyService, Looper serviceLooper)
    {
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.LOG_DIRECTORY_NAME, NetworkSurveyConstants.CDR_FILE_NAME_PREFIX);
    }

    @Override
    String[] getHeaders()
    {
        return CdrEvent.getHeaders();
    }

    @Override
    public void onCdrEvent(CdrEvent record)
    {
        try
        {
            printer.printRecord((Object[]) record.getCsvRowArray());
            printer.flush();
        } catch (IOException e)
        {
            Timber.e(e, "Could not log the CdrEvent to the CSV log file");
        }
    }
}
