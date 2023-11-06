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
        super(networkSurveyService, serviceLooper, NetworkSurveyConstants.CSV_LOG_DIRECTORY_NAME,
                NetworkSurveyConstants.CDR_FILE_NAME_PREFIX, false);
    }

    @Override
    String[] getHeaders()
    {
        return CdrEvent.getHeaders();
    }

    @Override
    String[] getHeaderComments()
    {
        return new String[]{"CSV Version=0.1.0"};
    }

    // Needs to be synchronized so we don't write two records at the same time... which I saw happen
    @Override
    public synchronized void onCdrEvent(CdrEvent record)
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
