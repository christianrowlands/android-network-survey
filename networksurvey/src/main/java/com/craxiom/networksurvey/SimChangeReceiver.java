package com.craxiom.networksurvey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.craxiom.networksurvey.services.NetworkSurveyService;

import timber.log.Timber;

/**
 * Handles receiving SIM state changes from the Android OS and publishing them to in-process
 * observers so that the {@link NetworkSurveyService} can update its CDR logging state when CDR
 * logging is enabled.
 */
public class SimChangeReceiver extends BroadcastReceiver
{
    public static final String SIM_CHANGED_INTENT = "SimChangedIntent";

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final MutableLiveData<Long> SIM_CHANGE_EVENTS = new MutableLiveData<>();

    private static long simChangeEventSequence;

    /**
     * Returns the shared SIM-change event stream. Values are monotonically increasing event
     * sequences; subscribers must ignore values at or below the sequence captured before they
     * subscribe so that LiveData replay does not deliver pre-subscription events.
     */
    public static LiveData<Long> getSimChangeEvents()
    {
        return SIM_CHANGE_EVENTS;
    }

    /**
     * Returns the latest accepted SIM-change event sequence for use as an observer baseline.
     */
    public static synchronized long getCurrentSimChangeEventSequence()
    {
        return simChangeEventSequence;
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (null == intent) return;

        if (!"android.intent.action.SIM_STATE_CHANGED".equals(intent.getAction())) return;

        Timber.d("Received the SIM changed intent action");

        String state = "";
        Bundle extras = intent.getExtras();
        if (extras != null)
        {
            state = extras.getString("ss");
        }
        Timber.i("SIM State Change Detected %s", state);

        publishSimChangeEvent();
    }

    private static synchronized void publishSimChangeEvent()
    {
        final long eventSequence = ++simChangeEventSequence;
        MAIN_HANDLER.post(() -> SIM_CHANGE_EVENTS.setValue(eventSequence));
    }
}
