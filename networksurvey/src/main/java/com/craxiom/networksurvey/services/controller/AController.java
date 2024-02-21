package com.craxiom.networksurvey.services.controller;

import com.craxiom.networksurvey.services.NetworkSurveyService;

import java.util.concurrent.ExecutorService;

import timber.log.Timber;

/**
 * A base class for all of the protocol controllers that the Network Survey Service uses to control
 * the various survey protocols.
 */
public class AController
{
    protected NetworkSurveyService surveyService;
    protected ExecutorService executorService;

    public AController(NetworkSurveyService surveyService, ExecutorService executorService)
    {
        this.surveyService = surveyService;
        this.executorService = executorService;
    }

    public void onDestroy()
    {
        surveyService = null;
        executorService = null;
    }

    /**
     * Wraps the execute command for the executor service in a try catch to prevent the app from crashing if something
     * goes wrong with submitting the runnable. The most common crash I am seeing seems to be from the executor service
     * shutting down but some scan results are coming in. Hopefully that is the only case because otherwise we are
     * losing some survey results.
     *
     * @param runnable The runnable to execute on the executor service.
     */
    protected void execute(Runnable runnable)
    {
        try
        {
            executorService.execute(runnable);
        } catch (Throwable t)
        {
            Timber.w(t, "Could not submit to the executor service");
        }
    }
}
